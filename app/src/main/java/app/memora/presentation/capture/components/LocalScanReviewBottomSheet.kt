package app.memora.presentation.capture.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.domain.extraction.LocalExtractionResult
import app.memora.presentation.common.PrimaryButton
import app.memora.presentation.common.SecondaryButton
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusOverdue
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption
import java.time.format.DateTimeFormatter

/**
 * Stage 3.5 Local Scan Review Bottom Sheet:
 * Presents locally extracted information before any AI API call:
 * - "Here's what Memora found"
 * - Detected item / title
 * - Detected date / date candidates
 * - Extracted fields (MFG, EXP, Batch, etc.)
 * - [ Confirm & Analyze with AI ] CTA
 * - [ Rescan ] CTA
 * - "Enter details manually" fallback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScanReviewBottomSheet(
    localResult: LocalExtractionResult,
    onConfirm: () -> Unit,
    onSkipGemini: () -> Unit,
    onRescan: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sheetValue ->
            if (sheetValue == SheetValue.Hidden) {
                onDismiss()
                false
            } else {
                true
            }
        },
    ),
) {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderLight)
            )
        },
        modifier = modifier.testTag("local_scan_review_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header icon + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentIndigo.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DocumentScanner,
                        contentDescription = null,
                        tint = AccentIndigo,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column {
                    Text(
                        text = "Here's what Memora found",
                        style = MemoraTypography.headline,
                        color = PrimaryNavy,
                    )
                    Text(
                        text = "Scanned on-device · Review before AI confirmation",
                        style = MemoraTypography.caption,
                        color = TextCaption,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCool),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_scan_card"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    // Detected Title / Item
                    Text(
                        text = "ITEM / DOCUMENT",
                        style = MemoraTypography.label.copy(fontSize = 11.sp),
                        color = TextCaption,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = localResult.title ?: "Detected Document",
                        style = MemoraTypography.title.copy(fontSize = 18.sp),
                        color = PrimaryNavy,
                        modifier = Modifier.padding(top = 2.dp, bottom = if (localResult.packagingType != app.memora.domain.extraction.PackagingType.UNKNOWN) 6.dp else 12.dp),
                    )

                    if (localResult.packagingType != app.memora.domain.extraction.PackagingType.UNKNOWN) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentIndigo.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "PACKAGING",
                                style = MemoraTypography.label.copy(fontSize = 10.sp),
                                color = AccentIndigo,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${localResult.packagingType.displayName} · ${localResult.dateLocation.displayName}",
                                style = MemoraTypography.caption.copy(fontWeight = FontWeight.Medium),
                                color = PrimaryNavy,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Detected Date or Ambiguity
                    if (localResult.selectedEventDate != null && !localResult.isAmbiguous) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (localResult.dateType ?: "DETECTED DATE").uppercase(),
                                style = MemoraTypography.label.copy(fontSize = 11.sp),
                                color = TextCaption,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ConfidenceChip(
                                confidence = localResult.confidence,
                                isAmbiguous = false,
                                showPercentage = true,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = if (!localResult.explanation.isNullOrBlank()) 4.dp else 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = localResult.selectedEventDate.format(dateFormatter),
                                style = MemoraTypography.headline.copy(fontSize = 20.sp),
                                color = PrimaryNavy,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (!localResult.explanation.isNullOrBlank()) {
                            Text(
                                text = localResult.explanation,
                                style = MemoraTypography.caption.copy(fontSize = 12.sp),
                                color = TextCaption,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    } else if (localResult.isAmbiguous) {
                        // Ambiguous warning banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFF3E0))
                                .border(BorderStroke(1.dp, Color(0xFFFFB74D)), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .testTag("ambiguous_dates_banner"),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Multiple dates detected",
                                            style = MemoraTypography.body.copy(fontSize = 13.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFE65100),
                                        )
                                    }
                                    ConfidenceChip(
                                        confidence = localResult.confidence,
                                        isAmbiguous = true,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Multiple dates found on this item. Tap 'Review & Save' to select the correct one manually, or use 'Analyze with AI'.",
                                    style = MemoraTypography.caption,
                                    color = Color(0xFFBF360C),
                                )
                                if (localResult.dateCandidates.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    localResult.dateCandidates.forEach { candidate ->
                                        Text(
                                            text = "• ${candidate.eventDate?.format(dateFormatter) ?: "Date"} (${candidate.context})",
                                            style = MemoraTypography.caption.copy(fontSize = 12.sp),
                                            color = PrimaryNavy,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        // No dates detected
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "DETECTED DATE",
                                style = MemoraTypography.label.copy(fontSize = 11.sp),
                                color = TextCaption,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ConfidenceChip(
                                confidence = 0f,
                                isAmbiguous = false,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF5F5F5))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "No clear dates detected yet. Tap 'Enter details manually' or let AI inspect the scan.",
                                style = MemoraTypography.caption,
                                color = TextCaption,
                            )
                        }
                    }

                    // Structured Fields (if any) or Marking Style
                    val hasFields = localResult.fields.isNotEmpty() || localResult.markingStyle != app.memora.domain.extraction.MarkingStyle.UNKNOWN
                    if (hasFields) {
                        Text(
                            text = "EXTRACTED DETAILS",
                            style = MemoraTypography.label.copy(fontSize = 11.sp),
                            color = TextCaption,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        if (localResult.markingStyle != app.memora.domain.extraction.MarkingStyle.UNKNOWN) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Marking Technique",
                                    style = MemoraTypography.caption,
                                    color = TextCaption,
                                )
                                Text(
                                    text = localResult.markingStyle.displayName,
                                    style = MemoraTypography.caption.copy(fontWeight = FontWeight.Medium),
                                    color = PrimaryNavy,
                                )
                            }
                        }
                        localResult.fields.filterKeys { it != "Detected Title" }.forEach { (key, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = key,
                                    style = MemoraTypography.caption,
                                    color = TextCaption,
                                )
                                Text(
                                    text = value,
                                    style = MemoraTypography.caption.copy(fontWeight = FontWeight.Medium),
                                    color = PrimaryNavy,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            PrimaryButton(
                text = "Review & Save",
                icon = androidx.compose.material.icons.Icons.Outlined.CheckCircle,
                onClick = onSkipGemini,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_scan_skip_ai_button"),
            )

            Spacer(modifier = Modifier.height(10.dp))

            SecondaryButton(
                text = "✨ Analyze with AI",
                icon = androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_scan_confirm_ai_button"),
            )

            Spacer(modifier = Modifier.height(10.dp))

            SecondaryButton(
                text = "Rescan",
                icon = Icons.Filled.Refresh,
                onClick = onRescan,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_scan_rescan_button"),
            )

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onEnterManually,
                modifier = Modifier.testTag("local_scan_manual_entry_button"),
            ) {
                Text(
                    text = "Enter details manually",
                    style = MemoraTypography.caption.copy(fontSize = 13.sp),
                    color = AccentIndigo,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
