package com.nadavgu.bletestapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.google.android.material.snackbar.Snackbar
import no.nordicsemi.android.support.v18.scanner.ScanResult
import java.util.UUID
import android.util.Log
import com.nadavgu.bletestapp.server.BleGattServerController
import com.nadavgu.bletestapp.server.BleServerListener
import com.nadavgu.bletestapp.ServerSpeedCheckState

class MainActivity : AppCompatActivity(), BleScannerController.Listener, BleServerListener, BleConnectionController.Listener {

    companion object {
        private const val TAG = "MainActivity"
        private const val SORT_DEBOUNCE_MS = 1_500L // Re-sort every 1.5 seconds
        private const val RSSI_SMOOTHING_ALPHA = 0.3 // Exponential smoothing factor (0.0-1.0)
    }

    private var currentDestination by mutableStateOf(NavigationDestination.SCAN)

    // Scan state
    private var scanDevicesState by mutableStateOf<List<ScannedDevice>>(emptyList())
    private var isScanningState by mutableStateOf(false)
    private var scanFiltersState by mutableStateOf(ScanFiltersState(
        serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB",
        manufacturerId = "0x004C",
        manufacturerData = "01 02 03"
    ))

    // GATT Server state
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


    private val receivedDataHistoryByClientServiceAndCharacteristic = mutableMapOf<String, MutableMap<String, MutableMap<String, StringBuilder>>>()
    private val speedCheckStateByClient = mutableMapOf<String, ServerSpeedCheckState>()

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
        bleRequirements = BleRequirements(this)
        scannerController = BleScannerController(this, bleRequirements)
        gattServerController = BleGattServerController(this, this)
        connectionController = BleConnectionController(this, this)
        
