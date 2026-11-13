package com.onixgarage.phone.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DonutSmall
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.onixgarage.core.obd.OBDConnectionState
import com.onixgarage.phone.ui.bluetooth.BluetoothViewModel
import com.onixgarage.phone.ui.bluetooth.BtDeviceInfo
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.StatusAmber
import com.onixgarage.phone.ui.theme.StatusGreen
import com.onixgarage.phone.ui.theme.StatusRed

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(Routes.DASHBOARD, "Dashboard", Icons.Default.Home),
    NavItem(Routes.FUEL,      "Fuel",      Icons.Default.LocalGasStation),
    NavItem(Routes.SERVICE,   "Service",   Icons.Default.Build),
    NavItem(Routes.TIRES,     "Tires",     Icons.Default.DonutSmall),
    NavItem(Routes.TRIPS,     "Trips",     Icons.Default.DirectionsCar),
    NavItem(Routes.DTC,       "DTCs",      Icons.Default.Warning),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val btVm: BluetoothViewModel = viewModel()
    val connectionState by btVm.connectionState.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Onix Garage") },
                actions = {
                    ConnectionChip(
                        state = connectionState,
                        onClick = { showPicker = true },
                    )
                },
            )
        },
        bottomBar = { BottomNav(navController) },
    ) { innerPadding ->
        OnixNavHost(navController = navController, innerPadding = innerPadding)
    }

    if (showPicker) {
        DevicePickerDialog(
            connectionState = connectionState,
            devices = btVm.bondedDevices(),
            onConnect = { address ->
                btVm.connect(address)
                showPicker = false
            },
            onDisconnect = {
                btVm.disconnect()
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun ConnectionChip(state: OBDConnectionState, onClick: () -> Unit) {
    val (label, color) = when (state) {
        is OBDConnectionState.Connected    -> "Connected"     to StatusGreen
        is OBDConnectionState.Connecting   -> "Connecting..." to StatusAmber
        is OBDConnectionState.Initializing -> "Initializing..." to StatusAmber
        is OBDConnectionState.Error        -> "Error"         to StatusRed
        is OBDConnectionState.Disconnected -> "Disconnected"  to OnSurfaceVariantDark
    }
    Text(
        text = "* $label",
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun DevicePickerDialog(
    connectionState: OBDConnectionState,
    devices: List<BtDeviceInfo>,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isConnected = connectionState is OBDConnectionState.Connected
        || connectionState is OBDConnectionState.Connecting
        || connectionState is OBDConnectionState.Initializing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isConnected) "OBD Connection" else "Select OBD adapter") },
        text = {
            Column {
                if (isConnected) {
                    Text("Currently connected. Do you want to disconnect?")
                } else if (devices.isEmpty()) {
                    Text("No paired Bluetooth devices found. Pair your ELM327 adapter in Android settings first.")
                } else {
                    Text(
                        text = "Paired devices",
                        color = OnSurfaceVariantDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    devices.forEach { device ->
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConnect(device.address) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(device.name, fontWeight = FontWeight.SemiBold)
                                Text(device.address, color = OnSurfaceVariantDark, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isConnected) {
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BottomNav(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
