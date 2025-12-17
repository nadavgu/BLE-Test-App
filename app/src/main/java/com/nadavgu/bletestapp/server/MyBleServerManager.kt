package com.nadavgu.bletestapp.server

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleServerManager
import no.nordicsemi.android.ble.observer.ServerObserver
import java.util.UUID

class MyBleServerManager(private val services: List<BluetoothGattService>,
                         context: Context,
                         serverObserver: ServerObserver) : BleServerManager(context) {
    init {
        setServerObserver(serverObserver)
    }

    companion object {
        private const val TAG = "MyBleServerManager"

        fun create(context: Context, serverObserver: ServerObserver,
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
                serverObserver = serverObserver,
                services = listOf(service),
            )
        }
    }


    override fun initializeServer(): List<BluetoothGattService> {
        return services
    }
}
