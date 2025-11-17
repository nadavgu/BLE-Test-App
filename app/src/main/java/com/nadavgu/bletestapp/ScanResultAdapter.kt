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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_result, parent, false)
        return ScanResultViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun submitDevices(devices: List<ScannedDevice>) {
        submitList(devices.sortedByDescending { it.rssi })
    }
}

private object ScanResultDiffCallback : DiffUtil.ItemCallback<ScannedDevice>() {
    override fun areItemsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean =
        oldItem.address == newItem.address

    override fun areContentsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean =
        oldItem == newItem
}

class ScanResultViewHolder(private val cardView: MaterialCardView) :
    RecyclerView.ViewHolder(cardView) {

    private val nameView: TextView = cardView.findViewById(R.id.deviceNameText)
    private val addressView: TextView = cardView.findViewById(R.id.deviceAddressText)
    private val rssiView: TextView = cardView.findViewById(R.id.deviceRssiText)
    private val lastSeenView: TextView = cardView.findViewById(R.id.deviceLastSeenText)
    private val connectableView: TextView = cardView.findViewById(R.id.deviceConnectableText)

    fun bind(device: ScannedDevice) {
        val context = cardView.context
        
        nameView.text = device.name
        addressView.text = device.address
        
        // Color code RSSI values
        val rssiText = context.getString(R.string.scan_item_rssi, device.rssi)
        rssiView.text = rssiText
        val rssiColor = when {
            device.rssi >= -50 -> ContextCompat.getColor(context, R.color.rssi_good)
            device.rssi >= -70 -> ContextCompat.getColor(context, R.color.rssi_medium)
            else -> ContextCompat.getColor(context, R.color.rssi_poor)
        }
        rssiView.setTextColor(rssiColor)
        
        lastSeenView.text = DateUtils.getRelativeTimeSpanString(
            device.lastSeen,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS
        )
        
        // Update connectable badge
        connectableView.isVisible = true
        if (device.isConnectable) {
            connectableView.text = context.getString(R.string.scan_item_connectable_true)
            connectableView.setBackgroundResource(R.drawable.connectable_badge)
        } else {
            connectableView.text = context.getString(R.string.scan_item_connectable_false)
            connectableView.setBackgroundResource(R.drawable.non_connectable_badge)
        }
        connectableView.setTextColor(ContextCompat.getColor(context, R.color.white))
    }
}

