package com.nadavgu.bletestapp.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.nadavgu.bletestapp.BleRequirements
import com.nadavgu.bletestapp.server.spec.BleServerSpec
import com.nadavgu.bletestapp.server.spec.BleServiceSpec
import java.util.UUID

data class ConnectedClient(
    val address: String,
    val name: String
)

class BleGattServerController(
    private val context: Context,
    private val listener: BleServerListener
) {
    companion object {
        private const val TAG = "BleGattServerController"
        
        // Hardcoded Speed Check Service and Characteristic UUIDs
        val SPEED_CHECK_SERVICE_UUID = UUID.fromString("0000FF00-0000-1000-8000-00805F9B34FB")
        val SPEED_CHECK_CHARACTERISTIC_UUID = UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")
    }
    
    private val bleRequirements = BleRequirements(context)

    // Service UUID - can be set by user
    private var serviceUuid: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    
    // Manufacturer data - can be set by user
    private var manufacturerId: Int? = null
    private var manufacturerData: ByteArray? = null
    
    // Characteristic UUIDs (configurable, multiple)
    private var characteristicUuids: List<UUID> = listOf(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB"))
    
    // Speed check service toggle (hardcoded, cannot be removed)
    private var speedCheckEnabled: Boolean = true

    private var server: BleServer? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "onStartSuccess: Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "onStartFailure: Advertising failed with errorCode=$errorCode")
            listener.onServerError(errorCode)
        }
    }

    val isRunning: Boolean
        get() = server?.isRunning() ?: false

    val connectedClientCount: Int
        get() = server?.getConnectedClients()?.size ?: 0

    fun getConnectedClients() = server?.getConnectedClients() ?: emptyList()

    fun setServiceUuid(uuid: UUID) {
        if (isRunning) {
            Log.w(TAG, "setServiceUuid: Cannot change UUID while server is running")
            return
        }
        Log.d(TAG, "setServiceUuid: Setting service UUID to $uuid")
        serviceUuid = uuid
    }

    fun getServiceUuid(): UUID = serviceUuid

    fun getCharacteristicUuids(): List<UUID> = characteristicUuids

    fun getManufacturerId(): Int? = manufacturerId
    
    fun getManufacturerData(): ByteArray? = manufacturerData

    fun setManufacturerData(manufacturerId: Int?, data: ByteArray?): Boolean {
        if (isRunning) {
            Log.w(TAG, "setManufacturerData: Cannot change manufacturer data while server is running")
            listener.onServerError(-7) // Custom error code for "cannot change while running"
            return false
        }
        Log.d(TAG, "setManufacturerData: Setting manufacturer ID=$manufacturerId, data size=${data?.size ?: 0}")
        this.manufacturerId = manufacturerId
        this.manufacturerData = data
        return true
    }

    fun setCharacteristicUuids(uuids: List<UUID>): Boolean {
        if (isRunning) {
            Log.w(TAG, "setCharacteristicUuids: Cannot change characteristics while server is running")
            return false
        }
        Log.d(TAG, "setCharacteristicUuids: Setting ${uuids.size} characteristic UUIDs")
        this.characteristicUuids = uuids
        return true
    }
    
    fun setSpeedCheckEnabled(enabled: Boolean): Boolean {
        if (isRunning) {
            Log.w(TAG, "setSpeedCheckEnabled: Cannot change speed check setting while server is running")
            return false
        }
        Log.d(TAG, "setSpeedCheckEnabled: Setting speed check enabled=$enabled")
        this.speedCheckEnabled = enabled
        return true
    }
    
    fun getSpeedCheckEnabled(): Boolean = speedCheckEnabled

    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        Log.d(TAG, "startServer: Attempting to start GATT server")
        if (isRunning) {
            Log.w(TAG, "startServer: Server already running")
            return false
        }
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "startServer: Bluetooth not enabled")
            listener.onServerError(-2) // Bluetooth not enabled
            return false
        }

        // Check if BLE advertising is supported
        if (bluetoothAdapter?.bluetoothLeAdvertiser == null) {
            Log.e(TAG, "startServer: BLE advertising not supported")
            listener.onServerError(-3) // Advertising not supported
            return false
        }

        return try {
            Log.i(TAG, "startServer: Creating server manager with service UUID=$serviceUuid")
            // Build list of services
            val services = mutableListOf<BleServiceSpec>()
            
            // Add user-configured service
            services.add(BleServiceSpec(serviceUuid, characteristicUuids))
            
            // Add speed check service if enabled
            if (speedCheckEnabled) {
                Log.d(TAG, "startServer: Adding speed check service")
                services.add(BleServiceSpec(
                    SPEED_CHECK_SERVICE_UUID,
                    listOf(SPEED_CHECK_CHARACTERISTIC_UUID)
                ))
            }
            
            // Create server manager
            val spec = BleServerSpec(services)
            server = BleServer.open(context, spec, listener)

            Log.d(TAG, "startServer: Server manager opened, starting advertising")
            
            // Start advertising so the server can be discovered
            startAdvertising()
            
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "startServer: SecurityException", e)
            listener.onServerError(-6) // Permission denied
            false
        } catch (e: Exception) {
            Log.e(TAG, "startServer: Exception", e)
            listener.onServerError(-1) // Generic error
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "startAdvertising: No advertiser available")
            return
        }

        Log.d(TAG, "startAdvertising: Configuring advertisement with service UUID=$serviceUuid")
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUuid))
        
        // Add manufacturer data if provided
        if (manufacturerId != null && manufacturerData != null) {
            Log.d(TAG, "startAdvertising: Adding manufacturer data (ID=$manufacturerId, size=${manufacturerData!!.size})")
            dataBuilder.addManufacturerData(manufacturerId!!, manufacturerData!!)
        }

        Log.i(TAG, "startAdvertising: Starting BLE advertisement")
        advertiser.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
        isAdvertising = true
    }

    @SuppressLint("MissingPermission")
    fun stopServer(): Boolean {
        Log.d(TAG, "stopServer: Attempting to stop GATT server")
        if (!isRunning) {
            Log.w(TAG, "stopServer: Server not running")
            return false
        }
        
        return try {
            // Stop advertising
            if (isAdvertising) {
                Log.d(TAG, "stopServer: Stopping advertisement")
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
            }
            
            Log.d(TAG, "stopServer: Closing server manager")
            server?.close()
            server = null

            Log.i(TAG, "stopServer: Server stopped")
            listener.onServerStopped()
            true
        } catch (e: Exception) {
            Log.e(TAG, "stopServer: Exception", e)
            false
        }
    }

    fun getServerAddress(): String? {
        if (!bleRequirements.hasAllPermissions()) {
            Log.w(TAG, "getServerAddress: Missing permissions")
            return null
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val address = bluetoothAdapter?.address
        Log.v(TAG, "getServerAddress: $address")
        return address
    }
}
