package app.memora.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.BuildConfig
import app.memora.R
import app.memora.domain.benchmark.ExpiryEvaluationHarness
import app.memora.platform.logging.AppLog
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Developer-only diagnostics, event log inspector, and OCR benchmark harness UI.
 * Extracted from SettingsScreen to keep production settings untangled.
 */
@Composable
internal fun DiagnosticsSettingsSection() {
    val context = LocalContext.current
    var showScanDialog by remember { mutableStateOf(false) }
    var entryCount by remember { mutableIntStateOf(AppLog.entryCount) }
    val currentScan = AppLog.currentScan

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_diagnostics_warning_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFEF3C7),
            ),
            border = BorderStroke(1.dp, Color(0xFFFCD34D)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_diagnostics_warning),
                    style = MemoraTypography.caption,
                    color = Color(0xFF92400E),
                    lineHeight = 16.sp,
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_current_scan_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, BorderLight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCool),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            tint = PrimaryNavy,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentScan != null) "Current Scan: #${currentScan.scanId}" else stringResource(R.string.settings_current_scan_title),
                            style = MemoraTypography.headline.copy(fontSize = 15.sp),
                            color = PrimaryNavy,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (currentScan != null) {
                                "OCR: ${currentScan.ocrStatus} (${currentScan.ocrDurationMs}ms) | Gemini: ${currentScan.geminiStatus} (${currentScan.geminiDurationMs}ms)"
                            } else {
                                stringResource(R.string.settings_current_scan_desc)
                            },
                            style = MemoraTypography.caption,
                            color = TextCaption,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        AppLog.recordUserAction("Settings Screen", "Diagnostics opened")
                        showScanDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("view_current_scan_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryNavy,
                    ),
                    border = BorderStroke(1.dp, BorderLight),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_view_current_scan), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_all_logs_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, BorderLight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_all_logs_title),
                    style = MemoraTypography.headline.copy(fontSize = 15.sp),
                    color = PrimaryNavy,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$entryCount events recorded in memory buffer",
                    style = MemoraTypography.caption,
                    color = TextCaption,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            AppLog.recordUserAction("Settings Screen", "Copy logs clicked")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Memora Diagnostics", AppLog.getFormattedDump(context))
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.logs_copied_feedback), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_copy_logs_button"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_copy_logs), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            AppLog.recordUserAction("Settings Screen", "Share logs clicked")
                            val dump = AppLog.getFormattedDump(context)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, dump)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Diagnostic Logs")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_share_logs_button"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_share_logs), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            AppLog.recordUserAction("Settings Screen", "Clear diagnostics clicked")
                            AppLog.clear()
                            entryCount = AppLog.entryCount
                            Toast.makeText(context, context.getString(R.string.logs_cleared_feedback), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_clear_logs_button"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_clear_logs), fontSize = 12.sp)
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            ExpiryEvaluationCard()
        }
    }

    if (showScanDialog) {
        CurrentScanDialog(
            scan = currentScan,
            onDismiss = { showScanDialog = false },
        )
    }
}

