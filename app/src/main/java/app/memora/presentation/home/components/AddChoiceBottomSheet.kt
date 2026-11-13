package app.memora.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusDueToday
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChoiceBottomSheet(
    onScanClick: () -> Unit,
    onManualClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCool,
        modifier = modifier.testTag("add_choice_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "New Memory",
                style = MemoraTypography.title,
                color = PrimaryNavy,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How would you like to add this item?",
                style = MemoraTypography.body,
                color = TextCaption,
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Option 1: Scan Product with Camera AI
            ChoiceActionTile(
                icon = Icons.Filled.DocumentScanner,
                iconTint = AccentIndigo,
                iconBg = AccentIndigo.copy(alpha = 0.12f),
                title = "Scan Product (Camera AI)",
                subtitle = "Point at packaging to extract expiry date automatically",
                testTag = "choice_scan_product",
                onClick = {
                    onDismiss()
                    onScanClick()
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Add Manually
            ChoiceActionTile(
                icon = Icons.Filled.Edit,
                iconTint = StatusDueToday,
                iconBg = StatusDueToday.copy(alpha = 0.14f),
                title = "Add Manually (Type Details)",
                subtitle = "Type title, pick date & category • Unlimited free",
                testTag = "choice_add_manually",
                onClick = {
                    onDismiss()
                    onManualClick()
                },
            )
        }
    }
}

@Composable
private fun ChoiceActionTile(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MemoraTypography.title,
                fontSize = 15.sp,
                color = PrimaryNavy,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MemoraTypography.caption,
                color = TextCaption,
                lineHeight = 15.sp,
            )
        }
    }
}
