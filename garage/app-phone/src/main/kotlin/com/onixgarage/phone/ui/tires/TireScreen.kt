package com.onixgarage.phone.ui.tires

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.core.domain.AlertLevel
import com.onixgarage.core.domain.TirePressureStatus
import com.onixgarage.phone.ui.common.MetricCard
import com.onixgarage.phone.ui.common.MetricRow
import com.onixgarage.phone.ui.common.formatTimestamp
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.StatusAmber
import com.onixgarage.phone.ui.theme.StatusGreen
import com.onixgarage.phone.ui.theme.StatusRed
import com.onixgarage.phone.ui.theme.SurfaceVariantDark

private const val EMPTY = "—"

@Composable
fun TireScreen(
    state: TireUiState,
    formState: TireFormState,
    onFormChange: (TireFormField) -> Unit,
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
            AlertBanner(state.latestStatus)
            CurrentPressureSection(state.latestStatus)
            RecommendedPsiCard(state.latestStatus)
            TireFormCard(formState, onFormChange, onSave, state.isSaving)
            TireHistorySection(state.history, onDelete)
        }
    }
}

@Composable
private fun AlertBanner(latestStatus: TirePressureStatus?) {
    if (latestStatus == null || latestStatus.overall == AlertLevel.GREEN) return
    val bgColor = when (latestStatus.overall) {
        AlertLevel.RED -> StatusRed.copy(alpha = 0.15f)
        AlertLevel.AMBER -> StatusAmber.copy(alpha = 0.15f)
        AlertLevel.GREEN -> StatusGreen.copy(alpha = 0.15f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = "Check tires — pressure out of range",
            modifier = Modifier.padding(12.dp),
            color = when (latestStatus.overall) {
                AlertLevel.RED -> StatusRed
                AlertLevel.AMBER -> StatusAmber
                AlertLevel.GREEN -> StatusGreen
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CurrentPressureSection(latestStatus: TirePressureStatus?) {
    val flColor = latestStatus?.frontLeftAlert?.toColor()
    val frColor = latestStatus?.frontRightAlert?.toColor()
    val rlColor = latestStatus?.rearLeftAlert?.toColor()
    val rrColor = latestStatus?.rearRightAlert?.toColor()

    val fl = latestStatus?.record?.frontLeftPsi?.let { "%.1f psi".format(it) } ?: EMPTY
    val fr = latestStatus?.record?.frontRightPsi?.let { "%.1f psi".format(it) } ?: EMPTY
    val rl = latestStatus?.record?.rearLeftPsi?.let { "%.1f psi".format(it) } ?: EMPTY
    val rr = latestStatus?.record?.rearRightPsi?.let { "%.1f psi".format(it) } ?: EMPTY

    MetricRow(
        leftLabel = "Front Left",
        leftValue = fl,
        rightLabel = "Front Right",
        rightValue = fr,
        leftColor = flColor,
        rightColor = frColor,
    )
    MetricRow(
        leftLabel = "Rear Left",
        leftValue = rl,
        rightLabel = "Rear Right",
        rightValue = rr,
        leftColor = rlColor,
        rightColor = rrColor,
    )

    val spareStatus = latestStatus?.spareAlert
    val sparePsi = latestStatus?.record?.sparePsi
    if (sparePsi != null && spareStatus != null) {
        MetricCard(
            label = "Spare",
            value = "%.1f psi".format(sparePsi),
            valueColor = spareStatus.toColor(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecommendedPsiCard(latestStatus: TirePressureStatus?) {
    val value = latestStatus?.record?.recommendedPsi?.let { "%.0f PSI".format(it) } ?: "32 PSI"
    MetricCard(
        label = "Recommended pressure",
        value = value,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TireFormCard(
    form: TireFormState,
    onFormChange: (TireFormField) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
) {
    if (!form.showForm) {
        Button(
            onClick = { onFormChange(TireFormField.ShowForm(true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add reading")
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
                        text = "Add reading",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { onFormChange(TireFormField.ShowForm(false)) }) {
                        Text("Cancel")
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = form.frontLeft,
                        onValueChange = { onFormChange(TireFormField.FrontLeft(it)) },
                        label = { Text("Front Left (psi)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.frontRight,
                        onValueChange = { onFormChange(TireFormField.FrontRight(it)) },
                        label = { Text("Front Right (psi)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.rearLeft,
                        onValueChange = { onFormChange(TireFormField.RearLeft(it)) },
                        label = { Text("Rear Left (psi)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.rearRight,
                        onValueChange = { onFormChange(TireFormField.RearRight(it)) },
                        label = { Text("Rear Right (psi)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.spare,
                        onValueChange = { onFormChange(TireFormField.Spare(it)) },
                        label = { Text("Spare (psi, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.recommendedPsi,
                        onValueChange = { onFormChange(TireFormField.RecommendedPsi(it)) },
                        label = { Text("Recommended PSI") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = { onFormChange(TireFormField.Notes(it)) },
                        label = { Text("Notes (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onSave,
                        enabled = form.isValid && !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isSaving) "Saving..." else "Save reading")
                    }
                }
            }
        }
    }
}

@Composable
private fun TireHistorySection(history: List<TirePressureStatus>, onDelete: (Long) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "History", color = OnSurfaceVariantDark, fontSize = 12.sp)
            if (history.isEmpty()) {
                Text(
                    text = "No readings recorded yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.forEach { status -> TireHistoryItem(status, onDelete) }
                }
            }
        }
    }
}

@Composable
private fun TireHistoryItem(status: TirePressureStatus, onDelete: (Long) -> Unit) {
    val dotColor = status.overall.toColor()
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
                    text = formatTimestamp(status.record.timestampMs),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "FL:%.1f FR:%.1f RL:%.1f RR:%.1f".format(
                        status.record.frontLeftPsi,
                        status.record.frontRightPsi,
                        status.record.rearLeftPsi,
                        status.record.rearRightPsi,
                    ),
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

private fun AlertLevel.toColor(): Color = when (this) {
    AlertLevel.GREEN -> StatusGreen
    AlertLevel.AMBER -> StatusAmber
    AlertLevel.RED -> StatusRed
}
