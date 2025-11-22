package com.nadavgu.bletestapp

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int, // Raw RSSI for display
    val smoothedRssi: Double, // Smoothed RSSI for sorting
    val isConnectable: Boolean,
    val lastSeen: Long,
    val manufacturerData: Map<Int, ByteArray> = emptyMap() // Map of manufacturer ID to data
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScannedDevice

        if (address != other.address) return false
        if (name != other.name) return false
        if (rssi != other.rssi) return false
        if (smoothedRssi != other.smoothedRssi) return false
        if (isConnectable != other.isConnectable) return false
        if (lastSeen != other.lastSeen) return false
        if (manufacturerData.size != other.manufacturerData.size) return false
        manufacturerData.forEach { (key, value) ->
            val otherValue = other.manufacturerData[key]
            if (otherValue == null || !value.contentEquals(otherValue)) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + rssi
        result = 31 * result + smoothedRssi.hashCode()
        result = 31 * result + isConnectable.hashCode()
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + manufacturerData.hashCode()
        return result
    }
}

