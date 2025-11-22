package com.nadavgu.bletestapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver

class BleConnectionController(
    private val context: Context,
    private val listener: Listener
) {
    private val bleRequirements = BleRequirements(context)
    private val connections = mutableMapOf<String, MyBleManager>()
    private val connectedDevices = mutableMapOf<String, ConnectedDevice>()

    interface Listener {
        fun onDeviceConnected(address: String, name: String)
        fun onDeviceDisconnected(address: String)
        fun onConnectionFailed(address: String, errorCode: Int)
    }

    private inner class MyBleManager(context: Context) : BleManager(context) {
        override fun getMinLogPriority(): Int = android.util.Log.DEBUG

        override fun log(priority: Int, message: String) {
            // Logging can be implemented if needed
        }
        
        // Override to ensure connection stays alive
        // Without services to discover, we still want to maintain the connection
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Boolean {
        if (!bleRequirements.hasAllPermissions()) {
            return false
        }

        val address = device.address
        if (connections.containsKey(address)) {
            // Already connecting or connected
            return false
        }

        return try {
            val deviceName = try {
                device.name ?: "Unknown"
            } catch (_: SecurityException) {
                "Unknown"
            }
            
            // Store device info as connecting
            connectedDevices[address] = ConnectedDevice(address, deviceName, isConnecting = true)
            
            val manager = MyBleManager(context)
            manager.setConnectionObserver(object : ConnectionObserver {
                override fun onDeviceConnecting(device: BluetoothDevice) {
                    // Device is connecting
                }

                override fun onDeviceConnected(device: BluetoothDevice) {
                    // Connection established, but wait for onDeviceReady before reporting
                }

                override fun onDeviceDisconnecting(device: BluetoothDevice) {
                    // Device is disconnecting
                }

                override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                    connections.remove(device.address)
                    connectedDevices.remove(device.address)
                    listener.onDeviceDisconnected(device.address)
                }

                override fun onDeviceReady(device: BluetoothDevice) {
                    // Device is ready for communication - now report as connected
                    val name = try {
                        device.name ?: connectedDevices[device.address]?.name ?: "Unknown"
                    } catch (_: SecurityException) {
                        connectedDevices[device.address]?.name ?: "Unknown"
                    }
                    connectedDevices[device.address] = ConnectedDevice(device.address, name, isConnecting = false)
                    listener.onDeviceConnected(device.address, name)
                }

                override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                    connections.remove(device.address)
                    connectedDevices.remove(device.address)
                    listener.onConnectionFailed(device.address, reason)
                }
            })

            manager.connect(device)
                .retry(3, 100)
                .useAutoConnect(false)
                .enqueue()

            connections[address] = manager
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun disconnectDevice(address: String): Boolean {
        val manager = connections[address] ?: return false
        // Don't remove from map immediately - let the callback handle it
        manager.disconnect().enqueue()
        return true
    }

    fun getConnectedDevices(): List<ConnectedDevice> {
        return connectedDevices.values.toList()
    }

    fun disconnectAll() {
        connections.keys.toList().forEach { address ->
            disconnectDevice(address)
        }
    }
}

