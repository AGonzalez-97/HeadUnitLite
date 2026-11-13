package com.onixgarage.phone.ui.fuel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.core.data.entity.FuelLoad
import com.onixgarage.core.data.entity.FuelType
import com.onixgarage.phone.ui.common.MetricRow
import com.onixgarage.phone.ui.common.formatArs
import com.onixgarage.phone.ui.common.formatTimestamp
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.SecondaryAccent
import com.onixgarage.phone.ui.theme.StatusRed
import com.onixgarage.phone.ui.theme.SurfaceVariantDark

private const val EMPTY = "—"

@Composable
fun FuelScreen(
    state: FuelUiState,
    formState: FuelFormState,
    onFormChange: (FuelFormField) -> Unit,
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
            LiveLevelSection(state)
            EstimationsSection(state)
            FillFormSection(formState, onFormChange, onSave, state.isSaving)
            HistorySection(state.loads, onDelete)
        }
    }
}

@Composable
private fun LiveLevelSection(state: FuelUiState) {
    val pct = state.currentFuelLevelPct ?: 0f
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
            Text(text = "Fuel level", color = OnSurfaceVariantDark, fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = OnSurfaceVariantDark, style = Stroke(width = 2.dp.toPx()))
                    val fillHeight = size.height * animatedLevel
                    drawRect(
                        color = SecondaryAccent,
                        topLeft = Offset(0f, size.height - fillHeight),
                        size = Size(size.width, fillHeight),
                    )
                }
                val pctLabel = state.currentFuelLevelPct?.let { "%.0f%%".format(it) } ?: EMPTY
                val litersLabel = state.litersRemaining?.let { "  ·  %.1f L".format(it) } ?: ""
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
private fun EstimationsSection(state: FuelUiState) {
    val autonomy = state.autonomyKm?.let { "%.0f km".format(it) } ?: EMPTY
    val days = state.daysUntilEmpty?.let { "%.1f days".format(it) } ?: EMPTY
    val dailyCost = state.dailyCostARS?.takeIf { it > 0.0 }?.let { formatArs(it) } ?: formatArs(0.0)
    val costKm = state.costPerKm?.let { formatArs(it) + "/km" } ?: EMPTY
    val monthly = state.monthlyProjectedCostARS?.let { formatArs(it) } ?: EMPTY
    val avgC = state.avgConsumptionL100km?.let { "%.1f L/100km".format(it) } ?: EMPTY

    MetricRow(
        leftLabel = "Autonomy",
        leftValue = autonomy,
        rightLabel = "Days until empty",
        rightValue = days,
    )
    MetricRow(
        leftLabel = "Today cost",
        leftValue = dailyCost,
        rightLabel = "Cost / km",
        rightValue = costKm,
    )
    MetricRow(
        leftLabel = "Monthly projection",
        leftValue = monthly,
        rightLabel = "Avg consumption",
        rightValue = avgC,
    )
}

@Composable
private fun FillFormSection(
    form: FuelFormState,
    onFormChange: (FuelFormField) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
) {
    if (!form.showForm) {
        Button(
            onClick = { onFormChange(FuelFormField.ShowForm(true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Register fill")
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
                        text = "Register fill",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { onFormChange(FuelFormField.ShowForm(false)) }) {
                        Text("Cancel")
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    FuelTypeDropdown(form.fuelType) { onFormChange(FuelFormField.Type(it)) }
                    OutlinedTextField(
                        value = form.liters,
                        onValueChange = { onFormChange(FuelFormField.Liters(it)) },
                        label = { Text("Liters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.pricePerLiter,
                        onValueChange = { onFormChange(FuelFormField.PricePerLiter(it)) },
                        label = { Text("Price per liter (ARS)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.odometerKm,
                        onValueChange = { onFormChange(FuelFormField.OdometerKm(it)) },
                        label = { Text("Odometer (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = form.isTankFull,
                            onCheckedChange = { onFormChange(FuelFormField.TankFull(it)) },
                        )
                        Text("Full tank", color = MaterialTheme.colorScheme.onSurface)
                    }
                    val total = form.totalCost
                    Text(
                        text = "Total: " + (total?.let { formatArs(it) } ?: EMPTY),
                        color = OnSurfaceVariantDark,
                    )
                    Button(
                        onClick = onSave,
                        enabled = form.isValid && !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isSaving) "Saving..." else "Save fill")
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelTypeDropdown(selected: FuelType, onSelect: (FuelType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected.dbValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("Fuel type") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FuelType.entries.forEach { type ->
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
private fun HistorySection(loads: List<FuelLoad>, onDelete: (Long) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "History", color = OnSurfaceVariantDark, fontSize = 12.sp)
            if (loads.isEmpty()) {
                Text(
                    text = "No fills recorded yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    loads.forEach { load -> FuelLoadItem(load, onDelete) }
                }
            }
        }
    }
}

@Composable
private fun FuelLoadItem(load: FuelLoad, onDelete: (Long) -> Unit) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${formatTimestamp(load.timestampMs)} · ${load.fuelType}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "%.2f L · %s/L · %s".format(
                        load.liters,
                        formatArs(load.pricePerLiter),
                        formatArs(load.totalCost),
                    ),
                    color = OnSurfaceVariantDark,
                    fontSize = 12.sp,
                )
                val consumption = load.fuelConsumptionL100km?.let {
                    "%.1f L/100km".format(it)
                } ?: EMPTY
                Text(
                    text = "Consumption: $consumption · ${load.odometerKm} km",
                    color = OnSurfaceVariantDark,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = { onDelete(load.id) }) {
                Text("Delete", color = StatusRed)
            }
        }
    }
}
