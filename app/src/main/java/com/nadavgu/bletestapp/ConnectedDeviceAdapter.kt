package com.nadavgu.bletestapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ConnectedDeviceAdapter(
    private val onDisconnectClick: (String) -> Unit
) : ListAdapter<ConnectedDevice, ConnectedDeviceAdapter.ConnectedDeviceViewHolder>(ConnectedDeviceDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectedDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_connected_device, parent, false)
        return ConnectedDeviceViewHolder(view as MaterialCardView, onDisconnectClick)
    }

    override fun onBindViewHolder(holder: ConnectedDeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
    }

    class ConnectedDeviceViewHolder(
        private val cardView: MaterialCardView,
        private val onDisconnectClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(cardView) {

        private val nameView: TextView = cardView.findViewById(R.id.deviceNameText)
        private val addressView: TextView = cardView.findViewById(R.id.deviceAddressText)
        private val statusView: TextView = cardView.findViewById(R.id.deviceStatusText)
        private val disconnectButton: MaterialButton = cardView.findViewById(R.id.disconnectButton)

        fun bind(device: ConnectedDevice) {
            val context = cardView.context

            nameView.text = device.name
            addressView.text = device.address

            statusView.text = if (device.isConnecting) {
                context.getString(R.string.connected_device_status_connecting)
            } else {
                context.getString(R.string.connected_device_status_connected)
            }

            disconnectButton.setOnClickListener {
                onDisconnectClick(device.address)
            }
        }
    }
}

private object ConnectedDeviceDiffCallback : DiffUtil.ItemCallback<ConnectedDevice>() {
    override fun areItemsTheSame(oldItem: ConnectedDevice, newItem: ConnectedDevice): Boolean =
        oldItem.address == newItem.address

    override fun areContentsTheSame(oldItem: ConnectedDevice, newItem: ConnectedDevice): Boolean =
        oldItem.address == newItem.address &&
        oldItem.name == newItem.name &&
        oldItem.isConnecting == newItem.isConnecting
}


