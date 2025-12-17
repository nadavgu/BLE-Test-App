package com.nadavgu.bletestapp.server

import android.bluetooth.BluetoothDevice

class ClientManagerServerListener(private val clientConnectionsManager: ClientConnectionsManager)
    : BleServerListener {
    override fun onClientConnected(device: BluetoothDevice) {
        clientConnectionsManager.onClientConnected(device)
    }

    override fun onClientDisconnected(device: BluetoothDevice) {
        clientConnectionsManager.onClientDisconnected(device)
    }
}