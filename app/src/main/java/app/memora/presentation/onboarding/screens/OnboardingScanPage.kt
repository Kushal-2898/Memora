package app.memora.presentation.onboarding.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.presentation.onboarding.components.OnboardingProgressIndicator
import app.memora.presentation.onboarding.components.OnboardingVideoPlayer
import app.memora.ui.theme.BorderOatmeal
import app.memora.ui.theme.CardCream
import app.memora.ui.theme.DeepForestGreen
import app.memora.ui.theme.MemoraTheme
import app.memora.ui.theme.PrimaryText
import app.memora.ui.theme.SecondaryText
import app.memora.ui.theme.WarmPaperBeige

/**
 * Screen 2 — Scan Product Labels
 *
 * Primary focus: Hero looping scanning demonstration video (R.raw.onboarding_scan_demo).
 *
 * Composition:
 * - Top bar with warm beige background and right-aligned "Skip" button
 * - Bold left-aligned heading: "Scan product labels"
 * - Subtitle: "Scan a barcode or product label to quickly capture details like expiry date, manufacture date and more."
 * - Hero 16:9 video container with rounded corners and subtle oatmeal border
 * - Feature Information Card with 3 rows:
 *   1. Barcode & QR scanning
 *   2. Auto-detect product details
 *   3. Manually add if needed
 * - 4-step progress indicator: ○ ● ○ ○ (dot 1 active)
 * - Full-width "Next →" primary action button
 */
@Composable
fun OnboardingScanPage(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
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
                    text = "Scan product labels",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepForestGreen,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Scan a barcode or product label to quickly capture details like expiry date, manufacture date and more.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = SecondaryText,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Hero Video Container (16:9 Aspect Ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 3.dp,
                            shape = RoundedCornerShape(18.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f),
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, BorderOatmeal, RoundedCornerShape(18.dp))
                        .background(Color(0xFF19251F)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentAlignment = Alignment.Center,
                    ) {
                        OnboardingVideoPlayer(
                            videoResId = R.raw.onboarding_scan_demo,
                            modifier = Modifier.fillMaxSize(),
                            isActive = isActive,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Feature Information Card (3 rows)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderOatmeal, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardCream),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        // Row 1: Barcode & QR scanning
                        FeatureRow(
                            icon = Icons.Outlined.QrCodeScanner,
                            title = "Barcode & QR scanning",
                        )

                        HorizontalDivider(
                            color = BorderOatmeal.copy(alpha = 0.8f),
                            thickness = 1.dp,
                        )

                        // Row 2: Auto-detect product details
                        FeatureRow(
                            icon = Icons.Outlined.DocumentScanner,
                            title = "Auto-detect product details",
                        )

                        HorizontalDivider(
                            color = BorderOatmeal.copy(alpha = 0.8f),
                            thickness = 1.dp,
                        )

                        // Row 3: Manually add if needed
                        FeatureRow(
                            icon = Icons.Outlined.Edit,
                            title = "Manually add if needed",
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Navigation: Indicator Dots + Next Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Progress Indicator: Step 1 active (○ ● ○ ○ ○ ○)
                OnboardingProgressIndicator(
                    activeStep = 1,
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
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryText,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryText,
            letterSpacing = (-0.2).sp,
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun OnboardingScanPagePreview() {
    MemoraTheme {
        OnboardingScanPage(
            onNext = {},
            onSkip = {},
            isActive = true,
        )
    }
}
