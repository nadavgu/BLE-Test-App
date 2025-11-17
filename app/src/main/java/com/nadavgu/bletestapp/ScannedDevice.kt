package com.nadavgu.bletestapp

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isConnectable: Boolean,
    val lastSeen: Long
)

