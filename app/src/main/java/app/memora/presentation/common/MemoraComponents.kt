package app.memora.presentation.common

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import app.memora.presentation.common.pressScaleAndHaptic
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.domain.model.EventType
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.ReminderStatus
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusCompleted
import app.memora.ui.theme.StatusDueToday
import app.memora.ui.theme.StatusOverdue
import app.memora.ui.theme.StatusReminderActive
import app.memora.ui.theme.StatusUpcoming
import app.memora.ui.theme.TextCaption
import app.memora.ui.theme.WarmOchreBg
import app.memora.ui.theme.WarmParchment
import app.memora.ui.theme.WarmSageBg
import app.memora.ui.theme.WarmTerracottaBg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Maps [ReminderStatus] to its dedicated design system status color.
 */
fun ReminderStatus.toColor(): Color = when (this) {
    ReminderStatus.UPCOMING -> StatusUpcoming
    ReminderStatus.REMINDER_ACTIVE -> StatusReminderActive
    ReminderStatus.DUE_TODAY -> StatusDueToday
    ReminderStatus.OVERDUE -> StatusOverdue
    ReminderStatus.COMPLETED -> StatusCompleted
}

/**
 * Formats [ReminderStatus] in sentence case for clean visual labels.
 */
fun ReminderStatus.toDisplayName(): String = when (this) {
    ReminderStatus.UPCOMING -> "Upcoming"
    ReminderStatus.REMINDER_ACTIVE -> "Active"
    ReminderStatus.DUE_TODAY -> "Due today"
    ReminderStatus.OVERDUE -> "Overdue"
    ReminderStatus.COMPLETED -> "Completed"
}

/**
 * Formats [EventType] in clean sentence case.
 */
fun EventType.toDisplayName(): String = when (this) {
    EventType.DUE_DATE -> "Due date"
    EventType.EXPIRATION -> "Expiration"
    EventType.BEST_BEFORE -> "Best before"
    EventType.WARRANTY_END -> "Warranty"
    EventType.RETURN_DEADLINE -> "Return"
    EventType.SUBSCRIPTION_END -> "Subscription"
    EventType.OTHER -> "Event"
}

/**
 * Prominent relative days representation (e.g. "Today", "Tomorrow", "In 5 days", "3 days ago").
 */
fun formatDaysRelative(eventDate: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, eventDate)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days > 1L -> "In $days days"
        else -> "${abs(days)} days ago"
    }
}

/**
 * Status Badge Component:
 * - Pill shape, warm soft paper background tint
 * - Sentence-case status label with tabular alignment
 */
@Composable
fun StatusBadge(
    status: ReminderStatus,
    modifier: Modifier = Modifier,
) {
    val color = status.toColor()
    val bg = when (status) {
        ReminderStatus.OVERDUE -> WarmTerracottaBg
        ReminderStatus.DUE_TODAY -> WarmOchreBg
        ReminderStatus.COMPLETED -> WarmSageBg
        else -> WarmParchment
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.toDisplayName(),
            color = color,
            style = MemoraTypography.label.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Small outlined Event Type chip with warm linen parchment background.
 */
@Composable
fun EventTypeChip(
    eventType: EventType,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(WarmParchment)
            .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = eventType.toDisplayName(),
            color = TextCaption,
            style = MemoraTypography.label.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Primary Button:
 * - Filled terracotta/charcoal, linen cream text, 16dp radius
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current

    if (isPressed) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = 400f),
        label = "ButtonScale"
    )

    Button(
        onClick = {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryNavy,
            contentColor = Color.White,
            disabledContainerColor = PrimaryNavy.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MemoraTypography.body.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
        )
    }
}

/**
 * Secondary Button:
 * - Crisp white with warm border, 16dp radius
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderLight),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CardWhite,
            contentColor = PrimaryNavy,
        ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryNavy,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MemoraTypography.body.copy(fontWeight = FontWeight.Medium),
            color = PrimaryNavy,
        )
    }
}

/**
 * Memora Extended FAB:
 * - Warm terracotta/charcoal background, white icon
 */
