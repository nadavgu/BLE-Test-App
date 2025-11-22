package com.nadavgu.bletestapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver

class BleConnectionController(
    private val context: Context,
    private val listener: Listener
) {
    private val bleRequirements = BleRequirements(context)
    private val connections = mutableMapOf<String, MyBleManager>()
    private val connectedDevices = mutableMapOf<String, ConnectedDevice>()
    
    companion object {
        private const val TAG = "BleConnectionController"
    }

    interface Listener {
        fun onDeviceConnected(address: String, name: String)
        fun onDeviceDisconnected(address: String)
        fun onConnectionFailed(address: String, errorCode: Int)
    }

    private inner class MyBleManager(context: Context) : BleManager(context) {
        override fun getMinLogPriority(): Int = android.util.Log.DEBUG

        override fun log(priority: Int, message: String) {
            Log.println(priority, TAG, message)
        }
        
        // Override to ensure connection stays alive
        // Without services to discover, we still want to maintain the connection
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Boolean {
        val address = device.address
        Log.d(TAG, "connectToDevice: Attempting to connect to $address")
        
        if (!bleRequirements.hasAllPermissions()) {
            Log.w(TAG, "connectToDevice: Missing permissions")
            return false
        }

        if (connections.containsKey(address)) {
            Log.w(TAG, "connectToDevice: Already connecting or connected to $address")
            return false
        }

        return try {
            val deviceName = try {
                device.name ?: "Unknown"
            } catch (_: SecurityException) {
                "Unknown"
            }
            
            Log.i(TAG, "connectToDevice: Initiating connection to $address ($deviceName)")
            
            // Store device info as connecting
            connectedDevices[address] = ConnectedDevice(address, deviceName, isConnecting = true)
            
            val manager = MyBleManager(context)
            manager.setConnectionObserver(object : ConnectionObserver {
                override fun onDeviceConnecting(device: BluetoothDevice) {
                    Log.d(TAG, "onDeviceConnecting: ${device.address}")
                }

                override fun onDeviceConnected(device: BluetoothDevice) {
                    Log.d(TAG, "onDeviceConnected: ${device.address} - waiting for ready state")
                }

                override fun onDeviceDisconnecting(device: BluetoothDevice) {
                    Log.d(TAG, "onDeviceDisconnecting: ${device.address}")
                }

                override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                    Log.i(TAG, "onDeviceDisconnected: ${device.address}, reason=$reason")
                    connections.remove(device.address)
                    connectedDevices.remove(device.address)
                    listener.onDeviceDisconnected(device.address)
                }

                override fun onDeviceReady(device: BluetoothDevice) {
                    val name = try {
                        device.name ?: connectedDevices[device.address]?.name ?: "Unknown"
                    } catch (_: SecurityException) {
                        connectedDevices[device.address]?.name ?: "Unknown"
                    }
                    Log.i(TAG, "onDeviceReady: ${device.address} ($name) - connection fully established")
                    connectedDevices[device.address] = ConnectedDevice(device.address, name, isConnecting = false)
                    listener.onDeviceConnected(device.address, name)
                }

                override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                    Log.e(TAG, "onDeviceFailedToConnect: ${device.address}, reason=$reason")
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
            Log.d(TAG, "connectToDevice: Connection request enqueued for $address")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "connectToDevice: SecurityException", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "connectToDevice: Exception", e)
            false
        }
    }

    fun disconnectDevice(address: String): Boolean {
        val manager = connections[address] ?: run {
            Log.w(TAG, "disconnectDevice: No connection found for $address")
            return false
        }
        Log.i(TAG, "disconnectDevice: Disconnecting $address")
        // Don't remove from map immediately - let the callback handle it
        manager.disconnect().enqueue()
        return true
    }

    fun getConnectedDevices(): List<ConnectedDevice> {
        val devices = connectedDevices.values.toList()
        Log.v(TAG, "getConnectedDevices: Returning ${devices.size} devices")
        return devices
    }

    fun disconnectAll() {
        val count = connections.size
        Log.i(TAG, "disconnectAll: Disconnecting $count devices")
        connections.keys.toList().forEach { address ->
            disconnectDevice(address)
        }
    }
}

