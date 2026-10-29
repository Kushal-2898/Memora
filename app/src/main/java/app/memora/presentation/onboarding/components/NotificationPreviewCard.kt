package app.memora.presentation.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusCompleted
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

@Composable
fun NotificationPreviewCard(
    modifier: Modifier = Modifier,
) {
    var isDone by remember { mutableStateOf(false) }
    var isSnoozed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("notification_preview_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Notification Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AccentIndigo),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = CardWhite,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MEMORA • MORNING ALERT",
                        style = MemoraTypography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = TextCaption,
                    )
                }

                Text(
                    text = "09:00 AM",
                    style = MemoraTypography.caption,
                    color = TextCaption,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notification Content
            Text(
                text = if (isDone)
                    "✓ Organic Rolled Oats marked completed!"
                else if (isSnoozed)
                    "⏰ Reminder snoozed to tomorrow morning"
                else
                    "Organic Rolled Oats expires in 2 days",
                style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (isDone) StatusCompleted else PrimaryNavy,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isDone)
                    "Archived in your completed history. Zero waste!"
                else if (isSnoozed)
                    "We'll quietly nudge you again tomorrow at 9:00 AM."
                else
                    "Stored in Pantry. Best before Nov 14, 2026. Use it soon!",
                style = MemoraTypography.caption,
                color = TextCaption,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        isDone = !isDone
                        if (isDone) isSnoozed = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("notification_preview_mark_done_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDone) StatusCompleted else AccentIndigo,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = CardWhite,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDone) "Completed" else "Mark Done",
                        style = MemoraTypography.label.copy(color = CardWhite),
                    )
                }

                OutlinedButton(
                    onClick = {
                        isSnoozed = !isSnoozed
                        if (isSnoozed) isDone = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("notification_preview_snooze_btn"),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = PrimaryNavy,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSnoozed) "Snoozed" else "Snooze 1d",
                        style = MemoraTypography.label.copy(color = PrimaryNavy),
                    )
                }
            }
        }
    }
}
