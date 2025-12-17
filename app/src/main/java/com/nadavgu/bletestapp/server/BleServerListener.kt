package com.nadavgu.bletestapp.server

import android.bluetooth.BluetoothDevice

interface BleServerListener {
    fun onServerStarted() {}
    fun onServerStopped() {}
    fun onServerError(errorCode: Int) {}
    fun onClientConnected(device: BluetoothDevice) {}
    fun onClientDisconnected(device: BluetoothDevice) {}
    fun onDataReceived(data: ByteArray) {}
}
