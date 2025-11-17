package com.nadavgu.bletestapp

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
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
        nameView.text = device.name
        addressView.text = device.address
        rssiView.text = cardView.context.getString(
            R.string.scan_item_rssi,
            device.rssi
        )
        lastSeenView.text = DateUtils.getRelativeTimeSpanString(
            device.lastSeen,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS
        )
        connectableView.isVisible = true
        connectableView.text = if (device.isConnectable) {
            cardView.context.getString(R.string.scan_item_connectable_true)
        } else {
            cardView.context.getString(R.string.scan_item_connectable_false)
        }
    }
}

