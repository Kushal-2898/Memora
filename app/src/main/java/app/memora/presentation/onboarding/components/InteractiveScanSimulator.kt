package app.memora.presentation.onboarding.components

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
import androidx.compose.material.icons.filled.CheckCircle
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scan_simulator_container"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Viewfinder Simulated Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryNavy.copy(alpha = 0.04f))
                    .border(
                        width = 1.dp,
                        color = if (simulatorState == ScanSimulatorState.EXTRACTED) StatusCompleted else BorderLight,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = triggerScan,
                    )
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Viewfinder Corners
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "ORGANIC OAT MILK 1L",
                        style = MemoraTypography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = TextCaption,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Simulated expiry date stamp on the pack
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (simulatorState == ScanSimulatorState.EXTRACTED)
                                    StatusCompleted.copy(alpha = 0.15f)
                                else
                                    CardWhite
                            )
                            .border(
                                width = if (simulatorState == ScanSimulatorState.EXTRACTED) 1.5.dp else 1.dp,
                                color = if (simulatorState == ScanSimulatorState.EXTRACTED) StatusCompleted else BorderLight,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "EXP: 28/11/2026",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (simulatorState == ScanSimulatorState.EXTRACTED) StatusCompleted else PrimaryNavy,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BATCH #OM-492",
                        style = MemoraTypography.caption,
                        color = TextCaption.copy(alpha = 0.6f),
                    )
                }

                // Laser scan sweep bar
                if (simulatorState == ScanSimulatorState.SCANNING) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, (laserProgress.value * 220 - 110).toInt()) }
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        AccentIndigo,
                                        Color.Cyan,
                                        AccentIndigo,
                                        Color.Transparent,
                                    )
                                )
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Extraction Result or Prompt
            if (simulatorState == ScanSimulatorState.EXTRACTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusCompleted.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("scan_simulator_result_chip"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StatusCompleted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Extracted: Nov 28, 2026",
                            style = MemoraTypography.label.copy(fontWeight = FontWeight.SemiBold),
                            color = PrimaryNavy,
                        )
                    }

                    Text(
                        text = "100% confidence",
                        style = MemoraTypography.caption,
                        color = StatusCompleted,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (simulatorState != ScanSimulatorState.EXTRACTED) {
                Button(
                    onClick = triggerScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("scan_simulator_trigger_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = CardWhite,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (simulatorState == ScanSimulatorState.SCANNING) "Scanning offline…" else "Tap to simulate 1s scan",
                        style = MemoraTypography.label.copy(color = CardWhite),
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
                            .height(36.dp)
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
                            text = "Try scan again",
                            style = MemoraTypography.caption.copy(color = AccentIndigo, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}
