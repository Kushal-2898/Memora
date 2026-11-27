package app.memora.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.memora.R
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

/**
 * Modal dialog presenting Memora's Data Protection Charter & Privacy Policy,
 * compliant with India's Digital Personal Data Protection Act, 2023 (DPDP Act).
 */
@Composable
fun PrivacyPolicyDialog(
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("privacy_policy_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCool),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = PrimaryNavy,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.dialog_policy_title),
                            style = MemoraTypography.headline.copy(fontSize = 17.sp),
                            color = PrimaryNavy,
                        )
                        Text(
                            text = "Hybrid On-Device & AI Architecture",
                            style = MemoraTypography.caption.copy(fontSize = 11.sp),
                            color = Color(0xFF0D9488),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Charter Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PolicySectionItem(
                        number = "1",
                        title = "Offline-First Architecture & Account Sign-In",
                        content = "• Account Sign-In: Memora requires a Google or Email account to protect and synchronize your vault across sessions and devices.\n• Private Profile: When signing in with Google, we receive only your basic profile (display name, email, avatar) strictly for authentication. We never sell, rent, or share your data with advertisers.",
                    )

                    PolicySectionItem(
                        number = "2",
                        title = "On-Device OCR, Gemini AI & Compressed Backup",
                        content = "Primary optical character recognition (OCR) runs locally on your device using ML Kit. Optional Cloud Gemini AI refinement is invoked only when explicitly tapped. Saved scans delete the raw camera photo and store a GZIP-compressed backup (<750 KB) privately under your account.",
                    )

                    PolicySectionItem(
                        number = "3",
                        title = "Storage, Billing & Cloud Sync",
                        content = "• Local & Cloud Sync: Memories and exact alarms run offline-first on your device and sync privately to your user-scoped Cloud Firestore vault.\n• Google Play Billing: Memora Pro subscriptions and the 5 free monthly scans quota are managed securely through Google Play Billing; Memora never sees your card or UPI details.",
                    )

                    PolicySectionItem(
                        number = "4",
                        title = "Auxiliary Aid & Limitation of Liability",
                        content = "Memora is an auxiliary personal reminder aid. Users have the sole responsibility to physically inspect all food, medicine, and perishable goods for condition and safety before consumption. Governed by the laws of India.",
                    )

                    PolicySectionItem(
                        number = "5",
                        title = "Your Data Rights & Account Deletion",
                        content = "Under the DPDP Act 2023 and GDPR, you have the right to:\n• Right to Access: Inspect all stored data directly in the app.\n• Right to Correction: Edit any memory, title, or date at any time.\n• Right to Erasure & Deletion: Permanently delete individual memories, wipe all app data, or delete your linked cloud account and associated records directly from Settings.\n• Right to Grievance Redressal: Direct contact channel with our Data Protection Officer.",
                    )

                    PolicySectionItem(
                        number = "6",
                        title = "Permission Justifications",
                        content = "• Camera: Optical capture for document & expiry date recognition only. Never used for surveillance or background capture.\n• Notifications: Platform alarms to alert you when expiry or due dates arrive.",
                    )

                    PolicySectionItem(
                        number = "7",
                        title = "Grievance Officer & Contact",
                        content = "For questions, compliance inquiries, or Data Principal requests, contact our designated Grievance Officer at: klabsstudios@gmail.com.",
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Close Button
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_policy_close_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = app.memora.ui.theme.DeepForestGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.dialog_policy_close),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicySectionItem(
    number: String,
    title: String,
    content: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCool)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(app.memora.ui.theme.DeepForestGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MemoraTypography.headline.copy(fontSize = 13.sp),
                color = PrimaryNavy,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content,
            style = MemoraTypography.body.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = Color(0xFF374151),
        )
    }
}
