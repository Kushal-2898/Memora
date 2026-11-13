package app.memora.presentation.onboarding.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.presentation.onboarding.components.OnboardingProgressIndicator
import app.memora.ui.theme.BorderOatmeal
import app.memora.ui.theme.CardCream
import app.memora.ui.theme.DeepForestGreen
import app.memora.ui.theme.PrimaryText
import app.memora.ui.theme.SecondaryText
import app.memora.ui.theme.WarmPaperBeige

/**
 * Screen 5 — Stay On Top
 *
 * Pixel-accurate visual reconstruction matching approved design:
 * - Top Action Bar with Skip button
 * - Bold heading: "Stay on top"
 * - Subtitle: "Get timely notifications and never miss important dates again."
 * - Hero Phone preview showing lock screen notification for "Oats expires in 3 days"
 * - 3-Row Benefits Card:
 *   1. Daily reminders (Terracotta Bell)
 *   2. Upcoming & overdue view (Slate Calendar)
 *   3. Keep your life organized (Forest Green Bar Chart)
 * - 4-step progress indicator: ○ ○ ○ ● (4th dot active)
 * - Primary "Next →" button
 */
@Composable
fun OnboardingStayOnTopPage(
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
            // Top Section (Skip + Heading + Subtitle)
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
                    text = "Stay on top",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepForestGreen,
                    letterSpacing = (-0.4).sp,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = "Get timely notifications and\nnever miss important dates\nagain.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = SecondaryText,
                    lineHeight = 20.sp,
                )
            }

            // Middle Content: Phone Notification Preview + 3-Benefit Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Phone Mockup Image
                Image(
                    painter = painterResource(id = R.drawable.img_phone_notification_preview),
                    contentDescription = "Notification Preview Phone Mockup",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .height(256.dp),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Benefits Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = Color(0x14000000),
                            spotColor = Color(0x14000000),
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardCream),
                    border = BorderStroke(1.dp, BorderOatmeal),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        // Row 1: Daily reminders
                        BenefitRow(
                            iconRes = R.drawable.ic_ben_bell,
                            title = "Daily reminders",
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            thickness = 1.dp,
                            color = BorderOatmeal.copy(alpha = 0.7f),
                        )

                        // Row 2: Upcoming & overdue view
                        BenefitRow(
                            iconRes = R.drawable.ic_ben_calendar,
                            title = "Upcoming & overdue view",
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            thickness = 1.dp,
                            color = BorderOatmeal.copy(alpha = 0.7f),
                        )

                        // Row 3: Keep your life organized
                        BenefitRow(
                            iconRes = R.drawable.ic_ben_chart,
                            title = "Keep your life organized",
                        )
                    }
                }
            }

            // Bottom Section: Progress Dots + Primary Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 6-Step Progress Indicator: Step 5 active (○ ○ ○ ○ ● ○)
                OnboardingProgressIndicator(
                    activeStep = 4,
                    totalSteps = 6,
                    modifier = Modifier.padding(bottom = 20.dp),
                )

                // Primary Next Button
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_btn_next"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepForestGreen,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Next",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(
    iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryText,
            lineHeight = 22.sp,
        )
    }
}
