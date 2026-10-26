package app.memora.presentation.onboarding.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusOverdue
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

@Composable
fun LossAversionCard(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("onboarding_loss_aversion_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header: Before vs After
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "THE OLD WAY",
                    style = MemoraTypography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = StatusOverdue,
                )
                Text(
                    text = "WITH MEMORA",
                    style = MemoraTypography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = AccentIndigo,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two-column comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Left Column: Manual Calendar Typing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StatusOverdue.copy(alpha = 0.05f))
                        .border(1.dp, StatusOverdue.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(StatusOverdue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditCalendar,
                                contentDescription = null,
                                tint = StatusOverdue,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Manual Form Entry",
                            style = MemoraTypography.label.copy(fontWeight = FontWeight.SemiBold),
                            color = PrimaryNavy,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "45s per item • Titles, dates, and times typed by hand",
                            style = MemoraTypography.caption,
                            color = TextCaption,
                            lineHeight = 14.sp,
                        )
                    }
                }

                // Right Column: Memora Snap
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentIndigo.copy(alpha = 0.08f))
                        .border(1.5.dp, AccentIndigo.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(AccentIndigo),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                tint = CardWhite,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1-Second Snap",
                            style = MemoraTypography.label.copy(fontWeight = FontWeight.SemiBold),
                            color = AccentIndigo,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "100% On-device OCR reads the date automatically",
                            style = MemoraTypography.caption,
                            color = PrimaryNavy,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Badge: Household savings reassurance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCool)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "🔒 Never miss an expiry date or renewal deadline again",
                    style = MemoraTypography.caption.copy(fontWeight = FontWeight.Medium),
                    color = PrimaryNavy,
                )
            }
        }
    }
}
