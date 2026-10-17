package app.memora.presentation.capture.components

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.memora.R
import app.memora.platform.camera.CameraXManager
import app.memora.platform.camera.PackagingDetectionResult
import app.memora.platform.camera.ScanDocumentAnalyzer
import app.memora.platform.image.ImageStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fullscreen camera viewfinder powered by CameraX with real-time Auto-Snap,
 * torch/flash toggle, lock-on visual reticle feedback, and manual shutter controls.
 */
@Composable
fun CameraPreviewView(
    cameraManager: CameraXManager?,
    imageStorage: ImageStorage,
    onPhotoCaptured: (File) -> Unit,
    onCaptureError: (Throwable) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null,
    onPackagingIdentified: ((PackagingDetectionResult) -> Unit)? = null,
    isAnalysisPaused: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isCapturing by remember { mutableStateOf(false) }
    var cameraBoundError by remember { mutableStateOf<Throwable?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isDocumentDetected by remember { mutableStateOf(false) }
    var detectedPackaging by remember { mutableStateOf<PackagingDetectionResult?>(null) }

    val analyzer = remember {
        ScanDocumentAnalyzer(
            onDocumentDetected = {
                isDocumentDetected = true
            },
            onDetectionCleared = {
                isDocumentDetected = false
                detectedPackaging = null
            },
            onPackagingDetected = { result ->
                detectedPackaging = result
                onPackagingIdentified?.invoke(result)
            }
        )
    }

    fun performCapture() {
        if (isCapturing || cameraManager == null || isAnalysisPaused) return
        isCapturing = true
        isDocumentDetected = false
        detectedPackaging = null
        analyzer.pause()
        runCatching {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        val targetFile = imageStorage.createCaptureFile("scan")
        scope.launch {
            val result = cameraManager.takePicture(targetFile)
            result.fold(
                onSuccess = { file -> 
                    onPhotoCaptured(file)
                },
                onFailure = { err -> 
                    onCaptureError(err)
                }
            )
        }
    }

    LaunchedEffect(isAnalysisPaused) {
        if (isAnalysisPaused) {
            isDocumentDetected = false
            detectedPackaging = null
            analyzer.pause()
            if (isTorchOn) {
                cameraManager?.enableTorch(false)
            }
        } else {
            isCapturing = false
            analyzer.resume()
            if (isTorchOn) {
                cameraManager?.enableTorch(true)
            }
        }
    }

    DisposableEffect(cameraManager, analyzer) {
        onDispose {
            analyzer.close()
            cameraManager?.unbind()
        }
    }

    // Auto-Snap Trigger: When a document is detected and held steady, automatically capture.
    // Single unified block handles both initial detection and resume-from-pause scenarios.
    LaunchedEffect(isDocumentDetected, isCapturing, isAnalysisPaused) {
        if (isDocumentDetected && !isCapturing && !isAnalysisPaused && cameraManager != null && cameraBoundError == null) {
            delay(400) // Brief pause to let user see the green reticle lock on
            if (isDocumentDetected && !isCapturing && !isAnalysisPaused) {
                performCapture()
            }
        }
    }

    // Timeout: Stop scanning and show error if nothing is found for 15 seconds
    LaunchedEffect(isAnalysisPaused) {
        if (!isAnalysisPaused) {
            delay(15000L)
            if (!isCapturing && !isAnalysisPaused) {
                onCaptureError(app.memora.domain.model.MemoraError.Capture.Timeout("Scanner timed out. No document detected."))
            }
        }
    }

    val reticleBorderColor by animateColorAsState(
        targetValue = if (isDocumentDetected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f),
        animationSpec = tween(durationMillis = 280),
        label = "reticle_border_color",
    )
    val reticleBorderWidth by animateDpAsState(
        targetValue = if (isDocumentDetected) 3.5.dp else 2.dp,
        animationSpec = tween(durationMillis = 280),
        label = "reticle_border_width",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (cameraManager != null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_viewfinder"),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        
                        val scaleGestureDetector = android.view.ScaleGestureDetector(ctx, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                                val zoomState = cameraManager.getZoomState()?.value ?: return false
                                val currentZoomRatio = zoomState.zoomRatio
                                val delta = detector.scaleFactor
                                cameraManager.setZoomRatio(currentZoomRatio * delta)
                                return true
                            }
                        })

                        setOnTouchListener { view, event ->
                            scaleGestureDetector.onTouchEvent(event)
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                cameraManager.focusAt(event.x, event.y, this)
                                view.performClick()
                            }
                            true
                        }
                        cameraManager.bindCamera(
                            lifecycleOwner = lifecycleOwner,
                            previewView = this,
                            imageAnalyzer = analyzer,
                            onSuccess = { cameraBoundError = null },
                            onError = { err ->
                                cameraBoundError = err
                                onCaptureError(err)
                            },
                        )
                    }
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.capture_camera_unavailable),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // Central Document/Expiry Scanner Reticle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .border(reticleBorderWidth, reticleBorderColor, RoundedCornerShape(20.dp))
                    .background(
                        if (isDocumentDetected) Color(0x1A4CAF50) else Color.Transparent,
                        RoundedCornerShape(20.dp)
                    )
            )

            // Scanning Guidance Badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isDocumentDetected) Color(0xFF1B5E20).copy(alpha = 0.85f)
                        else Color.Black.copy(alpha = 0.65f)
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isDocumentDetected) Icons.Filled.CheckCircle else Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    tint = if (isDocumentDetected) Color(0xFF81C784) else Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = when {
                        isCapturing -> stringResource(R.string.capture_guidance_capturing)
                        isDocumentDetected -> detectedPackaging?.guidanceMessage ?: stringResource(R.string.scanner_guidance_detected)
                        else -> stringResource(R.string.scanner_guidance_align)
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Top Navigation and Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("camera_back_button")
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.capture_camera_back),
                    tint = Color.White,
                )
            }

            // Torch / Flash Toggle Button
            IconButton(
                onClick = {
                    val next = !isTorchOn
                    isTorchOn = next
                    cameraManager?.enableTorch(next)
                },
                modifier = Modifier
                    .testTag("camera_torch_button")
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (isTorchOn) stringResource(R.string.capture_torch_on) else stringResource(R.string.capture_torch_off),
                    tint = if (isTorchOn) Color(0xFFFFD54F) else Color.White,
                )
            }
        }

        // Bottom Controls Bar with Gallery quick action
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(bottom = 36.dp, top = 20.dp, start = 28.dp, end = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: Gallery Pick
                if (onOpenGallery != null) {
                    IconButton(
                        onClick = onOpenGallery,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .testTag("camera_gallery_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = stringResource(R.string.scanner_option_gallery),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Center: Scanner Activity Indicator
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // Right: Empty spacer for symmetry
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

