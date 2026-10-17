package app.memora.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.memora.BuildConfig
import app.memora.domain.benchmark.ExpiryEvaluationHarness
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClearAllData: (suspend () -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showPolicyDialog by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var isErasing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Settings opened")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCool),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Screen Header: Title (22sp weight 600) + Caption (13sp #6B7280)
            Text(
                text = stringResource(R.string.settings_title),
                style = MemoraTypography.title,
                color = PrimaryNavy,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MemoraTypography.caption,
                color = TextCaption,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 1: Reminders
            SettingsSectionHeader(title = "Reminders")
            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.settings_default_time_title),
                subtitle = stringResource(R.string.settings_default_time_desc),
                testTag = "settings_reminders_card",
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Notifications
            SettingsSectionHeader(title = "Notifications")
            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.settings_notifications_title),
                subtitle = stringResource(R.string.settings_notifications_desc),
                testTag = "settings_notifications_card",
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Privacy & Security (DPDP Act, 2023)
            SettingsSectionHeader(title = stringResource(R.string.settings_section_privacy))
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCard(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.settings_privacy_title),
                    subtitle = stringResource(R.string.settings_privacy_desc),
                    testTag = "settings_privacy_card",
                )

                SettingsActionCard(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.settings_dpdp_title),
                    subtitle = stringResource(R.string.settings_dpdp_desc),
                    actionLabel = stringResource(R.string.settings_action_view_policy),
                    testTag = "settings_dpdp_card",
                    onClick = { showPolicyDialog = true },
                )

                SettingsActionCard(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.settings_erasure_title),
                    subtitle = stringResource(R.string.settings_erasure_desc),
                    actionLabel = stringResource(R.string.settings_action_erase_all),
                    testTag = "settings_erasure_card",
                    isDestructive = true,
                    onClick = { showEraseDialog = true },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: About
            SettingsSectionHeader(title = "About")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCard(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_version_title),
                    subtitle = stringResource(R.string.settings_version_val),
                    testTag = "settings_version_card",
                )
                SettingsCard(
                    icon = Icons.Outlined.Architecture,
                    title = stringResource(R.string.settings_architecture_title),
                    subtitle = stringResource(R.string.settings_architecture_desc),
                    testTag = "settings_architecture_card",
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 5: Diagnostics & Logs — Testing
            SettingsSectionHeader(title = stringResource(R.string.settings_section_diagnostics))
            Spacer(modifier = Modifier.height(12.dp))
            DiagnosticsSettingsSection()

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showPolicyDialog) {
            PrivacyPolicyDialog(
                onDismissRequest = { showPolicyDialog = false }
            )
        }

        if (showEraseDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (!isErasing) showEraseDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.dialog_erase_confirm_title),
                        style = MemoraTypography.headline,
                        color = PrimaryNavy,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.dialog_erase_confirm_msg),
                        style = MemoraTypography.body,
                        color = TextCaption,
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            coroutineScope.launch {
                                isErasing = true
                                try {
                                    onClearAllData?.invoke()
                                    Toast.makeText(context, context.getString(R.string.toast_all_data_erased), Toast.LENGTH_LONG).show()
                                } finally {
                                    isErasing = false
                                    showEraseDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        enabled = !isErasing,
                    ) {
                        Text(stringResource(R.string.dialog_erase_confirm_action), color = Color.White)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showEraseDialog = false },
                        enabled = !isErasing,
                    ) {
                        Text(stringResource(R.string.action_discard))
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MemoraTypography.headline.copy(fontSize = 15.sp),
        color = PrimaryNavy,
        modifier = modifier.padding(start = 2.dp),
    )
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
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
                .padding(16.dp),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MemoraTypography.headline.copy(fontSize = 15.sp),
                    color = PrimaryNavy,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MemoraTypography.caption,
                    color = TextCaption,
                )
            }
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    testTag: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite,
        ),
        border = BorderStroke(1.dp, if (isDestructive) Color(0xFFFCA5A5) else BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        .background(if (isDestructive) Color(0xFFFEE2E2) else SurfaceCool),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDestructive) Color(0xFFDC2626) else PrimaryNavy,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MemoraTypography.headline.copy(fontSize = 15.sp),
                        color = if (isDestructive) Color(0xFFDC2626) else PrimaryNavy,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        style = MemoraTypography.caption,
                        color = TextCaption,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDestructive) Color(0xFFDC2626) else PrimaryNavy,
                ),
                border = BorderStroke(1.dp, if (isDestructive) Color(0xFFF87171) else BorderLight),
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsSettingsSection() {
    val context = LocalContext.current
    var showScanDialog by remember { mutableStateOf(false) }
    var entryCount by remember { mutableIntStateOf(app.memora.platform.logging.AppLog.entryCount) }
    val currentScan = app.memora.platform.logging.AppLog.currentScan

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Warning Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_diagnostics_warning_card"),
            shape = RoundedCornerShape(16.dp),
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

        // Current Scan Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_current_scan_card"),
            shape = RoundedCornerShape(16.dp),
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

                FilledTonalButton(
                    onClick = {
                        app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Diagnostics opened")
                        showScanDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("view_current_scan_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PrimaryNavy,
                        contentColor = CardWhite,
                    ),
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

        // All Event Logs Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_all_logs_card"),
            shape = RoundedCornerShape(16.dp),
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
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Copy logs clicked")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Memora Diagnostics", app.memora.platform.logging.AppLog.getFormattedDump(context))
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
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Share logs clicked")
                            val dump = app.memora.platform.logging.AppLog.getFormattedDump(context)
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
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Clear diagnostics clicked")
                            app.memora.platform.logging.AppLog.clear()
                            entryCount = app.memora.platform.logging.AppLog.entryCount
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
        shape = RoundedCornerShape(16.dp),
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
                androidx.compose.material3.Switch(
                    checked = runAllPasses,
                    onCheckedChange = { runAllPasses = it },
                    enabled = !isEvaluating,
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
                            text = "Overall Accuracy: ${String.format(java.util.Locale.ROOT, "%.1f", summary.accuracyPercent)}% (${summary.correctCount}/${summary.totalCount})",
                            style = MemoraTypography.headline.copy(fontSize = 14.sp),
                            color = PrimaryNavy,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "OCR Date Text Present: ${String.format(java.util.Locale.ROOT, "%.1f", summary.ocrRecallPercent)}% (${summary.ocrRecallCount}/${summary.totalCount})",
                            style = MemoraTypography.caption,
                            color = PrimaryNavy,
                        )
                        val tuning = summary.tuningSummary
                        if (tuning != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tuning Split: ${String.format(java.util.Locale.ROOT, "%.1f", tuning.accuracyPercent)}% (${tuning.correctCount}/${tuning.totalCount}) | OCR: ${tuning.ocrRecallCount}/${tuning.totalCount}",
                                style = MemoraTypography.caption,
                                color = PrimaryNavy,
                            )
                        }
                        val heldout = summary.heldoutSummary
                        if (heldout != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Heldout Split: ${String.format(java.util.Locale.ROOT, "%.1f", heldout.accuracyPercent)}% (${heldout.correctCount}/${heldout.totalCount}) | OCR: ${heldout.ocrRecallCount}/${heldout.totalCount}",
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
                            text = "Latency: Avg=${String.format(java.util.Locale.ROOT, "%.0f", summary.averageTotalMs)}ms, Worst=${summary.worstCaseTotalMs}ms | Winner: ${summary.mostFrequentWinningPass}",
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
                FilledTonalButton(
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
                                    statusMessage = "Completed: ${res.correctCount}/${res.totalCount} correct (${String.format(java.util.Locale.ROOT, "%.1f", res.accuracyPercent)}%)"
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
                        .testTag("run_evaluation_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PrimaryNavy,
                        contentColor = CardWhite,
                    ),
                ) {
                    if (isEvaluating) {
                        CircularProgressIndicator(
                            color = CardWhite,
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