        // Initialize state with current values or defaults
        val defaultCharacteristicUuids = gattServerController.getCharacteristicUuids()
        gattServerState = gattServerState.copy(
            serviceUuid = gattServerController.getServiceUuid().toString(),
            characteristics = if (defaultCharacteristicUuids.isNotEmpty()) {
                defaultCharacteristicUuids.mapIndexed { index, uuid ->
                    CharacteristicEntry(
                        entryId = UUID.randomUUID().toString(),
                        uuid = uuid.toString()
                    )
                }
            } else {
                listOf(CharacteristicEntry(
                    entryId = UUID.randomUUID().toString(),
                    uuid = UUID.randomUUID().toString()
                ))
            },
            manufacturerId = gattServerController.getManufacturerId()?.let { "0x%04X".format(it) } ?: "0x004C",
            manufacturerData = gattServerController.getManufacturerData()?.joinToString(" ") { "%02X".format(it) } ?: "01 02 03",
            speedCheckEnabled = gattServerController.getSpeedCheckEnabled()
        )
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                MainScreen(
                    currentDestination = currentDestination,
                    onDestinationChange = { destination ->
                        currentDestination = destination
                        when (destination) {
                            NavigationDestination.SCAN -> {
                                updateUiForScanState()
                            }
                            NavigationDestination.CONNECTED_DEVICES -> {
                                updateConnectedDevicesUi()
                            }
                            NavigationDestination.GATT_SERVER -> {
                                updateGattServerUi()
                            }
                        }
                    },
                    scanScreen = {
                        ScanScreen(
                            devices = scanDevicesState,
                            isScanning = isScanningState,
                            scanFilters = scanFiltersState,
                            onToggleScan = {
                                if (scannerController.isScanning) {
                                    stopBleScan()
                                } else {
                                    ensureBluetoothEnabledAndScan()
                                }
                            },
                            onConnectClick = { address ->
                                onConnectToDevice(address)
                            },
                            onServiceUuidChange = { uuid ->
                                scanFiltersState = scanFiltersState.copy(
                                    serviceUuid = uuid,
                                    serviceUuidError = null
                                )
                            },
                            onManufacturerIdChange = { id ->
                                scanFiltersState = scanFiltersState.copy(
                                    manufacturerId = id,
                                    manufacturerIdError = null
                                )
                            },
                            onManufacturerDataChange = { data ->
                                scanFiltersState = scanFiltersState.copy(
                                    manufacturerData = data,
                                    manufacturerDataError = null
                                )
                            }
                        )
                    },
                    connectedDevicesScreen = {
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
                            },
                            onWriteCharacteristic = { deviceAddress, serviceUuid, characteristicUuid, data ->
                                Log.d(TAG, "onWriteCharacteristic: Writing to $deviceAddress, service=$serviceUuid, characteristic=$characteristicUuid, data size=${data.size}")
                                connectionController.writeCharacteristic(deviceAddress, serviceUuid, characteristicUuid, data)
                            }
                        )
                    },
                            gattServerScreen = {
                                GattServerScreen(
                                    state = gattServerState,
                                    onUuidChange = { uuid ->
                                        gattServerState = gattServerState.copy(
                                            serviceUuid = uuid,
                                            uuidError = null
                                        )
                                    },
                                    onAddCharacteristic = {
                                        val newEntryId = UUID.randomUUID().toString()
                                        val randomUuid = UUID.randomUUID().toString()
                                        gattServerState = gattServerState.copy(
                                            characteristics = gattServerState.characteristics + CharacteristicEntry(
                                                entryId = newEntryId,
                                                uuid = randomUuid
                                            )
                                        )
                                    },
                                    onRemoveCharacteristic = { entryId ->
                                        gattServerState = gattServerState.copy(
                                            characteristics = gattServerState.characteristics.filter { it.entryId != entryId }
                                        )
                                    },
                                    onCharacteristicUuidChange = { entryId, uuid ->
                                        gattServerState = gattServerState.copy(
                                            characteristics = gattServerState.characteristics.map { char ->
                                                if (char.entryId == entryId) {
                                                    char.copy(uuid = uuid, uuidError = null)
                                                } else {
                                                    char
                                                }
                                            }
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
                                    onSpeedCheckToggle = { enabled ->
                                        if (gattServerController.setSpeedCheckEnabled(enabled)) {
                                            gattServerState = gattServerState.copy(
                                                speedCheckEnabled = enabled
                                            )
                                        }
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
                )
            }
        }
        
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


    private fun onStartScanClicked() {
        Log.d(TAG, "onStartScanClicked: User requested to start scan")
        when {
            !bleRequirements.isBleSupported() -> {
                Log.w(TAG, "onStartScanClicked: BLE not supported")
                val rootView = window.decorView.rootView
                Snackbar.make(
                    rootView,
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
        
        // Build scan filters from state
        val filters = buildScanFilters()
        scannerController.setScanFilters(filters)
        Log.d(TAG, "startBleScan: Applied ${filters.size} scan filters")
        
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
    
    private fun buildScanFilters(): List<no.nordicsemi.android.support.v18.scanner.ScanFilter> {
        val filters = mutableListOf<no.nordicsemi.android.support.v18.scanner.ScanFilter>()
        
        // Service UUID filter
        val serviceUuidString = scanFiltersState.serviceUuid.trim()
        if (serviceUuidString.isNotEmpty()) {
            try {
                val uuid = UUID.fromString(serviceUuidString)
                val filter = no.nordicsemi.android.support.v18.scanner.ScanFilter.Builder()
                    .setServiceUuid(android.os.ParcelUuid(uuid))
                    .build()
                filters.add(filter)
                Log.d(TAG, "buildScanFilters: Added service UUID filter: $uuid")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "buildScanFilters: Invalid service UUID: $serviceUuidString", e)
                runOnUiThread {
                    scanFiltersState = scanFiltersState.copy(
                        serviceUuidError = getString(R.string.scan_filter_service_uuid_invalid)
                    )
                }
            }
        }
        
        // Manufacturer data filter
        val manufacturerIdString = scanFiltersState.manufacturerId.trim()
        val manufacturerDataString = scanFiltersState.manufacturerData.trim()
        
        if (manufacturerIdString.isNotEmpty() || manufacturerDataString.isNotEmpty()) {
            if (manufacturerIdString.isEmpty()) {
                runOnUiThread {
                    scanFiltersState = scanFiltersState.copy(
                        manufacturerIdError = getString(R.string.scan_filter_manufacturer_id_invalid)
                    )
                }
                return filters // Return what we have so far
            }
            
            try {
                val manufacturerId = parseManufacturerId(manufacturerIdString)
                val data = if (manufacturerDataString.isNotEmpty()) {
                    parseManufacturerDataBytes(manufacturerDataString)
                } else {
                    byteArrayOf() // Empty data is valid
                }
                
                val filter = no.nordicsemi.android.support.v18.scanner.ScanFilter.Builder()
                    .setManufacturerData(manufacturerId, data)
                    .build()
                filters.add(filter)
                Log.d(TAG, "buildScanFilters: Added manufacturer data filter: ID=0x%04X, data size=${data.size}".format(manufacturerId))
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "buildScanFilters: Invalid manufacturer data", e)
                if (e.message?.contains("ID") == true) {
                    runOnUiThread {
                        scanFiltersState = scanFiltersState.copy(
                            manufacturerIdError = getString(R.string.scan_filter_manufacturer_id_invalid)
                        )
                    }
                } else {
                    runOnUiThread {
                        scanFiltersState = scanFiltersState.copy(
                            manufacturerDataError = getString(R.string.scan_filter_manufacturer_data_invalid)
                        )
                    }
                }
            }
        }
        
        return filters
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
        val rootView = window.decorView.rootView
        Snackbar.make(
            rootView,
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
        val rootView = window.decorView.rootView
        Snackbar.make(
            rootView,
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
            val rootView = window.decorView.rootView
            Snackbar.make(
                rootView,
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
                val rootView = window.decorView.rootView
                Snackbar.make(
                    rootView,
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
                
                // Validate and set characteristic UUIDs
                if (gattServerState.characteristics.isEmpty()) {
                    Log.w(TAG, "onStartGattServerClicked: No characteristics defined")
                    val rootView = window.decorView.rootView
                    Snackbar.make(
                        rootView,
                        "At least one characteristic is required",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                
                val characteristicUuids = mutableListOf<UUID>()
                val updatedCharacteristics = gattServerState.characteristics.mapIndexed { index, char ->
                    val uuidString = char.uuid.trim()
                    if (uuidString.isEmpty()) {
                        Log.w(TAG, "onStartGattServerClicked: Characteristic ${index + 1} UUID is empty")
                        char.copy(uuidError = getString(R.string.gatt_server_characteristic_uuid_invalid))
                    } else {
                        try {
                            val uuid = UUID.fromString(uuidString)
                            characteristicUuids.add(uuid)
                            char.copy(uuidError = null)
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "onStartGattServerClicked: Invalid characteristic UUID: $uuidString", e)
                            char.copy(uuidError = getString(R.string.gatt_server_characteristic_uuid_invalid))
                        }
                    }
                }
                
                // Check if any characteristics have errors
                val hasErrors = updatedCharacteristics.any { it.uuidError != null }
                if (hasErrors) {
                    gattServerState = gattServerState.copy(characteristics = updatedCharacteristics)
                    return
                }
                
                if (characteristicUuids.isEmpty()) {
                    Log.w(TAG, "onStartGattServerClicked: No valid characteristics")
                    return
                }
                
                if (!gattServerController.setCharacteristicUuids(characteristicUuids)) {
                    // Error already reported via callback
                    return
                }
                gattServerState = gattServerState.copy(characteristics = updatedCharacteristics)
                
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
            val rootView = window.decorView.rootView
            Snackbar.make(
                rootView,
                errorMessage,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onClientConnected(device: BluetoothDevice) {
        Log.i(TAG, "onClientConnected: Client connected to GATT server - ${device.address}")
        runOnUiThread {
            updateGattServerUi()
        }
    }

    override fun onClientDisconnected(device: BluetoothDevice) {
        Log.i(TAG, "onClientDisconnected: Client disconnected from GATT server - ${device.address}")
        runOnUiThread {
            // Clean up data history for disconnected client
            receivedDataHistoryByClientServiceAndCharacteristic.remove(device.address)
            updateGattServerUi()
        }
    }

    override fun onDataReceived(clientDevice: BluetoothDevice, serviceUuid: String, characteristicUuid: String, data: ByteArray) {
        runOnUiThread {
            val speedCheckServiceUuid = BleGattServerController.SPEED_CHECK_SERVICE_UUID.toString()
            val speedCheckCharacteristicUuid = BleGattServerController.SPEED_CHECK_CHARACTERISTIC_UUID.toString()
            
            // Check if this is speed check data
            if (serviceUuid == speedCheckServiceUuid && characteristicUuid == speedCheckCharacteristicUuid) {
                // Check if this is a control message (speed check start)
                val totalPackets = BleGattServerController.parseSpeedCheckControlMessage(data)
                if (totalPackets != null) {
                    // This is a control message - reset/initialize speed check state
                    Log.d(TAG, "onDataReceived: Speed check control message received: totalPackets=$totalPackets")
                    speedCheckStateByClient[clientDevice.address] = ServerSpeedCheckState(
                        isRunning = true,
                        packetsReceived = 0,
                        totalPackets = totalPackets,
                        bytesReceived = 0,
                        startTime = System.currentTimeMillis(),
                        lastUpdateTime = System.currentTimeMillis()
                    )
                } else {
                    // This is a data packet - update speed check state
                    val currentState = speedCheckStateByClient[clientDevice.address]
                    if (currentState != null && currentState.isRunning) {
                        val newState = currentState.copy(
                            packetsReceived = currentState.packetsReceived + 1,
                            bytesReceived = currentState.bytesReceived + data.size,
                            lastUpdateTime = System.currentTimeMillis()
                        )
                        speedCheckStateByClient[clientDevice.address] = newState
                    } else {
                        // No active speed check, but received data - initialize state
                        speedCheckStateByClient[clientDevice.address] = ServerSpeedCheckState(
                            isRunning = true,
                            packetsReceived = 1,
                            totalPackets = 0, // Unknown total
                            bytesReceived = data.size.toLong(),
                            startTime = System.currentTimeMillis(),
                            lastUpdateTime = System.currentTimeMillis()
                        )
                    }
                }
            } else {
                // Store regular data (not speed check)
                val dataString = data.joinToString(" ") { "%02X".format(it) }
                val clientMap = receivedDataHistoryByClientServiceAndCharacteristic.getOrPut(clientDevice.address) { mutableMapOf() }
                val serviceMap = clientMap.getOrPut(serviceUuid) { mutableMapOf() }
                val history = serviceMap.getOrPut(characteristicUuid) { StringBuilder() }
                history.append("${System.currentTimeMillis()}: $dataString\n")
                if (history.length > 1000) {
                    history.delete(0, history.length - 1000)
                }
            }
            
            // Convert nested map to Map<String, Map<String, Map<String, String>>> for state
            // Filter out speed check service/characteristic
            val dataReceivedByClientServiceAndCharacteristic = receivedDataHistoryByClientServiceAndCharacteristic.mapValues { clientMap ->
                clientMap.value.filterKeys { it != speedCheckServiceUuid }.mapValues { serviceMap ->
                    serviceMap.value.mapValues { it.value.toString() }
                }
            }
            
            // Convert speed check state map
            val speedCheckStateByClientMap = speedCheckStateByClient.toMap()
            
            gattServerState = gattServerState.copy(
                dataReceivedByClientServiceAndCharacteristic = dataReceivedByClientServiceAndCharacteristic,
                speedCheckStateByClient = speedCheckStateByClientMap
            )
        }
    }

    private fun updateGattServerUi() {
        val running = gattServerController.isRunning
        val address = gattServerController.getServerAddress()
        val clientCount = gattServerController.connectedClientCount
        val connectedClients = gattServerController.getConnectedClients()
        
        // Convert nested map to Map<String, Map<String, Map<String, String>>> for state
        // Filter out speed check service/characteristic
        val speedCheckServiceUuid = BleGattServerController.SPEED_CHECK_SERVICE_UUID.toString()
        val dataReceivedByClientServiceAndCharacteristic = receivedDataHistoryByClientServiceAndCharacteristic.mapValues { clientMap ->
            clientMap.value.filterKeys { it != speedCheckServiceUuid }.mapValues { serviceMap ->
                serviceMap.value.mapValues { it.value.toString() }
            }
        }
        
        // Convert speed check state map
        val speedCheckStateByClientMap = speedCheckStateByClient.toMap()
        
        gattServerState = gattServerState.copy(
            isRunning = running,
            serverAddress = address,
            connectedClientCount = clientCount,
            connectedClients = connectedClients,
            dataReceivedByClientServiceAndCharacteristic = dataReceivedByClientServiceAndCharacteristic,
            speedCheckStateByClient = speedCheckStateByClientMap,
            speedCheckEnabled = gattServerController.getSpeedCheckEnabled(),
            // Clear errors when server state changes
            uuidError = if (!running) null else gattServerState.uuidError,
            characteristics = if (!running) gattServerState.characteristics else gattServerState.characteristics.map { it.copy(uuidError = null) },
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
            val rootView = window.decorView.rootView
            Snackbar.make(
                rootView,
                getString(R.string.connected_devices_address_invalid),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        if (connectionController.connectToDevice(bluetoothDevice)) {
            Log.i(TAG, "connectToDeviceByAddress: Connection initiated to $address")
            // Navigate to connected devices view if not already there
            if (currentDestination != NavigationDestination.CONNECTED_DEVICES) {
                currentDestination = NavigationDestination.CONNECTED_DEVICES
            }
            // Device info is now stored in the controller
            updateConnectedDevicesUi()
        } else {
            Log.w(TAG, "connectToDeviceByAddress: Failed to initiate connection to $address")
            val rootView = window.decorView.rootView
            Snackbar.make(
                rootView,
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
            val rootView = window.decorView.rootView
            Snackbar.make(
                rootView,
                getString(R.string.connection_error),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun updateConnectedDevicesUi() {
        connectedDevicesState = connectionController.getConnectedDevices()
    }
}

