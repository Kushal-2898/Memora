package app.memora.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.R
import app.memora.data.local.DefaultOnboardingPreferences
import app.memora.data.local.OnboardingPreferences
import app.memora.presentation.onboarding.components.CategorySelectionGrid
import app.memora.presentation.onboarding.components.FinalReadySummaryCard
import app.memora.presentation.onboarding.components.InteractiveScanSimulator
import app.memora.presentation.onboarding.components.LossAversionCard
import app.memora.presentation.onboarding.components.NotificationPreviewCard
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val testTag: String,
)

private val pages = listOf(
    OnboardingPageData(
        icon = Icons.Outlined.EventAvailable,
        titleRes = R.string.onboarding_title_1,
        descRes = R.string.onboarding_desc_1,
        testTag = "onboarding_page_1",
    ),
    OnboardingPageData(
        icon = Icons.Outlined.DocumentScanner,
        titleRes = R.string.onboarding_title_2,
        descRes = R.string.onboarding_desc_2,
        testTag = "onboarding_page_2",
    ),
    OnboardingPageData(
        icon = Icons.Outlined.EditCalendar,
        titleRes = R.string.onboarding_title_3,
        descRes = R.string.onboarding_desc_3,
        testTag = "onboarding_page_3",
    ),
    OnboardingPageData(
        icon = Icons.Outlined.NotificationsActive,
        titleRes = R.string.onboarding_title_4,
        descRes = R.string.onboarding_desc_4,
        testTag = "onboarding_page_4",
    ),
    OnboardingPageData(
        icon = Icons.Outlined.CheckCircle,
        titleRes = R.string.onboarding_title_final,
        descRes = R.string.onboarding_desc_final,
        testTag = "onboarding_page_5",
    ),
)

