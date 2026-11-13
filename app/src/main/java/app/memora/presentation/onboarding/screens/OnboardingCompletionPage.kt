package app.memora.presentation.onboarding.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.presentation.onboarding.components.OnboardingProgressIndicator
import app.memora.ui.theme.DeepForestGreen
import app.memora.ui.theme.PrimaryText
import app.memora.ui.theme.SecondaryText
import app.memora.ui.theme.WarmPaperBeige

/**
 * Screen 6 — Completion ("You're all set!")
 *
 * Pixel-accurate visual reconstruction:
 * - Centered 3D Origami M app logo tile
 * - Centered heading: "You're all set!"
 * - Centered description: "Start adding your first item and let Memora remember the rest."
 * - 4 Checklist rows with green checkmark circles:
 *   1. Scan or add items
 *   2. Set reminders
 *   3. Stay organized
 *   4. Never miss important dates
 * - Organic wave and foliage illustration in the footer
 * - Primary Action: "Add your first item →" (opens AddChoiceBottomSheet)
 * - Secondary Action: "Maybe later" (completes onboarding to Home)
 */
@Composable
fun OnboardingCompletionPage(
    onAddFirstItem: () -> Unit,
    onMaybeLater: () -> Unit,
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
                    top = statusBarPadding.calculateTopPadding() + 24.dp,
                    bottom = navBarPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Header: 3D Origami M Logo + Heading + Description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Memora 3D App Icon Tile
                Image(
                    painter = painterResource(id = R.drawable.ic_memora_app_icon),
                    contentDescription = "Memora App Logo",
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = Color(0x1F000000),
                            spotColor = Color(0x1F000000),
                        )
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Heading
                Text(
                    text = "You’re all set!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepForestGreen,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    text = "Start adding your first item\nand let Memora remember\nthe rest.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }

            // Middle: 4 Checklist Items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ChecklistRow(title = "Scan or add items")
                ChecklistRow(title = "Set reminders")
                ChecklistRow(title = "Stay organized")
                ChecklistRow(title = "Never miss important dates")
            }

            // Bottom Section: Footer Waves + Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Organic Waves & Foliage Artwork
                Image(
                    painter = painterResource(id = R.drawable.img_onboarding_s6_waves),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clipToBounds(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 6-Step Progress Indicator: Step 6 active (○ ○ ○ ○ ○ ●)
                    OnboardingProgressIndicator(
                        activeStep = 5,
                        totalSteps = 6,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    // Primary Action: Add your first item
                    Button(
                        onClick = onAddFirstItem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_btn_get_started"),
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
                                text = "Add your first item",
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Action: Maybe later
                    TextButton(
                        onClick = onMaybeLater,
                        modifier = Modifier.testTag("onboarding_btn_maybe_later"),
                    ) {
                        Text(
                            text = "Maybe later",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SecondaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_check_circle_green),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryText,
        )
    }
}
