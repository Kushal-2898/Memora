package app.memora.presentation.onboarding.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.memora.ui.theme.BorderOatmeal

/**
 * Robust, lifecycle-aware video player for the onboarding scanning demonstration.
 * Reuses MediaPlayer + TextureView with zero extraneous external dependencies.
 *
 * Responsibilities:
 * - Automatically plays when isActive is true.
 * - Pauses when navigating away (isActive becomes false).
 * - Pauses when the application is backgrounded.
 * - Resumes on return when isActive is true.
 * - Loops seamlessly with OnCompletionListener fallback.
 * - Releases resources on disposal.
 * - Handles errors gracefully with fallback UI.
 */
@Composable
fun OnboardingVideoPlayer(
    videoResId: Int,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }

    fun initPlayer(surfaceTexture: SurfaceTexture, ctx: Context) {
        if (mediaPlayer != null) return
        try {
            val newSurface = Surface(surfaceTexture)
            surfaceRef = newSurface

            val player = MediaPlayer().apply {
                setSurface(newSurface)
                val uri = Uri.parse("android.resource://${ctx.packageName}/$videoResId")
                setDataSource(ctx, uri)
                isLooping = true
                setVolume(0f, 0f) // Silent demonstration
                setOnCompletionListener { mp ->
                    try {
                        mp.seekTo(0)
                        mp.start()
                    } catch (_: Throwable) {
                    }
                }
                setOnPreparedListener {
                    isPrepared = true
                    if (isActive) {
                        try {
                            start()
                        } catch (_: Throwable) {
                        }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    hasError = true
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (_: Throwable) {
            hasError = true
        }
    }

    // Respond to isActive changes (swiping between pager pages)
    LaunchedEffect(isActive, isPrepared) {
        val player = mediaPlayer
        if (player != null && isPrepared) {
            try {
                if (isActive) {
                    if (!player.isPlaying) {
                        player.start()
                    }
                } else {
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    // Lifecycle observer for background/foreground transitions
    DisposableEffect(lifecycleOwner, isActive) {
        val observer = LifecycleEventObserver { _, event ->
            val player = mediaPlayer
            if (player != null && isPrepared) {
                try {
                    when (event) {
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                            if (player.isPlaying) player.pause()
                        }
                        Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                            if (isActive && !player.isPlaying) player.start()
                        }
                        else -> {}
                    }
                } catch (_: Throwable) {
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Release player resources when this composable is removed from composition
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) player.stop()
                    player.reset()
                    player.release()
                }
            } catch (_: Throwable) {
            }
            mediaPlayer = null
            surfaceRef?.release()
            surfaceRef = null
            isPrepared = false
        }
    }

    Box(
        modifier = modifier.background(Color(0xFF19251F)),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasError) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                initPlayer(surfaceTexture, ctx)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {}

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                try {
                                    mediaPlayer?.let { player ->
                                        if (player.isPlaying) player.stop()
                                        player.reset()
                                        player.release()
                                    }
                                } catch (_: Throwable) {
                                }
                                mediaPlayer = null
                                surfaceRef?.release()
                                surfaceRef = null
                                isPrepared = false
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }

                        if (isAvailable && surfaceTexture != null) {
                            initPlayer(surfaceTexture!!, ctx)
                        }
                    }
                },
                update = { tv ->
                    if (tv.isAvailable && tv.surfaceTexture != null && mediaPlayer == null) {
                        initPlayer(tv.surfaceTexture!!, tv.context)
                    }
                    val player = mediaPlayer
                    if (player != null && isPrepared) {
                        try {
                            if (isActive && !player.isPlaying) {
                                player.start()
                            } else if (!isActive && player.isPlaying) {
                                player.pause()
                            }
                        } catch (_: Throwable) {
                        }
                    }
                },
            )
        } else {
            // Elegant fallback UI in case media loading fails
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF214A34)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "Scan Demo",
                    tint = BorderOatmeal,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
