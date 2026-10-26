package app.memora.presentation.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.memora.domain.model.EventType
import app.memora.domain.model.MemoryWithReminders
import app.memora.presentation.common.EventTypeChip
import app.memora.presentation.common.PrimaryButton
import app.memora.presentation.common.toDisplayName
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMemoryBottomSheet(
    memoryItem: MemoryWithReminders,
    onSave: (title: String, eventType: EventType, eventDate: LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(memoryItem.memory.title) }
    var eventType by remember { mutableStateOf(memoryItem.memory.eventType) }
    var eventDate by remember { mutableStateOf(memoryItem.memory.eventDate) }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                eventDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            eventDate.year,
            eventDate.monthValue - 1,
            eventDate.dayOfMonth
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCool,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Edit Memory",
                style = MemoraTypography.title,
                color = PrimaryNavy
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Title") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderLight,
                    focusedBorderColor = AccentIndigo,
                    unfocusedContainerColor = CardWhite,
                    focusedContainerColor = CardWhite,
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Date
            OutlinedTextField(
                value = eventDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() },
                readOnly = true,
                enabled = false,
                label = { Text("Expiration Date") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.EditCalendar,
                        contentDescription = "Edit Date",
                        tint = AccentIndigo
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = BorderLight,
                    disabledContainerColor = CardWhite,
                    disabledTextColor = PrimaryNavy,
                    disabledLabelColor = PrimaryNavy,
                    disabledTrailingIconColor = AccentIndigo,
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Category",
                style = MemoraTypography.headline,
                color = PrimaryNavy
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            val categories = EventType.entries
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { type ->
                    val isSelected = type == eventType
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentIndigo else SurfaceCool)
                            .border(
                                androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AccentIndigo else BorderLight),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { eventType = type }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("event_type_${type.name.lowercase()}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = type.toDisplayName(),
                            style = MemoraTypography.label,
                            color = if (isSelected) Color.White else PrimaryNavy,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Save Changes",
                onClick = { onSave(title, eventType, eventDate) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
