package com.onixgarage.phone.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.core.obd.OBDConnectionState
import com.onixgarage.phone.ui.common.MetricRow
import com.onixgarage.phone.ui.common.formatArs
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.SecondaryAccent
import com.onixgarage.phone.ui.theme.StatusAmber
import com.onixgarage.phone.ui.theme.StatusGreen
import com.onixgarage.phone.ui.theme.StatusRed
import com.onixgarage.phone.ui.theme.SurfaceVariantDark

private const val EMPTY = "—"

@Composable
fun DashboardScreen(state: DashboardUiState, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionStatusBar(state.connection)
            FuelTankCard(state)
            AutonomyCard(state.autonomyKm)
            MetricRow(
                leftLabel = "Speed",
                leftValue = state.snapshot?.speedKmh?.let { "$it km/h" } ?: EMPTY,
                rightLabel = "Consumption",
                rightValue = state.snapshot?.fuelConsumptionL100km?.let {
                    "%.1f L/100km".format(it)
                } ?: EMPTY,
            )
            MetricRow(
                leftLabel = "RPM",
                leftValue = state.snapshot?.rpm?.toInt()?.toString() ?: EMPTY,
                rightLabel = "Coolant",
                rightValue = state.snapshot?.coolantTempC?.let { "$it °C" } ?: EMPTY,
                rightColor = state.snapshot?.coolantTempC?.let { temp ->
                    when {
                        temp > 105 -> StatusRed
                        temp > 95 -> StatusAmber
                        else -> null
                    }
                },
            )
            MetricRow(
                leftLabel = "Trip cost",
                leftValue = state.ongoingTrip?.costARS
                    ?.takeIf { it > 0.0 }
                    ?.let { formatArs(it) } ?: EMPTY,
                rightLabel = "Cost / hour",
                rightValue = state.estimatedCostPerHour
                    ?.takeIf { it > 0.0 }
                    ?.let { formatArs(it) + "/h" } ?: EMPTY,
            )
            MetricRow(
                leftLabel = "Load",
                leftValue = state.snapshot?.engineLoadPct?.let { "%.0f%%".format(it) } ?: EMPTY,
                rightLabel = "Throttle",
                rightValue = state.snapshot?.throttlePct?.let { "%.0f%%".format(it) } ?: EMPTY,
            )
        }
    }
}

@Composable
private fun ConnectionStatusBar(connection: OBDConnectionState) {
    val color = connection.toStatusColor()
    val label = connection.toStatusLabel()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.20f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .background(color = color, shape = RoundedCornerShape(50))
                    .padding(5.dp),
            ) {
                Box(modifier = Modifier.height(10.dp).background(color)) {}
            }
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FuelTankCard(state: DashboardUiState) {
    val pct = state.snapshot?.fuelLevelPct ?: 0f
    val animatedLevel by animateFloatAsState(
        targetValue = (pct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "fuelLevel",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Fuel",
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = OnSurfaceVariantDark,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    val fillHeight = size.height * animatedLevel
                    drawRect(
                        color = SecondaryAccent,
                        topLeft = Offset(0f, size.height - fillHeight),
                        size = Size(size.width, fillHeight),
                    )
                }
                val pctLabel = if (state.snapshot != null) "%.0f%%".format(pct) else EMPTY
                val litersLabel = state.litersRemaining?.let {
                    "  ·  %.1f L".format(it)
                } ?: ""
                Text(
                    text = pctLabel + litersLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun AutonomyCard(autonomyKm: Double?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Autonomy",
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
            )
            Text(
                text = autonomyKm?.let { "%.0f km".format(it) } ?: EMPTY,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            )
        }
    }
}

private fun OBDConnectionState.toStatusColor(): Color = when (this) {
    is OBDConnectionState.Connected -> StatusGreen
    is OBDConnectionState.Connecting, is OBDConnectionState.Initializing -> StatusAmber
    is OBDConnectionState.Disconnected -> OnSurfaceVariantDark
    is OBDConnectionState.Error -> StatusRed
}

private fun OBDConnectionState.toStatusLabel(): String = when (this) {
    is OBDConnectionState.Connected -> "Connected"
    is OBDConnectionState.Connecting -> "Connecting..."
    is OBDConnectionState.Initializing -> "Initializing..."
    is OBDConnectionState.Disconnected -> "Disconnected"
    is OBDConnectionState.Error -> "Error: $message"
}
