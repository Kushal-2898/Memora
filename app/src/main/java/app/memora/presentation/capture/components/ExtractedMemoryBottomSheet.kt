package app.memora.presentation.capture.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.model.EventType
import app.memora.presentation.common.PrimaryButton
import app.memora.presentation.common.SecondaryButton
import app.memora.presentation.common.formatDaysRelative
import app.memora.presentation.common.toDisplayName
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Stage 4 Capture Flow Review Bottom Sheet:
 * - Slides up with extracted result
 * - Date shown large and editable
 * - Scroll picker (1–10 days) default 1, clean drum-roll style
 * - Calendar option as secondary tap below scroll picker
 * - Single "Remember" CTA button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractedMemoryBottomSheet(
    candidate: ExtractedMemoryCandidate?,
    onSave: (title: String, eventType: EventType, eventDate: LocalDate, remindDaysBefore: Int) -> Unit,
    onRetake: () -> Unit = {},
    onReplace: () -> Unit = {},
    onRescan: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestDismiss: () -> Unit = onDismiss,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sheetValue ->
            if (sheetValue == SheetValue.Hidden) {
                onRequestDismiss()
                false
            } else {
                true
            }
        },
    ),
) {
    val context = LocalContext.current
    val handleRescan = onRescan ?: onRetake

    val today = remember { LocalDate.now() }
    val candidateDate = candidate?.suggestedEventDate
    val futureAlt = candidate?.alternatives?.firstOrNull { it.eventDate != null && !it.eventDate.isBefore(today) }
    val initialDate = when {
        candidateDate != null && !candidateDate.isBefore(today) -> candidateDate
        futureAlt?.eventDate != null -> futureAlt.eventDate
        candidateDate != null -> candidateDate
        else -> today.plusDays(7)
    }
    val initialTitle = candidate?.suggestedTitle?.takeIf { it.isNotBlank() } ?: "Important date"
    val initialEventType = candidate?.suggestedEventType ?: EventType.OTHER

    var title by remember { mutableStateOf(initialTitle) }
    var selectedEventType by remember { mutableStateOf(initialEventType) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var remindDaysBefore by remember { mutableIntStateOf(1) }

    val isPastDate = selectedDate.isBefore(today)

    fun showCalendarPicker() {
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth,
        )
        dialog.show()
    }

    val isAmbiguous = (candidate?.alternatives?.size ?: 0) > 1

    ModalBottomSheet(
        onDismissRequest = onRequestDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderLight)
            )
        },
        modifier = modifier.testTag("capture_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isAmbiguous) "Which date to remember?" else "Does this look right?",
                    style = MemoraTypography.title.copy(fontSize = 19.sp),
                    color = PrimaryNavy,
                    modifier = Modifier.weight(1f, fill = false),
                )
                ConfidenceChip(
                    confidence = candidate?.confidence ?: 0.5f,
                    isAmbiguous = isAmbiguous,
                    showPercentage = true,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isAmbiguous) {
                Text(
                    text = "Multiple dates detected. Select the correct one:",
                    style = MemoraTypography.caption,
                    color = TextCaption,
                )
                Spacer(modifier = Modifier.height(8.dp))
                candidate?.alternatives?.forEach { alt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(BorderStroke(1.dp, if (selectedDate == alt.eventDate) AccentIndigo else BorderLight), RoundedCornerShape(8.dp))
                            .background(if (selectedDate == alt.eventDate) AccentIndigo.copy(alpha = 0.05f) else Color.Transparent)
                            .clickable { alt.eventDate?.let { selectedDate = it } }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = alt.eventDate?.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) ?: "",
                                style = MemoraTypography.headline.copy(fontSize = 15.sp),
                                color = PrimaryNavy,
                            )
                            if (alt.context.isNotBlank()) {
                                Text(
                                    text = alt.context,
                                    style = MemoraTypography.caption,
                                    color = TextCaption,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "Verify the details below and configure your reminder",
                style = MemoraTypography.caption,
                color = TextCaption,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Title Input
            Text(
                text = "Title",
                style = MemoraTypography.label,
                color = PrimaryNavy,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_title_input"),
                textStyle = MemoraTypography.body.copy(color = PrimaryNavy),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentIndigo,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = SurfaceCool,
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Event Type Selection Chips
            Text(
                text = "Event type",
                style = MemoraTypography.label,
                color = PrimaryNavy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EventType.values().forEach { type ->
                    val isSelected = type == selectedEventType
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentIndigo else SurfaceCool)
                            .border(
                                BorderStroke(1.dp, if (isSelected) AccentIndigo else BorderLight),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { selectedEventType = type }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("event_type_${type.name.lowercase()}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = type.toDisplayName(),
                            style = MemoraTypography.label,
                            color = if (isSelected) Color.White else PrimaryNavy,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Date shown large and editable
            Text(
                text = "Event date",
                style = MemoraTypography.label,
                color = PrimaryNavy,
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (isPastDate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF3CD))
                        .border(BorderStroke(1.dp, Color(0xFFFFC107)), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFF856404),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "This date has already passed. Please verify if your bill has a future Payment Due Date.",
                        style = MemoraTypography.caption,
                        color = Color(0xFF856404),
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCool)
                    .border(
                        BorderStroke(1.dp, if (isPastDate) Color(0xFFE57373) else BorderLight),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { showCalendarPicker() }
                    .padding(16.dp)
                    .testTag("capture_large_date_display"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        // Large date typography
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                            style = MemoraTypography.title.copy(fontSize = 20.sp),
                            color = PrimaryNavy,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatDaysRelative(selectedDate),
                            style = MemoraTypography.caption,
                            color = if (isPastDate) Color(0xFFD32F2F) else AccentIndigo,
                            fontWeight = if (isPastDate) FontWeight.Bold else FontWeight.Medium,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardWhite)
                            .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditCalendar,
                            contentDescription = "Edit date",
                            tint = AccentIndigo,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Scroll picker (1–10 days) default 1, clean drum-roll style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Remind me before",
                    style = MemoraTypography.label,
                    color = PrimaryNavy,
                )
                Text(
                    text = if (remindDaysBefore == 1) "1 day before" else "$remindDaysBefore days before",
                    style = MemoraTypography.caption,
                    color = AccentIndigo,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal drum-roll style scroll picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("scroll_picker_days"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..10).forEach { days ->
                    val isSelected = days == remindDaysBefore
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AccentIndigo else SurfaceCool)
                            .border(
                                BorderStroke(1.dp, if (isSelected) AccentIndigo else BorderLight),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { remindDaysBefore = days }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("picker_day_$days"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (days == 1) "1 day" else "$days days",
                            style = MemoraTypography.body.copy(fontSize = 14.sp),
                            color = if (isSelected) Color.White else PrimaryNavy,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Calendar option as secondary tap below scroll picker
            SecondaryButton(
                text = "Choose date on calendar",
                icon = Icons.Outlined.CalendarMonth,
                onClick = { showCalendarPicker() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_calendar_secondary_tap"),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            PrimaryButton(
                text = "Save Memory",
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(title, selectedEventType, selectedDate, remindDaysBefore)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_remember_button"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryButton(
                text = "Rescan",
                icon = Icons.Filled.Refresh,
                onClick = handleRescan,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_action_rescan"),
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
