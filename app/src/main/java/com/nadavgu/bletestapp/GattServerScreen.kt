package com.nadavgu.bletestapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GattServerState(
    val isRunning: Boolean = false,
    val serverAddress: String? = null,
    val connectedClientCount: Int = 0,
    val serviceUuid: String = "",
    val manufacturerId: String = "",
    val manufacturerData: String = "",
    val dataReceived: String = "",
    val uuidError: String? = null,
    val manufacturerIdError: String? = null,
    val manufacturerDataError: String? = null
)

@Composable
fun GattServerScreen(
    state: GattServerState,
    onUuidChange: (String) -> Unit,
    onManufacturerIdChange: (String) -> Unit,
    onManufacturerDataChange: (String) -> Unit,
    onToggleServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
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
        
        // UUID input card
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
                    text = context.getString(R.string.gatt_server_uuid_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = state.serviceUuid,
                    onValueChange = onUuidChange,
                    label = { Text(context.getString(R.string.gatt_server_uuid_hint)) },
                    enabled = !state.isRunning,
                    isError = state.uuidError != null,
                    supportingText = state.uuidError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    singleLine = true
                )
            }
        }
        
        // Manufacturer data card
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
                    text = context.getString(R.string.gatt_server_manufacturer_data_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = state.manufacturerId,
                    onValueChange = onManufacturerIdChange,
                    label = { Text(context.getString(R.string.gatt_server_manufacturer_id_hint)) },
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
        
        // Data received card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.gatt_server_data_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = state.dataReceived.ifEmpty { "No data received yet" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                )
            }
        }
    }
}

