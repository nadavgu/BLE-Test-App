package com.nadavgu.bletestapp.server

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.nadavgu.bletestapp.server.spec.BleServerSpec
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.BleServerManager

class ClientConnectionsManager(private val context: Context,
                               private val listener: BleServerListener,
                               private val serverSpec: BleServerSpec,
                               private val serverManager: BleServerManager) {
    companion object {
        private const val TAG = "ClientConnectionsManager"
    }

    private val connectedClients = mutableMapOf<String, ConnectedClient>()
    private val clientManagers = mutableMapOf<String, BleManager>()

    fun getConnectedClients(): List<ConnectedClient> {
        return connectedClients.values.toList()
    }

    fun close() {
        // Close all client managers
        clientManagers.values.forEach { it.close() }
        clientManagers.clear()

        val clientCount = connectedClients.size
        connectedClients.clear()

        Log.i(TAG, "close: disconnected $clientCount clients")
    }

    fun onClientConnected(device: BluetoothDevice) {
        val address = device.address
        val name = try {
            device.name ?: "Unknown"
        } catch (_: SecurityException) {
            "Unknown"
        }
        Log.i(TAG, "onClientConnected: $address ($name)")

        // Store client information
        connectedClients[address] = ConnectedClient(address, name)

        // Attach a BleManager to this client connection
        // The nordicsemi library's BleServerManager manages the server-side connection,
        // but we create a BleManager to interact with the client
        val manager = ConnectedClientBleManager(context, device, serverSpec) { device, data ->
            listener.onDataReceived(data.value ?: byteArrayOf())
        }
        manager.useServer(serverManager)
        manager.attachClientConnection(device)

        clientManagers[address] = manager

        // Note: The server manager already handles the connection, so we don't need to
        // explicitly connect the manager. It's attached for potential future interactions.
        Log.d(TAG, "onClientConnected: Attached BleManager to client $address")
    }

    fun onClientDisconnected(device: BluetoothDevice) {
        val address = device.address
        Log.i(TAG, "onClientDisconnected: $address")

        // Remove client and its manager
        connectedClients.remove(address)
        clientManagers[address]?.close()
        clientManagers.remove(address)
    }
}