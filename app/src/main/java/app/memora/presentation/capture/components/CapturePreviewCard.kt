package app.memora.presentation.capture.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import app.memora.R
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.presentation.common.PrimaryButton
import app.memora.presentation.common.SecondaryButton
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

/**
 * Preview review screen displaying the temporary captured image with Continue and Retake/Replace actions.
 *
 * Implements Stage 2 UI specifications:
 * - Clear image preview
 * - Source badge (Camera, Gallery, Screenshot)
 * - [ CONTINUE ] button (proceeds to confirmed captured image)
 * - [ RETAKE / REPLACE ] button
 */
@Composable
fun CapturePreviewCard(
    capturedImage: CapturedImage,
    onContinue: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()

    val sourceLabel = when (capturedImage.source) {
        CaptureSource.CAMERA -> stringResource(R.string.capture_source_camera)
        CaptureSource.GALLERY -> stringResource(R.string.capture_source_gallery)
        CaptureSource.SCREENSHOT -> stringResource(R.string.capture_source_screenshot)
    }

    val replaceButtonText = when (capturedImage.source) {
        CaptureSource.CAMERA -> stringResource(R.string.capture_action_retake)
        CaptureSource.GALLERY, CaptureSource.SCREENSHOT -> stringResource(R.string.capture_action_replace)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCool),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Navigation / Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("preview_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryNavy,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                // Source Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentIndigo.copy(alpha = 0.10f),
                    modifier = Modifier.testTag("preview_source_badge"),
                ) {
                    Text(
                        text = sourceLabel,
                        style = MemoraTypography.label,
                        color = AccentIndigo,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Image Container
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardWhite,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = BorderLight,
                ),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("capture_image_preview"),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = capturedImage.uri,
                        contentDescription = stringResource(R.string.capture_preview_question),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = AccentIndigo,
                                )
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Confirmation Heading
            Text(
                text = stringResource(R.string.capture_preview_question),
                style = MemoraTypography.headline,
                color = PrimaryNavy,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Continue / Confirm Button
            PrimaryButton(
                text = stringResource(R.string.capture_action_continue),
                onClick = onContinue,
                icon = Icons.Filled.Check,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preview_action_continue"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Replace / Retake Button
            SecondaryButton(
                text = replaceButtonText,
                onClick = onReplace,
                icon = Icons.Filled.Refresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preview_action_replace"),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
