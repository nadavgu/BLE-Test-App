package com.nadavgu.bletestapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadavgu.bletestapp.server.ConnectedClient

data class CharacteristicEntry(
    val entryId: String,
    val uuid: String,
    val uuidError: String? = null
)

data class GattServerState(
    val isRunning: Boolean = false,
    val serverAddress: String? = null,
    val connectedClientCount: Int = 0,
    val connectedClients: List<ConnectedClient> = emptyList(),
    val serviceUuid: String = "",
    val characteristics: List<CharacteristicEntry> = emptyList(),
    val manufacturerId: String = "",
    val manufacturerData: String = "",
    val dataReceivedByClientServiceAndCharacteristic: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    val uuidError: String? = null,
    val manufacturerIdError: String? = null,
    val manufacturerDataError: String? = null
)

@Composable
fun GattServerScreen(
    state: GattServerState,
    onUuidChange: (String) -> Unit,
    onAddCharacteristic: () -> Unit,
    onRemoveCharacteristic: (String) -> Unit,
    onCharacteristicUuidChange: (String, String) -> Unit,
    onManufacturerIdChange: (String) -> Unit,
    onManufacturerDataChange: (String) -> Unit,
    onToggleServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAdvancedParams by remember { mutableStateOf(false) }
    var expandedClients by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                text = if (state.isRunning) {
                    context.getString(R.string.gatt_server_status_running)
                } else {
                    context.getString(R.string.gatt_server_status_stopped)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            
            if (state.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
            }
        }
        
        // Server info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.gatt_server_info_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Address: ${state.serverAddress ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = context.getString(R.string.gatt_server_connected_clients, state.connectedClientCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        // Advanced parameters card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedParams = !showAdvancedParams },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.gatt_server_advanced_params_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (showAdvancedParams) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showAdvancedParams) {
                            context.getString(R.string.gatt_server_advanced_params_collapse)
                        } else {
                            context.getString(R.string.gatt_server_advanced_params_expand)
                        }
                    )
                }
                
                AnimatedVisibility(
                    visible = showAdvancedParams,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        // Service UUID field
                        Text(
                            text = context.getString(R.string.gatt_server_uuid_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = state.serviceUuid,
                            onValueChange = onUuidChange,
                            label = { Text(context.getString(R.string.gatt_server_uuid_hint)) },
                            enabled = !state.isRunning,
                            isError = state.uuidError != null,
                            supportingText = state.uuidError?.let { { Text(it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            singleLine = true
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Characteristics section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.getString(R.string.gatt_server_characteristics_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            
                            IconButton(
                                onClick = onAddCharacteristic,
                                enabled = !state.isRunning,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add characteristic"
                                )
                            }
                        }

                        // List of characteristics
                        val hintText = context.getString(R.string.gatt_server_characteristic_uuid_hint)
                        state.characteristics.forEachIndexed { index, characteristic ->
                            OutlinedTextField(
                                value = characteristic.uuid,
                                onValueChange = { newUuid: String ->
                                    onCharacteristicUuidChange(characteristic.entryId, newUuid)
                                },
                                label = { Text(hintText) },
                                placeholder = { Text("00002A19-0000-1000-8000-00805F9B34FB") },
                                enabled = !state.isRunning,
                                isError = characteristic.uuidError != null,
                                supportingText = characteristic.uuidError?.let { { Text(it) } },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onRemoveCharacteristic(characteristic.entryId) },
                                        enabled = !state.isRunning,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove characteristic",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (index < state.characteristics.size - 1) 8.dp else 16.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                singleLine = true
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Manufacturer data fields
                        Text(
                            text = context.getString(R.string.gatt_server_manufacturer_data_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = state.manufacturerId,
                            onValueChange = onManufacturerIdChange,
                            label = { Text(context.getString(R.string.gatt_server_manufacturer_id_hint)) },
                            placeholder = { Text("0x004C") },
                            enabled = !state.isRunning,
                            isError = state.manufacturerIdError != null,
                            supportingText = state.manufacturerIdError?.let { { Text(it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = state.manufacturerData,
                            onValueChange = onManufacturerDataChange,
                            label = { Text(context.getString(R.string.gatt_server_manufacturer_data_hint)) },
                            placeholder = { Text("01 02 03") },
                            enabled = !state.isRunning,
                            isError = state.manufacturerDataError != null,
                            supportingText = state.manufacturerDataError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        }
        
        // Toggle server button
        Button(
            onClick = onToggleServer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(56.dp)
        ) {
            Text(
                text = if (state.isRunning) {
                    context.getString(R.string.gatt_server_stop_button)
                } else {
                    context.getString(R.string.gatt_server_start_button)
                },
                fontSize = 16.sp
            )
        }
        
        // Connected Clients section
        if (state.isRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "Connected Clients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                if (state.connectedClients.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "No clients connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    state.connectedClients.forEach { client ->
                        val isExpanded = expandedClients.contains(client.address)
                        val clientDataByServiceAndCharacteristic = state.dataReceivedByClientServiceAndCharacteristic[client.address] ?: emptyMap()
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Client header (always visible)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedClients = if (isExpanded) {
                                                expandedClients - client.address
                                            } else {
                                                expandedClients + client.address
                                            }
                                        }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = client.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = client.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                                    )
                                }
                                
                                // Expanded content
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        
                                        // Received Data section
                                        Text(
                                            text = context.getString(R.string.gatt_server_data_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        
                                        if (clientDataByServiceAndCharacteristic.isEmpty()) {
                                            Text(
                                                text = "No data received yet",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                        } else {
                                            // Show data grouped by service, then by characteristic
                                            val serviceEntries = clientDataByServiceAndCharacteristic.entries.toList()
                                            serviceEntries.forEachIndexed { serviceIndex, (serviceUuid, characteristicsMap) ->
                                                Column(
                                                    modifier = Modifier.padding(bottom = 16.dp)
                                                ) {
                                                    // Service UUID header
                                                    Text(
                                                        text = serviceUuid,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                    
                                                    // Characteristics within this service
                                                    val characteristicEntries = characteristicsMap.entries.toList()
                                                    characteristicEntries.forEachIndexed { charIndex, (characteristicUuid, data) ->
                                                        Column(
                                                            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                                                        ) {
                                                            // Characteristic UUID header
                                                            Text(
                                                                text = characteristicUuid,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.secondary,
                                                                modifier = Modifier.padding(bottom = 4.dp)
                                                            )
                                                            
                                                            // Data for this characteristic
                                                            if (data.isEmpty()) {
                                                                Text(
                                                                    text = "No data received yet",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                                    modifier = Modifier.padding(start = 8.dp)
                                                                )
                                                            } else {
                                                                Text(
                                                                    text = data,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .heightIn(min = 40.dp)
                                                                        .padding(start = 8.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Divider between services (except for last one)
                                                    if (serviceIndex < serviceEntries.size - 1) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            color = MaterialTheme.colorScheme.outlineVariant
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
                }
            }
        }
    }
}

