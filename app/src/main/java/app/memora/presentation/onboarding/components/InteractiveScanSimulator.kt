package app.memora.presentation.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

enum class ScanSimulatorState {
    IDLE,
    SCANNING,
    EXTRACTED,
}

@Composable
fun InteractiveScanSimulator(
    modifier: Modifier = Modifier,
) {
    var simulatorState by remember { mutableStateOf(ScanSimulatorState.IDLE) }
    val laserProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val triggerScan = {
        if (simulatorState != ScanSimulatorState.SCANNING) {
            simulatorState = ScanSimulatorState.SCANNING
            coroutineScope.launch {
                laserProgress.snapTo(0f)
                laserProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 400, easing = LinearEasing),
                )
                simulatorState = ScanSimulatorState.EXTRACTED
            }
        }
    }

    val reticleColor by animateColorAsState(
        targetValue = if (simulatorState == ScanSimulatorState.EXTRACTED) StatusCompleted else Color.White.copy(alpha = 0.6f),
        label = "reticle_color",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scan_simulator_container"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Top: Camera Viewfinder HUD (Option 1 - Authentic scanning reticle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White),
                        onClick = triggerScan,
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // HUD Reticle Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .height(82.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (simulatorState == ScanSimulatorState.EXTRACTED) 1.5.dp else 1.dp,
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

                            // Status Indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (simulatorState == ScanSimulatorState.EXTRACTED) StatusCompleted else AccentIndigo),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (simulatorState == ScanSimulatorState.EXTRACTED) "LOCKED" else "DETECTING",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (simulatorState == ScanSimulatorState.EXTRACTED) StatusCompleted else Color(0xFF94A3B8),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Target Extracted Date Stamp in Document
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
                                color = if (simulatorState == ScanSimulatorState.EXTRACTED) Color(0xFF34D399) else Color.White,
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

                    // Laser scan sweep bar
                    if (simulatorState == ScanSimulatorState.SCANNING) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, (laserProgress.value * 160 - 80).toInt()) }
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Bottom: Extraction Result (Option 2 - Structured SaaS Memory Card)
            if (simulatorState == ScanSimulatorState.EXTRACTED) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_simulator_result_chip"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Engine Attribution Tag
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

                    // Structured Memory Card Output
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCool)
                            .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$84.50 · Bills & Due Dates",
                                    style = MemoraTypography.caption,
                                    color = TextCaption,
                                )
                            }

                            // High-contrast reminder status pill
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Action Button (Test Anchor & User Control)
            if (simulatorState != ScanSimulatorState.EXTRACTED) {
                Button(
                    onClick = triggerScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("scan_simulator_trigger_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CardWhite,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (simulatorState == ScanSimulatorState.SCANNING) "Scanning document…" else "Tap to simulate scan",
                        style = MemoraTypography.label.copy(color = CardWhite, fontWeight = FontWeight.SemiBold),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = triggerScan,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("scan_simulator_trigger_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCool),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AccentIndigo,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Simulate another capture",
                            style = MemoraTypography.caption.copy(color = AccentIndigo, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}
