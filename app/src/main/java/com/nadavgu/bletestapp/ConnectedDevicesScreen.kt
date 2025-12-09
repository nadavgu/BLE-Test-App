package com.nadavgu.bletestapp

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.nordicsemi.android.ble.observer.ConnectionObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedDevicesScreen(
    devices: List<ConnectedDevice>,
    onConnectByAddress: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val addressInput = remember { mutableStateOf("") }
    val addressError = remember { mutableStateOf<String?>(null) }

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
                items(devices, key = { it.address }) { device ->
                    ConnectedDeviceItem(
                        device = device,
                        onDisconnect = onDisconnect,
                        onRemove = onRemove
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
    onRemove: (String) -> Unit
) {
    val context = LocalContext.current
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
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1
            )
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

