package app.memora.presentation.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memora.domain.model.CapturedImage
import app.memora.platform.camera.CameraXManager
import app.memora.platform.image.ImageStorage
import app.memora.presentation.scanner.ScannerScreen

/**
 * Backward-compatible delegating wrapper forwarding to [ScannerScreen].
 */
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    cameraManager: CameraXManager?,
    imageStorage: ImageStorage,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = onNavigateBack,
    onImageConfirmed: ((CapturedImage) -> Unit)? = null,
) {
    ScannerScreen(
        viewModel = viewModel,
        cameraManager = cameraManager,
        imageStorage = imageStorage,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        onNavigateToHome = onNavigateToHome,
        onImageConfirmed = onImageConfirmed,
    )
}
