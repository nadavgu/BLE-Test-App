package com.nadavgu.bletestapp.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.content.Context
import android.util.Log
import com.nadavgu.bletestapp.server.spec.BleServerSpec
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver

// This BleManager is attached to each client connection for potential interactions
class ConnectedClientBleManager(context: Context, private val device: BluetoothDevice,
                                private val serverSpec: BleServerSpec,
                                private val listener: BleServerListener) : BleManager(context) {
    companion object {
        private const val TAG = "ConnectedClientBleManager"
    }

    override fun getMinLogPriority(): Int = Log.DEBUG

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, "[Client ${device.address}] $message")
    }

    @SuppressLint("MissingPermission")
    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        // Accept any service - we're the server, so we don't need to validate services
        Log.d(TAG, "ConnectedClientBleManager.isRequiredServiceSupported: Client ${device.address} connected")
        return true
    }

    override fun onServerReady(server: BluetoothGattServer) {
        serverSpec.services.forEach { serviceSpec ->
            val service = server.getService(serviceSpec.uuid)!!
            serviceSpec.characteristicUuids.forEach { characteristicUuid ->
                val characteristic = service.getCharacteristic(characteristicUuid)!!
                val serviceUuidStr = serviceSpec.uuid.toString()
                val characteristicUuidStr = characteristicUuid.toString()
                super.setWriteCallback(characteristic).with { device, data ->
                    listener.onDataReceived(device, serviceUuidStr,
                        characteristicUuidStr, data.value ?: byteArrayOf())

                }
            }
        }
    }

    init {
        // Set up connection observer for the manager
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.d(TAG, "ConnectedClientBleManager: Client ${device.address} connecting")
            }

            override fun onDeviceConnected(device: BluetoothDevice) {
                Log.d(TAG, "ConnectedClientBleManager: Client ${device.address} connection established")
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                Log.d(TAG, "ConnectedClientBleManager: Client ${device.address} disconnecting")
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                Log.d(TAG, "ConnectedClientBleManager: Client ${device.address} disconnected, reason=$reason")
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                Log.d(TAG, "ConnectedClientBleManager: Client ${device.address} ready")
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.e(TAG, "ConnectedClientBleManager: Client ${device.address} failed to connect, reason=$reason")
            }
        })
    }
}
