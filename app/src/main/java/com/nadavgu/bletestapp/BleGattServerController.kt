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
import no.nordicsemi.android.ble.BleServerManager
import no.nordicsemi.android.ble.observer.ServerObserver
import java.util.UUID

class BleGattServerController(
    private val context: Context,
    private val listener: Listener
) {
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
    
    // Characteristic UUIDs
    private val READ_WRITE_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    private val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("00002A1A-0000-1000-8000-00805F9B34FB")

    private var serverManager: MyBleServerManager? = null
    private var isServerRunning = false
    private val connectedClients = mutableSetOf<String>()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
        }

        override fun onStartFailure(errorCode: Int) {
            listener.onServerError(errorCode)
        }
    }

    private inner class MyBleServerManager(context: Context) : BleServerManager(context) {
        init {
            setServerObserver(connectionObserver)
        }

        override fun initializeServer(): List<BluetoothGattService> {
            // Create GATT service
            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // Create read/write characteristic
            val readWriteCharacteristic = BluetoothGattCharacteristic(
                READ_WRITE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // Create notify characteristic
            val notifyCharacteristic = BluetoothGattCharacteristic(
                NOTIFY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            // Add descriptor for notifications
            val descriptor = BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"), // CLIENT_CHARACTERISTIC_CONFIG_UUID
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            notifyCharacteristic.addDescriptor(descriptor)

            service.addCharacteristic(readWriteCharacteristic)
            service.addCharacteristic(notifyCharacteristic)

            return listOf(service)
        }
    }

    private val connectionObserver = object : ServerObserver {
        override fun onServerReady() {
            isServerRunning = true
            listener.onServerStarted()
        }

        override fun onDeviceConnectedToServer(device: BluetoothDevice) {
            val address = device.address
            connectedClients.add(address)
            listener.onClientConnected(address)
        }

        override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
            val address = device.address
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
            // Cannot change UUID while server is running
            return
        }
        serviceUuid = uuid
    }

    fun getServiceUuid(): UUID = serviceUuid

    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        if (isServerRunning) {
            return false
        }
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            listener.onServerError(-2) // Bluetooth not enabled
            return false
        }

        // Check if BLE advertising is supported
        if (bluetoothAdapter?.bluetoothLeAdvertiser == null) {
            listener.onServerError(-3) // Advertising not supported
            return false
        }

        return try {
            // Create server manager
            serverManager = MyBleServerManager(context)

            // Start the server - initializeServer() will be called automatically
            serverManager!!.open()
            
            // Start advertising so the server can be discovered
            startAdvertising()
            
            true
        } catch (e: SecurityException) {
            listener.onServerError(-6) // Permission denied
            false
        } catch (e: Exception) {
            listener.onServerError(-1) // Generic error
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
        isAdvertising = true
    }

    @SuppressLint("MissingPermission")
    fun stopServer(): Boolean {
        if (!isServerRunning) return false
        
        return try {
            // Stop advertising
            if (isAdvertising) {
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
            }
            
            serverManager?.close()
            serverManager = null
            isServerRunning = false
            connectedClients.clear()
            listener.onServerStopped()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getServerAddress(): String? {
        if (!bleRequirements.hasAllPermissions()) {
            return null
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothAdapter?.address
    }
}
