package app.memora.presentation.scanner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import app.memora.R
import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.platform.camera.CameraXManager
import app.memora.platform.image.ImageStorage
import app.memora.presentation.capture.CaptureNavEvent
import app.memora.presentation.capture.CaptureUiState
import app.memora.presentation.capture.CaptureViewModel
import app.memora.presentation.capture.components.CameraPreviewView
import app.memora.presentation.capture.components.CapturePermissionView
import app.memora.presentation.capture.components.ExtractedMemoryBottomSheet
import app.memora.presentation.capture.components.LocalScanReviewBottomSheet
import app.memora.presentation.common.PrimaryButton
import app.memora.presentation.common.SecondaryButton
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusOverdue
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

/**
 * ScannerScreen: Immediate-launch automatic document and data scanner.
 *
 * Flow:
 * 1. Launches camera scanner immediately upon opening.
 * 2. Continuously analyzes preview frames for document boundaries and readability.
 * 3. Automatically acquires scan frame when stable (no manual shutter button required).
 * 4. Extracts text and candidates locally (0 Gemini calls).
 * 5. Displays [LocalScanReviewBottomSheet] directly over viewfinder (no intermediate preview card).
 * 6. User confirms review -> calls Gemini AI refinement (cost invariant: exactly 1 call).
 * 7. Displays [ExtractedMemoryBottomSheet] with editable fields and [Save Memory].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: CaptureViewModel,
    cameraManager: CameraXManager?,
    imageStorage: ImageStorage,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = onNavigateBack,
    onImageConfirmed: ((CapturedImage) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    var pendingSource by remember { mutableStateOf<CaptureSource?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showDiscardSheetDialog by remember { mutableStateOf(false) }
    var extractedCandidate by remember { mutableStateOf<ExtractedMemoryCandidate?>(null) }

    // Listen to ViewModel navigation events (e.g. on memory save -> Home)
    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is CaptureNavEvent.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    // Modern Android Photo Picker launcher
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val source = pendingSource ?: CaptureSource.GALLERY
        viewModel.onImageSelected(uri?.toString(), source)
    }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val permanentlyDenied = !isGranted && (activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false)
        viewModel.onPermissionResult(isGranted, permanentlyDenied)
    }

    // Immediate launch: check and start camera scanner on initial entry
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startScanner(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Intercept bottom sheet dismissal on back -> show discard confirmation dialog
    BackHandler(enabled = showBottomSheet) {
        showDiscardSheetDialog = true
    }

    // Handle system back navigation according to scanner lifecycle rules
    BackHandler(enabled = !showBottomSheet) {
        when (uiState) {
            is CaptureUiState.LocalExtractionReady, is CaptureUiState.GeminiProcessing -> {
                viewModel.rescan()
            }
            is CaptureUiState.CameraActive -> {
                onNavigateBack()
            }
            else -> {
                onNavigateBack()
            }
        }
    }

    val isCameraPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED || uiState is CaptureUiState.CameraActive

    val shouldMountCamera = (uiState is CaptureUiState.CameraActive || uiState is CaptureUiState.Idle) && isCameraPermissionGranted
    val isAnalysisPaused = uiState !is CaptureUiState.CameraActive

    val frozenCapturedImage = when (val state = uiState) {
        is CaptureUiState.Preview -> state.capturedImage
        is CaptureUiState.Extracting -> state.capturedImage
        is CaptureUiState.Confirmed -> state.capturedImage
        is CaptureUiState.LocalExtractionReady -> state.capturedImage
        is CaptureUiState.GeminiProcessing -> state.capturedImage
        is CaptureUiState.ExtractionReady -> state.capturedImage
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (shouldMountCamera) {
            CameraPreviewView(
                cameraManager = cameraManager,
                imageStorage = imageStorage,
                onPhotoCaptured = { file ->
                    val capturedImage = imageStorage.createCapturedImageFromFile(file, CaptureSource.CAMERA)
                    viewModel.onScanAcquired(capturedImage)
                },
                onCaptureError = viewModel::onCaptureError,
                onBack = onNavigateBack,
                onOpenGallery = {
                    pendingSource = CaptureSource.GALLERY
                    pickVisualMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPackagingIdentified = { packagingResult ->
                    viewModel.onPackagingDetected(packagingResult)
                },
                isAnalysisPaused = isAnalysisPaused,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (frozenCapturedImage != null) {
            SubcomposeAsyncImage(
                model = frozenCapturedImage.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
        }

        when (val state = uiState) {
            is CaptureUiState.Idle -> {
                if (!shouldMountCamera) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceCool),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = AccentIndigo,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            is CaptureUiState.CameraActive -> {
                // Viewfinder is mounted and active underneath
            }

            is CaptureUiState.PermissionRequired -> {
                CapturePermissionView(
                    permanentlyDenied = state.permanentlyDenied,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onCancel = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is CaptureUiState.Preview -> {
                // Bypass manual preview confirmation card: immediately confirm and extract locally
                LaunchedEffect(state.capturedImage) {
                    val confirmed = viewModel.confirmImage()
                    if (confirmed != null) {
                        onImageConfirmed?.invoke(confirmed)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.scanner_guidance_extracting),
                            style = MemoraTypography.body,
                            color = Color.White,
                        )
                    }
                }
            }

            is CaptureUiState.Processing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("capture_processing_indicator"),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MemoraTypography.body,
                            color = Color.White,
                        )
                    }
                }
            }

            is CaptureUiState.Extracting, is CaptureUiState.Confirmed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.scanner_guidance_extracting),
                            style = MemoraTypography.body,
                            color = Color.White,
                        )
                    }
                }
            }

            is CaptureUiState.LocalExtractionReady -> {
                LocalScanReviewBottomSheet(
                    localResult = state.localResult,
                    onConfirm = { viewModel.confirmLocalExtraction() },
                    onSkipGemini = { viewModel.skipGeminiAndProceed() },
                    onRescan = { viewModel.rescan() },
                    onEnterManually = { viewModel.startManualEntry(state.capturedImage) },
                    onDismiss = { viewModel.rescan() },
                )
            }

            is CaptureUiState.GeminiProcessing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MemoraTypography.body,
                            color = Color.White,
                        )
                    }
                }
            }

            is CaptureUiState.ExtractionReady -> {
                LaunchedEffect(state.result) {
                    extractedCandidate = state.result.candidate
                    showBottomSheet = true
                }
            }

            is CaptureUiState.Error -> {
                ScannerErrorView(
                    message = state.message,
                    canRetry = state.canRetry,
                    canEnterManually = state.canEnterManually,
                    onEnterManually = if (state.capturedImage != null) {
                        { viewModel.startManualEntry(state.capturedImage) }
                    } else null,
                    onDismiss = {
                        viewModel.dismissError()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showBottomSheet) {
        ExtractedMemoryBottomSheet(
            candidate = extractedCandidate,
            onSave = { title, eventType, eventDate, remindDaysBefore ->
                viewModel.saveMemory(title, eventType, eventDate, remindDaysBefore)
                showBottomSheet = false
            },
            onRetake = {
                showBottomSheet = false
                viewModel.rescan()
            },
            onReplace = {
                showBottomSheet = false
                viewModel.retakeOrReplaceImage { source ->
                    pendingSource = source
                    pickVisualMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onRequestDismiss = {
                showDiscardSheetDialog = true
            },
            onDismiss = {
                showBottomSheet = false
            },
        )
    }

    if (showDiscardSheetDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardSheetDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.capture_discard_dialog_title),
                    style = MemoraTypography.headline,
                    color = PrimaryNavy,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.capture_discard_dialog_message),
                    style = MemoraTypography.body,
                    color = TextCaption,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardSheetDialog = false
                        showBottomSheet = false
                        viewModel.cancelCapture()
                        viewModel.rescan()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = StatusOverdue,
                    ),
                    modifier = Modifier.testTag("dialog_confirm_discard"),
                ) {
                    Text(
                        text = stringResource(R.string.action_discard),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardSheetDialog = false },
                    modifier = Modifier.testTag("dialog_cancel_discard"),
                ) {
                    Text(
                        text = stringResource(R.string.action_keep_editing),
                        color = PrimaryNavy,
                    )
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("discard_sheet_confirmation_dialog"),
        )
    }
}

@Composable
private fun ScannerErrorView(
    message: String,
    canRetry: Boolean,
    canEnterManually: Boolean = false,
    onEnterManually: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCool)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(StatusOverdue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = StatusOverdue,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (canEnterManually) "Couldn't extract the details" else stringResource(R.string.capture_error_title),
                style = MemoraTypography.title,
                color = PrimaryNavy,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Text(
                text = message,
                style = MemoraTypography.caption,
                color = TextCaption,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            PrimaryButton(
                text = if (canRetry) stringResource(R.string.capture_error_retry) else stringResource(R.string.capture_error_dismiss),
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("error_dismiss_button"),
            )

            if (canEnterManually && onEnterManually != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SecondaryButton(
                    text = "Enter Manually",
                    onClick = onEnterManually,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
