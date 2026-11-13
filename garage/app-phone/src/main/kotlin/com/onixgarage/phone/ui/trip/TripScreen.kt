package com.onixgarage.phone.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.core.data.entity.DailyTrip
import com.onixgarage.phone.ui.common.MetricCard
import com.onixgarage.phone.ui.common.MetricRow
import com.onixgarage.phone.ui.common.formatArs
import com.onixgarage.phone.ui.common.formatDuration
import com.onixgarage.phone.ui.common.formatTimestamp
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.StatusAmber
import com.onixgarage.phone.ui.theme.SurfaceVariantDark

@Composable
fun TripScreen(
    state: TripUiState,
    onPeriodChange: (TripPeriod) -> Unit,
) {
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
            state.ongoingTrip?.let { OngoingTripBanner(it) }
            PeriodSelector(selected = state.period, onSelect = onPeriodChange)
            TripSummarySection(state)
            TripHistorySection(state.trips)
        }
    }
}

@Composable
private fun OngoingTripBanner(trip: DailyTrip) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StatusAmber.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Trip in progress",
                color = StatusAmber,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Started ${formatTimestamp(trip.startTimestampMs)}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
            )
            Text(
                text = "%.1f km · %.0f km/h avg".format(trip.distanceKm, trip.avgSpeedKmh),
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PeriodSelector(selected: TripPeriod, onSelect: (TripPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TripPeriod.entries.forEach { p ->
            val isSelected = p == selected
            TextButton(onClick = { onSelect(p) }) {
                Text(
                    text = p.name,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else OnSurfaceVariantDark,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun TripSummarySection(state: TripUiState) {
    MetricRow(
        leftLabel = "Total km",
        leftValue = "%.1f".format(state.totalKm),
        rightLabel = "Trips",
        rightValue = state.tripCount.toString(),
    )
    MetricRow(
        leftLabel = "Total cost",
        leftValue = formatArs(state.totalCostARS),
        rightLabel = "Fuel used",
        rightValue = "%.1f L".format(state.totalFuelL),
    )
    val avg = state.avgConsumption?.let { "%.1f L/100km".format(it) } ?: "—"
    MetricCard(
        label = "Avg consumption",
        value = avg,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TripHistorySection(trips: List<DailyTrip>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "History", color = OnSurfaceVariantDark, fontSize = 12.sp)
            if (trips.isEmpty()) {
                Text(
                    text = "No trips recorded",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    trips.forEach { trip -> TripHistoryItem(trip) }
                }
            }
        }
    }
}

@Composable
private fun TripHistoryItem(trip: DailyTrip) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestamp(trip.startTimestampMs),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatDuration(trip.durationMs),
                    color = OnSurfaceVariantDark,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "%.1f km · %.0f km/h avg".format(trip.distanceKm, trip.avgSpeedKmh),
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "${formatArs(trip.costARS)} · %.1f L".format(trip.fuelConsumedLiters),
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
            )
        }
    }
}
