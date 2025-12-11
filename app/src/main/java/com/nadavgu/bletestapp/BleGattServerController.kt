package com.nadavgu.bletestapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import no.nordicsemi.android.ble.BleServerManager
import no.nordicsemi.android.ble.observer.ServerObserver
import java.util.UUID

class BleGattServerController(
    private val context: Context,
    private val listener: Listener
) {
    companion object {
        private const val TAG = "BleGattServerController"
    }
    
    private val bleRequirements = BleRequirements(context)

    interface Listener {
        fun onServerStarted()
        fun onServerStopped()
        fun onServerError(errorCode: Int)
        fun onClientConnected(address: String)
        fun onClientDisconnected(address: String)
        fun onDataReceived(data: ByteArray)
    }

    // Service UUID - can be set by user
    private var serviceUuid: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    
    // Manufacturer data - can be set by user
    private var manufacturerId: Int? = null
    private var manufacturerData: ByteArray? = null
    
    // Characteristic UUIDs (configurable)
    private var readWriteCharacteristicUuid: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    private var notifyCharacteristicUuid: UUID = UUID.fromString("00002A1A-0000-1000-8000-00805F9B34FB")
    private var includeNotifyCharacteristic: Boolean = true

    private var serverManager: MyBleServerManager? = null
    private var isServerRunning = false
    private val connectedClients = mutableSetOf<String>()
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

    private inner class MyBleServerManager(context: Context) : BleServerManager(context) {
        init {
            setServerObserver(connectionObserver)
        }

        override fun initializeServer(): List<BluetoothGattService> {
            Log.d(TAG, "initializeServer: Creating GATT service with UUID=$serviceUuid")
            // Create GATT service
            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // Create read/write characteristic
            val readWriteCharacteristic = BluetoothGattCharacteristic(
                readWriteCharacteristicUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // Create notify characteristic
            if (includeNotifyCharacteristic) {
                val notifyCharacteristic = BluetoothGattCharacteristic(
                    notifyCharacteristicUuid,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )

                // Add descriptor for notifications
                val descriptor = BluetoothGattDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"), // CLIENT_CHARACTERISTIC_CONFIG_UUID
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                notifyCharacteristic.addDescriptor(descriptor)

                service.addCharacteristic(notifyCharacteristic)
            }

            service.addCharacteristic(readWriteCharacteristic)

            Log.d(TAG, "initializeServer: Service initialized with ${service.characteristics.size} characteristics")
            return listOf(service)
        }
    }

    private val connectionObserver = object : ServerObserver {
        override fun onServerReady() {
            Log.i(TAG, "onServerReady: GATT server is ready")
            isServerRunning = true
            listener.onServerStarted()
        }

        override fun onDeviceConnectedToServer(device: BluetoothDevice) {
            val address = device.address
            val name = try {
                device.name ?: "Unknown"
            } catch (_: SecurityException) {
                "Unknown"
            }
            Log.i(TAG, "onDeviceConnectedToServer: $address ($name)")
            connectedClients.add(address)
            listener.onClientConnected(address)
        }

        override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
            val address = device.address
            Log.i(TAG, "onDeviceDisconnectedFromServer: $address")
            connectedClients.remove(address)
            listener.onClientDisconnected(address)
        }
    }

    val isRunning: Boolean
        get() = isServerRunning

    val connectedClientCount: Int
        get() = connectedClients.size

    fun setServiceUuid(uuid: UUID) {
        if (isServerRunning) {
            Log.w(TAG, "setServiceUuid: Cannot change UUID while server is running")
            return
        }
        Log.d(TAG, "setServiceUuid: Setting service UUID to $uuid")
        serviceUuid = uuid
    }

    fun getServiceUuid(): UUID = serviceUuid

    fun getReadWriteCharacteristicUuid(): UUID = readWriteCharacteristicUuid

    fun getNotifyCharacteristicUuid(): UUID = notifyCharacteristicUuid

    fun isNotifyCharacteristicIncluded(): Boolean = includeNotifyCharacteristic

    fun getManufacturerId(): Int? = manufacturerId
    
    fun getManufacturerData(): ByteArray? = manufacturerData

    fun setManufacturerData(manufacturerId: Int?, data: ByteArray?): Boolean {
        if (isServerRunning) {
            Log.w(TAG, "setManufacturerData: Cannot change manufacturer data while server is running")
            listener.onServerError(-7) // Custom error code for "cannot change while running"
            return false
        }
        Log.d(TAG, "setManufacturerData: Setting manufacturer ID=$manufacturerId, data size=${data?.size ?: 0}")
        this.manufacturerId = manufacturerId
        this.manufacturerData = data
        return true
    }

    fun setCharacteristicUuids(
        readWriteUuid: UUID,
        notifyUuid: UUID,
        includeNotify: Boolean
    ): Boolean {
        if (isServerRunning) {
            Log.w(TAG, "setCharacteristicUuids: Cannot change characteristics while server is running")
            return false
        }
        Log.d(TAG, "setCharacteristicUuids: read/write=$readWriteUuid, notify=$notifyUuid, includeNotify=$includeNotify")
        this.readWriteCharacteristicUuid = readWriteUuid
        this.notifyCharacteristicUuid = notifyUuid
        this.includeNotifyCharacteristic = includeNotify
        return true
    }

    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        Log.d(TAG, "startServer: Attempting to start GATT server")
        if (isServerRunning) {
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
            // Create server manager
            serverManager = MyBleServerManager(context)

            // Start the server - initializeServer() will be called automatically
            serverManager!!.open()
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
        if (!isServerRunning) {
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
            serverManager?.close()
            serverManager = null
            isServerRunning = false
            val clientCount = connectedClients.size
            connectedClients.clear()
            Log.i(TAG, "stopServer: Server stopped (disconnected $clientCount clients)")
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
