package com.nadavgu.bletestapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadavgu.bletestapp.server.BleGattServerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

private const val SPEED_CHECK_PACKET_SIZE = 512

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedDevicesScreen(
    devices: List<ConnectedDevice>,
    onConnectByAddress: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onWriteCharacteristic: (String, UUID, UUID, ByteArray, Int) -> Boolean,
    onRefreshPhy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val addressInput = remember { mutableStateOf("") }
    val addressError = remember { mutableStateOf<String?>(null) }
    val showSpeedCheckOptions = remember { mutableStateOf(false) }
    val totalBytesMB = remember { mutableStateOf("1") }
    val totalBytesMBError = remember { mutableStateOf<String?>(null) }
    // Default to WRITE_TYPE_NO_RESPONSE (faster, no acknowledgment)
    val useWriteWithResponse = remember { mutableStateOf(false) }

    fun validateAndConnect() {
        val text = addressInput.value.trim()
        if (text.isEmpty()) {
            addressError.value = context.getString(R.string.connected_devices_address_empty)
            return
        }
        val macAddressPattern = "^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$".toRegex()
        if (!macAddressPattern.matches(text)) {
            addressError.value = context.getString(R.string.connected_devices_address_invalid)
            return
        }
        addressError.value = null
        onConnectByAddress(text)
        addressInput.value = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = context.getString(
                R.string.connected_devices_status_format,
                devices.size
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.connected_devices_connect_by_address_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = addressInput.value,
                    onValueChange = { addressInput.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    label = { Text(text = context.getString(R.string.connected_devices_address_hint)) },
                    singleLine = true,
                    isError = addressError.value != null,
                    supportingText = {
                        addressError.value?.let { err ->
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { validateAndConnect() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = context.getString(R.string.connected_devices_connect_button))
                }
            }
        }

        if (devices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Text(
                    text = context.getString(R.string.connected_devices_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Speed Check Advanced Options as first item
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSpeedCheckOptions.value = !showSpeedCheckOptions.value },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Speed Check Options",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (showSpeedCheckOptions.value) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showSpeedCheckOptions.value) "Collapse" else "Expand"
                                )
                            }
                            
                            AnimatedVisibility(
                                visible = showSpeedCheckOptions.value,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                ) {
                                    // Packet size info (read-only)
                                    Text(
                                        text = "Packet Size: $SPEED_CHECK_PACKET_SIZE bytes (fixed)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    
                                    // Total bytes field
                                    Text(
                                        text = "Total Bytes (MB)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    OutlinedTextField(
                                        value = totalBytesMB.value,
                                        onValueChange = { value ->
                                            totalBytesMB.value = value
                                            totalBytesMBError.value = null
                                            val doubleValue = value.toDoubleOrNull()
                                            if (doubleValue == null || doubleValue <= 0) {
                                                totalBytesMBError.value = "Must be greater than 0"
                                            } else if (doubleValue > 100) {
                                                totalBytesMBError.value = "Maximum 100 MB recommended"
                                            }
                                        },
                                        label = { Text("Total bytes in megabytes") },
                                        placeholder = { Text("1.0") },
                                        isError = totalBytesMBError.value != null,
                                        supportingText = totalBytesMBError.value?.let { { Text(it) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp
                                        ),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors()
                                    )
                                    
                                    // Show calculated packet count
                                    totalBytesMB.value.toDoubleOrNull()?.let { mb ->
                                        if (mb > 0) {
                                            val totalBytes = (mb * 1024 * 1024).toLong()
                                            val packetCount = (totalBytes / SPEED_CHECK_PACKET_SIZE).toInt()
                                            Text(
                                                text = "Will send approximately $packetCount packets (${String.format("%.2f", totalBytes / 1024.0 / 1024.0)} MB)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                    }
                                    
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                                    
                                    // Write type toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Write with Response",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (useWriteWithResponse.value) {
                                                    "Slower but more reliable (with acknowledgment)"
                                                } else {
                                                    "Faster (no acknowledgment, default)"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = useWriteWithResponse.value,
                                            onCheckedChange = { useWriteWithResponse.value = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                items(devices, key = { it.address }) { device ->
                    ConnectedDeviceItem(
                        device = device,
                        onDisconnect = onDisconnect,
                        onRemove = onRemove,
                        onWriteCharacteristic = onWriteCharacteristic,
                        onRefreshPhy = onRefreshPhy,
                        totalBytesMB = totalBytesMB.value.toDoubleOrNull() ?: 1.0,
                        useWriteWithResponse = useWriteWithResponse.value
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedDeviceItem(
    device: ConnectedDevice,
    onDisconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onWriteCharacteristic: (String, UUID, UUID, ByteArray, Int) -> Boolean,
    onRefreshPhy: (String) -> Unit,
    totalBytesMB: Double = 1.0,
    useWriteWithResponse: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expandedServices = remember { mutableStateOf(setOf<String>()) }
    val speedCheckState = remember { mutableStateOf<SpeedCheckState?>(null) }
    val statusText = when {
        device.isConnecting -> context.getString(R.string.connected_device_status_connecting)
        device.isDisconnected -> context.getString(R.string.connected_device_status_disconnected)
        else -> context.getString(R.string.connected_device_status_connected)
    }

    val reasonText = disconnectionReasonText(context, device.disconnectionReason)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = device.name ?: context.getString(R.string.scan_unknown_device),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1
                )
                if (device.phy != null) {
                    Text(
                        text = " • ${device.phy}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onRefreshPhy(device.address) },
                    enabled = !device.isConnecting && !device.isDisconnected
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh PHY"
                    )
                }
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    device.isDisconnected -> MaterialTheme.colorScheme.error
                    device.isConnecting -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            if (device.isDisconnected && reasonText != null) {
                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            // Speed check section
            val hasSpeedCheckCharacteristic = deviceHasSpeedCheckCharacteristic(device)
            val isButtonEnabled = hasSpeedCheckCharacteristic && !device.isConnecting && !device.isDisconnected && speedCheckState.value?.isRunning != true
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Speed check results display
            speedCheckState.value?.let { state ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Speed Check Results",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        if (state.isRunning) {
                            Text(
                                text = "Running... Packets sent: ${state.packetsSent}/${state.totalPackets}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (state.error != null) {
                            Text(
                                text = "Error: ${state.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (state.throughputBytesPerSecond != null) {
                            Text(
                                text = "Throughput: ${String.format("%.2f", state.throughputBytesPerSecond / 1024.0)} KB/s",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Time: ${state.elapsedTimeMs}ms | Packets: ${state.packetsSent} | Total bytes: ${state.totalBytesSent}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            Button(
                onClick = {
                    performSpeedCheck(
                        deviceAddress = device.address,
                        onWriteCharacteristic = onWriteCharacteristic,
                        speedCheckState = speedCheckState,
                        scope = scope,
                        totalBytesMB = totalBytesMB,
                        useWriteWithResponse = useWriteWithResponse
                    )
                },
                enabled = isButtonEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (speedCheckState.value?.isRunning == true) "Speed Check Running..." else "Run Speed Check")
            }
            
            // Services section
            val visibleServices = device.services.filter { it.uuid != BleGattServerController.SPEED_CHECK_SERVICE_UUID }
            if (visibleServices.isNotEmpty() && !device.isConnecting && !device.isDisconnected) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = context.getString(R.string.connected_device_services_title, visibleServices.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                visibleServices.forEach { service ->
                        // Show regular service with expandable details
                        val serviceKey = "${device.address}_${service.uuid}"
                        val isExpanded = expandedServices.value.contains(serviceKey)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            val currentSet = expandedServices.value.toMutableSet()
                                            if (isExpanded) {
                                                currentSet.remove(serviceKey)
                                            } else {
                                                currentSet.add(serviceKey)
                                            }
                                            expandedServices.value = currentSet
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = context.getString(R.string.connected_device_service_uuid),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = service.uuid.toString().uppercase(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = context.getString(
                                                R.string.connected_device_characteristics_count,
                                                service.characteristics.size
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) {
                                            context.getString(R.string.connected_device_collapse)
                                        } else {
                                            context.getString(R.string.connected_device_expand)
                                        }
                                    )
                                }
                                
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        Text(
                                            text = context.getString(R.string.connected_device_characteristics_title),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        
                                        service.characteristics.forEach { characteristic ->
                                            val hasWrite = (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                                                    (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                                            
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 4.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = context.getString(R.string.connected_device_characteristic_uuid),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = characteristic.uuid.toString().uppercase(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = context.getString(
                                                            R.string.connected_device_characteristic_properties,
                                                            formatCharacteristicProperties(characteristic.properties)
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                    
                                                    // Write section for writable characteristics
                                                    if (hasWrite) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                                        CharacteristicWriteSection(
                                                            deviceAddress = device.address,
                                                            serviceUuid = service.uuid,
                                                            characteristicUuid = characteristic.uuid,
                                                            onWrite = onWriteCharacteristic
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            } else if (!device.isConnecting && !device.isDisconnected && visibleServices.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = context.getString(R.string.connected_device_no_services),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (device.isDisconnected) {
                    TextButton(onClick = { onRemove(device.address) }) {
                        Text(text = context.getString(R.string.connected_device_remove))
                    }
                } else {
                    TextButton(onClick = { onDisconnect(device.address) }) {
                        Text(text = context.getString(R.string.connected_device_disconnect))
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacteristicWriteSection(
    deviceAddress: String,
    serviceUuid: UUID,
    characteristicUuid: UUID,
    onWrite: (String, UUID, UUID, ByteArray, Int) -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writeData = remember { mutableStateOf("") }
    val writeError = remember { mutableStateOf<String?>(null) }
    
    fun parseHexString(hex: String): ByteArray? {
        val cleaned = hex.replace(" ", "").replace(":", "").replace("-", "")
        if (cleaned.isEmpty()) return null
        if (cleaned.length % 2 != 0) return null
        
        return try {
            cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    fun handleWrite() {
        writeError.value = null
        val data = parseHexString(writeData.value.trim())
        if (data == null) {
            writeError.value = context.getString(R.string.connected_device_write_invalid_hex)
            return
        }
        if (data.isEmpty()) {
            writeError.value = context.getString(R.string.connected_device_write_empty)
            return
        }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // For regular characteristic writes, use default write type
            val success = onWrite(deviceAddress, serviceUuid, characteristicUuid, data, android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            if (success) {
                writeData.value = ""
            } else {
                writeError.value = "Write failed"
            }
        }
    }
    
    Column {
        Text(
            text = context.getString(R.string.connected_device_write_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = writeData.value,
            onValueChange = { writeData.value = it },
            label = { Text(context.getString(R.string.connected_device_write_hint)) },
            placeholder = { Text("01 02 03") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            singleLine = true,
            isError = writeError.value != null,
            supportingText = {
                writeError.value?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { handleWrite() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.connected_device_write_button))
        }
    }
}

private fun formatCharacteristicProperties(properties: Int): String {
    val props = mutableListOf<String>()
    if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ != 0) {
        props.add("READ")
    }
    if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
        props.add("WRITE")
    }
    if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
        props.add("WRITE_NO_RESPONSE")
    }
    if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
        props.add("NOTIFY")
    }
    if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
        props.add("INDICATE")
    }
    if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) {
        props.add("SIGNED_WRITE")
    }
    return props.joinToString(", ").ifEmpty { "NONE" }
}

private data class SpeedCheckState(
    val isRunning: Boolean = false,
    val packetsSent: Int = 0,
    val totalPackets: Int = 0,
    val totalBytesSent: Int = 0,
    val elapsedTimeMs: Long = 0,
    val throughputBytesPerSecond: Double? = null,
    val error: String? = null
)

private fun deviceHasSpeedCheckCharacteristic(device: ConnectedDevice): Boolean {
    return device.services.any { service ->
        service.uuid == BleGattServerController.SPEED_CHECK_SERVICE_UUID &&
        service.characteristics.any { characteristic ->
            characteristic.uuid == BleGattServerController.SPEED_CHECK_CHARACTERISTIC_UUID
        }
    }
}

private fun performSpeedCheck(
    deviceAddress: String,
    onWriteCharacteristic: (String, UUID, UUID, ByteArray, Int) -> Boolean,
    speedCheckState: androidx.compose.runtime.MutableState<SpeedCheckState?>,
    scope: CoroutineScope,
    totalBytesMB: Double = 1.0,
    useWriteWithResponse: Boolean = false
) {
    val packetSize = SPEED_CHECK_PACKET_SIZE // Fixed packet size
    val totalBytes = (totalBytesMB * 1024 * 1024).toLong()
    val totalPackets = (totalBytes / packetSize).toInt()
    val packetData = ByteArray(packetSize) { it.toByte() }
    
    speedCheckState.value = SpeedCheckState(
        isRunning = true,
        totalPackets = totalPackets
    )
    
    val startTime = System.currentTimeMillis()
    var packetsSent = 0
    
    val writeType = if (useWriteWithResponse) {
        android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    } else {
        android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    }
    
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // First, send control message to notify server of speed check start and total packets
            val controlMessage = BleGattServerController.createSpeedCheckControlMessage(totalPackets)
            val controlSuccess = onWriteCharacteristic(
                deviceAddress,
                BleGattServerController.SPEED_CHECK_SERVICE_UUID,
                BleGattServerController.SPEED_CHECK_CHARACTERISTIC_UUID,
                controlMessage,
                writeType
            )
            if (!controlSuccess) {
                throw Exception("Failed to send speed check control message")
            }
            
            // Then send data packets sequentially - each write blocks until completion
            repeat(totalPackets) { index ->
                val success = onWriteCharacteristic(
                    deviceAddress,
                    BleGattServerController.SPEED_CHECK_SERVICE_UUID,
                    BleGattServerController.SPEED_CHECK_CHARACTERISTIC_UUID,
                    packetData,
                    writeType
                )
                if (success) {
                    packetsSent++
                } else {
                    throw Exception("Write failed at packet $index")
                }
                
                // Update progress
                speedCheckState.value = SpeedCheckState(
                    isRunning = true,
                    packetsSent = packetsSent,
                    totalPackets = totalPackets,
                    totalBytesSent = packetsSent * packetSize
                )
            }
            
            val endTime = System.currentTimeMillis()
            val elapsedTimeMs = endTime - startTime
            val totalBytesSent = packetsSent * packetSize
            val throughputBytesPerSecond = if (elapsedTimeMs > 0) {
                (totalBytesSent * 1000.0) / elapsedTimeMs
            } else {
                0.0
            }
            
            speedCheckState.value = SpeedCheckState(
                isRunning = false,
                packetsSent = packetsSent,
                totalPackets = totalPackets,
                totalBytesSent = totalBytesSent,
                elapsedTimeMs = elapsedTimeMs,
                throughputBytesPerSecond = throughputBytesPerSecond
            )
        } catch (e: Exception) {
            speedCheckState.value = SpeedCheckState(
                isRunning = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
}

@Composable
private fun disconnectionReasonText(context: android.content.Context, reason: Int?): String? {
    if (reason == null) return null
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

