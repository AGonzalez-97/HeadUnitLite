package com.onixgarage.phone.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onixgarage.phone.ui.theme.OnSurfaceVariantDark
import com.onixgarage.phone.ui.theme.SurfaceVariantDark

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Surface(
        modifier = modifier,
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                color = OnSurfaceVariantDark,
                fontSize = 12.sp,
            )
            Text(
                text = value,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
fun MetricRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    leftColor: Color? = null,
    rightColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            label = leftLabel,
            value = leftValue,
            valueColor = leftColor,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = rightLabel,
            value = rightValue,
            valueColor = rightColor,
            modifier = Modifier.weight(1f),
        )
    }
}
