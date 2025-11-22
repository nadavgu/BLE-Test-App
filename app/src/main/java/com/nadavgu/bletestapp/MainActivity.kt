package com.nadavgu.bletestapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import no.nordicsemi.android.support.v18.scanner.ScanResult
import java.util.UUID
import androidx.core.util.isNotEmpty
import androidx.core.util.size

class MainActivity : AppCompatActivity(), BleScannerController.Listener, BleGattServerController.Listener, NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val SORT_DEBOUNCE_MS = 1_500L // Re-sort every 1.5 seconds
        private const val RSSI_SMOOTHING_ALPHA = 0.3 // Exponential smoothing factor (0.0-1.0)
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var contentFrame: FrameLayout

    // Scan view components
    private lateinit var scanView: View
    private lateinit var statusText: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var emptyStateCard: MaterialCardView
    private lateinit var toggleScanButton: MaterialButton
    private lateinit var recyclerView: RecyclerView

    // GATT Server view components
    private lateinit var gattServerView: View
    private lateinit var gattServerStatusText: TextView
    private lateinit var gattServerProgressIndicator: CircularProgressIndicator
    private lateinit var gattServerAddressText: TextView
    private lateinit var gattServerConnectedClientsText: TextView
    private lateinit var gattServerDataReceivedText: TextView
    private lateinit var gattServerUuidInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var gattServerUuidLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var gattServerManufacturerIdInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var gattServerManufacturerIdLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var gattServerManufacturerDataInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var gattServerManufacturerDataLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var toggleGattServerButton: MaterialButton

    private val scanResults = linkedMapOf<String, ScannedDevice>()
    private val resultsAdapter = ScanResultAdapter()

    // Track smoothed RSSI values for each device
    private val smoothedRssiMap = mutableMapOf<String, Double>()

    // Track current device order to preserve it when not sorting
    private var currentDeviceOrder = listOf<ScannedDevice>()

    private lateinit var bleRequirements: BleRequirements
    private lateinit var scannerController: BleScannerController
    private lateinit var gattServerController: BleGattServerController

    private var receivedDataHistory = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSort = false
    private val sortRunnable = Runnable {
        performSort()
        pendingSort = false
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            val allGranted = grantResults.values.all { it }
            if (allGranted) {
                ensureBluetoothEnabledAndScan()
            } else {
                showPermissionDenied()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bleRequirements.isBluetoothEnabled()) {
                startBleScan()
            } else {
                showBluetoothRequired()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bleRequirements = BleRequirements(this)
        scannerController = BleScannerController(this, bleRequirements)
        gattServerController = BleGattServerController(this, this)
        bindViews()
        configureToolbar()
        setupViews()
        showScanView()
        updateUiForScanState()
        updateGattServerUi()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        stopBleScan()
        gattServerController.stopServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up handler to prevent leaks
        handler.removeCallbacks(sortRunnable)
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.mainToolbar)
        contentFrame = findViewById(R.id.contentFrame)
    }

    private fun setupViews() {
        // Inflate scan view
        scanView = layoutInflater.inflate(R.layout.view_scan, contentFrame, false)
        statusText = scanView.findViewById(R.id.scanStatusText)
        progressIndicator = scanView.findViewById(R.id.scanProgressIndicator)
        emptyStateCard = scanView.findViewById(R.id.emptyStateCard)
        toggleScanButton = scanView.findViewById(R.id.toggleScanButton)
        recyclerView = scanView.findViewById(R.id.scanResultsRecyclerView)

        configureRecyclerView()
        toggleScanButton.setOnClickListener {
            if (scannerController.isScanning) {
                stopBleScan()
            } else {
                onStartScanClicked()
            }
        }

        // Inflate GATT server view
        gattServerView = layoutInflater.inflate(R.layout.view_gatt_server, contentFrame, false)
        gattServerStatusText = gattServerView.findViewById(R.id.gattServerStatusText)
        gattServerProgressIndicator = gattServerView.findViewById(R.id.gattServerProgressIndicator)
        gattServerAddressText = gattServerView.findViewById(R.id.gattServerAddressText)
        gattServerConnectedClientsText = gattServerView.findViewById(R.id.gattServerConnectedClientsText)
        gattServerDataReceivedText = gattServerView.findViewById(R.id.gattServerDataReceivedText)
            gattServerUuidInput = gattServerView.findViewById(R.id.gattServerUuidInput)
            gattServerUuidLayout = gattServerView.findViewById(R.id.gattServerUuidLayout)
            gattServerManufacturerIdInput = gattServerView.findViewById(R.id.gattServerManufacturerIdInput)
            gattServerManufacturerIdLayout = gattServerView.findViewById(R.id.gattServerManufacturerIdLayout)
            gattServerManufacturerDataInput = gattServerView.findViewById(R.id.gattServerManufacturerDataInput)
            gattServerManufacturerDataLayout = gattServerView.findViewById(R.id.gattServerManufacturerDataLayout)
            toggleGattServerButton = gattServerView.findViewById(R.id.toggleGattServerButton)
            
            // Initialize UUID input with current UUID
            gattServerUuidInput.setText(gattServerController.getServiceUuid().toString())
            
            // Initialize Manufacturer ID and Data inputs with current values
            gattServerController.getManufacturerId()?.let { id ->
                gattServerManufacturerIdInput.setText("0x%04X".format(id))
                gattServerController.getManufacturerData()?.let { data ->
                    val dataHex = data.joinToString(" ") { "%02X".format(it) }
                    gattServerManufacturerDataInput.setText(dataHex)
                } ?: run {
                    gattServerManufacturerDataInput.setText("")
                }
            } ?: run {
                gattServerManufacturerIdInput.setText("")
                gattServerManufacturerDataInput.setText("")
            }

        toggleGattServerButton.setOnClickListener {
            if (gattServerController.isRunning) {
                gattServerController.stopServer()
            } else {
                onStartGattServerClicked()
            }
        }

        // Setup navigation
        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun configureRecyclerView() {
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = resultsAdapter
            // Disable item animations to prevent flickering during RSSI updates
            itemAnimator = null
            addItemDecoration(
                DividerItemDecoration(
                    context,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {
                showScanView()
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.menu_gatt_server -> {
                showGattServerView()
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
        }
        return false
    }

    private fun showScanView() {
        toolbar.title = getString(R.string.scan_title)
        contentFrame.removeAllViews()
        contentFrame.addView(scanView)
        // Update menu selection
        navigationView.menu.findItem(R.id.menu_scan)?.isChecked = true
        navigationView.menu.findItem(R.id.menu_gatt_server)?.isChecked = false
    }

    private fun showGattServerView() {
        toolbar.title = getString(R.string.gatt_server_title)
        contentFrame.removeAllViews()
        contentFrame.addView(gattServerView)
        // Update menu selection
        navigationView.menu.findItem(R.id.menu_scan)?.isChecked = false
        navigationView.menu.findItem(R.id.menu_gatt_server)?.isChecked = true
    }

    private fun onStartScanClicked() {
        when {
            !bleRequirements.isBleSupported() -> {
                Snackbar.make(
                    recyclerView,
                    R.string.scan_error_ble_not_supported,
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.scan_settings_button) {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }.show()
            }

            !bleRequirements.hasAllPermissions() -> {
                permissionLauncher.launch(bleRequirements.requiredRuntimePermissions())
            }

            !bleRequirements.isBluetoothEnabled() -> {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }

            else -> startBleScan()
        }
    }

    private fun ensureBluetoothEnabledAndScan() {
        if (bleRequirements.isBluetoothEnabled()) {
            startBleScan()
        } else {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun startBleScan() {
        if (!bleRequirements.hasAllPermissions()) {
            onStartScanClicked()
            return
        }
        if (!scannerController.startScan()) {
            // If startScan returns false, it might be due to permissions
            if (!bleRequirements.hasAllPermissions()) {
                onStartScanClicked()
            }
            return
        }
        scanResults.clear()
        smoothedRssiMap.clear()
        currentDeviceOrder = emptyList()
        resultsAdapter.submitDevices(emptyList())
        updateUiForScanState()
    }

    private fun stopBleScan() {
        scannerController.stopScan()
        handler.removeCallbacks(sortRunnable)
        pendingSort = false
        updateUiForScanState()
    }

    private fun updateUiForScanState() {
        val scanning = scannerController.isScanning
        progressIndicator.isVisible = scanning
        toggleScanButton.apply {
            isEnabled = true
            text = if (scanning) {
                getString(R.string.scan_stop_button)
            } else {
                getString(R.string.scan_start_button)
            }
        }
        statusText.text = if (scanning) {
            getString(R.string.scan_status_scanning)
        } else {
            getString(R.string.scan_status_idle)
        }
        emptyStateCard.isVisible = scanResults.isEmpty() && !scanning
    }

    private fun showPermissionDenied() {
        Snackbar.make(
            recyclerView,
            R.string.scan_error_permission_denied,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.scan_open_settings_button) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
        }.show()
    }

    private fun showBluetoothRequired() {
        Snackbar.make(
            recyclerView,
            R.string.scan_error_bluetooth_disabled,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun addOrUpdateResult(result: ScanResult) {
        val device = result.device
        val address = device.address
        val name = try {
            device.name ?: result.scanRecord?.deviceName ?: getString(R.string.scan_unknown_device)
        } catch (_: SecurityException) {
            result.scanRecord?.deviceName ?: getString(R.string.scan_unknown_device)
        }

        // Calculate smoothed RSSI using exponential smoothing
        val rawRssi = result.rssi.toDouble()
        val currentSmoothedRssi = smoothedRssiMap[address]
        val smoothedRssi = if (currentSmoothedRssi != null) {
            // Exponential smoothing: new = alpha * raw + (1 - alpha) * old
            RSSI_SMOOTHING_ALPHA * rawRssi + (1 - RSSI_SMOOTHING_ALPHA) * currentSmoothedRssi
        } else {
            // First time seeing this device, use raw value
            rawRssi
        }
        smoothedRssiMap[address] = smoothedRssi

        // Extract all manufacturer data from scan record
        val manufacturerDataMap = mutableMapOf<Int, ByteArray>()
        result.scanRecord?.manufacturerSpecificData?.forEach { mfgId, data ->
            if (data != null) {
                manufacturerDataMap[mfgId] = data
            }
        }

        // Extract service UUIDs from scan record
        val serviceUuidsList = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList<UUID>()

        val scannedDevice = ScannedDevice(
            address = address,
            name = name,
            rssi = result.rssi, // Raw RSSI for display
            smoothedRssi = smoothedRssi, // Smoothed RSSI for sorting
            isConnectable = result.isConnectable,
            lastSeen = System.currentTimeMillis(),
            manufacturerData = manufacturerDataMap,
            serviceUuids = serviceUuidsList
        )
        scanResults[address] = scannedDevice

        // Update UI immediately but debounce sorting
        updateDeviceList(updateSort = false)
        scheduleSort()
    }

    private fun scheduleSort() {
        if (!pendingSort) {
            pendingSort = true
            handler.removeCallbacks(sortRunnable)
            handler.postDelayed(sortRunnable, SORT_DEBOUNCE_MS)
        }
    }

    private fun performSort() {
        updateDeviceList(updateSort = true)
    }

    private fun updateDeviceList(updateSort: Boolean) {
        val devices = if (updateSort) {
            // Re-sort by smoothed RSSI
            scanResults.values.toList().sortedByDescending { it.smoothedRssi }
        } else {
            // Preserve current order, just update the data
            // Create a map for quick lookup
            val deviceMap = scanResults.values.associateBy { it.address }
            // Update items in current order with new data
            currentDeviceOrder.map { oldDevice ->
                deviceMap[oldDevice.address] ?: oldDevice
            }.filter { scanResults.containsKey(it.address) } +
                    // Add any new devices that weren't in the current order
                    scanResults.values.filterNot { device ->
                        currentDeviceOrder.any { it.address == device.address }
                    }
        }
        currentDeviceOrder = devices
        resultsAdapter.submitDevices(devices)
        emptyStateCard.isVisible = scanResults.isEmpty() && !scannerController.isScanning
    }

    override fun onScanResult(result: ScanResult) {
        runOnUiThread { addOrUpdateResult(result) }
    }

    override fun onScanFailed(errorCode: Int) {
        runOnUiThread {
            updateUiForScanState()
            Snackbar.make(
                recyclerView,
                getString(R.string.scan_error_generic, errorCode),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    // GATT Server Controller Listener
    private fun onStartGattServerClicked() {
        when {
            !bleRequirements.isBleSupported() -> {
                Snackbar.make(
                    gattServerView,
                    R.string.scan_error_ble_not_supported,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            !bleRequirements.hasAllPermissions() -> {
                permissionLauncher.launch(bleRequirements.requiredRuntimePermissions())
            }
            !bleRequirements.isBluetoothEnabled() -> {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            else -> {
                // Validate and set UUID
                val uuidString = gattServerUuidInput.text?.toString()?.trim() ?: ""
                if (uuidString.isEmpty()) {
                    gattServerUuidLayout.error = getString(R.string.gatt_server_uuid_invalid)
                    return
                }

                try {
                    val uuid = UUID.fromString(uuidString)
                    gattServerController.setServiceUuid(uuid)
                    gattServerUuidLayout.error = null
                } catch (e: IllegalArgumentException) {
                    gattServerUuidLayout.error = getString(R.string.gatt_server_uuid_invalid)
                    return
                }
                
                // Parse and set manufacturer data if provided
                val manufacturerIdString = gattServerManufacturerIdInput.text?.toString()?.trim() ?: ""
                val manufacturerDataString = gattServerManufacturerDataInput.text?.toString()?.trim() ?: ""
                
                if (manufacturerIdString.isNotEmpty() || manufacturerDataString.isNotEmpty()) {
                    // Both fields must be provided if one is provided
                    if (manufacturerIdString.isEmpty()) {
                        gattServerManufacturerIdLayout.error = getString(R.string.gatt_server_manufacturer_id_invalid)
                        return
                    }
                    if (manufacturerDataString.isEmpty()) {
                        gattServerManufacturerDataLayout.error = getString(R.string.gatt_server_manufacturer_data_invalid)
                        return
                    }
                    
                    try {
                        val manufacturerId = parseManufacturerId(manufacturerIdString)
                        val data = parseManufacturerDataBytes(manufacturerDataString)
                        if (!gattServerController.setManufacturerData(manufacturerId, data)) {
                            // Error already reported via callback
                            return
                        }
                        gattServerManufacturerIdLayout.error = null
                        gattServerManufacturerDataLayout.error = null
                    } catch (e: IllegalArgumentException) {
                        if (e.message?.contains("ID") == true) {
                            gattServerManufacturerIdLayout.error = getString(R.string.gatt_server_manufacturer_id_invalid)
                        } else {
                            gattServerManufacturerDataLayout.error = getString(R.string.gatt_server_manufacturer_data_invalid)
                        }
                        return
                    }
                } else {
                    // Clear manufacturer data if both fields are empty
                    if (!gattServerController.setManufacturerData(null, null)) {
                        // Error already reported via callback
                        return
                    }
                    gattServerManufacturerIdLayout.error = null
                    gattServerManufacturerDataLayout.error = null
                }
                
                // Disable button while starting to prevent multiple attempts
                toggleGattServerButton.isEnabled = false
                if (!gattServerController.startServer()) {
                    // Re-enable if start failed immediately
                    toggleGattServerButton.isEnabled = true
                    // Error will be reported via callback
                }
                // If startServer returns true, button will be re-enabled in onServerStarted/onServerError callbacks
            }
        }
    }

    override fun onServerStarted() {
        runOnUiThread {
            toggleGattServerButton.isEnabled = true
            updateGattServerUi()
        }
    }

    override fun onServerStopped() {
        runOnUiThread {
            toggleGattServerButton.isEnabled = true
            updateGattServerUi()
        }
    }

    override fun onServerError(errorCode: Int) {
        runOnUiThread {
            toggleGattServerButton.isEnabled = true
            updateGattServerUi()
            val errorMessage = when (errorCode) {
                -1 -> "Unknown error occurred"
                -2 -> "Bluetooth not enabled"
                -3 -> "BLE advertising not supported on this device"
                -4 -> "Failed to open GATT server"
                -5 -> "Failed to add GATT service"
                -6 -> "Permission denied - check Bluetooth permissions"
                -7 -> "Cannot change settings while server is running"
                AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising already started"
                AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertisement data too large"
                AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Advertising feature not supported"
                AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal advertising error"
                AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Server error (code $errorCode)"
            }
            Snackbar.make(
                gattServerView,
                errorMessage,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onClientConnected(address: String) {
        runOnUiThread {
            updateGattServerUi()
        }
    }

    override fun onClientDisconnected(address: String) {
        runOnUiThread {
            updateGattServerUi()
        }
    }

    override fun onDataReceived(data: ByteArray) {
        runOnUiThread {
            val dataString = data.joinToString(" ") { "%02X".format(it) }
            receivedDataHistory.append("${System.currentTimeMillis()}: $dataString\n")
            if (receivedDataHistory.length > 1000) {
                receivedDataHistory.delete(0, receivedDataHistory.length - 1000)
            }
            gattServerDataReceivedText.text = receivedDataHistory.toString()
        }
    }

    private fun updateGattServerUi() {
        val running = gattServerController.isRunning
        gattServerProgressIndicator.isVisible = running
        toggleGattServerButton.apply {
            isEnabled = true
            text = if (running) {
                getString(R.string.gatt_server_stop_button)
            } else {
                getString(R.string.gatt_server_start_button)
            }
        }
        gattServerStatusText.text = if (running) {
            getString(R.string.gatt_server_status_running)
        } else {
            getString(R.string.gatt_server_status_stopped)
        }

        val address = gattServerController.getServerAddress() ?: "Unknown"
        gattServerAddressText.text = "Address: $address"

        val clientCount = gattServerController.connectedClientCount
        gattServerConnectedClientsText.text = getString(R.string.gatt_server_connected_clients, clientCount)
        
        // Enable/disable UUID and manufacturer data inputs based on server state
        gattServerUuidInput.isEnabled = !running
        gattServerManufacturerIdInput.isEnabled = !running
        gattServerManufacturerDataInput.isEnabled = !running
        
        // Clear errors when server state changes
        if (!running) {
            gattServerUuidLayout.error = null
            gattServerManufacturerIdLayout.error = null
            gattServerManufacturerDataLayout.error = null
        }
    }

    private fun parseManufacturerId(input: String): Int {
        // Expected format: "0x004C" or "004C" (hex, 16-bit)
        val trimmed = input.trim()
        
        // Remove 0x prefix if present
        val hexString = if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2)
        } else {
            trimmed
        }
        
        if (hexString.isEmpty()) {
            throw IllegalArgumentException("Invalid manufacturer ID: empty")
        }
        
        val manufacturerId = try {
            hexString.toInt(16)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid manufacturer ID format: $trimmed", e)
        }
        
        if (manufacturerId < 0 || manufacturerId > 0xFFFF) {
            throw IllegalArgumentException("Invalid manufacturer ID: must be 0x0000-0xFFFF")
        }
        
        return manufacturerId
    }
    
    private fun parseManufacturerDataBytes(input: String): ByteArray {
        // Expected format: "01 02 03" or "010203" (hex bytes)
        val trimmed = input.trim()
        
        if (trimmed.isEmpty()) {
            return ByteArray(0)
        }
        
        // Split by spaces or parse as pairs of hex digits
        val hexBytes = if (trimmed.contains(" ")) {
            trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        } else {
            // Parse as consecutive pairs of hex digits
            trimmed.chunked(2)
        }
        
        val data = hexBytes.map { hexByte ->
            if (hexByte.length == 2) {
                try {
                    hexByte.toInt(16).toByte()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid hex byte: $hexByte", e)
                }
            } else {
                throw IllegalArgumentException("Invalid hex byte length: $hexByte (must be 2 hex digits)")
            }
        }.toByteArray()
        
        return data
    }
}

