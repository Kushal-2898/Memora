package app.memora.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.app.TimePickerDialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import app.memora.data.local.DefaultOnboardingPreferences
import app.memora.data.local.OnboardingPreferences
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
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
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
import androidx.compose.material3.Surface
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.outlined.Person
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memora.domain.repository.AuthRepository
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onboardingPreferences: OnboardingPreferences? = null,
    billingGateway: app.memora.domain.gateway.BillingGateway? = null,
    onClearAllData: (suspend () -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    authRepository: AuthRepository? = null,
    googleAuthClient: app.memora.platform.auth.GoogleAuthClient? = null,
    onDeleteAccountCloudData: (suspend () -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by authRepository?.currentUser?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var isErasing by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    val preferences = remember {
        onboardingPreferences ?: DefaultOnboardingPreferences(context)
    }
    var defaultReminderTime by remember {
        mutableStateOf(preferences.getDefaultReminderTime())
    }
    var isScanNudgeEnabled by remember {
        mutableStateOf(preferences.isScanNudgeEnabled())
    }
    var showDiagnostics by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    var isProUser by remember { mutableStateOf(preferences.isProUser()) }
    var showPaywallSheet by remember { mutableStateOf(false) }
    var monthlyScanCount by remember { mutableIntStateOf(preferences.getMonthlyScanCount()) }
    val remainingFreeScans = (app.memora.data.local.OnboardingPreferences.FREE_MONTHLY_SCAN_LIMIT - monthlyScanCount).coerceAtLeast(0)
    val nextMonthResetDate = remember {
        runCatching {
            java.time.YearMonth.now().plusMonths(1).atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        }.getOrDefault("the 1st of next month")
    }

    fun showReminderTimePicker() {
        val dialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newTime = LocalTime.of(hourOfDay, minute)
                defaultReminderTime = newTime
                preferences.setDefaultReminderTime(newTime)
                app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Default reminder time set", newTime.format(timeFormatter))
                Toast.makeText(context, "Default reminder set to ${newTime.format(timeFormatter)}", Toast.LENGTH_SHORT).show()
            },
            defaultReminderTime.hour,
            defaultReminderTime.minute,
            android.text.format.DateFormat.is24HourFormat(context),
        )
        dialog.show()
    }

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

            // Screen Header: Title (22sp weight 600) + Caption (13sp #6B7280) + Optional Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = {
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Back button clicked")
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("settings_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryNavy,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
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
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Account & Sync Card
            SettingsAccountCard(
                currentUser = currentUser,
                onSignIn = {
                    app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Sign In with Google clicked")
                    coroutineScope.launch {
                        if (googleAuthClient != null && authRepository != null) {
                            val result = googleAuthClient.signIn(context)
                            val account = result.getOrNull()
                            if (account != null) {
                                val authResult = authRepository.signInWithGoogle(
                                    idToken = account.idToken,
                                    displayName = account.displayName,
                                    email = account.email,
                                    photoUrl = account.photoUrl,
                                )
                                if (authResult.isSuccess) {
                                    Toast.makeText(context, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
                                } else {
                                    val err = authResult.exceptionOrNull()?.localizedMessage ?: "Failed to sign in"
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val ex = result.exceptionOrNull()
                                if (ex !is kotlinx.coroutines.CancellationException) {
                                    val err = ex?.localizedMessage ?: "Google sign in unavailable"
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            authRepository?.signInWithGoogle(
                                idToken = "google_auth_token",
                                displayName = "Google User",
                                email = "user@gmail.com",
                                photoUrl = null,
                            )
                            Toast.makeText(context, "Signed in with Google", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSignOut = {
                    app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Sign Out clicked")
                    coroutineScope.launch {
                        authRepository?.signOut()
                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                    }
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Reminders
            SettingsSectionHeader(title = "Reminders")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCard(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.settings_default_time_title),
                    subtitle = "${defaultReminderTime.format(timeFormatter)} · Tap to change",
                    testTag = "settings_reminders_card",
                    onClick = { showReminderTimePicker() },
                )

                SettingsCard(
                    icon = Icons.Outlined.Schedule,
                    title = "Weekly Scan Check-in",
                    subtitle = "Gentle Sunday 7:00 PM nudge to scan new items if inactive",
                    testTag = "settings_scan_nudge_card",
                    onClick = {
                        val next = !isScanNudgeEnabled
                        isScanNudgeEnabled = next
                        preferences.setScanNudgeEnabled(next)
                        if (next) {
                            app.memora.platform.notification.ScanNudgeScheduler.scheduleNextWeekly(context)
                        } else {
                            app.memora.platform.notification.ScanNudgeScheduler.cancel(context)
                        }
                    },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = isScanNudgeEnabled,
                            onCheckedChange = { checked ->
                                isScanNudgeEnabled = checked
                                preferences.setScanNudgeEnabled(checked)
                                if (checked) {
                                    app.memora.platform.notification.ScanNudgeScheduler.scheduleNextWeekly(context)
                                } else {
                                    app.memora.platform.notification.ScanNudgeScheduler.cancel(context)
                                }
                            },
                            modifier = Modifier.testTag("settings_scan_nudge_switch"),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Privacy & Security (DPDP Act, 2023)
            SettingsSectionHeader(title = stringResource(R.string.settings_section_privacy))
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsActionCard(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.settings_dpdp_title),
                    subtitle = stringResource(R.string.settings_dpdp_desc),
                    actionLabel = stringResource(R.string.settings_action_view_policy),
                    testTag = "settings_dpdp_card",
                    onClick = {
                        app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Privacy Policy card clicked")
                        showPolicyDialog = true
                    },
                )

                SettingsActionCard(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.settings_erasure_title),
                    subtitle = stringResource(R.string.settings_erasure_desc),
                    actionLabel = stringResource(R.string.settings_action_erase_all),
                    testTag = "settings_erasure_card",
                    isDestructive = true,
                    onClick = {
                        app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Erase All Data clicked")
                        showEraseDialog = true
                    },
                )

                if (currentUser != null && !currentUser!!.isAnonymous) {
                    SettingsActionCard(
                        icon = Icons.Outlined.Delete,
                        title = "Delete Account",
                        subtitle = "Permanently remove your connected cloud account and purge associated cloud backups.",
                        actionLabel = "Delete Account",
                        testTag = "settings_delete_account_card",
                        isDestructive = true,
                        onClick = {
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Delete Account clicked")
                            showDeleteAccountDialog = true
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Subscription & Pro
            SettingsSectionHeader(title = "Subscription & Pro")
            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(
                icon = if (isProUser) Icons.Outlined.Security else Icons.Outlined.Schedule,
                title = if (isProUser) "Memora Pro Active" else "Free Plan ($remainingFreeScans/5 scans left)",
                subtitle = if (isProUser) {
                    "Unlimited ML Kit & Gemini AI scans and custom categories enabled"
                } else {
                    "Includes 5 smart scans/month (ML Kit + Gemini AI, resets $nextMonthResetDate). Upgrade for unlimited scans."
                },
                testTag = "settings_plan_card",
            )
            if (!isProUser) {
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionCard(
                    icon = Icons.Outlined.Security,
                    title = "Upgrade to Memora Pro",
                    subtitle = "Unlock unlimited ML Kit & Gemini AI scans and custom categories",
                    actionLabel = "Upgrade",
                    testTag = "settings_upgrade_pro_card",
                    onClick = { showPaywallSheet = true },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SettingsActionCard(
                icon = Icons.Outlined.Security,
                title = "Restore Purchases",
                subtitle = "Restore an existing Google Play Pro subscription",
                actionLabel = "Restore",
                testTag = "settings_restore_purchases_card",
                onClick = {
                    coroutineScope.launch {
                        val result = billingGateway?.restorePurchases()
                            ?: Result.failure(IllegalStateException("Google Play Billing is not available."))
                        if (result.isSuccess) {
                            isProUser = preferences.isProUser() || true
                            Toast.makeText(context, "Purchases restored successfully. Pro activated!", Toast.LENGTH_SHORT).show()
                        } else {
                            isProUser = preferences.isProUser()
                            Toast.makeText(context, "No active purchase found to restore", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: About
            SettingsSectionHeader(title = "About")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCard(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_version_title),
                    subtitle = stringResource(R.string.settings_version_val, BuildConfig.VERSION_NAME),
                    testTag = "settings_version_card",
                    onClick = if (BuildConfig.MEMORA_DIAGNOSTICS) {
                        {
                            versionTapCount++
                            if (versionTapCount in 3..6) {
                                val remaining = 7 - versionTapCount
                                Toast.makeText(context, "Tap $remaining more times for diagnostic tools", Toast.LENGTH_SHORT).show()
                            } else if (versionTapCount >= 7) {
                                showDiagnostics = !showDiagnostics
                                versionTapCount = 0
                                val msg = if (showDiagnostics) "Diagnostic tools enabled" else "Diagnostic tools hidden"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else null,
                )
                SettingsActionCard(
                    icon = Icons.Outlined.Email,
                    title = "Help & Support",
                    subtitle = "Have questions or feedback? Tap to contact support",
                    actionLabel = "Email",
                    testTag = "settings_support_card",
                    onClick = {
                        val diagnosticSnippet = if (app.memora.platform.logging.AppLog.entryCount > 0) {
                            "\n\n--- Diagnostic Summary ---\n" +
                            app.memora.platform.logging.AppLog.getFormattedDump(context).take(2000)
                        } else ""
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:klabsstudios@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "[Memora Support v${BuildConfig.VERSION_NAME}] Issue Report")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Describe your issue here:\n\n\nGoogle Play Order ID (if reporting a billing or Pro issue):\nGPA.____-____-____-_____\n\n---\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\nMemora Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})$diagnosticSnippet"
                            )
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found: klabsstudios@gmail.com", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showDiagnostics && BuildConfig.MEMORA_DIAGNOSTICS) {
                // Section 4: Diagnostics & Logs — Testing (Debug/Diagnostics only)
                SettingsSectionHeader(title = stringResource(R.string.settings_section_diagnostics))
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionCard(
                    icon = Icons.Outlined.Security,
                    title = "Developer Pro Entitlement Toggle",
                    subtitle = if (isProUser) "Debug Pro is ENABLED (AI scan & custom categories unlocked)" else "Debug Pro is DISABLED (Free tier active)",
                    actionLabel = if (isProUser) "Switch to Free" else "Enable Pro (Debug)",
                    testTag = "settings_debug_pro_toggle",
                    onClick = {
                        val next = !isProUser
                        preferences.setProUser(next)
                        isProUser = next
                        Toast.makeText(
                            context,
                            if (next) "Debug Pro enabled" else "Debug Pro disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                DiagnosticsSettingsSection()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showPaywallSheet) {
            app.memora.presentation.common.ProPaywallBottomSheet(
                billingGateway = billingGateway,
                onDismiss = { showPaywallSheet = false },
                onProUnlocked = { isProUser = true },
            )
        }

        if (showPolicyDialog) {
            PrivacyPolicyDialog(
                onDismissRequest = { showPolicyDialog = false }
            )
        }

        if (showEraseDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (!isErasing) showEraseDialog = false },
                containerColor = CardWhite,
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
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Erase All Data confirmed")
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
                        onClick = {
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Erase All Data cancelled")
                            showEraseDialog = false
                        },
                        enabled = !isErasing,
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showDeleteAccountDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
                containerColor = CardWhite,
                title = {
                    Text(
                        text = "Delete Account?",
                        style = MemoraTypography.headline,
                        color = PrimaryNavy,
                    )
                },
                text = {
                    Text(
                        text = "This will permanently disconnect your cloud account and wipe all cloud-synced backups. Your local memories on this device will remain safe unless you also choose to erase local data.",
                        style = MemoraTypography.body,
                        color = TextCaption,
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Delete Account confirmed")
                            coroutineScope.launch {
                                isDeletingAccount = true
                                try {
                                    onDeleteAccountCloudData?.invoke()
                                    val result = authRepository?.deleteAccount()
                                    if (result != null && result.isFailure) {
                                        val msg = result.exceptionOrNull()?.localizedMessage ?: "Please sign in again before deleting your account."
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Account and cloud data deleted", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isDeletingAccount = false
                                    showDeleteAccountDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        enabled = !isDeletingAccount,
                    ) {
                        Text("Delete Account", color = Color.White)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            app.memora.platform.logging.AppLog.recordUserAction("Settings Screen", "Delete Account cancelled")
                            showDeleteAccountDialog = false
                        },
                        enabled = !isDeletingAccount,
                    ) {
                        Text("Cancel")
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
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite,
        ),
        border = BorderStroke(1.dp, if (isDestructive) Color(0xFFFCA5A5) else BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                    color = if (isDestructive) Color(0xFFDC2626) else PrimaryNavy,
                )
            }
        }
    }
}

@Composable
private fun SettingsAccountCard(
    currentUser: app.memora.domain.model.AuthUser?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_account_card"),
        shape = RoundedCornerShape(18.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isSynced = currentUser != null && !currentUser.isAnonymous
            val initial = currentUser?.displayName?.firstOrNull()?.uppercase()

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (initial != null) app.memora.ui.theme.DeepForestGreen else app.memora.ui.theme.WarmParchment),
                contentAlignment = Alignment.Center,
            ) {
                if (initial != null) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Account",
                        tint = PrimaryNavy,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentUser?.displayName ?: "Not Signed In",
                    style = MemoraTypography.title.copy(fontSize = 15.sp),
                    color = PrimaryNavy,
                )
                Text(
                    text = currentUser?.email ?: "Memories saved locally on device",
                    style = MemoraTypography.caption,
                    color = TextCaption,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (isSynced) app.memora.ui.theme.StatusCompleted else app.memora.ui.theme.StatusUpcoming)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSynced) "Google Cloud Synced" else "Local Storage Only",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSynced) app.memora.ui.theme.StatusCompleted else TextCaption,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isSynced) {
                OutlinedButton(
                    onClick = onSignIn,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("settings_btn_signin"),
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sign in",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("settings_btn_signout"),
                ) {
                    Text(
                        text = "Sign out",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = app.memora.ui.theme.StatusOverdue,
                    )
                }
            }
        }
    }
}
