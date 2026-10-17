package app.memora.presentation.capture.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.ui.theme.MemoraTypography

/**
 * Visual confidence indicator chip displaying high, medium, or low OCR/AI confidence
 * to help users quickly assess date extraction certainty.
 */
@Composable
fun ConfidenceChip(
    confidence: Float,
    isAmbiguous: Boolean = false,
    showPercentage: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (bgColor, borderColor, contentColor, labelText, icon) = when {
        isAmbiguous -> ConfidenceVisual(
            bg = Color(0xFFFFF3E0),
            border = Color(0xFFFFB74D),
            content = Color(0xFFE65100),
            label = "Multiple Dates",
            icon = Icons.Outlined.Warning,
        )
        confidence >= 0.80f -> ConfidenceVisual(
            bg = Color(0xFFE8F5E9),
            border = Color(0xFFA5D6A7),
            content = Color(0xFF2E7D32),
            label = if (showPercentage) "High Confidence (${(confidence * 100).toInt()}%)" else "High Confidence",
            icon = Icons.Outlined.CheckCircle,
        )
        confidence >= 0.50f -> ConfidenceVisual(
            bg = Color(0xFFFFF8E1),
            border = Color(0xFFFFE082),
            content = Color(0xFFF57F17),
            label = if (showPercentage) "Medium Confidence (${(confidence * 100).toInt()}%)" else "Medium Confidence",
            icon = Icons.Outlined.Info,
        )
        else -> ConfidenceVisual(
            bg = Color(0xFFFFEBEE),
            border = Color(0xFFFFCDD2),
            content = Color(0xFFC62828),
            label = if (showPercentage) "Low Confidence (${(confidence * 100).toInt()}%)" else "Low Confidence",
            icon = Icons.Outlined.ErrorOutline,
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("confidence_chip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = labelText,
            style = MemoraTypography.label.copy(fontSize = 11.sp),
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class ConfidenceVisual(
    val bg: Color,
    val border: Color,
    val content: Color,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
