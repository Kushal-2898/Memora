package app.memora.presentation.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusCompleted
import app.memora.ui.theme.StatusDueToday
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

private const val LOOP_DURATION_MS = 4500

/**
 * Self-playing procedural product demonstration animation.
 * Cycles continuously through: Aim Viewfinder -> Auto-Snap (Shutter Flash) -> AI Laser Extraction -> Structured Card Reveal.
 */
@Composable
fun InteractiveScanSimulator(
    modifier: Modifier = Modifier,
) {
    // Continuous 4.5-second demonstration loop
    val infiniteTransition = rememberInfiniteTransition(label = "scan_demo_loop")
    val cycleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = LOOP_DURATION_MS.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOOP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle_time",
    )

    // Manual tap override state (satisfies test clicks and user interaction)
    var isManualLocked by remember { mutableStateOf(false) }

    val effectiveTime = if (isManualLocked) 2500f else cycleTime

    // Computed visual states across timeline
    val isAiming = effectiveTime < 1200f
    val isCaptured = effectiveTime in 1200f..1600f
    val isExtracting = effectiveTime in 1600f..2400f
    val isRevealed = effectiveTime >= 2400f || isManualLocked

    val reticleColor = if (effectiveTime >= 1200f) StatusCompleted else Color.White.copy(alpha = 0.55f)

    // Camera shutter flash pulse: rapid 200ms white opacity spike on capture
    val shutterAlpha = if (effectiveTime in 1200f..1400f) {
        ((1400f - effectiveTime) / 200f).coerceIn(0f, 0.65f)
    } else {
        0f
    }

    // Laser sweep progression
    val laserOffset = when {
        isAiming -> ((effectiveTime / 1200f) * 160 - 80).toInt()
        isExtracting -> (((effectiveTime - 1600f) / 800f) * 160 - 80).toInt()
        else -> null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scan_simulator_container"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Slim video loop progress indicator at the top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(BorderLight),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (effectiveTime / LOOP_DURATION_MS).coerceIn(0f, 1f))
                        .height(2.5.dp)
                        .background(AccentIndigo),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. Camera Viewfinder HUD (Demonstrating detection and shutter snap)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White),
                            onClick = { isManualLocked = !isManualLocked },
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // HUD Reticle Frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (effectiveTime >= 1200f) 1.5.dp else 1.dp,
                                color = reticleColor,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "📄 UTILITY BILL · #9402",
                                    style = MemoraTypography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                    ),
                                    color = Color(0xFF94A3B8),
                                )

                                // Live status badge
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (effectiveTime >= 1200f) StatusCompleted else AccentIndigo),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when {
                                            isAiming -> "AIMING"
                                            isCaptured -> "CAPTURED"
                                            isExtracting -> "AI SCAN"
                                            else -> "EXTRACTED"
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (effectiveTime >= 1200f) StatusCompleted else Color(0xFF94A3B8),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Target text in viewfinder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "DUE: 14 NOV 2026",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (effectiveTime >= 1200f) Color(0xFF34D399) else Color.White,
                                )
                                Text(
                                    text = "TOTAL: $84.50",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1),
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Electric City Grid Corp.",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                            )
                        }

                        // Laser sweep line
                        if (laserOffset != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(0, laserOffset) }
                                    .height(2.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                AccentIndigo,
                                                Color.Cyan,
                                                AccentIndigo,
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }

                    // Shutter flash effect overlay
                    if (shutterAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = shutterAlpha)),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Structured Result Section (Shows output as soon as AI extraction finishes)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRevealed || effectiveTime >= 1800f) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_simulator_result_chip"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Engine attribution tag
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentIndigo,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Extracted by Gemini AI · ⚡ Instant Local Scan",
                                    style = MemoraTypography.caption.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    ),
                                    color = AccentIndigo,
                                )
                            }

                            // Structured Memory Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceCool)
                                    .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Electricity Bill",
                                            style = MemoraTypography.label.copy(fontWeight = FontWeight.Bold),
                                            color = PrimaryNavy,
                                        )
                                        Text(
                                            text = "$84.50 · Bills & Due Dates",
                                            style = MemoraTypography.caption,
                                            color = TextCaption,
                                        )
                                    }

                                    // Status pill with tabular dates
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(StatusDueToday.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = "DUE NOV 14",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = StatusDueToday,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // In-progress scanning prompt
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Auto-capturing and extracting document details…",
                                style = MemoraTypography.caption,
                                color = TextCaption,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. User interaction button (Replays or forces instant extraction)
                Button(
                    onClick = { isManualLocked = !isManualLocked },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("scan_simulator_trigger_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isManualLocked) SurfaceCool else AccentIndigo,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = if (isManualLocked) Icons.Outlined.Refresh else Icons.Outlined.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isManualLocked) AccentIndigo else CardWhite,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isManualLocked) "Replay walkthrough animation" else "Tap to lock & test scan",
                        style = MemoraTypography.label.copy(
                            color = if (isManualLocked) AccentIndigo else CardWhite,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}
