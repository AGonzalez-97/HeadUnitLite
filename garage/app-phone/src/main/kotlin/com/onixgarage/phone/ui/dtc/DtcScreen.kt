package com.onixgarage.phone.ui.dtc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.StatusAmber
import com.onixgarage.phone.ui.theme.StatusRed
import com.onixgarage.phone.ui.theme.SurfaceVariantDark

@Composable
fun DtcScreen(
    state: DtcUiState,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
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
            ErrorBanner(state.lastError)
            ActionsRow(
                hasCodes = state.codes.isNotEmpty(),
                isRefreshing = state.isRefreshing,
                isClearing = state.isClearing,
                onRefresh = onRefresh,
                onClear = onClear,
            )
            DtcListSection(state.codes)
        }
    }
}

@Composable
private fun ErrorBanner(error: String?) {
    if (error == null) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StatusRed.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(12.dp),
            color = StatusRed,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActionsRow(
    hasCodes: Boolean,
    isRefreshing: Boolean,
    isClearing: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onRefresh,
            enabled = !isRefreshing && !isClearing,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isRefreshing) "Reading..." else "Read DTCs")
        }
        if (hasCodes) {
            OutlinedButton(
                onClick = onClear,
                enabled = !isRefreshing && !isClearing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isClearing) "Clearing..." else "Clear DTCs")
            }
        }
    }
}

@Composable
private fun DtcListSection(codes: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (codes.isEmpty()) "No fault codes" else "${codes.size} fault code(s) found",
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
            )
            if (codes.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    codes.forEach { code -> DtcItem(code) }
                }
            }
        }
    }
}

@Composable
private fun DtcItem(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StatusAmber.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                color = StatusAmber,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
            )
            Text(
                text = dtcCategory(code),
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
            )
        }
    }
}

private fun dtcCategory(code: String): String = when (code.firstOrNull()?.uppercaseChar()) {
    'P' -> "Powertrain"
    'C' -> "Chassis"
    'B' -> "Body"
    'U' -> "Network"
    else -> "Unknown"
}
