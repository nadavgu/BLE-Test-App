package com.nadavgu.bletestapp

import java.util.UUID

data class GattCharacteristic(
    val uuid: UUID,
    val properties: Int,
    val permissions: Int
)

data class GattService(
    val uuid: UUID,
    val type: Int,
    val characteristics: List<GattCharacteristic>
)

data class ConnectedDevice(
    val address: String,
    val name: String,
    val isConnecting: Boolean = false,
    val isDisconnected: Boolean = false,
    val disconnectionReason: Int? = null,
    val services: List<GattService> = emptyList()
)


