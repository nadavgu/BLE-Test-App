package com.nadavgu.bletestapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

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
        fun onDeviceDisconnected(address: String, reason: Int)
        fun onConnectionFailed(address: String, errorCode: Int)
    }

    private inner class MyBleManager(context: Context) : BleManager(context) {
        @SuppressLint("MissingPermission")
        private var cachedServices: List<BluetoothGattService> = emptyList()
        
        override fun getMinLogPriority(): Int = Log.DEBUG

        override fun log(priority: Int, message: String) {
            Log.println(priority, TAG, message)
        }

        @SuppressLint("MissingPermission")
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            // Don't require any specific services - just maintain the connection
            // This prevents disconnection with REASON_NOT_SUPPORTED when no services are required
            // Cache services when they're discovered
            cachedServices = gatt.services ?: emptyList()
            Log.d(TAG, "isRequiredServiceSupported: Cached ${cachedServices.size} services")
            return true
        }

        fun getDiscoveredServices() = cachedServices
        
        fun writeCharacteristic(
            serviceUuid: UUID,
            characteristicUuid: UUID,
            data: ByteArray,
            writeType: Int
        ): Boolean {
            val service = cachedServices.find { it.uuid == serviceUuid }
            val characteristic = service?.characteristics?.find { it.uuid == characteristicUuid }
            
            if (characteristic == null) {
                Log.w(TAG, "writeCharacteristic: Characteristic not found")
                return false
            }
            
            // Set write type if needed
            if (writeType != BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                characteristic.writeType = writeType
            }

            // Use the protected method from parent class
            writeCharacteristic(characteristic, data, writeType).enqueue()
            return true
        }
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
            connectedDevices[address] = ConnectedDevice(
                address = address,
                name = deviceName,
                isConnecting = true,
                isDisconnected = false,
                disconnectionReason = null
            )
            
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
                    // Mark device as disconnected instead of removing it
                    val existingDevice = connectedDevices[device.address]
                    if (existingDevice != null) {
                        connectedDevices[device.address] = existingDevice.copy(
                            isDisconnected = true,
                            disconnectionReason = reason,
                            isConnecting = false
                        )
                    }
                    listener.onDeviceDisconnected(device.address, reason)
                }

                override fun onDeviceReady(device: BluetoothDevice) {
                    val name = try {
                        device.name ?: connectedDevices[device.address]?.name ?: "Unknown"
                    } catch (_: SecurityException) {
                        connectedDevices[device.address]?.name ?: "Unknown"
                    }
                    Log.i(TAG, "onDeviceReady: ${device.address} ($name) - connection fully established")
                    
                    // Services are automatically discovered when device is ready
                    // Extract them directly from the manager
                    val services = extractServices(device.address)
                    connectedDevices[device.address] = ConnectedDevice(
                        address = device.address,
                        name = name,
                        isConnecting = false,
                        isDisconnected = false,
                        disconnectionReason = null,
                        services = services
                    )
                    Log.d(TAG, "onDeviceReady: Found ${services.size} services for ${device.address}")
                    listener.onDeviceConnected(device.address, name)
                }
                
                @SuppressLint("MissingPermission")
                private fun extractServices(address: String): List<GattService> {
                    val services = mutableListOf<GattService>()
                    val manager = connections[address] as? MyBleManager ?: return emptyList()
                    
                    try {
                        // Access services through the manager's public method
                        val gattServices = manager.getDiscoveredServices()
                        
                        gattServices.forEach { service: BluetoothGattService ->
                            val characteristics = mutableListOf<GattCharacteristic>()
                            val serviceCharacteristics = service.characteristics ?: emptyList()
                            
                            serviceCharacteristics.forEach { characteristic: BluetoothGattCharacteristic ->
                                characteristics.add(
                                    GattCharacteristic(
                                        uuid = characteristic.uuid,
                                        properties = characteristic.properties,
                                        permissions = characteristic.permissions
                                    )
                                )
                            }
                            
                            services.add(
                                GattService(
                                    uuid = service.uuid,
                                    type = service.type,
                                    characteristics = characteristics
                                )
                            )
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "extractServices: SecurityException", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "extractServices: Exception", e)
                    }
                    
                    return services
                }

                override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                    Log.e(TAG, "onDeviceFailedToConnect: ${device.address}, reason=$reason")
                    connections.remove(device.address)
                    connectedDevices.remove(device.address)
                    listener.onConnectionFailed(device.address, reason)
                }
            })
            
            // Store manager reference for service extraction
            connections[address] = manager

            manager.connect(device)
                .retry(3, 100)
                .useAutoConnect(false)
                .enqueue()
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
            // If device is already disconnected, remove it from the list
            if (connectedDevices[address]?.isDisconnected == true) {
                connectedDevices.remove(address)
            }
            return false
        }
        Log.i(TAG, "disconnectDevice: Disconnecting $address")
        // Don't remove from map immediately - let the callback handle it
        manager.disconnect().enqueue()
        return true
    }
    
    fun removeDisconnectedDevice(address: String) {
        if (connectedDevices[address]?.isDisconnected == true) {
            Log.d(TAG, "removeDisconnectedDevice: Removing disconnected device $address")
            connectedDevices.remove(address)
        }
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

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        deviceAddress: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean {
        val manager = connections[deviceAddress] as? MyBleManager ?: run {
            Log.w(TAG, "writeCharacteristic: No connection found for $deviceAddress")
            return false
        }

        if (!bleRequirements.hasAllPermissions()) {
            Log.w(TAG, "writeCharacteristic: Missing permissions")
            return false
        }

        return try {
            Log.d(TAG, "writeCharacteristic: Writing to $deviceAddress, service=$serviceUuid, characteristic=$characteristicUuid, data size=${data.size}")
            
            val success = manager.writeCharacteristic(serviceUuid, characteristicUuid, data, writeType)
            
            if (success) {
                Log.d(TAG, "writeCharacteristic: Write request enqueued")
            }
            success
        } catch (e: SecurityException) {
            Log.e(TAG, "writeCharacteristic: SecurityException", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "writeCharacteristic: Exception", e)
            false
        }
    }
}

