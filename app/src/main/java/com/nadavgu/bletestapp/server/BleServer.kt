package com.nadavgu.bletestapp.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleServerManager
import no.nordicsemi.android.ble.observer.ServerObserver
import java.util.UUID
import kotlin.collections.forEach

class BleServer(private val manager: BleServerManager,
                private val clientConnectionsManager: ClientConnectionsManager,
                listener: BleServerListener) {
    private val serverListener = CompoundBleServerListener(
        ClientManagerServerListener(clientConnectionsManager),
        listener
    )

    private var isRunning = false

    fun isRunning() = isRunning

    companion object {
        private const val TAG = "BleServer"

        private fun createManager(context: Context,
                   serviceUuid: UUID,
                   characteristicUuids: List<UUID>): MyBleServerManager {
            Log.d(TAG, "initializeServer: Creating GATT service with UUID=$serviceUuid")
            // Create GATT service
            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // Create characteristics with read, write, and notify properties
            characteristicUuids.forEach { uuid ->
                val characteristic = BluetoothGattCharacteristic(
                    uuid,
                    BluetoothGattCharacteristic.PROPERTY_READ or
                            BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ or
                            BluetoothGattCharacteristic.PERMISSION_WRITE
                )

                // Add descriptor for notifications
                val descriptor = BluetoothGattDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"), // CLIENT_CHARACTERISTIC_CONFIG_UUID
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                characteristic.addDescriptor(descriptor)

                service.addCharacteristic(characteristic)
                Log.d(TAG, "initializeServer: Added characteristic with UUID=$uuid")
            }

            Log.d(TAG, "initializeServer: Service initialized with ${service.characteristics.size} characteristics")

            return MyBleServerManager(
                context = context,
                services = listOf(service),
            )
        }

        fun open(context: Context,
                 serviceUuid: UUID,
                 characteristicUuids: List<UUID>,
                 listener: BleServerListener): BleServer {
            val manager = createManager(context, serviceUuid, characteristicUuids)
            val clientConnectionsManager = ClientConnectionsManager(context, manager)
            val server = BleServer(manager, clientConnectionsManager, listener)

            manager.setServerObserver(server.connectionObserver)
            manager.open()
            return server
        }
    }

    fun close() {
        manager.close()
        clientConnectionsManager.close()
        isRunning = false
    }

    fun getConnectedClients() = clientConnectionsManager.getConnectedClients()

    private val connectionObserver = object : ServerObserver {
        override fun onServerReady() {
            Log.i(TAG, "onServerReady: GATT server is ready")
            isRunning = true
            serverListener.onServerStarted()
        }

        override fun onDeviceConnectedToServer(device: BluetoothDevice) {
            serverListener.onClientConnected(device)
        }

        override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
            serverListener.onClientDisconnected(device)
        }
    }
}