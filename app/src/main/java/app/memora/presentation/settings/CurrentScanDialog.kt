package app.memora.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.memora.platform.logging.ScanDiagnostics
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

@Composable
fun CurrentScanDialog(
    scan: ScanDiagnostics?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .testTag("current_scan_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (scan != null) "Scan #${scan.scanId}" else "Current Scan Diagnostics",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                        )
                        if (scan != null) {
                            Text(
                                text = "Total Time: ${scan.totalDurationMs} ms | Image Present: ${scan.imagePresent}",
                                fontSize = 12.sp,
                                color = TextCaption,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_scan_dialog_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = PrimaryNavy)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (scan == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No scan recorded in current session.\nCapture a document or bill to view real-time diagnostics.",
                            color = TextCaption,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Stage 1: OCR
                        DiagnosticStageCard(
                            stageNumber = 1,
                            title = "OCR (ML Kit)",
                            status = scan.ocrStatus,
                            durationMs = scan.ocrDurationMs,
                            summary = "Chars: ${scan.ocrCharCount} | Words: ${scan.ocrWordCount}",
                            rawContentLabel = "View OCR Text",
                            rawContent = scan.rawOcrText,
                        )

                        // Stage 2: Gemini
                        DiagnosticStageCard(
                            stageNumber = 2,
                            title = "Gemini Extraction",
                            status = scan.geminiStatus,
                            durationMs = scan.geminiDurationMs,
                            summary = buildString {
                                append("Model: ${scan.geminiModel}\n")
                                append("Thread: ${scan.threadName ?: "UNKNOWN"} | Dispatcher: ${scan.dispatcherLabel}")
                                if (scan.exceptionCategory != null) {
                                    append(" | Category: ${scan.exceptionCategory}")
                                }
                                if (scan.geminiFailureReason != null) {
                                    val detail = if (!scan.geminiErrorDetail.isNullOrBlank()) " (${scan.geminiErrorDetail})" else ""
                                    append("\nReason: ${scan.geminiFailureReason}$detail")
                                }
                            },
                            rawContentLabel = "View JSON Response",
                            rawContent = scan.rawGeminiJson,
                        )

                        // Stage 3: Candidate Parsing & Date Selection Reasoning
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCool),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "3. Date Selection & Reasoning",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = PrimaryNavy,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Selected Date: ${scan.selectedDate ?: "None"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryNavy,
                                )
                                if (scan.dateCandidates.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Candidates evaluated:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextCaption,
                                    )
                                    scan.dateCandidates.forEach { candidate ->
                                        Text(
                                            text = " • $candidate",
                                            fontSize = 12.sp,
                                            color = PrimaryNavy,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE0E7FF))
                                        .padding(8.dp),
                                ) {
                                    Text(
                                        text = "Reason: ${scan.dateSelectionReason ?: "No selection recorded"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E3A8A),
                                    )
                                }
                            }
                        }

                        // Stage 4: Extractor Path & Final Result
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCool),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "4. Final Result & Path",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = PrimaryNavy,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Path: ${scan.extractorPath}",
                                    fontSize = 12.sp,
                                    color = TextCaption,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Disposition: ${scan.finalDisposition ?: "N/A"} | Confidence: ${scan.confidence}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryNavy,
                                )
                            }
                        }

                        // Stage 5: Persistence
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCool),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "5. Persistence",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = PrimaryNavy,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Save: ${scan.saveStatus ?: "NOT_ATTEMPTED"} | Reminder: ${scan.reminderStatus ?: "N/A"}",
                                    fontSize = 13.sp,
                                    color = PrimaryNavy,
                                )
                                if (scan.dbDurationMs > 0) {
                                    Text(
                                        text = "DB Duration: ${scan.dbDurationMs} ms",
                                        fontSize = 12.sp,
                                        color = TextCaption,
                                    )
                                }
                            }
                        }

                        // Stage 6: Thread & Watchdog Health
                        val watchdogStatus = app.memora.platform.logging.AppLog.getWatchdogStatus()
                        val lastFreeze = app.memora.platform.logging.AppLog.getLastFreezeSnapshot()
                        val lastUserAction = app.memora.platform.logging.AppLog.getLastUserAction()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (lastFreeze != null) Color(0xFFFEF2F2) else SurfaceCool
                            ),
                            border = if (lastFreeze != null) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171)) else null,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "6. Thread & Watchdog Health",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (lastFreeze != null) Color(0xFF991B1B) else PrimaryNavy,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Watchdog: ${if (watchdogStatus.running) "Active" else "Stopped"} (Heartbeat #${watchdogStatus.heartbeatCount}, age ${watchdogStatus.heartbeatAgeMs}ms)",
                                    fontSize = 12.sp,
                                    color = TextCaption,
                                )
                                if (lastUserAction != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Last Action: ${lastUserAction.action}${lastUserAction.stateTransition?.let { " -> $it" } ?: ""}",
                                        fontSize = 12.sp,
                                        color = PrimaryNavy,
                                    )
                                }
                                if (lastFreeze != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFEE2E2))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "FREEZE DETECTED: ${lastFreeze.unresponsiveDurationMs}ms on ${lastFreeze.currentScreen}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C),
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Operation: ${lastFreeze.currentOperation}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF7F1D1D),
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = lastFreeze.threadDump.mainThreadStack.joinToString("\n").take(500),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF991B1B),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Performance Summary Table
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Timing Breakdown",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = PrimaryNavy,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TimingRow("OCR Execution", "${scan.ocrDurationMs} ms")
                                TimingRow("Gemini API", "${scan.geminiDurationMs} ms")
                                TimingRow("Candidate Parsing", "${scan.parsingDurationMs} ms")
                                TimingRow("DB & Notification", "${scan.dbDurationMs} ms")
                                Spacer(modifier = Modifier.height(4.dp))
                                TimingRow("TOTAL DURATION", "${scan.totalDurationMs} ms", isBold = true)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PrimaryNavy,
                        contentColor = CardWhite,
                    ),
                ) {
                    Text("Close Diagnostics", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStageCard(
    stageNumber: Int,
    title: String,
    status: String,
    durationMs: Long,
    summary: String,
    rawContentLabel: String,
    rawContent: String?,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCool),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$stageNumber. $title",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = PrimaryNavy,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (status) {
                                "SUCCESS" -> Color(0xFFDCFCE7)
                                "FAILURE" -> Color(0xFFFEE2E2)
                                else -> Color(0xFFE2E8F0)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "$status (${durationMs}ms)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (status) {
                            "SUCCESS" -> Color(0xFF166534)
                            "FAILURE" -> Color(0xFF991B1B)
                            else -> Color(0xFF475569)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary,
                fontSize = 12.sp,
                color = TextCaption,
            )

            if (!rawContent.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isExpanded) "Hide $rawContentLabel" else rawContentLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = rawContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFF8FAFC),
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingRow(
    label: String,
    value: String,
    isBold: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = PrimaryNavy,
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = PrimaryNavy,
        )
    }
}
