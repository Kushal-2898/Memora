package app.memora.presentation.onboarding.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.memora.ui.theme.BorderOatmeal
import app.memora.ui.theme.DeepForestGreen

/**
 * 6-step progress indicator reproducing the 6-screen onboarding visual design.
 * Circular dots: active dot in Deep Forest Green, inactive dots in warm oatmeal beige.
 */
@Composable
fun OnboardingProgressIndicator(
    activeStep: Int,
    modifier: Modifier = Modifier,
    totalSteps: Int = 6,
) {
    Row(
        modifier = modifier
            .testTag("onboarding_indicator_dots")
            .semantics {
                contentDescription = "Step ${activeStep + 1} of $totalSteps"
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalSteps) {
            val isActive = (i == activeStep)
            val dotSize by animateDpAsState(
                targetValue = if (isActive) 8.dp else 6.dp,
                animationSpec = tween(durationMillis = 200),
                label = "dot_size_$i",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (isActive) DeepForestGreen else BorderOatmeal),
            )
        }
    }
}
