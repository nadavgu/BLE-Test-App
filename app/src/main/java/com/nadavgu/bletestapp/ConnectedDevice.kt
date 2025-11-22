package com.nadavgu.bletestapp

data class ConnectedDevice(
    val address: String,
    val name: String,
    val isConnecting: Boolean = false
)


