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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ScanResultAdapter(
    private val onConnectClick: (String) -> Unit
) : ListAdapter<ScannedDevice, ScanResultViewHolder>(ScanResultDiffCallback) {

    companion object {
        const val PAYLOAD_RSSI_CHANGED = "rssi_changed"
    }

    private val expandedItems = mutableSetOf<String>()

    fun isExpanded(address: String): Boolean = expandedItems.contains(address)

    fun toggleExpanded(address: String) {
        if (expandedItems.contains(address)) {
            expandedItems.remove(address)
        } else {
            expandedItems.add(address)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_result, parent, false)
        return ScanResultViewHolder(view as MaterialCardView, this, onConnectClick)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device, isExpanded(device.address))
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
            val device = getItem(position)
            holder.bind(device, isExpanded(device.address))
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
        oldItem.manufacturerData == newItem.manufacturerData &&
        oldItem.serviceUuids == newItem.serviceUuids &&
        // Only check lastSeen if it's significantly different (to avoid constant updates)
        kotlin.math.abs(oldItem.lastSeen - newItem.lastSeen) < 1000

    override fun getChangePayload(oldItem: ScannedDevice, newItem: ScannedDevice): Any? {
        // If only RSSI changed, return a payload to trigger partial update
        return if (oldItem.rssi != newItem.rssi &&
            oldItem.address == newItem.address &&
            oldItem.name == newItem.name &&
            oldItem.isConnectable == newItem.isConnectable &&
            oldItem.manufacturerData == newItem.manufacturerData &&
            oldItem.serviceUuids == newItem.serviceUuids &&
            kotlin.math.abs(oldItem.lastSeen - newItem.lastSeen) < 1000
        ) {
            ScanResultAdapter.PAYLOAD_RSSI_CHANGED
        } else {
            null
        }
    }
}

class ScanResultViewHolder(
    private val cardView: MaterialCardView,
    private val adapter: ScanResultAdapter,
    private val onConnectClick: (String) -> Unit
) :
    RecyclerView.ViewHolder(cardView) {

    private val nameView: TextView = cardView.findViewById(R.id.deviceNameText)
    private val addressView: TextView = cardView.findViewById(R.id.deviceAddressText)
    private val rssiView: TextView = cardView.findViewById(R.id.deviceRssiText)
    private val lastSeenView: TextView = cardView.findViewById(R.id.deviceLastSeenText)
    private val connectButton: MaterialButton = cardView.findViewById(R.id.deviceConnectButton)
    private val expandedInfoView: android.view.View = cardView.findViewById(R.id.expandedInfoView)
    private val manufacturerDataView: TextView = cardView.findViewById(R.id.manufacturerDataText)
    private val serviceUuidsView: TextView = cardView.findViewById(R.id.serviceUuidsText)
    
    // Track previous values to avoid unnecessary updates
    private var previousRssi: Int? = null
    private var previousLastSeen: Long? = null
    private var previousIsConnectable: Boolean? = null

    init {
        cardView.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < adapter.itemCount) {
                val device = adapter.currentList[position]
                adapter.toggleExpanded(device.address)
                adapter.notifyItemChanged(position)
            }
        }
        
        connectButton.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < adapter.itemCount) {
                val device = adapter.currentList[position]
                if (device.isConnectable) {
                    onConnectClick(device.address)
                }
            }
        }
    }

    fun bind(device: ScannedDevice, isExpanded: Boolean) {
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
        
        // Update connect button based on connectable state
        if (previousIsConnectable != device.isConnectable) {
            if (device.isConnectable) {
                connectButton.text = context.getString(R.string.scan_item_connect)
                connectButton.isEnabled = true
                connectButton.isVisible = true
            } else {
                connectButton.text = context.getString(R.string.scan_item_connectable_false)
                connectButton.isEnabled = false
                connectButton.isVisible = true
            }
            previousIsConnectable = device.isConnectable
        }
        
        // Update expanded state
        expandedInfoView.isVisible = isExpanded
        if (isExpanded) {
            // Show all manufacturer data if available
            if (device.manufacturerData.isNotEmpty()) {
                val manufacturerDataText = device.manufacturerData.entries.joinToString("\n") { (mfgId, data) ->
                    val mfgIdHex = "0x%04X".format(mfgId)
                    val dataHex = data.joinToString(" ") { "%02X".format(it) }
                    "$mfgIdHex: $dataHex"
                }
                manufacturerDataView.text = manufacturerDataText
                manufacturerDataView.isVisible = true
            } else {
                manufacturerDataView.isVisible = false
            }
            
            // Show service UUIDs if available
            if (device.serviceUuids.isNotEmpty()) {
                val serviceUuidsText = device.serviceUuids.joinToString("\n") { it.toString().uppercase() }
                serviceUuidsView.text = serviceUuidsText
                serviceUuidsView.isVisible = true
            } else {
                serviceUuidsView.text = context.getString(R.string.scan_item_no_service_uuids)
                serviceUuidsView.isVisible = true
            }
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

