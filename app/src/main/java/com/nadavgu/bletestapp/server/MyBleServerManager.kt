package com.nadavgu.bletestapp.server

import android.bluetooth.BluetoothGattService
import android.content.Context
import no.nordicsemi.android.ble.BleServerManager
import no.nordicsemi.android.ble.observer.ServerObserver

class MyBleServerManager(private val services: List<BluetoothGattService>,
                         context: Context) : BleServerManager(context) {
    override fun initializeServer(): List<BluetoothGattService> {
        return services
    }
}
