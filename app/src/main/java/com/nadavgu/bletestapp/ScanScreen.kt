package com.nadavgu.bletestapp

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun ScanScreen(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    onToggleScan: () -> Unit,
    onConnectClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expandedItems = remember { mutableSetOf<String>() }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isScanning) {
                    context.getString(R.string.scan_status_scanning)
                } else {
                    context.getString(R.string.scan_status_idle)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
            }
        }
        
        // Empty state
        if (devices.isEmpty() && !isScanning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = context.getString(R.string.scan_empty_state),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp
                )
            }
        }
        
        // Device list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(devices, key = { it.address }) { device ->
                ScanResultItem(
                    device = device,
                    isExpanded = expandedItems.contains(device.address),
                    onItemClick = {
                        if (expandedItems.contains(device.address)) {
                            expandedItems.remove(device.address)
                        } else {
                            expandedItems.add(device.address)
                        }
                    },
                    onConnectClick = { onConnectClick(device.address) }
                )
            }
        }
        
        // Toggle scan button
        Button(
            onClick = onToggleScan,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(56.dp)
        ) {
            Text(
                text = if (isScanning) {
                    context.getString(R.string.scan_stop_button)
                } else {
                    context.getString(R.string.scan_start_button)
                },
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ScanResultItem(
    device: ScannedDevice,
    isExpanded: Boolean,
    onItemClick: () -> Unit,
    onConnectClick: () -> Unit
) {
    val context = LocalContext.current
    val rssiColor = when {
        device.rssi >= -50 -> Color(0xFF4CAF50) // rssi_good
        device.rssi >= -70 -> Color(0xFFFF9800) // rssi_medium
        else -> Color(0xFFF44336) // rssi_poor
    }
    
    val currentTime = System.currentTimeMillis()
    val lastSeenText = DateUtils.getRelativeTimeSpanString(
        device.lastSeen,
        currentTime,
        DateUtils.SECOND_IN_MILLIS
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Main device info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = context.getString(R.string.scan_item_rssi, device.rssi),
                        style = MaterialTheme.typography.bodyMedium,
                        color = rssiColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = lastSeenText.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Connect button
            if (device.isConnectable) {
                OutlinedButton(
                    onClick = {
                        onConnectClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(context.getString(R.string.scan_item_connect))
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(context.getString(R.string.scan_item_connectable_false))
                }
            }
            
            // Expanded info
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Manufacturer data
                    if (device.manufacturerData.isNotEmpty()) {
                        Text(
                            text = context.getString(R.string.scan_item_manufacturer_data_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        device.manufacturerData.forEach { (mfgId, data) ->
                            val mfgIdHex = "0x%04X".format(mfgId)
                            val dataHex = data.joinToString(" ") { "%02X".format(it) }
                            Text(
                                text = "$mfgIdHex: $dataHex",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                    
                    // Service UUIDs
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = context.getString(R.string.scan_item_service_uuids_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (device.serviceUuids.isNotEmpty()) {
                        device.serviceUuids.forEach { uuid ->
                            Text(
                                text = uuid.toString().uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = context.getString(R.string.scan_item_no_service_uuids),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

