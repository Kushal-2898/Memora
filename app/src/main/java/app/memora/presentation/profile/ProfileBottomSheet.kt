package app.memora.presentation.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.domain.model.AuthUser
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.DeepForestGreen
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusCompleted
import app.memora.ui.theme.StatusOverdue
import app.memora.ui.theme.StatusUpcoming
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption
import app.memora.ui.theme.WarmParchment

/**
 * Option A Profile & Account Bottom Sheet
 *
 * Provides a unified profile overview matching Memora's soft minimal aesthetic:
 * - Current account information (Google account or Guest mode)
 * - Cloud synchronization indicator
 * - Direct "Settings & Preferences" action (satisfies Pattern A: Settings inside Profile)
 * - Account linking / Sign Out actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    currentUser: AuthUser?,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignInWithEmail: () -> Unit = {},
    onSignOut: () -> Unit,
    onShowPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCool,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderLight) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.testTag("profile_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Account Summary Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_sheet_account_card"),
                shape = RoundedCornerShape(18.dp),
                color = CardWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar
                    val initial = currentUser?.displayName?.firstOrNull()?.uppercase()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (initial != null) DeepForestGreen else WarmParchment)
                            .border(1.dp, BorderLight, CircleShape),
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
                                contentDescription = "Guest",
                                tint = PrimaryNavy,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // User Info & Sync Status
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.displayName ?: "Guest User",
                            style = MemoraTypography.title.copy(fontSize = 17.sp),
                            color = PrimaryNavy,
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = currentUser?.email ?: "Memories saved locally on device",
                            style = MemoraTypography.caption,
                            color = TextCaption,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Sync Status Pill
                        val isSynced = currentUser != null && !currentUser.isAnonymous &&
                            !currentUser.uid.startsWith("email_") && !currentUser.uid.startsWith("google_")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSynced) Color(0xFFEDF7EE) else WarmParchment)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isSynced) StatusCompleted else StatusUpcoming)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSynced) "Cloud Backup Active" else "Local Storage Only",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSynced) StatusCompleted else TextCaption,
                            )
                        }
                    }
                }
            }

            // 2. Action: Settings & Preferences (Option A core entry)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onNavigateToSettings()
                    }
                    .testTag("profile_sheet_settings_button"),
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(WarmParchment),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = PrimaryNavy,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settings & Preferences",
                            style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = PrimaryNavy,
                        )
                        Text(
                            text = "Reminders, notifications, backup",
                            style = MemoraTypography.caption,
                            color = TextCaption,
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = TextCaption,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 3. Action: Account Linking / Sign Out
            if (currentUser == null || currentUser.isAnonymous) {
                // Link Google Account
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSignInWithGoogle() }
                        .testTag("profile_sheet_google_signin_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_google_logo),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Continue with Google",
                                style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = PrimaryNavy,
                            )
                            Text(
                                text = "Back up and sync across devices",
                                style = MemoraTypography.caption,
                                color = TextCaption,
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = TextCaption,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Link Email & Password Account
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSignInWithEmail() }
                        .testTag("profile_sheet_email_signin_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentIndigo.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null,
                                tint = AccentIndigo,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sign In with Email",
                                style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = PrimaryNavy,
                            )
                            Text(
                                text = "Use email & password to sync",
                                style = MemoraTypography.caption,
                                color = TextCaption,
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = TextCaption,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                // Sign Out
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSignOut() }
                        .testTag("profile_sheet_signout_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE8E4)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                tint = StatusOverdue,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sign Out",
                                style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = StatusOverdue,
                            )
                            Text(
                                text = "Your local memories will stay safe on device",
                                style = MemoraTypography.caption,
                                color = TextCaption,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Privacy Policy",
                    style = MemoraTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = TextCaption,
                    modifier = Modifier.clickable { onShowPrivacyPolicy() },
                )
                Text(
                    text = "  •  ",
                    style = MemoraTypography.caption.copy(fontSize = 11.sp),
                    color = TextCaption,
                )
                Text(
                    text = "Terms of Service",
                    style = MemoraTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = TextCaption,
                    modifier = Modifier.clickable { onShowPrivacyPolicy() },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Close Button
            androidx.compose.material3.Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("profile_sheet_close_button"),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = DeepForestGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Close",
                    style = MemoraTypography.label.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }
    }
}
