package app.memora.presentation.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.TextCaption

data class CategoryOption(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
)

val defaultCategoryOptions = listOf(
    CategoryOption(
        id = "groceries",
        emoji = "🥛",
        title = "Groceries & Dairy",
        subtitle = "Pantry items, milk, fresh goods",
    ),
    CategoryOption(
        id = "medicines",
        emoji = "💊",
        title = "Medicines & Rx",
        subtitle = "Prescriptions, vitamins, first aid",
    ),
    CategoryOption(
        id = "bills",
        emoji = "📄",
        title = "Bills & Due Dates",
        subtitle = "Utilities, credit cards, rent",
    ),
    CategoryOption(
        id = "warranties",
        emoji = "🏷️",
        title = "Warranties & Gear",
        subtitle = "Appliances, electronics, receipts",
    ),
    CategoryOption(
        id = "renewals",
        emoji = "🚗",
        title = "Vehicle & Licenses",
        subtitle = "Insurance policies, registrations",
    ),
)

@Composable
fun CategorySelectionGrid(
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("onboarding_category_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        defaultCategoryOptions.forEach { option ->
            val isSelected = selectedCategories.contains(option.id)
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) AccentIndigo.copy(alpha = 0.08f) else CardWhite,
                label = "bgColor",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) AccentIndigo else BorderLight,
                label = "borderColor",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .testTag("category_chip_${option.id}")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = AccentIndigo),
                        onClick = { onToggleCategory(option.id) },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics {
                        contentDescription = "${option.title}, ${if (isSelected) "selected" else "not selected"}"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = option.emoji,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column {
                        Text(
                            text = option.title,
                            style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = PrimaryNavy,
                        )
                        Text(
                            text = option.subtitle,
                            style = MemoraTypography.caption,
                            color = TextCaption,
                        )
                    }
                }

                // Checkbox circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AccentIndigo else Color.Transparent)
                        .border(
                            width = if (isSelected) 0.dp else 1.5.dp,
                            color = if (isSelected) AccentIndigo else BorderLight,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = CardWhite,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
