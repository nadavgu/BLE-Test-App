package com.nadavgu.bletestapp.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.nadavgu.bletestapp.BleRequirements
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import no.nordicsemi.android.ble.observer.ServerObserver
import java.util.UUID

data class ConnectedClient(
    val address: String,
    val name: String
)

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
    
    // Characteristic UUIDs (configurable, multiple)
    private var characteristicUuids: List<UUID> = listOf(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB"))

    private var serverManager: MyBleServerManager? = null
    private var isServerRunning = false
    private val connectedClients = mutableMapOf<String, ConnectedClient>()
    private val clientManagers = mutableMapOf<String, MyBleManager>()
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
            
            // Store client information
            connectedClients[address] = ConnectedClient(address, name)
            
            // Attach a BleManager to this client connection
            // The nordicsemi library's BleServerManager manages the server-side connection,
            // but we create a BleManager to interact with the client
            val manager = MyBleManager(context, device)
            manager.attachClientConnection(device)
            clientManagers[address] = manager
            
            // Note: The server manager already handles the connection, so we don't need to
            // explicitly connect the manager. It's attached for potential future interactions.
            Log.d(TAG, "onDeviceConnectedToServer: Attached BleManager to client $address")
            
            listener.onClientConnected(address)
        }

        override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
            val address = device.address
            Log.i(TAG, "onDeviceDisconnectedFromServer: $address")
            
            // Remove client and its manager
            connectedClients.remove(address)
            clientManagers[address]?.close()
            clientManagers.remove(address)
            
            listener.onClientDisconnected(address)
        }
    }

    val isRunning: Boolean
        get() = isServerRunning

    val connectedClientCount: Int
        get() = connectedClients.size
    
    fun getConnectedClients(): List<ConnectedClient> {
        return connectedClients.values.toList()
    }
    
    fun getClientManager(address: String): BleManager? {
        return clientManagers[address]
    }
    
    // Inner class for managing client connections
    // This BleManager is attached to each client connection for potential interactions
    private inner class MyBleManager(context: Context, private val device: BluetoothDevice) : BleManager(context) {
        override fun getMinLogPriority(): Int = Log.DEBUG

        override fun log(priority: Int, message: String) {
            Log.println(priority, TAG, "[Client ${device.address}] $message")
        }

        @SuppressLint("MissingPermission")
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            // Accept any service - we're the server, so we don't need to validate services
            Log.d(TAG, "MyBleManager.isRequiredServiceSupported: Client ${device.address} connected")
            return true
        }
        
        init {
            // Set up connection observer for the manager
            setConnectionObserver(object : ConnectionObserver {
                override fun onDeviceConnecting(device: BluetoothDevice) {
                    Log.d(TAG, "MyBleManager: Client ${device.address} connecting")
                }
                
                override fun onDeviceConnected(device: BluetoothDevice) {
                    Log.d(TAG, "MyBleManager: Client ${device.address} connection established")
                }
                
                override fun onDeviceDisconnecting(device: BluetoothDevice) {
                    Log.d(TAG, "MyBleManager: Client ${device.address} disconnecting")
                }
                
                override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                    Log.d(TAG, "MyBleManager: Client ${device.address} disconnected, reason=$reason")
                }
                
                override fun onDeviceReady(device: BluetoothDevice) {
                    Log.d(TAG, "MyBleManager: Client ${device.address} ready")
                }
                
                override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                    Log.e(TAG, "MyBleManager: Client ${device.address} failed to connect, reason=$reason")
                }
            })
        }
    }

    fun setServiceUuid(uuid: UUID) {
        if (isServerRunning) {
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

    fun setCharacteristicUuids(uuids: List<UUID>): Boolean {
        if (isServerRunning) {
            Log.w(TAG, "setCharacteristicUuids: Cannot change characteristics while server is running")
            return false
        }
        Log.d(TAG, "setCharacteristicUuids: Setting ${uuids.size} characteristic UUIDs")
        this.characteristicUuids = uuids
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
            serverManager = MyBleServerManager.create(context, connectionObserver,
                serviceUuid, characteristicUuids)

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
            
            // Close all client managers
            clientManagers.values.forEach { it.close() }
            clientManagers.clear()
            
            val clientCount = connectedClients.size
            connectedClients.clear()
            isServerRunning = false
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
