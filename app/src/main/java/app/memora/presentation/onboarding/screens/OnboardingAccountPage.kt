package app.memora.presentation.onboarding.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.ui.theme.DeepForestGreen
import app.memora.ui.theme.PrimaryText
import app.memora.ui.theme.SecondaryText
import app.memora.ui.theme.TerracottaOrange
import app.memora.ui.theme.WarmPaperBeige

/**
 * Screen 7 — Account Creation ("Create your account")
 *
 * Faithfully matches the user-provided reference design:
 * - Upper canopy in Deep Forest Green (~42% height)
 * - Centered 3D M Origami logo + "Memora" brand title
 * - "Create your account" title & description
 * - Top-right "Skip" text action for guest continuation
 * - Organic terracotta wave transition with botanical branch on the right
 * - Warm Paper Beige lower half:
 *   1. "Continue with Google" elevated white pill button
 *   2. "or" divider
 *   3. "Continue with Email" outlined pill button
 *   4. Three benefit pillars: "Sync across devices", "Keep your data safe", "Never miss important dates"
 *   5. "Continue →" primary button
 */
@Composable
fun OnboardingAccountPage(
    onContinueWithGoogle: () -> Unit,
    onContinueWithEmail: () -> Unit,
    onShowPrivacyPolicy: () -> Unit = {},
    onShowTerms: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepForestGreen),
    ) {
        // Background Organic Wave Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Wave horizon positioned around 40% of screen height
            val waveY = h * 0.40f

            // 1. Terracotta Accent Wave (Right side organic dune)
            val terracottaPath = Path().apply {
                moveTo(w * 0.38f, waveY + h * 0.05f)
                cubicTo(
                    w * 0.58f, waveY - h * 0.02f,
                    w * 0.74f, waveY - h * 0.08f,
                    w, waveY - h * 0.03f
                )
                lineTo(w, waveY + h * 0.16f)
                cubicTo(
                    w * 0.82f, waveY + h * 0.17f,
                    w * 0.60f, waveY + h * 0.13f,
                    w * 0.38f, waveY + h * 0.05f
                )
                close()
            }
            drawPath(
                path = terracottaPath,
                color = TerracottaOrange,
                style = Fill,
            )

            // 2. Botanical branch with organic leaves on the right
            val leafColor = Color(0xFF264C36)
            val stemPath = Path().apply {
                moveTo(w * 0.78f, waveY + h * 0.07f)
                cubicTo(
                    w * 0.84f, waveY + h * 0.01f,
                    w * 0.89f, waveY - h * 0.05f,
                    w * 0.94f, waveY - h * 0.08f
                )
            }
            drawPath(
                path = stemPath,
                color = leafColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            val leaves = listOf(
                Triple(Offset(w * 0.84f, waveY + h * 0.02f), -40f, 20f to 34f),
                Triple(Offset(w * 0.89f, waveY - h * 0.005f), 42f, 22f to 36f),
                Triple(Offset(w * 0.87f, waveY - h * 0.035f), -36f, 20f to 32f),
                Triple(Offset(w * 0.92f, waveY - h * 0.045f), 38f, 18f to 30f),
                Triple(Offset(w * 0.93f, waveY - h * 0.075f), 15f, 16f to 26f),
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

            // 3. Warm Paper Beige Lower Section
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

            // 4. Subtle bottom dunes
            val bottomDunePath = Path().apply {
                moveTo(0f, h - h * 0.03f)
                cubicTo(
                    w * 0.3f, h - h * 0.01f,
                    w * 0.7f, h - h * 0.045f,
                    w, h - h * 0.02f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = bottomDunePath,
                color = TerracottaOrange.copy(alpha = 0.25f),
                style = Fill,
            )
        }

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top Section (Upper Canopy): Logo + Titles (~42% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
                    .padding(top = statusBarPadding.calculateTopPadding()),
            ) {
                // Canopy Center Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_memora_app_icon),
                        contentDescription = "Memora Logo",
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(18.dp),
                                ambientColor = Color(0x33000000),
                                spotColor = Color(0x33000000),
                            )
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Memora",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFDF9),
                        letterSpacing = (-0.4).sp,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Create your account",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFDF9),
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.3).sp,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Sync your items, reminders, and\nsettings across your devices.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFFAF7F2).copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }
            }

            // Lower Section (Warm Paper Beige body): Auth Actions + Benefits + Continue CTA (~58% height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = 16.dp,
                        bottom = navBarPadding.calculateBottomPadding() + 16.dp,
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Auth Buttons Area
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Continue with Google Button
                    Surface(
                        onClick = onContinueWithGoogle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_google_signin"),
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White,
                        shadowElevation = 3.dp,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x1F000000)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryText,
                            )
                        }
                    }

                    // "or" Divider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFDED8D0),
                            thickness = 1.dp,
                        )
                        Text(
                            text = "or",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SecondaryText,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFDED8D0),
                            thickness = 1.dp,
                        )
                    }

                    // Continue with Email Button
                    Surface(
                        onClick = onContinueWithEmail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_email_signin"),
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, DeepForestGreen),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_email_outline),
                                contentDescription = "Email Icon",
                                tint = DeepForestGreen,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Email",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryText,
                            )
                        }
                    }
                }

                // 3 Benefit Pillars (Equal 3-column row with vertical dividers)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BenefitPillar(
                        iconRes = R.drawable.ic_pill_cloud,
                        title = "Sync across\ndevices",
                        modifier = Modifier.weight(1f),
                    )

                    VerticalDivider(
                        modifier = Modifier.height(44.dp),
                        color = Color(0xFFE2DCD4),
                        thickness = 1.dp,
                    )

                    BenefitPillar(
                        iconRes = R.drawable.ic_pill_shield,
                        title = "Keep your\ndata safe",
                        modifier = Modifier.weight(1f),
                    )

                    VerticalDivider(
                        modifier = Modifier.height(44.dp),
                        color = Color(0xFFE2DCD4),
                        thickness = 1.dp,
                    )

                    BenefitPillar(
                        iconRes = R.drawable.ic_pill_sync,
                        title = "Never miss\nimportant dates",
                        modifier = Modifier.weight(1f),
                    )
                }

                // Legal Disclaimer Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "By continuing, you agree to our ",
                        fontSize = 11.sp,
                        color = SecondaryText,
                    )
                    Text(
                        text = "Terms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepForestGreen,
                        modifier = Modifier
                            .clickable(onClick = onShowTerms)
                            .testTag("onboarding_link_terms"),
                    )
                    Text(
                        text = " & ",
                        fontSize = 11.sp,
                        color = SecondaryText,
                    )
                    Text(
                        text = "Privacy Policy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepForestGreen,
                        modifier = Modifier
                            .clickable(onClick = onShowPrivacyPolicy)
                            .testTag("onboarding_link_privacy"),
                    )
                }
            }
        }
    }
}

@Composable
private fun BenefitPillar(
    iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(1.5.dp, DeepForestGreen, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = DeepForestGreen,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeepForestGreen,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
        )
    }
}