@Composable
fun MemoraExtendedFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "home_capture_button",
    icon: ImageVector = Icons.Outlined.DocumentScanner,
    text: String = "Scan",
) {
    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current

    if (isPressed) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = 400f),
        label = "FabScale"
    )

    ExtendedFloatingActionButton(
        onClick = {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .testTag(testTag)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        },
        text = {
            Text(
                text = text,
                style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        },
        containerColor = AccentIndigo,
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
    )
}

/**
 * Soft Minimal Memora Card specification (Bear / Craft Aesthetic):
 * - border-radius 20dp
 * - 1dp warm hairline border (#EDE7DD), soft paper elevation 2dp
 * - 48dp tactile category glyph box with warm natural backgrounds
 * - Sentence-case metadata and tabular date numerals
 */
@Composable
fun MemoraMemoryCard(
    item: MemoryWithReminders,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "memory_card_${item.memory.id}",
) {
    val status = item.effectiveStatus
    val statusColor = status.toColor()
    val relativeDays = formatDaysRelative(item.memory.eventDate)
    val formattedDate = item.memory.eventDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .pressScaleAndHaptic(scaleDownTo = 0.985f, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite,
        ),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category Glyph Box (48x48dp, 14dp radius)
            val glyphBg = when (status) {
                ReminderStatus.OVERDUE -> WarmTerracottaBg
                ReminderStatus.DUE_TODAY -> WarmOchreBg
                ReminderStatus.COMPLETED -> WarmSageBg
                else -> WarmParchment
            }
            val glyphBorder = when (status) {
                ReminderStatus.OVERDUE -> Color(0xFFF5DACF)
                ReminderStatus.DUE_TODAY -> Color(0xFFF3E5C9)
                ReminderStatus.COMPLETED -> Color(0xFFD5E5DA)
                else -> BorderLight
            }
            val glyphIcon = when (item.memory.eventType) {
                EventType.DUE_DATE -> Icons.Outlined.Notifications
                EventType.EXPIRATION -> Icons.Outlined.Schedule
                EventType.BEST_BEFORE -> Icons.Outlined.Schedule
                EventType.WARRANTY_END -> Icons.Outlined.CheckCircle
                EventType.RETURN_DEADLINE -> Icons.Outlined.Schedule
                EventType.SUBSCRIPTION_END -> Icons.Outlined.Info
                EventType.OTHER -> Icons.Outlined.Info
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(glyphBg)
                    .border(BorderStroke(1.dp, glyphBorder), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = glyphIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center details: Title, description, date & event type chip
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.memory.title,
                    style = MemoraTypography.headline,
                    color = PrimaryNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!item.memory.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.memory.description,
                        style = MemoraTypography.body.copy(fontSize = 13.sp),
                        color = TextCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EventTypeChip(eventType = item.memory.eventType)
                    Text(
                        text = formattedDate,
                        style = MemoraTypography.caption,
                        color = TextCaption,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right side: Prominent relative days badge & status badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                val badgeBg = when (status) {
                    ReminderStatus.OVERDUE -> WarmTerracottaBg
                    ReminderStatus.DUE_TODAY -> WarmOchreBg
                    ReminderStatus.COMPLETED -> WarmSageBg
                    else -> WarmParchment
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = relativeDays,
                        style = MemoraTypography.label.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                        color = statusColor,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                StatusBadge(status = status)
            }
        }
    }
}

/**
 * Standard Memora Empty State:
 * - Outlined icon (no illustrations)
 * - Single short sentence
 * - One action button
 */
@Composable
fun MemoraEmptyState(
    icon: ImageVector,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "empty_state",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AccentIndigo.copy(alpha = 0.08f))
                .border(BorderStroke(1.dp, AccentIndigo.copy(alpha = 0.16f)), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentIndigo,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MemoraTypography.body,
            color = TextCaption,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = actionLabel,
            onClick = onAction,
        )
    }
}
