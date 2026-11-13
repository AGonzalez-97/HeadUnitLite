package com.onixgarage.headunit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.core.obd.OBDConnectionState
import com.onixgarage.core.obd.OBDSnapshot
import com.onixgarage.headunit.ui.theme.OnSurfaceVariantDark
import com.onixgarage.headunit.ui.theme.PrimaryAccent
import com.onixgarage.headunit.ui.theme.StatusAmber
import com.onixgarage.headunit.ui.theme.StatusGreen
import com.onixgarage.headunit.ui.theme.StatusRed
import com.onixgarage.headunit.ui.theme.SurfaceVariantDark

private const val EMPTY = "—"

@Composable
fun HuDashboardScreen(state: HuUiState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionBar(state.connectionState)
            if (state.snapshot == null) {
                DisconnectedContent()
            } else {
                MetricsGrid(state.snapshot)
            }
        }
    }
}

@Composable
private fun ConnectionBar(connectionState: OBDConnectionState) {
    val (label, color) = when (connectionState) {
        is OBDConnectionState.Connected -> "Connected" to StatusGreen
        is OBDConnectionState.Connecting -> "Connecting..." to StatusAmber
        is OBDConnectionState.Initializing -> "Initializing..." to StatusAmber
        is OBDConnectionState.Error -> "Error" to StatusRed
        is OBDConnectionState.Disconnected -> "Disconnected" to OnSurfaceVariantDark
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = "● $label",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun DisconnectedContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No OBD connection",
                color = OnSurfaceVariantDark,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Waiting for Bluetooth adapter...",
                color = OnSurfaceVariantDark,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MetricsGrid(snap: OBDSnapshot) {
    val consumption = snap.fuelConsumptionL100km?.let { "%.1f L/100".format(it) } ?: EMPTY
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                label = "SPEED",
                value = "${snap.speedKmh}",
                unit = "km/h",
                valueColor = PrimaryAccent,
                valueFontSize = 56,
                modifier = Modifier.weight(1.5f),
            )
            MetricTile(
                label = "RPM",
                value = "${snap.rpm.toInt()}",
                unit = "rpm",
                valueFontSize = 48,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "FUEL",
                value = "%.0f".format(snap.fuelLevelPct),
                unit = "%",
                valueColor = fuelColor(snap.fuelLevelPct),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                label = "COOLANT",
                value = "${snap.coolantTempC}",
                unit = "°C",
                valueColor = coolantColor(snap.coolantTempC),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "ENGINE LOAD",
                value = "%.0f".format(snap.engineLoadPct),
                unit = "%",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "THROTTLE",
                value = "%.0f".format(snap.throttlePct),
                unit = "%",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "CONSUMPTION",
                value = consumption,
                unit = "",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueFontSize: Int = 40,
) {
    Surface(
        modifier = modifier,
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = OnSurfaceVariantDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = valueFontSize.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = valueFontSize.sp,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        color = OnSurfaceVariantDark,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

private fun fuelColor(pct: Float): Color = when {
    pct < 15f -> StatusRed
    pct < 25f -> StatusAmber
    else -> StatusGreen
}

private fun coolantColor(tempC: Int): Color = when {
    tempC > 105 -> StatusRed
    tempC > 95 -> StatusAmber
    else -> Color(0xFFE6E8EB)
}
