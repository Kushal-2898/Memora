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
import androidx.compose.material.icons.outlined.DocumentScanner
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
 * - Pill shape, 6dp radius
 * - Background: status color at 12% opacity
 * - Text: status color, 12sp, weight 500
 * - Sentence-case text (e.g. "Due today")
 */
@Composable
fun StatusBadge(
    status: ReminderStatus,
    modifier: Modifier = Modifier,
) {
    val color = status.toColor()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.toDisplayName(),
            color = color,
            style = MemoraTypography.label,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Small outlined Event Type chip.
 */
@Composable
fun EventTypeChip(
    eventType: EventType,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
 * - Filled indigo (#4F6BED), white text, 12dp radius
 * - No gradients
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentIndigo,
            contentColor = Color.White,
            disabledContainerColor = AccentIndigo.copy(alpha = 0.4f),
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
 * - White with indigo border (#4F6BED), 12dp radius
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentIndigo),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CardWhite,
            contentColor = AccentIndigo,
        ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentIndigo,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MemoraTypography.body.copy(fontWeight = FontWeight.Medium),
            color = AccentIndigo,
        )
    }
}

/**
 * Memora Extended FAB:
 * - Indigo background (#4F6BED), white icon
 * - Extended FAB with label "Scan"
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
        shape = RoundedCornerShape(16.dp),
    )
}

/**
 * Standard Memora Card specification:
 * - border-radius 16dp
 * - 1dp border (#E4E6ED), no drop shadows
 * - 16dp internal padding
 * - Left accent bar (4dp wide) in status color
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite,
        ),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Left status accent bar (4dp wide)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor),
            )

            // Content Container with 16dp internal padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                // Top row: Title and Prominent Days Until/Since
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = item.memory.title,
                        style = MemoraTypography.headline,
                        color = PrimaryNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )

                    // Days until/since event (right-aligned, prominent)
                    Text(
                        text = relativeDays,
                        style = MemoraTypography.headline.copy(fontSize = 15.sp),
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (!item.memory.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.memory.description,
                        style = MemoraTypography.body.copy(fontSize = 14.sp),
                        color = TextCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom row: Event type chip, Date text, Status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

                    StatusBadge(status = status)
                }
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
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryNavy,
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
