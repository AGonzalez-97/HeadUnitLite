package com.onixgarage.phone.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.core.data.entity.ServiceType
import com.onixgarage.core.domain.AlertLevel
import com.onixgarage.core.domain.ServiceStatus
import com.onixgarage.phone.ui.common.MetricRow
import com.onixgarage.phone.ui.common.formatArs
import com.onixgarage.phone.ui.common.formatTimestamp
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.StatusAmber
import com.onixgarage.phone.ui.theme.StatusGreen
import com.onixgarage.phone.ui.theme.StatusRed
import com.onixgarage.phone.ui.theme.SurfaceVariantDark

@Composable
fun ServiceScreen(
    state: ServiceUiState,
    formState: ServiceFormState,
    onFormChange: (ServiceFormField) -> Unit,
    onSave: () -> Unit,
    onDelete: (Long) -> Unit,
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
            if (state.alertStatuses.isNotEmpty()) {
                AlertsSection(state.alertStatuses)
            }
            OdometerSection(state)
            ServiceFormCard(formState, onFormChange, onSave, state.isSaving)
            HistorySection(state.statuses, onDelete)
        }
    }
}

@Composable
private fun AlertsSection(alertStatuses: List<ServiceStatus>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Alerts",
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alertStatuses.forEach { status -> AlertCard(status) }
            }
        }
    }
}

@Composable
private fun AlertCard(status: ServiceStatus) {
    val bgColor = when (status.alert) {
        AlertLevel.RED -> StatusRed.copy(alpha = 0.15f)
        AlertLevel.AMBER -> StatusAmber.copy(alpha = 0.15f)
        AlertLevel.GREEN -> StatusGreen.copy(alpha = 0.15f)
    }
    val accentColor = when (status.alert) {
        AlertLevel.RED -> StatusRed
        AlertLevel.AMBER -> StatusAmber
        AlertLevel.GREEN -> StatusGreen
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.record.serviceType,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                val days = status.minDaysUntilDue
                val daysLabel = when {
                    days == null -> "No threshold set"
                    days <= 0.0 -> "Overdue"
                    else -> "In %.0f days".format(days)
                }
                Text(text = daysLabel, color = accentColor, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OdometerSection(state: ServiceUiState) {
    MetricRow(
        leftLabel = "Estimated odometer",
        leftValue = "${state.estimatedOdometerKm} km",
        rightLabel = "Avg daily km (30d)",
        rightValue = "%.1f km/day".format(state.avgDailyKm),
    )
}

@Composable
private fun ServiceFormCard(
    form: ServiceFormState,
    onFormChange: (ServiceFormField) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
) {
    if (!form.showForm) {
        Button(
            onClick = { onFormChange(ServiceFormField.ShowForm(true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add service")
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceVariantDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Add service",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { onFormChange(ServiceFormField.ShowForm(false)) }) {
                        Text("Cancel")
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    ServiceTypeDropdown(form.serviceType) { onFormChange(ServiceFormField.Type(it)) }
                    OutlinedTextField(
                        value = form.odometerKm,
                        onValueChange = { onFormChange(ServiceFormField.Odometer(it)) },
                        label = { Text("Odometer (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.costARS,
                        onValueChange = { onFormChange(ServiceFormField.Cost(it)) },
                        label = { Text("Cost (ARS)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = { onFormChange(ServiceFormField.Notes(it)) },
                        label = { Text("Notes (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.nextServiceAtKm,
                        onValueChange = { onFormChange(ServiceFormField.NextKm(it)) },
                        label = { Text("Next service at km (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.nextServiceAtDays,
                        onValueChange = { onFormChange(ServiceFormField.NextDays(it)) },
                        label = { Text("Next service in days (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onSave,
                        enabled = form.isValid && !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isSaving) "Saving..." else "Save service")
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceTypeDropdown(selected: ServiceType, onSelect: (ServiceType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected.dbValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("Service type") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ServiceType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.dbValue) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HistorySection(statuses: List<ServiceStatus>, onDelete: (Long) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "History", color = OnSurfaceVariantDark, fontSize = 12.sp)
            if (statuses.isEmpty()) {
                Text(
                    text = "No services recorded yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    statuses.forEach { status -> ServiceHistoryItem(status, onDelete) }
                }
            }
        }
    }
}

@Composable
private fun ServiceHistoryItem(status: ServiceStatus, onDelete: (Long) -> Unit) {
    val dotColor = when (status.alert) {
        AlertLevel.GREEN -> StatusGreen
        AlertLevel.AMBER -> StatusAmber
        AlertLevel.RED -> StatusRed
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = "${status.record.serviceType} · ${formatTimestamp(status.record.timestampMs)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${status.record.odometerKm} km · ${formatArs(status.record.costARS)}",
                    color = OnSurfaceVariantDark,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = { onDelete(status.record.id) }) {
                Text("Delete", color = StatusRed)
            }
        }
    }
}
