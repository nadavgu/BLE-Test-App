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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.material3.MaterialTheme
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import no.nordicsemi.android.support.v18.scanner.ScanResult
import java.util.UUID
import android.util.Log

class MainActivity : AppCompatActivity(), BleScannerController.Listener, BleGattServerController.Listener, BleConnectionController.Listener, NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val SORT_DEBOUNCE_MS = 1_500L // Re-sort every 1.5 seconds
        private const val RSSI_SMOOTHING_ALPHA = 0.3 // Exponential smoothing factor (0.0-1.0)
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var contentFrame: FrameLayout

    // Scan view components
    private lateinit var scanView: ComposeView
    private var scanDevicesState by mutableStateOf<List<ScannedDevice>>(emptyList())
    private var isScanningState by mutableStateOf(false)

    // GATT Server view components
    private lateinit var gattServerView: ComposeView
    private var gattServerState by mutableStateOf(GattServerState())

    private val scanResults = linkedMapOf<String, ScannedDevice>()
    private var connectedDevicesState by mutableStateOf<List<ConnectedDevice>>(emptyList())

    // Track smoothed RSSI values for each device
    private val smoothedRssiMap = mutableMapOf<String, Double>()

    // Track current device order to preserve it when not sorting
    private var currentDeviceOrder = listOf<ScannedDevice>()

    private lateinit var bleRequirements: BleRequirements
    private lateinit var scannerController: BleScannerController
    private lateinit var gattServerController: BleGattServerController
    private lateinit var connectionController: BleConnectionController

    // Connected devices view
    private lateinit var connectedDevicesView: ComposeView

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
            Log.d(TAG, "permissionLauncher: Permissions granted=$allGranted")
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
        Log.d(TAG, "onCreate: Initializing MainActivity")
        setContentView(R.layout.activity_main)
        bleRequirements = BleRequirements(this)
        scannerController = BleScannerController(this, bleRequirements)
        gattServerController = BleGattServerController(this, this)
        connectionController = BleConnectionController(this, this)
        bindViews()
        configureToolbar()
        setupViews()
        showScanView()
        updateUiForScanState()
        updateGattServerUi()
        updateConnectedDevicesUi()
        Log.d(TAG, "onCreate: MainActivity initialized")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Activity started")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Stopping BLE operations")
        stopBleScan()
        gattServerController.stopServer()
        connectionController.disconnectAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Cleaning up")
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
        // Create Compose scan view
        scanView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    ScanScreen(
                        devices = scanDevicesState,
                        isScanning = isScanningState,
                        onToggleScan = {
                            if (scannerController.isScanning) {
                                stopBleScan()
                            } else {
                                ensureBluetoothEnabledAndScan()
                            }
                        },
                        onConnectClick = { address ->
                            onConnectToDevice(address)
                        }
                    )
                }
            }
        }

        // Create Compose GATT server view
        gattServerView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    GattServerScreen(
                        state = gattServerState,
                        onUuidChange = { uuid ->
                            gattServerState = gattServerState.copy(
                                serviceUuid = uuid,
                                uuidError = null
                            )
                        },
                        onManufacturerIdChange = { id ->
                            gattServerState = gattServerState.copy(
                                manufacturerId = id,
                                manufacturerIdError = null
                            )
                        },
                        onManufacturerDataChange = { data ->
                            gattServerState = gattServerState.copy(
                                manufacturerData = data,
                                manufacturerDataError = null
                            )
                        },
                        onToggleServer = {
                            if (gattServerController.isRunning) {
                                gattServerController.stopServer()
                            } else {
                                onStartGattServerClicked()
                            }
                        }
                    )
                }
            }
        }
        
        // Initialize state with current values
        gattServerState = gattServerState.copy(
            serviceUuid = gattServerController.getServiceUuid().toString(),
            manufacturerId = gattServerController.getManufacturerId()?.let { "0x%04X".format(it) } ?: "",
            manufacturerData = gattServerController.getManufacturerData()?.joinToString(" ") { "%02X".format(it) } ?: ""
        )

        // Compose-based connected devices view
        connectedDevicesView = ComposeView(this).apply {
            setContent {
                androidx.compose.material3.MaterialTheme {
                    ConnectedDevicesScreen(
                        devices = connectedDevicesState,
                        onConnectByAddress = { address ->
                            onConnectByAddress(address)
                        },
                        onDisconnect = { address ->
                            connectionController.disconnectDevice(address)
                        },
                        onRemove = { address ->
                            connectionController.removeDisconnectedDevice(address)
                            updateConnectedDevicesUi()
                        }
                    )
                }
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


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {
                showScanView()
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.menu_connected_devices -> {
                showConnectedDevicesView()
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
        Log.d(TAG, "showScanView: Switching to scan view")
        toolbar.title = getString(R.string.scan_title)
        contentFrame.removeAllViews()
        contentFrame.addView(scanView)
        // Update menu selection
        navigationView.menu.findItem(R.id.menu_scan)?.isChecked = true
        navigationView.menu.findItem(R.id.menu_connected_devices)?.isChecked = false
        navigationView.menu.findItem(R.id.menu_gatt_server)?.isChecked = false
        // Ensure scan UI is updated
        updateUiForScanState()
    }

    private fun showConnectedDevicesView() {
        Log.d(TAG, "showConnectedDevicesView: Switching to connected devices view")
        toolbar.title = getString(R.string.connected_devices_title)
        contentFrame.removeAllViews()
        contentFrame.addView(connectedDevicesView)
        // Update menu selection
        navigationView.menu.findItem(R.id.menu_scan)?.isChecked = false
        navigationView.menu.findItem(R.id.menu_connected_devices)?.isChecked = true
        navigationView.menu.findItem(R.id.menu_gatt_server)?.isChecked = false
        // Ensure connected devices UI is updated
        updateConnectedDevicesUi()
    }

    private fun showGattServerView() {
        Log.d(TAG, "showGattServerView: Switching to GATT server view")
        toolbar.title = getString(R.string.gatt_server_title)
        contentFrame.removeAllViews()
        contentFrame.addView(gattServerView)
        // Update menu selection
        navigationView.menu.findItem(R.id.menu_scan)?.isChecked = false
        navigationView.menu.findItem(R.id.menu_connected_devices)?.isChecked = false
        navigationView.menu.findItem(R.id.menu_gatt_server)?.isChecked = true
        // Ensure GATT server UI is updated
        updateGattServerUi()
    }

    private fun onStartScanClicked() {
        Log.d(TAG, "onStartScanClicked: User requested to start scan")
        when {
            !bleRequirements.isBleSupported() -> {
                Log.w(TAG, "onStartScanClicked: BLE not supported")
                Snackbar.make(
                    contentFrame,
                    R.string.scan_error_ble_not_supported,
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.scan_settings_button) {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }.show()
            }

            !bleRequirements.hasAllPermissions() -> {
                Log.d(TAG, "onStartScanClicked: Requesting permissions")
                permissionLauncher.launch(bleRequirements.requiredRuntimePermissions())
            }

            !bleRequirements.isBluetoothEnabled() -> {
                Log.d(TAG, "onStartScanClicked: Requesting Bluetooth enable")
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
        Log.d(TAG, "startBleScan: Starting BLE scan")
        if (!bleRequirements.hasAllPermissions()) {
            Log.w(TAG, "startBleScan: Missing permissions, requesting")
            onStartScanClicked()
            return
        }
        if (!scannerController.startScan()) {
            Log.w(TAG, "startBleScan: Failed to start scan")
            // If startScan returns false, it might be due to permissions
            if (!bleRequirements.hasAllPermissions()) {
                onStartScanClicked()
            }
            return
        }
        Log.i(TAG, "startBleScan: Scan started successfully")
        scanResults.clear()
        smoothedRssiMap.clear()
        currentDeviceOrder = emptyList()
        scanDevicesState = emptyList()
        updateUiForScanState()
    }

    private fun stopBleScan() {
        Log.d(TAG, "stopBleScan: Stopping BLE scan")
        scannerController.stopScan()
        handler.removeCallbacks(sortRunnable)
        pendingSort = false
        updateUiForScanState()
        Log.i(TAG, "stopBleScan: Scan stopped")
    }

    private fun updateUiForScanState() {
        isScanningState = scannerController.isScanning
        // Compose UI will automatically update based on state
    }

    private fun showPermissionDenied() {
        Snackbar.make(
            contentFrame,
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
            contentFrame,
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
        Log.v(TAG, "addOrUpdateResult: $address ($name), RSSI=${result.rssi}")

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
        scanDevicesState = devices
    }

    override fun onScanResult(result: ScanResult) {
        runOnUiThread { addOrUpdateResult(result) }
    }

    override fun onScanFailed(errorCode: Int) {
        runOnUiThread {
            updateUiForScanState()
            Snackbar.make(
                contentFrame,
                getString(R.string.scan_error_generic, errorCode),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    // GATT Server Controller Listener
    private fun onStartGattServerClicked() {
        Log.d(TAG, "onStartGattServerClicked: User requested to start GATT server")
        when {
            !bleRequirements.isBleSupported() -> {
                Log.w(TAG, "onStartGattServerClicked: BLE not supported")
                Snackbar.make(
                    gattServerView,
                    R.string.scan_error_ble_not_supported,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            !bleRequirements.hasAllPermissions() -> {
                Log.d(TAG, "onStartGattServerClicked: Requesting permissions")
                permissionLauncher.launch(bleRequirements.requiredRuntimePermissions())
            }
            !bleRequirements.isBluetoothEnabled() -> {
                Log.d(TAG, "onStartGattServerClicked: Requesting Bluetooth enable")
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            else -> {
                Log.d(TAG, "onStartGattServerClicked: Validating inputs and starting server")
                // Validate and set UUID
                val uuidString = gattServerState.serviceUuid.trim()
                if (uuidString.isEmpty()) {
                    Log.w(TAG, "onStartGattServerClicked: UUID is empty")
                    gattServerState = gattServerState.copy(
                        uuidError = getString(R.string.gatt_server_uuid_invalid)
                    )
                    return
                }

                try {
                    val uuid = UUID.fromString(uuidString)
                    Log.d(TAG, "onStartGattServerClicked: Setting service UUID to $uuid")
                    gattServerController.setServiceUuid(uuid)
                    gattServerState = gattServerState.copy(uuidError = null)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "onStartGattServerClicked: Invalid UUID format: $uuidString", e)
                    gattServerState = gattServerState.copy(
                        uuidError = getString(R.string.gatt_server_uuid_invalid)
                    )
                    return
                }
                
                // Parse and set manufacturer data if provided
                val manufacturerIdString = gattServerState.manufacturerId.trim()
                val manufacturerDataString = gattServerState.manufacturerData.trim()
                
                if (manufacturerIdString.isNotEmpty() || manufacturerDataString.isNotEmpty()) {
                    // Both fields must be provided if one is provided
                    if (manufacturerIdString.isEmpty()) {
                        gattServerState = gattServerState.copy(
                            manufacturerIdError = getString(R.string.gatt_server_manufacturer_id_invalid)
                        )
                        return
                    }
                    if (manufacturerDataString.isEmpty()) {
                        gattServerState = gattServerState.copy(
                            manufacturerDataError = getString(R.string.gatt_server_manufacturer_data_invalid)
                        )
                        return
                    }
                    
                    try {
                        val manufacturerId = parseManufacturerId(manufacturerIdString)
                        val data = parseManufacturerDataBytes(manufacturerDataString)
                        if (!gattServerController.setManufacturerData(manufacturerId, data)) {
                            // Error already reported via callback
                            return
                        }
                        gattServerState = gattServerState.copy(
                            manufacturerIdError = null,
                            manufacturerDataError = null
                        )
                    } catch (e: IllegalArgumentException) {
                        if (e.message?.contains("ID") == true) {
                            gattServerState = gattServerState.copy(
                                manufacturerIdError = getString(R.string.gatt_server_manufacturer_id_invalid)
                            )
                        } else {
                            gattServerState = gattServerState.copy(
                                manufacturerDataError = getString(R.string.gatt_server_manufacturer_data_invalid)
                            )
                        }
                        return
                    }
                } else {
                    // Clear manufacturer data if both fields are empty
                    if (!gattServerController.setManufacturerData(null, null)) {
                        // Error already reported via callback
                        return
                    }
                    gattServerState = gattServerState.copy(
                        manufacturerIdError = null,
                        manufacturerDataError = null
                    )
                }
                
                if (!gattServerController.startServer()) {
                    Log.w(TAG, "onStartGattServerClicked: Failed to start server")
                    // Error will be reported via callback
                } else {
                    Log.d(TAG, "onStartGattServerClicked: Server start request sent")
                }
            }
        }
    }

    override fun onServerStarted() {
        Log.i(TAG, "onServerStarted: GATT server started successfully")
        runOnUiThread {
            updateGattServerUi()
        }
    }

    override fun onServerStopped() {
        Log.i(TAG, "onServerStopped: GATT server stopped")
        runOnUiThread {
            updateGattServerUi()
        }
    }

    override fun onServerError(errorCode: Int) {
        Log.e(TAG, "onServerError: GATT server error with errorCode=$errorCode")
        runOnUiThread {
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
                contentFrame,
                errorMessage,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onClientConnected(address: String) {
        Log.i(TAG, "onClientConnected: Client connected to GATT server - $address")
        runOnUiThread {
            updateGattServerUi()
        }
    }

    override fun onClientDisconnected(address: String) {
        Log.i(TAG, "onClientDisconnected: Client disconnected from GATT server - $address")
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
            gattServerState = gattServerState.copy(
                dataReceived = receivedDataHistory.toString()
            )
        }
    }

    private fun updateGattServerUi() {
        val running = gattServerController.isRunning
        val address = gattServerController.getServerAddress()
        val clientCount = gattServerController.connectedClientCount
        
        gattServerState = gattServerState.copy(
            isRunning = running,
            serverAddress = address,
            connectedClientCount = clientCount,
            // Clear errors when server state changes
            uuidError = if (!running) null else gattServerState.uuidError,
            manufacturerIdError = if (!running) null else gattServerState.manufacturerIdError,
            manufacturerDataError = if (!running) null else gattServerState.manufacturerDataError
        )
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

    // Connection Controller Listener
    private fun onConnectToDevice(address: String) {
        Log.d(TAG, "onConnectToDevice: User requested connection to $address")
        val device = scanResults[address] ?: run {
            Log.w(TAG, "onConnectToDevice: Device not found in scan results: $address")
            return
        }
        connectToDeviceByAddress(address)
    }

    private fun onConnectByAddress(address: String) {
        Log.d(TAG, "onConnectByAddress: User requested connection to $address")
        connectToDeviceByAddress(address)
    }
    
    private fun connectToDeviceByAddress(address: String) {
        val bluetoothDevice = try {
            bleRequirements.bluetoothAdapter()?.getRemoteDevice(address)
        } catch (e: SecurityException) {
            Log.e(TAG, "connectToDeviceByAddress: SecurityException getting device", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "connectToDeviceByAddress: Invalid address format: $address", e)
            null
        } ?: run {
            Log.w(TAG, "connectToDeviceByAddress: Failed to get BluetoothDevice for $address")
            Snackbar.make(
                contentFrame,
                getString(R.string.connected_devices_address_invalid),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        if (connectionController.connectToDevice(bluetoothDevice)) {
            Log.i(TAG, "connectToDeviceByAddress: Connection initiated to $address")
            // Device info is now stored in the controller
            updateConnectedDevicesUi()
            // Navigate to connected devices view if not already there
            if (contentFrame.indexOfChild(connectedDevicesView) == -1) {
                showConnectedDevicesView()
            }
        } else {
            Log.w(TAG, "connectToDeviceByAddress: Failed to initiate connection to $address")
            Snackbar.make(
                contentFrame,
                getString(R.string.connection_error),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onDeviceConnected(address: String, name: String) {
        Log.i(TAG, "onDeviceConnected: Device connected - $address ($name)")
        runOnUiThread {
            updateConnectedDevicesUi()
        }
    }

    override fun onDeviceDisconnected(address: String, reason: Int) {
        Log.i(TAG, "onDeviceDisconnected: Device disconnected - $address, reason=$reason")
        runOnUiThread {
            updateConnectedDevicesUi()
        }
    }

    override fun onConnectionFailed(address: String, errorCode: Int) {
        Log.e(TAG, "onConnectionFailed: Connection failed - $address, errorCode=$errorCode")
        runOnUiThread {
            updateConnectedDevicesUi()
            Snackbar.make(
                contentFrame,
                getString(R.string.connection_error),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun updateConnectedDevicesUi() {
        connectedDevicesState = connectionController.getConnectedDevices()
    }
}

