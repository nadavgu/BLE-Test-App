package com.nadavgu.bletestapp

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import no.nordicsemi.android.support.v18.scanner.ScanResult

class ScanActivity : AppCompatActivity(), BleScannerController.Listener {

    companion object {
        private const val SORT_DEBOUNCE_MS = 1_500L // Re-sort every 1.5 seconds
        private const val RSSI_SMOOTHING_ALPHA = 0.3 // Exponential smoothing factor (0.0-1.0)
    }

    private lateinit var statusText: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var emptyStateCard: MaterialCardView
    private lateinit var toggleScanButton: MaterialButton
    private lateinit var recyclerView: RecyclerView

    private val scanResults = linkedMapOf<String, ScannedDevice>()
    private val resultsAdapter = ScanResultAdapter()
    
    // Track smoothed RSSI values for each device
    private val smoothedRssiMap = mutableMapOf<String, Double>()
    
    // Track current device order to preserve it when not sorting
    private var currentDeviceOrder = listOf<ScannedDevice>()

    private lateinit var bleRequirements: BleRequirements
    private lateinit var scannerController: BleScannerController
    
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
        setContentView(R.layout.activity_scan)
        bleRequirements = BleRequirements(this)
        scannerController = BleScannerController(this, bleRequirements)
        bindViews()
        configureToolbar()
        configureRecyclerView()
        updateUiForScanState()
        toggleScanButton.setOnClickListener {
            if (scannerController.isScanning) {
                stopBleScan()
            } else {
                onStartScanClicked()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Automatically prompt for permissions so the user can scan right away.
        if (!scannerController.isScanning) {
            onStartScanClicked()
        }
    }

    override fun onStop() {
        super.onStop()
        stopBleScan()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up handler to prevent leaks
        handler.removeCallbacks(sortRunnable)
    }

    private fun bindViews() {
        statusText = findViewById(R.id.scanStatusText)
        progressIndicator = findViewById(R.id.scanProgressIndicator)
        emptyStateCard = findViewById(R.id.emptyStateCard)
        toggleScanButton = findViewById(R.id.toggleScanButton)
        recyclerView = findViewById(R.id.scanResultsRecyclerView)
    }

    private fun configureToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.scanToolbar)
        setSupportActionBar(toolbar)
    }

    private fun configureRecyclerView() {
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScanActivity)
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
        
        val scannedDevice = ScannedDevice(
            address = address,
            name = name,
            rssi = result.rssi, // Raw RSSI for display
            smoothedRssi = smoothedRssi, // Smoothed RSSI for sorting
            isConnectable = result.isConnectable,
            lastSeen = System.currentTimeMillis()
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
}

