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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

private data class CategoryGridItem(
    val title: String,
    val iconRes: Int,
)

/**
 * Screen 4 — Track Everything
 *
 * Pixel-accurate visual reconstruction:
 * - Top Action Bar with Skip button
 * - Bold heading: "Track everything"
 * - Subtitle: "Manage renewals, subscriptions, bills, vehicle maintenance, documents and more."
 * - Balanced 2-Column × 4-Row Category Grid:
 *   1. Products (Terracotta filled shopping basket)
 *   2. Subscriptions (Solid deep forest green credit card)
 *   3. Bills & Renewals (Charcoal slate folded document with lines)
 *   4. Home Care (Terracotta filled house)
 *   5. Vehicle Maintenance (Deep forest green front-facing car)
 *   6. Documents (Terracotta filled folder)
 *   7. Follow-ups (Terracotta circular badge with white checkmark)
 *   8. Custom (Deep forest green 2x2 rounded squares)
 * - 4-step progress indicator: ○ ○ ● ○ (3rd dot active)
 * - Primary "Next →" button
 */
@Composable
fun OnboardingTrackPage(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    val categories = listOf(
        CategoryGridItem("Products", R.drawable.ic_cat_products),
        CategoryGridItem("Subscriptions", R.drawable.ic_cat_subscriptions),
        CategoryGridItem("Bills & Renewals", R.drawable.ic_cat_bills),
        CategoryGridItem("Home Care", R.drawable.ic_cat_home),
        CategoryGridItem("Vehicle Maintenance", R.drawable.ic_cat_vehicle),
        CategoryGridItem("Documents", R.drawable.ic_cat_documents),
        CategoryGridItem("Follow-ups", R.drawable.ic_cat_followups),
        CategoryGridItem("Custom", R.drawable.ic_cat_custom),
    )

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
                    text = "Track everything",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepForestGreen,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Manage renewals, subscriptions, bills, vehicle maintenance, documents and more.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = SecondaryText,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 2-Column × 4-Row Category Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (row in 0 until 4) {
                        val itemLeft = categories[row * 2]
                        val itemRight = categories[row * 2 + 1]

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryTile(
                                item = itemLeft,
                                modifier = Modifier.weight(1f),
                            )
                            CategoryTile(
                                item = itemRight,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
                // Progress Indicator: Step 3 active out of 6 total steps (○ ○ ○ ● ○ ○)
                OnboardingProgressIndicator(
                    activeStep = 3,
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
private fun CategoryTile(
    item: CategoryGridItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(82.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .border(1.dp, BorderOatmeal, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardCream),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.title,
                modifier = Modifier.size(28.dp),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun OnboardingTrackPagePreview() {
    MemoraTheme {
        OnboardingTrackPage(
            onNext = {},
            onSkip = {},
        )
    }
}