@Composable
private fun ExpiryEvaluationCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isEvaluating by remember { mutableStateOf(false) }
    var runAllPasses by remember { mutableStateOf(false) }
    var evaluationSummary by remember { mutableStateOf<ExpiryEvaluationHarness.EvaluationSummary?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val evalDir = remember { ExpiryEvaluationHarness.getEvaluationDirectory(context) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_evaluation_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCool),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Assessment,
                        contentDescription = null,
                        tint = PrimaryNavy,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_evaluation_title),
                        style = MemoraTypography.headline.copy(fontSize = 15.sp),
                        color = PrimaryNavy,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_evaluation_desc),
                        style = MemoraTypography.caption,
                        color = TextCaption,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Target Folders: ${evalDir.absolutePath}/tuning & /heldout",
                style = MemoraTypography.label.copy(fontSize = 11.sp),
                color = TextCaption,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Run ALL passes (no early exit)",
                        style = MemoraTypography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = PrimaryNavy,
                    )
                    Text(
                        text = "Records per-pass standalone date recall & latency",
                        style = MemoraTypography.caption.copy(fontSize = 11.sp),
                        color = TextCaption,
                    )
                }
                Switch(
                    checked = runAllPasses,
                    onCheckedChange = { runAllPasses = it },
                    enabled = !isEvaluating,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentIndigo,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = SurfaceCool,
                        uncheckedBorderColor = BorderLight,
                    ),
                    modifier = Modifier.testTag("toggle_run_all_passes"),
                )
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusMessage ?: "",
                    style = MemoraTypography.caption,
                    color = if (evaluationSummary != null) Color(0xFF047857) else Color(0xFFB45309),
                )
            }

            val summary = evaluationSummary
            if (summary != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCool),
                    border = BorderStroke(1.dp, BorderLight),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Overall Accuracy: ${String.format(Locale.ROOT, "%.1f", summary.accuracyPercent)}% (${summary.correctCount}/${summary.totalCount})",
                            style = MemoraTypography.headline.copy(fontSize = 14.sp),
                            color = PrimaryNavy,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "OCR Date Text Present: ${String.format(Locale.ROOT, "%.1f", summary.ocrRecallPercent)}% (${summary.ocrRecallCount}/${summary.totalCount})",
                            style = MemoraTypography.caption,
                            color = PrimaryNavy,
                        )
                        val tuning = summary.tuningSummary
                        if (tuning != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tuning Split: ${String.format(Locale.ROOT, "%.1f", tuning.accuracyPercent)}% (${tuning.correctCount}/${tuning.totalCount}) | OCR: ${tuning.ocrRecallCount}/${tuning.totalCount}",
                                style = MemoraTypography.caption,
                                color = PrimaryNavy,
                            )
                        }
                        val heldout = summary.heldoutSummary
                        if (heldout != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Heldout Split: ${String.format(Locale.ROOT, "%.1f", heldout.accuracyPercent)}% (${heldout.correctCount}/${heldout.totalCount}) | OCR: ${heldout.ocrRecallCount}/${heldout.totalCount}",
                                style = MemoraTypography.caption,
                                color = PrimaryNavy,
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Heldout Split: 0 images",
                                style = MemoraTypography.caption,
                                color = TextCaption,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failures: NONE=${summary.countsByFailureType["NONE"] ?: 0}, OCR_MISSING=${summary.countsByFailureType["OCR_MISSING"] ?: 0}, PARSE_WRONG=${summary.countsByFailureType["PARSE_WRONG"] ?: 0}, WRONG_PICK=${summary.countsByFailureType["WRONG_PICK"] ?: 0}",
                            style = MemoraTypography.caption,
                            color = TextCaption,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Latency: Avg=${String.format(Locale.ROOT, "%.0f", summary.averageTotalMs)}ms, Worst=${summary.worstCaseTotalMs}ms | Winner: ${summary.mostFrequentWinningPass}",
                            style = MemoraTypography.caption,
                            color = TextCaption,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isEvaluating = true
                            statusMessage = if (runAllPasses) "Evaluating ALL passes on-device..." else "Evaluating production passes on-device..."
                            val result = ExpiryEvaluationHarness.runEvaluation(
                                context = context,
                                runAllPasses = runAllPasses,
                            )
                            isEvaluating = false
                            result.fold(
                                onSuccess = { res ->
                                    evaluationSummary = res
                                    statusMessage = "Completed: ${res.correctCount}/${res.totalCount} correct (${String.format(Locale.ROOT, "%.1f", res.accuracyPercent)}%)"
                                },
                                onFailure = { err ->
                                    statusMessage = "Error: ${err.message}"
                                }
                            )
                        }
                    },
                    enabled = !isEvaluating,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("run_evaluation_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryNavy,
                    ),
                    border = BorderStroke(1.dp, BorderLight),
                ) {
                    if (isEvaluating) {
                        CircularProgressIndicator(
                            color = PrimaryNavy,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.evaluation_running), fontSize = 12.sp)
                    } else {
                        Text(text = stringResource(R.string.action_run_evaluation), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (summary != null) {
                    OutlinedButton(
                        onClick = {
                            val exportResult = ExpiryEvaluationHarness.exportCsvToDownloads(context, summary.generatedCsvFile)
                            exportResult.fold(
                                onSuccess = { file ->
                                    Toast.makeText(context, context.getString(R.string.evaluation_exported_toast, file.name), Toast.LENGTH_LONG).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_evaluation_csv_button"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_export_csv), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
