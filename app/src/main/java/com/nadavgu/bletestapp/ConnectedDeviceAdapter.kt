package com.nadavgu.bletestapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import no.nordicsemi.android.ble.observer.ConnectionObserver

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
        private val disconnectionReasonView: TextView = cardView.findViewById(R.id.disconnectionReasonText)
        private val disconnectButton: MaterialButton = cardView.findViewById(R.id.disconnectButton)

        fun bind(device: ConnectedDevice) {
            val context = cardView.context

            nameView.text = device.name
            addressView.text = device.address

            if (device.isDisconnected) {
                statusView.text = context.getString(R.string.connected_device_status_disconnected)
                statusView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                
                // Show disconnection reason
                val reasonText = getDisconnectionReasonText(context, device.disconnectionReason)
                disconnectionReasonView.text = reasonText
                disconnectionReasonView.visibility = android.view.View.VISIBLE
                
                // Change button text to "Remove"
                disconnectButton.text = context.getString(R.string.connected_device_remove)
            } else if (device.isConnecting) {
                statusView.text = context.getString(R.string.connected_device_status_connecting)
                statusView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
                disconnectionReasonView.visibility = android.view.View.GONE
                disconnectButton.text = context.getString(R.string.connected_device_disconnect)
            } else {
                statusView.text = context.getString(R.string.connected_device_status_connected)
                statusView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                disconnectionReasonView.visibility = android.view.View.GONE
                disconnectButton.text = context.getString(R.string.connected_device_disconnect)
            }

            disconnectButton.setOnClickListener {
                onDisconnectClick(device.address)
            }
        }
        
        private fun getDisconnectionReasonText(context: android.content.Context, reason: Int?): String {
            if (reason == null) {
                return context.getString(R.string.connected_device_disconnected_unknown)
            }
            
            // Use ConnectionObserver constants from Nordic BLE library
            return when (reason) {
                ConnectionObserver.REASON_SUCCESS -> context.getString(R.string.connected_device_disconnected_success)
                ConnectionObserver.REASON_TIMEOUT -> context.getString(R.string.connected_device_disconnected_timeout)
                ConnectionObserver.REASON_TERMINATE_LOCAL_HOST -> context.getString(R.string.connected_device_disconnected_local_host)
                ConnectionObserver.REASON_TERMINATE_PEER_USER -> context.getString(R.string.connected_device_disconnected_remote_user)
                ConnectionObserver.REASON_NOT_SUPPORTED -> context.getString(R.string.connected_device_disconnected_not_supported)
                ConnectionObserver.REASON_LINK_LOSS -> context.getString(R.string.connected_device_disconnected_link_loss)
                ConnectionObserver.REASON_CANCELLED -> context.getString(R.string.connected_device_disconnected_cancelled)
                ConnectionObserver.REASON_UNKNOWN -> context.getString(R.string.connected_device_disconnected_unknown)
                else -> context.getString(R.string.connected_device_disconnected_reason, reason)
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
        oldItem.isConnecting == newItem.isConnecting &&
        oldItem.isDisconnected == newItem.isDisconnected &&
        oldItem.disconnectionReason == newItem.disconnectionReason
}


