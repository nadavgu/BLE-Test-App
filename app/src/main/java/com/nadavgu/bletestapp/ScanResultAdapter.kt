package com.nadavgu.bletestapp

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ScanResultAdapter :
    ListAdapter<ScannedDevice, ScanResultViewHolder>(ScanResultDiffCallback) {

    companion object {
        const val PAYLOAD_RSSI_CHANGED = "rssi_changed"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_result, parent, false)
        return ScanResultViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: ScanResultViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_RSSI_CHANGED)) {
            // Only update RSSI, not the entire view
            // This prevents any layout recalculation or animation
            holder.updateRssi(getItem(position))
        } else {
            // Full bind
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun submitDevices(devices: List<ScannedDevice>) {
        // Devices are already sorted by smoothedRssi in ScanActivity
        submitList(devices)
    }
}

private object ScanResultDiffCallback : DiffUtil.ItemCallback<ScannedDevice>() {
    override fun areItemsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean =
        oldItem.address == newItem.address

    override fun areContentsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean =
        // Compare all fields except smoothedRssi (which is only for sorting, not display)
        oldItem.address == newItem.address &&
        oldItem.name == newItem.name &&
        oldItem.rssi == newItem.rssi &&
        oldItem.isConnectable == newItem.isConnectable &&
        // Only check lastSeen if it's significantly different (to avoid constant updates)
        kotlin.math.abs(oldItem.lastSeen - newItem.lastSeen) < 1000

    override fun getChangePayload(oldItem: ScannedDevice, newItem: ScannedDevice): Any? {
        // If only RSSI changed, return a payload to trigger partial update
        return if (oldItem.rssi != newItem.rssi &&
            oldItem.address == newItem.address &&
            oldItem.name == newItem.name &&
            oldItem.isConnectable == newItem.isConnectable &&
            kotlin.math.abs(oldItem.lastSeen - newItem.lastSeen) < 1000
        ) {
            ScanResultAdapter.PAYLOAD_RSSI_CHANGED
        } else {
            null
        }
    }
}

class ScanResultViewHolder(private val cardView: MaterialCardView) :
    RecyclerView.ViewHolder(cardView) {

    private val nameView: TextView = cardView.findViewById(R.id.deviceNameText)
    private val addressView: TextView = cardView.findViewById(R.id.deviceAddressText)
    private val rssiView: TextView = cardView.findViewById(R.id.deviceRssiText)
    private val lastSeenView: TextView = cardView.findViewById(R.id.deviceLastSeenText)
    private val connectableView: TextView = cardView.findViewById(R.id.deviceConnectableText)
    
    // Track previous values to avoid unnecessary updates
    private var previousRssi: Int? = null
    private var previousLastSeen: Long? = null
    private var previousIsConnectable: Boolean? = null

    fun bind(device: ScannedDevice) {
        val context = cardView.context
        
        nameView.text = device.name
        addressView.text = device.address
        
        updateRssi(device)
        
        // Only update last seen if it changed significantly (more than 1 second)
        val currentTime = System.currentTimeMillis()
        if (previousLastSeen == null || kotlin.math.abs(device.lastSeen - previousLastSeen!!) >= 1000) {
            lastSeenView.text = DateUtils.getRelativeTimeSpanString(
                device.lastSeen,
                currentTime,
                DateUtils.SECOND_IN_MILLIS
            )
            previousLastSeen = device.lastSeen
        }
        
        // Only update connectable badge if it changed
        if (previousIsConnectable != device.isConnectable) {
            connectableView.isVisible = true
            if (device.isConnectable) {
                connectableView.text = context.getString(R.string.scan_item_connectable_true)
                connectableView.setBackgroundResource(R.drawable.connectable_badge)
            } else {
                connectableView.text = context.getString(R.string.scan_item_connectable_false)
                connectableView.setBackgroundResource(R.drawable.non_connectable_badge)
            }
            connectableView.setTextColor(ContextCompat.getColor(context, R.color.white))
            previousIsConnectable = device.isConnectable
        }
    }
    
    fun updateRssi(device: ScannedDevice) {
        // Only update RSSI if it changed
        if (previousRssi != device.rssi) {
            val context = cardView.context
            val rssiText = context.getString(R.string.scan_item_rssi, device.rssi)
            rssiView.text = rssiText
            val rssiColor = when {
                device.rssi >= -50 -> ContextCompat.getColor(context, R.color.rssi_good)
                device.rssi >= -70 -> ContextCompat.getColor(context, R.color.rssi_medium)
                else -> ContextCompat.getColor(context, R.color.rssi_poor)
            }
            rssiView.setTextColor(rssiColor)
            previousRssi = device.rssi
        }
    }
}

