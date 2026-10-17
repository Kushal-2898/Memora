package app.memora.presentation.capture.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.presentation.common.PrimaryButton
import app.memora.presentation.common.SecondaryButton
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusCompleted
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

/**
 * Displayed when a memory has been created and saved into durable storage.
 */
@Composable
fun CaptureConfirmedCard(
    capturedImage: CapturedImage,
    onCaptureAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceLabel = when (capturedImage.source) {
        CaptureSource.CAMERA -> "Camera"
        CaptureSource.GALLERY -> "Gallery"
        CaptureSource.SCREENSHOT -> "Screenshot"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCool)
            .padding(horizontal = 20.dp, vertical = 32.dp),
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
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(StatusCompleted.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = StatusCompleted,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Memory remembered",
                style = MemoraTypography.title,
                color = PrimaryNavy,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your date has been securely scheduled. You'll receive a calm reminder before it's due.",
                style = MemoraTypography.caption,
                color = TextCaption,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Summary card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("captured_image_summary_card"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Source",
                            style = MemoraTypography.label,
                            color = TextCaption,
                        )
                        Text(
                            text = sourceLabel,
                            style = MemoraTypography.body.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Medium,
                            color = PrimaryNavy,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Format",
                            style = MemoraTypography.label,
                            color = TextCaption,
                        )
                        Text(
                            text = capturedImage.mimeType,
                            style = MemoraTypography.body.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Medium,
                            color = PrimaryNavy,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "Remember another",
                onClick = onCaptureAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_another_button"),
            )
        }
    }
}
