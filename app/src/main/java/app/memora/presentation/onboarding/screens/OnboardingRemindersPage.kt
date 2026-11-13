package app.memora.presentation.onboarding.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.presentation.onboarding.components.OnboardingProgressIndicator
import app.memora.ui.theme.BorderOatmeal
import app.memora.ui.theme.CardCream
import app.memora.ui.theme.DeepForestGreen
import app.memora.ui.theme.MemoraTheme
import app.memora.ui.theme.PrimaryText
import app.memora.ui.theme.SecondaryText
import app.memora.ui.theme.WarmPaperBeige

/**
 * Screen 3 — Set Reminders
 *
 * Pixel-accurate visual reconstruction:
 * - Top Action Bar with Skip button
 * - Bold heading: "Set reminders"
 * - Subtitle: "Choose when you want to be reminded. Get daily reminders until the due date."
 * - Premium Product Reminder Card:
 *   - Standing green Oats pouch thumbnail
 *   - Product name "Oats", "500 g"
 *   - "Active" status badge
 *   - 3 setting rows:
 *     1. Expiry date: "15 Mar 2026" (no chevron)
 *     2. Remind me: "3 days before" (with chevron)
 *     3. Repeat: "Daily until due date" (with chevron)
 * - 3-step progress indicator: ○ ● ○ (middle dot active)
 * - Primary "Next →" button
 */
@Composable
fun OnboardingRemindersPage(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmPaperBeige),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = statusBarPadding.calculateTopPadding(),
                    bottom = navBarPadding.calculateBottomPadding() + 20.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                // Top Action Bar with Skip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.testTag("onboarding_btn_skip"),
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SecondaryText,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Heading
                Text(
                    text = "Set reminders",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepForestGreen,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Choose when you want to be reminded. Get daily reminders until the due date.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = SecondaryText,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Premium Product Reminder Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.06f),
                            spotColor = Color.Black.copy(alpha = 0.08f),
                        )
                        .border(1.dp, BorderOatmeal, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardCream),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                    ) {
                        // Product Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Standing Green Oats Pouch Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, BorderOatmeal, RoundedCornerShape(12.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_oats_green_pouch),
                                        contentDescription = "Oats",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "Oats",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText,
                                        letterSpacing = (-0.3).sp,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "500 g",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = SecondaryText,
                                    )
                                }
                            }

                            // Active Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFE8F3EB))
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Active",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepForestGreen,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = BorderOatmeal.copy(alpha = 0.8f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 1: Expiry date (15 March 2027, no chevron)
                        ReminderSettingRow(
                            icon = Icons.Outlined.CalendarToday,
                            label = "Expiry date",
                            value = "15 March 2027",
                            showChevron = false,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderOatmeal.copy(alpha = 0.8f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 2: Advance reminder (3 days before, with chevron)
                        ReminderSettingRow(
                            icon = Icons.Outlined.Notifications,
                            label = "Remind me",
                            value = "3 days before",
                            showChevron = true,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderOatmeal.copy(alpha = 0.8f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 3: Daily repetition (Daily until due date, with chevron)
                        ReminderSettingRow(
                            icon = Icons.Outlined.Repeat,
                            label = "Repeat",
                            value = "Daily until due date",
                            showChevron = true,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Navigation: 4 Indicator Dots + Next Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Progress Indicator: Step 2 active out of 6 total steps (○ ○ ● ○ ○ ○)
                OnboardingProgressIndicator(
                    activeStep = 2,
                    totalSteps = 6,
                    modifier = Modifier.padding(bottom = 20.dp),
                )

                // Primary Action Button: "Next →"
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepForestGreen,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_btn_next"),
                ) {
                    Text(
                        text = "Next →",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderSettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    showChevron: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryText,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = SecondaryText,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText,
                )
            }
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SecondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun OnboardingRemindersPagePreview() {
    MemoraTheme {
        OnboardingRemindersPage(
            onNext = {},
            onSkip = {},
        )
    }
}
