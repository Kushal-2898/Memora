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
                            text = "DPDP Act, 2023 Certified Local Architecture",
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
                        title = "Data Minimisation & Zero PII (Sec 6)",
                        content = "Memora collects zero personal identifiers. No user accounts, passwords, email addresses, phone numbers, or advertising identifiers are required or collected.",
                    )

                    PolicySectionItem(
                        number = "2",
                        title = "100% On-Device Processing (Sec 8 & 16)",
                        content = "All optical character recognition (OCR) and document date parsing execute strictly offline using on-device neural processing (RapidOCR & ML Kit). No photos or document contents are ever transmitted to any remote cloud server in standard operation.",
                    )

                    PolicySectionItem(
                        number = "3",
                        title = "Local Encrypted Storage (Sec 8)",
                        content = "Your captured memories and reminder dates are saved exclusively in your phone's private internal application sandbox (Room SQLite database). No third-party or developer cloud backups exist.",
                    )

                    PolicySectionItem(
                        number = "4",
                        title = "Optional Cloud AI Transparency",
                        content = "If cloud AI refinement is enabled via API key, it is invoked ONLY upon explicit, manual user tap on the review screen. It is never triggered silently in the background.",
                    )

                    PolicySectionItem(
                        number = "5",
                        title = "Data Principal Rights (Sec 11–14)",
                        content = "As a Data Principal under India's DPDP Act, you have absolute rights to:\n• Right to Access: Inspect all stored data directly in the app.\n• Right to Correction: Edit any memory, title, or date at any time.\n• Right to Erasure: Permanently delete individual memories or wipe all app data with a single tap in Settings.\n• Right to Grievance Redressal: Direct contact channel with our Data Protection Officer.",
                    )

                    PolicySectionItem(
                        number = "6",
                        title = "Permission Justifications",
                        content = "• Camera: Optical capture for document & expiry date recognition only. Never used for surveillance or background capture.\n• Notifications: Platform alarms to alert you when expiry or due dates arrive.",
                    )

                    PolicySectionItem(
                        number = "7",
                        title = "Grievance Officer & Contact",
                        content = "For questions, compliance inquiries, or Data Principal requests, contact our designated Grievance Officer at: privacy@memora.app.",
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
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
                    .background(PrimaryNavy),
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
