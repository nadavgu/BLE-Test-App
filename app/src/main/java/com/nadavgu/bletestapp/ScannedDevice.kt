package com.nadavgu.bletestapp

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int, // Raw RSSI for display
    val smoothedRssi: Double, // Smoothed RSSI for sorting
    val isConnectable: Boolean,
    val lastSeen: Long
)

