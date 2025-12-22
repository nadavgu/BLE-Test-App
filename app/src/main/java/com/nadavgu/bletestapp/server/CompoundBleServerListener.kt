package com.nadavgu.bletestapp.server

import android.bluetooth.BluetoothDevice

class CompoundBleServerListener(private vararg val listeners: BleServerListener) : BleServerListener {
    override fun onServerStopped() {
        listeners.forEach { it.onServerStopped() }
    }

    override fun onServerStarted() {
        listeners.forEach { it.onServerStarted() }
    }

    override fun onServerError(errorCode: Int) {
        listeners.forEach { it.onServerError(errorCode) }
    }

    override fun onClientConnected(device: BluetoothDevice) {
        listeners.forEach { it.onClientConnected(device) }
    }

    override fun onClientDisconnected(device: BluetoothDevice) {
        listeners.forEach { it.onClientDisconnected(device) }
    }

    override fun onDataReceived(clientDevice: BluetoothDevice, data: ByteArray) {
        listeners.forEach { it.onDataReceived(clientDevice, data) }
    }
}
