package app.memora.presentation.home.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.data.local.OnboardingPreferences
import app.memora.domain.model.EventType
import app.memora.presentation.common.PrimaryButton
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemoryBottomSheet(
    onSave: (
        title: String,
        eventType: EventType,
        eventDate: LocalDate,
        remindDaysBefore: Int,
        reminderTime: LocalTime,
        customCategory: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
    preferences: OnboardingPreferences,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var selectedEventType by remember { mutableStateOf(EventType.EXPIRATION) }
    var selectedCustomCategory by remember { mutableStateOf<String?>(null) }
    var customCategories by remember { mutableStateOf(preferences.getCustomCategories().toList()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now().plusDays(3)) }
    var remindDaysBefore by remember { mutableIntStateOf(1) }
    var selectedReminderTime by remember {
        mutableStateOf(preferences.getDefaultReminderTime() ?: LocalTime.of(9, 0))
    }

    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var showProPaywallDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    val datePickerDialog = remember(selectedDate) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth,
        )
    }

    val timePickerDialog = remember(selectedReminderTime) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedReminderTime = LocalTime.of(hourOfDay, minute)
            },
            selectedReminderTime.hour,
            selectedReminderTime.minute,
            android.text.format.DateFormat.is24HourFormat(context),
        )
    }

    val standardCategories = listOf(
        EventType.EXPIRATION,
        EventType.DUE_DATE,
        EventType.WARRANTY_END,
        EventType.SUBSCRIPTION_END,
        EventType.BEST_BEFORE,
        EventType.OTHER,
    )

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = sheetState.isVisible) {
        if (isKeyboardVisible) {
            focusManager.clearFocus()
            keyboardController?.hide()
        } else {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    onDismiss()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
        ),
        containerColor = SurfaceCool,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderLight),
            )
        },
        modifier = modifier.testTag("add_memory_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Add Memory",
                style = MemoraTypography.title,
                color = PrimaryNavy,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Type details & set reminder date",
                style = MemoraTypography.body,
                color = TextCaption,
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 1. Title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120) },
                label = { Text("Item Title *") },
                placeholder = { Text("e.g. Milk, Electricity Bill, Passport") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_memory_title_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderLight,
                    focusedBorderColor = AccentIndigo,
                    unfocusedContainerColor = CardWhite,
                    focusedContainerColor = CardWhite,
                    focusedLabelColor = AccentIndigo,
                    unfocusedLabelColor = TextCaption,
                ),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Category selection row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Category *",
                    style = MemoraTypography.label,
                    color = PrimaryNavy,
                )
                if (selectedCustomCategory != null) {
                    Text(
                        text = "Custom: $selectedCustomCategory",
                        style = MemoraTypography.caption,
                        color = AccentIndigo,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Standard categories
                standardCategories.forEach { type ->
                    val isSelected = selectedCustomCategory == null && selectedEventType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) AccentIndigo else CardWhite)
                            .border(
                                BorderStroke(1.dp, if (isSelected) AccentIndigo else BorderLight),
                                RoundedCornerShape(50),
                            )
                            .clickable {
                                selectedCustomCategory = null
                                selectedEventType = type
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("category_chip_${type.name.lowercase()}"),
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

                // Custom categories (saved by user)
                customCategories.forEach { customCat ->
                    val isSelected = selectedCustomCategory == customCat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) AccentIndigo else CardWhite)
                            .border(
                                BorderStroke(1.dp, if (isSelected) AccentIndigo else BorderLight),
                                RoundedCornerShape(50),
                            )
                            .clickable {
                                selectedCustomCategory = customCat
                                selectedEventType = EventType.OTHER
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("custom_category_$customCat"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = customCat,
                            style = MemoraTypography.label,
                            color = if (isSelected) Color.White else PrimaryNavy,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }

                // "+ New Category" chip (Pro gated)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFFFBEB))
                        .border(
                            BorderStroke(1.dp, Color(0xFFF59E0B)),
                            RoundedCornerShape(50),
                        )
                        .clickable {
                            if (preferences.isProUser()) {
                                newCategoryInput = ""
                                showNewCategoryDialog = true
                            } else {
                                showProPaywallDialog = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("btn_new_category"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "New Category",
                            style = MemoraTypography.label,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFF59E0B))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "PRO",
                                style = MemoraTypography.label.copy(fontSize = 9.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Event Date Picker
            Text(
                text = "Event Date *",
                style = MemoraTypography.label,
                color = PrimaryNavy,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(16.dp))
                    .clickable { datePickerDialog.show() }
                    .padding(16.dp)
                    .testTag("add_memory_date_picker"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                            style = MemoraTypography.title.copy(fontSize = 18.sp),
                            color = PrimaryNavy,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatDaysRelative(selectedDate),
                            style = MemoraTypography.caption,
                            color = AccentIndigo,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCool)
                            .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditCalendar,
                            contentDescription = "Select date",
                            tint = AccentIndigo,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Quick Date chips row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val quickDates = listOf(
                    "Today" to LocalDate.now(),
                    "+3 days" to LocalDate.now().plusDays(3),
                    "+1 week" to LocalDate.now().plusWeeks(1),
                    "+1 month" to LocalDate.now().plusMonths(1),
                )
                quickDates.forEach { (label, date) ->
                    val isSelected = selectedDate == date
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AccentIndigo.copy(alpha = 0.12f) else CardWhite)
                            .border(
                                BorderStroke(1.dp, if (isSelected) AccentIndigo else BorderLight),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { selectedDate = date }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MemoraTypography.caption,
                            color = if (isSelected) AccentIndigo else PrimaryNavy,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Remind Me Before
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
                    text = if (remindDaysBefore == 0) "On the day" else if (remindDaysBefore == 1) "1 day before" else "$remindDaysBefore days before",
                    style = MemoraTypography.caption,
                    color = AccentIndigo,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val reminderOptions = listOf(
                0 to "Same day",
                1 to "1 day",
                2 to "2 days",
                3 to "3 days",
                7 to "1 week",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                reminderOptions.forEach { (days, label) ->
                    val isSelected = remindDaysBefore == days
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AccentIndigo else CardWhite)
                            .border(
                                BorderStroke(1.dp, if (isSelected) AccentIndigo else BorderLight),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { remindDaysBefore = days }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("remind_before_$days"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MemoraTypography.body.copy(fontSize = 13.sp),
                            color = if (isSelected) Color.White else PrimaryNavy,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Reminder Delivery Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Reminder delivery time",
                    style = MemoraTypography.label,
                    color = PrimaryNavy,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardWhite)
                        .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(8.dp))
                        .clickable { timePickerDialog.show() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("add_memory_time_picker"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = AccentIndigo,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = selectedReminderTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                            style = MemoraTypography.caption,
                            color = AccentIndigo,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 6. Save Button
            PrimaryButton(
                text = "Save Memory",
                onClick = {
                    onSave(
                        title.trim(),
                        selectedEventType,
                        selectedDate,
                        remindDaysBefore,
                        selectedReminderTime,
                        selectedCustomCategory,
                    )
                },
                enabled = title.trim().isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_manual_memory"),
            )
        }
    }

    // Dialog for creating a custom category (Pro users)
    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            containerColor = CardWhite,
            title = {
                Text(
                    text = "New Category",
                    style = MemoraTypography.title,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Add a custom category to tag and organize your memories.",
                        style = MemoraTypography.body,
                        color = TextCaption,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it.take(30) },
                        label = { Text("Category Name *") },
                        placeholder = { Text("e.g. Cosmetics, Vehicle, Insurance") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_category_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newCategoryInput.trim()
                        if (trimmed.isNotBlank()) {
                            preferences.addCustomCategory(trimmed)
                            customCategories = preferences.getCustomCategories().toList()
                            selectedCustomCategory = trimmed
                            selectedEventType = EventType.OTHER
                            newCategoryInput = ""
                            showNewCategoryDialog = false
                        }
                    },
                    enabled = newCategoryInput.trim().isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Paywall Dialog for Non-Pro users trying to add custom category
    if (showProPaywallDialog) {
        AlertDialog(
            onDismissRequest = { showProPaywallDialog = false },
            containerColor = CardWhite,
            icon = {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    text = "Memora Pro Feature",
                    style = MemoraTypography.title,
                )
            },
            text = {
                Text(
                    text = "Custom categories are exclusively available for Memora Pro members.\n\n" +
                        "Organize your memories by household, vehicle, cosmetics, pet care, and more.\n\n" +
                        "Upgrade to Pro in Settings or restore an existing purchase.",
                    style = MemoraTypography.body,
                )
            },
            confirmButton = {
                TextButton(onClick = { showProPaywallDialog = false }) {
                    Text("Got it")
                }
            },
        )
    }
}
