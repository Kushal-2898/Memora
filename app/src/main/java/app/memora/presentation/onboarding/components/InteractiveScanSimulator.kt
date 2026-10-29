package app.memora.presentation.onboarding.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.memora.R
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusCompleted
import app.memora.ui.theme.StatusDueToday
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

/**
 * Onboarding product scanning demonstration showing the real video capture
 * of product scanning and instant metadata extraction.
 */
@Composable
fun InteractiveScanSimulator(
    modifier: Modifier = Modifier,
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isManualReplay by remember { mutableStateOf(false) }

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
            // Live Demo Header Tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(StatusCompleted),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE PRODUCT SCAN",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentIndigo,
                        letterSpacing = 0.8.sp,
                    )
                }

                Text(
                    text = "AUTO LOOP · 0:08",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B),
                )
            }

            // 1. Looping Scan Video Player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center,
            ) {
                LoopingVideoPlayer(
                    videoResId = R.raw.onboarding_scan_demo,
                    modifier = Modifier.fillMaxSize(),
                    onPlayerReady = { player -> mediaPlayer = player },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Structured Extraction Result Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_simulator_result_chip"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                        text = "Instant Optical Scan · Structured Memory Created",
                        style = MemoraTypography.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                        color = AccentIndigo,
                    )
                }

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
                                text = "Organic Rolled Oats",
                                style = MemoraTypography.label.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryNavy,
                            )
                            Text(
                                text = "Groceries · Auto-detected Expiry",
                                style = MemoraTypography.caption,
                                color = TextCaption,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(StatusDueToday.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "EXP NOV 14",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = StatusDueToday,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. User interaction button (Replay video)
            Button(
                onClick = {
                    try {
                        mediaPlayer?.seekTo(0)
                        mediaPlayer?.start()
                    } catch (_: Throwable) {
                    }
                    isManualReplay = !isManualReplay
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("scan_simulator_trigger_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentIndigo,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = CardWhite,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Replay product scan video",
                    style = MemoraTypography.label.copy(
                        color = CardWhite,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LoopingVideoPlayer(
    videoResId: Int,
    modifier: Modifier = Modifier,
    onPlayerReady: (MediaPlayer) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> playerRef?.pause()
                    Lifecycle.Event.ON_RESUME -> playerRef?.start()
                    else -> {}
                }
            } catch (_: Throwable) {
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                playerRef?.stop()
                playerRef?.release()
                playerRef = null
            } catch (_: Throwable) {
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        try {
                            val player = MediaPlayer().apply {
                                setSurface(Surface(surface))
                                val uri = Uri.parse("android.resource://${ctx.packageName}/$videoResId")
                                setDataSource(ctx, uri)
                                isLooping = true
                                setVolume(0f, 0f)
                                prepareAsync()
                                setOnPreparedListener {
                                    start()
                                    playerRef = this
                                    onPlayerReady(this)
                                }
                            }
                        } catch (_: Throwable) {
                            // Non-fatal fallback for test/headless environments
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        try {
                            playerRef?.release()
                            playerRef = null
                        } catch (_: Throwable) {
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        update = {
            try {
                if (playerRef?.isPlaying == false) {
                    playerRef?.start()
                }
            } catch (_: Throwable) {
            }
        },
    )
}
