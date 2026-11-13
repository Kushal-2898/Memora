package app.memora.presentation.onboarding.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.presentation.onboarding.components.OnboardingProgressIndicator
import app.memora.ui.theme.DeepForestGreen
import app.memora.ui.theme.MemoraTheme
import app.memora.ui.theme.SecondaryText
import app.memora.ui.theme.TerracottaOrange
import app.memora.ui.theme.WarmPaperBeige

/**
 * Screen 1 — Welcome
 *
 * Reproduces the visual language of the reference design:
 * - Full-bleed Deep Forest Green upper canopy
 * - Center-prominent floating 3D M folded-paper launcher icon
 * - Cream wordmark & tagline: "Memora" & "Your life, remembered."
 * - Organic cream and terracotta wave transition with botanical leaves
 * - Warm paper beige lower section with left-aligned "Never miss\nwhat matters"
 * - Description: "Track expiry dates, renewals, subscriptions, maintenance and more — all in one place."
 * - 4-step progress dots (dot 0 active)
 * - Primary forest green button: "Get started →"
 */
@Composable
fun OnboardingWelcomePage(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepForestGreen),
    ) {
        // Background Canvas: Deep Forest Green upper, Terracotta accent wave, Botanical leaves, Warm Paper Beige lower
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Wave horizon positioned around 49% of screen height
            val waveY = h * 0.495f

            // 1. Terracotta Accent Wave (Right side organic shape)
            val terracottaPath = Path().apply {
                moveTo(w * 0.42f, waveY + h * 0.05f)
                cubicTo(
                    w * 0.60f, waveY - h * 0.015f,
                    w * 0.76f, waveY - h * 0.075f,
                    w, waveY - h * 0.02f
                )
                lineTo(w, waveY + h * 0.16f)
                cubicTo(
                    w * 0.82f, waveY + h * 0.18f,
                    w * 0.62f, waveY + h * 0.14f,
                    w * 0.42f, waveY + h * 0.05f
                )
                close()
            }
            drawPath(
                path = terracottaPath,
                color = TerracottaOrange,
                style = Fill,
            )

            // 2. Botanical Branch & Leaves (Dark Forest Green, emerging over terracotta)
            val leafColor = Color(0xFF1E3A2B)
            val stemPath = Path().apply {
                moveTo(w * 0.83f, waveY + h * 0.05f)
                quadraticTo(
                    w * 0.89f, waveY - h * 0.015f,
                    w * 0.93f, waveY - h * 0.07f
                )
            }
            drawPath(
                path = stemPath,
                color = leafColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // Draw organic leaves along the branch
            val leaves = listOf(
                Triple(Offset(w * 0.845f, waveY + h * 0.02f), -42f, 22f to 36f),
                Triple(Offset(w * 0.90f, waveY - h * 0.005f), 45f, 24f to 40f),
                Triple(Offset(w * 0.88f, waveY - h * 0.035f), -38f, 22f to 34f),
                Triple(Offset(w * 0.925f, waveY - h * 0.045f), 40f, 20f to 34f),
                Triple(Offset(w * 0.93f, waveY - h * 0.075f), 15f, 16f to 28f),
            )
            for ((offset, angle, dims) in leaves) {
                rotate(degrees = angle, pivot = offset) {
                    drawOval(
                        color = leafColor,
                        topLeft = Offset(offset.x - dims.first / 2, offset.y - dims.second / 2),
                        size = androidx.compose.ui.geometry.Size(dims.first, dims.second),
                    )
                }
            }

            // 3. Warm Paper Beige Lower Sheet (Smooth Organic S-Curve)
            val beigePath = Path().apply {
                moveTo(0f, waveY - h * 0.015f)
                cubicTo(
                    w * 0.28f, waveY - h * 0.035f,
                    w * 0.52f, waveY + h * 0.03f,
                    w * 0.75f, waveY + h * 0.015f
                )
                cubicTo(
                    w * 0.88f, waveY + h * 0.005f,
                    w * 0.96f, waveY + h * 0.025f,
                    w, waveY + h * 0.035f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = beigePath,
                color = WarmPaperBeige,
                style = Fill,
            )
        }

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Upper Canopy: Brand Identity
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.02f)
                    .padding(top = statusBarPadding.calculateTopPadding()),
                contentAlignment = Alignment.Center,
            ) {
                if (onSkip != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = 8.dp)
                            .size(48.dp)
                            .testTag("onboarding_btn_skip")
                            .clickable { onSkip() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            // Cream on Deep Forest Green keeps WCAG contrast; navy would be unreadable here.
                            color = Color(0xFFFFFDF9).copy(alpha = 0.85f),
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    // Floating 3D M Ribbon Logo
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "Memora Logo",
                        modifier = Modifier.size(260.dp),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Memora",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFDF9),
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier.testTag("onboarding_app_title"),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Your life, remembered.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFFAF7F2).copy(alpha = 0.88f),
                        letterSpacing = (-0.2).sp,
                    )
                }
            }

            // Lower Section: Heading, Subtitle, 4 Progress Dots, and Primary Action Button
            val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.98f)
                    .padding(
                        start = 28.dp,
                        end = 28.dp,
                        top = 24.dp,
                        bottom = navBarPadding.calculateBottomPadding() + 20.dp,
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Never miss\nwhat matters",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepForestGreen,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Start,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Track expiry dates, renewals, subscriptions, maintenance and more — all in one place.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = SecondaryText,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Start,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 6 progress dots (Screen 1 has dot 0 active)
                    OnboardingProgressIndicator(
                        activeStep = 0,
                        totalSteps = 6,
                        modifier = Modifier.padding(bottom = 22.dp),
                    )

                    // Primary Action Button: "Get started →"
                    Button(
                        onClick = onGetStarted,
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
                            text = "Get started →",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun OnboardingWelcomePagePreview() {
    MemoraTheme {
        OnboardingWelcomePage(onGetStarted = {})
    }
}
