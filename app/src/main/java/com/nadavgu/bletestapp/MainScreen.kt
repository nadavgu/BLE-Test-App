package com.nadavgu.bletestapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class NavigationDestination {
    SCAN,
    CONNECTED_DEVICES,
    GATT_SERVER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentDestination: NavigationDestination,
    onDestinationChange: (NavigationDestination) -> Unit,
    scanScreen: @Composable () -> Unit,
    connectedDevicesScreen: @Composable () -> Unit,
    gattServerScreen: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                currentDestination = currentDestination,
                onDestinationSelected = { destination ->
                    onDestinationChange(destination)
                    scope.launch {
                        drawerState.close()
                    }
                }
            )
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentDestination) {
                                NavigationDestination.SCAN -> context.getString(R.string.scan_title)
                                NavigationDestination.CONNECTED_DEVICES -> context.getString(R.string.connected_devices_title)
                                NavigationDestination.GATT_SERVER -> context.getString(R.string.gatt_server_title)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open navigation drawer"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    NavigationDestination.SCAN -> scanScreen()
                    NavigationDestination.CONNECTED_DEVICES -> connectedDevicesScreen()
                    NavigationDestination.GATT_SERVER -> gattServerScreen()
                }
            }
        }
    }
}

@Composable
private fun NavigationDrawerContent(
    currentDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Text(
                text = context.getString(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            NavigationDrawerItem(
                label = { Text(context.getString(R.string.scan_title)) },
                selected = currentDestination == NavigationDestination.SCAN,
                onClick = { onDestinationSelected(NavigationDestination.SCAN) },
                modifier = Modifier.fillMaxWidth()
            )
            
            NavigationDrawerItem(
                label = { Text(context.getString(R.string.connected_devices_title)) },
                selected = currentDestination == NavigationDestination.CONNECTED_DEVICES,
                onClick = { onDestinationSelected(NavigationDestination.CONNECTED_DEVICES) },
                modifier = Modifier.fillMaxWidth()
            )
            
            NavigationDrawerItem(
                label = { Text(context.getString(R.string.gatt_server_title)) },
                selected = currentDestination == NavigationDestination.GATT_SERVER,
                onClick = { onDestinationSelected(NavigationDestination.GATT_SERVER) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