private const val EDUCATIONAL_PAGE_COUNT = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onboardingPreferences: OnboardingPreferences? = null,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    var isCompleting by remember { mutableStateOf(false) }

    // Categories state
    var selectedCategories by remember {
        mutableStateOf(
            onboardingPreferences?.getSelectedCategories() ?: DefaultOnboardingPreferences.defaultCategories
        )
    }

    val toggleCategory: (String) -> Unit = { categoryId ->
        val updated = if (selectedCategories.contains(categoryId)) {
            if (selectedCategories.size > 1) selectedCategories - categoryId else selectedCategories
        } else {
            selectedCategories + categoryId
        }
        selectedCategories = updated
        onboardingPreferences?.setSelectedCategories(updated)
    }

    val handleComplete = {
        if (!isCompleting) {
            isCompleting = true
            onboardingPreferences?.setSelectedCategories(selectedCategories)
            onComplete()
        }
    }

    // Intercept system back gestures when beyond Screen 1 to retreat one screen.
    // When on Screen 1 (currentPage == 0), BackHandler is disabled, preserving system exit behavior.
    BackHandler(enabled = pagerState.currentPage > 0) {
        if (!pagerState.isScrollInProgress) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCool)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: App Title
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MemoraTypography.title,
                color = PrimaryNavy,
                modifier = Modifier.testTag("onboarding_app_title"),
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Main Carousel Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { pageIndex ->
                val page = pages[pageIndex]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .testTag(page.testTag)
                        .testTag(if (pageIndex == pages.size - 1) "onboarding_page_final" else page.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Headings: Title & Subtitle anchored cleanly at top
                    Text(
                        text = stringResource(page.titleRes),
                        style = MemoraTypography.headline,
                        color = PrimaryNavy,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(page.descRes),
                        style = MemoraTypography.body,
                        color = TextCaption,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hero element centered in the flexible remaining space
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (pageIndex) {
                            0 -> {
                                // Screen 1: Loss Aversion / Before vs After Card
                                LossAversionCard()
                            }
                            1 -> {
                                // Screen 2: Interactive Category Selection Chips
                                CategorySelectionGrid(
                                    selectedCategories = selectedCategories,
                                    onToggleCategory = toggleCategory,
                                )
                            }
                            2 -> {
                                // Screen 3: Interactive Mock Scan Simulator
                                InteractiveScanSimulator()
                            }
                            3 -> {
                                // Screen 4: Polished Micro-Animated Morning Alert
                                NotificationPreviewCard()
                            }
                            4 -> {
                                // Final Screen: Ready Summary & Verified Categories
                                FinalReadySummaryCard(selectedCategories = selectedCategories)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Educational Progress Indicator (Screens 1 to 4 only)
            val isEducationalPage = pagerState.currentPage < EDUCATIONAL_PAGE_COUNT
            if (isEducationalPage) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.onboarding_progress,
                            pagerState.currentPage + 1,
                            EDUCATIONAL_PAGE_COUNT,
                        ),
                        style = MemoraTypography.caption,
                        color = TextCaption,
                        modifier = Modifier.testTag("onboarding_progress_text"),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Indicator Dots (4 educational steps)
                    Row(
                        modifier = Modifier
                            .testTag("onboarding_indicator_dots")
                            .semantics {
                                contentDescription = "Page ${pagerState.currentPage + 1} of $EDUCATIONAL_PAGE_COUNT"
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(EDUCATIONAL_PAGE_COUNT) { iteration ->
                            val isSelected = pagerState.currentPage == iteration
                            val dotDesc = stringResource(
                                if (isSelected) R.string.cd_onboarding_dot_selected else R.string.cd_onboarding_dot,
                                iteration + 1,
                                EDUCATIONAL_PAGE_COUNT,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) AccentIndigo else BorderLight)
                                    .size(if (isSelected) 10.dp else 8.dp)
                                    .semantics {
                                        contentDescription = dotDesc
                                    },
                            )
                        }
                    }
                }
            } else {
                // Final screen: omit educational counter and dots for a clean ready state
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Navigation Controls Row
            val currentPage = pagerState.currentPage
            val isFirstPage = currentPage == 0
            val isFinalPage = currentPage == pages.size - 1

            when {
                isFinalPage -> {
                    // Final Screen: Back (optional) + Get started (primary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                if (!pagerState.isScrollInProgress && pagerState.currentPage > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                .testTag("onboarding_btn_back"),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_button_back),
                                style = MemoraTypography.body.copy(color = TextCaption),
                            )
                        }

                        Button(
                            onClick = handleComplete,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("onboarding_btn_get_started"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentIndigo,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_button_get_started),
                                style = MemoraTypography.headline.copy(color = CardWhite),
                            )
                        }
                    }
                }
                isFirstPage -> {
                    // Screen 1: Skip (secondary) on left, Next (primary) on right. No Back button.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = handleComplete,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                .testTag("onboarding_btn_skip"),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_button_skip),
                                style = MemoraTypography.body.copy(color = TextCaption),
                            )
                        }

                        Button(
                            onClick = {
                                if (!pagerState.isScrollInProgress && pagerState.currentPage < pages.size - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("onboarding_btn_next"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentIndigo,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_button_next),
                                style = MemoraTypography.headline.copy(color = CardWhite),
                            )
                        }
                    }
                }
                else -> {
                    // Screens 2 to 4: Back on left, Skip in center, Next on right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                if (!pagerState.isScrollInProgress && pagerState.currentPage > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                .testTag("onboarding_btn_back"),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_button_back),
                                style = MemoraTypography.body.copy(color = TextCaption),
                            )
                        }

                        TextButton(
                            onClick = handleComplete,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                .testTag("onboarding_btn_skip"),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_button_skip),
                                style = MemoraTypography.body.copy(color = TextCaption),
                            )
                        }

                        Button(
                            onClick = {
                                if (!pagerState.isScrollInProgress && pagerState.currentPage < pages.size - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("onboarding_btn_next"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentIndigo,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_button_next),
                                style = MemoraTypography.headline.copy(color = CardWhite),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
