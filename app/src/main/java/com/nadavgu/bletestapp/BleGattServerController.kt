package com.nadavgu.bletestapp

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
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

    fun startServer(): Boolean {
        if (isServerRunning) {
            return false
        }
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            listener.onServerError(-2) // Bluetooth not enabled
            return false
        }

        return try {
            // Create server manager
            serverManager = MyBleServerManager(context)

            // Start the server - initializeServer() will be called automatically
            serverManager!!.open()
            true
        } catch (e: SecurityException) {
            listener.onServerError(-6) // Permission denied
            false
        } catch (e: Exception) {
            listener.onServerError(-1) // Generic error
            false
        }
    }

    fun stopServer(): Boolean {
        if (!isServerRunning) return false
        
        return try {
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
