package app.memora.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import app.memora.presentation.home.components.EditMemoryBottomSheet
import java.time.LocalDate
import app.memora.domain.model.EventType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memora.R
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.ReminderStatus
import app.memora.presentation.common.MemoraEmptyState
import app.memora.presentation.common.MemoraExtendedFab
import app.memora.presentation.common.MemoraMemoryCard
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.StatusCompleted
import app.memora.ui.theme.StatusOverdue
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption

enum class HomeStatusTab(
    val title: String,
    val emptyIcon: ImageVector,
    val emptyMessage: String,
) {
    NEEDS_ACTION(
        title = "Needs Action",
        emptyIcon = Icons.Outlined.NotificationsActive,
        emptyMessage = "You're all caught up! No items need your attention right now.",
    ),
    UPCOMING(
        title = "Upcoming",
        emptyIcon = Icons.Outlined.Event,
        emptyMessage = "No upcoming reminders scheduled.",
    ),
}

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        searchQuery = searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onScannerClick = onNavigateToScanner,
        onCompleteReminder = viewModel::completeReminder,
        onUndoCompleteReminder = viewModel::undoCompleteReminder,
        onDeleteMemory = viewModel::deleteMemory,
        onUpdateMemory = viewModel::updateMemory,
        modifier = modifier,
    )
}

// Backward-compatible overload for callers using onNavigateToCapture
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onNavigateToCapture: () -> Unit,
    modifier: Modifier = Modifier,
    deprecatedTag: Unit = Unit,
) {
    HomeRoute(
        viewModel = viewModel,
        onNavigateToScanner = onNavigateToCapture,
        modifier = modifier,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onScannerClick: () -> Unit,
    onCompleteReminder: (String) -> Unit = {},
    onUndoCompleteReminder: (String) -> Unit = {},
    onDeleteMemory: (String) -> Unit = {},
    onUpdateMemory: (app.memora.domain.model.Memory, String, app.memora.domain.model.EventType, java.time.LocalDate) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(HomeStatusTab.NEEDS_ACTION) }
    var memoryPendingDelete by remember { mutableStateOf<MemoryWithReminders?>(null) }
    var memoryPendingEdit by remember { mutableStateOf<MemoryWithReminders?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceCool),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Screen Header: Title (22sp weight 600) + Subtitle caption (13sp #6B7280)
            HomeHeader()

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search memories...", color = TextCaption) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextCaption) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextCaption)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderLight,
                    focusedBorderColor = AccentIndigo,
                    unfocusedContainerColor = CardWhite,
                    focusedContainerColor = CardWhite,
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status Tabs Row: Upcoming | Active | Due Today | Overdue | Completed
            HomeStatusTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                uiState = uiState,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val currentItems = when (selectedTab) {
                HomeStatusTab.NEEDS_ACTION -> uiState.needsActionReminders
                HomeStatusTab.UPCOMING -> uiState.upcomingReminders
            }

            AnimatedContent(
                targetState = when {
                    uiState.isLoading -> 0
                    currentItems.isEmpty() -> 1
                    else -> 2
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(220, delayMillis = 90)) togetherWith
                        fadeOut(animationSpec = tween(90))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "HomeScreenContent"
            ) { state ->
                when (state) {
                    0 -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = AccentIndigo,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                    1 -> {
                        MemoraEmptyState(
                            icon = selectedTab.emptyIcon,
                            message = if (searchQuery.isNotBlank()) "No matching memories found for \"$searchQuery\"." else selectedTab.emptyMessage,
                            actionLabel = if (searchQuery.isNotBlank()) "Clear search" else "Scan",
                            onAction = {
                                if (searchQuery.isNotBlank()) {
                                    onSearchQueryChange("")
                                } else {
                                    onScannerClick()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            testTag = "home_empty_state",
                        )
                    }
                    2 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("home_reminders_list"),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(currentItems, key = { it.memory.id }) { item ->
                                SwipeableMemoryItem(
                                    item = item,
                                    onComplete = { completedItem ->
                                        val reminderId = completedItem.activeReminder?.id
                                        if (reminderId != null) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                            onCompleteReminder(reminderId)
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "\"${completedItem.memory.title}\" marked as completed",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onUndoCompleteReminder(reminderId)
                                                }
                                            }
                                        }
                                    },
                                    onDeleteRequest = { targetItem ->
                                        memoryPendingDelete = targetItem
                                    },
                                    onClick = { memoryPendingEdit = item },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Snackbar Host for Undo actions
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
                .testTag("home_snackbar_host"),
            snackbar = { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = PrimaryNavy,
                    contentColor = Color.White,
                    actionColor = Color(0xFFFFD54F),
                    actionContentColor = Color(0xFFFFD54F),
                    shape = RoundedCornerShape(20.dp),
                )
            }
        )

        // Extended FAB: Indigo background, white icon, Extended FAB with label "Scan"
        MemoraExtendedFab(
            onClick = onScannerClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp),
            testTag = "home_capture_button",
        )

        // Delete Confirmation Dialog
        if (memoryPendingDelete != null) {
            val target = memoryPendingDelete!!
            AlertDialog(
                onDismissRequest = { memoryPendingDelete = null },
                title = {
                    Text(
                        text = "Delete Memory?",
                        style = MemoraTypography.headline,
                        color = PrimaryNavy,
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete \"${target.memory.title}\"? This action cannot be undone and will cancel all scheduled reminders.",
                        style = MemoraTypography.body,
                        color = TextCaption,
                    )
                },
                confirmButton = {
                    val view = androidx.compose.ui.platform.LocalView.current
                    TextButton(
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            onDeleteMemory(target.memory.id)
                            memoryPendingDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = StatusOverdue,
                        ),
                        modifier = Modifier.testTag("dialog_confirm_delete"),
                    ) {
                        Text(
                            text = "Delete",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { memoryPendingDelete = null },
                        modifier = Modifier.testTag("dialog_cancel_delete"),
                    ) {
                        Text(
                            text = "Cancel",
                            color = PrimaryNavy,
                        )
                    }
                },
                containerColor = CardWhite,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("delete_confirmation_dialog"),
            )
        }

        memoryPendingEdit?.let { memoryToEdit ->
            EditMemoryBottomSheet(
                memoryItem = memoryToEdit,
                onSave = { newTitle, newEventType, newDate ->
                    onUpdateMemory(memoryToEdit.memory, newTitle, newEventType, newDate)
                    memoryPendingEdit = null
                },
                onDismiss = { memoryPendingEdit = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableMemoryItem(
    item: MemoryWithReminders,
    onComplete: (MemoryWithReminders) -> Unit,
    onDeleteRequest: (MemoryWithReminders) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem by rememberUpdatedState(item)
    val isCompleted = currentItem.effectiveStatus == ReminderStatus.COMPLETED

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    val reminderId = currentItem.activeReminder?.id
                    if (reminderId != null && !isCompleted) {
                        onComplete(currentItem)
                        true
                    } else {
                        false
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequest(currentItem)
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isCompleted,
        enableDismissFromEndToStart = true,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .testTag("swipe_item_${item.memory.id}"),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> StatusCompleted.copy(alpha = 0.9f)
                SwipeToDismissBoxValue.EndToStart -> StatusOverdue.copy(alpha = 0.9f)
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Outlined.CheckCircle
                SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.Delete
                else -> null
            }
            val label = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> stringResource(R.string.action_complete)
                SwipeToDismissBoxValue.EndToStart -> stringResource(R.string.action_delete)
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment,
            ) {
                if (icon != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = label,
                            color = Color.White,
                            style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        },
        content = {
            MemoraMemoryCard(
                item = item,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    )
}

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_title),
            style = MemoraTypography.title,
            color = PrimaryNavy,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.app_tagline),
            style = MemoraTypography.caption,
            color = TextCaption,
        )
    }
}

@Composable
private fun HomeStatusTabBar(
    selectedTab: HomeStatusTab,
    onTabSelected: (HomeStatusTab) -> Unit,
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val tabs = HomeStatusTab.values()
    val selectedIndex = tabs.indexOf(selectedTab)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = SurfaceCool,
        contentColor = PrimaryNavy,
        indicator = { tabPositions ->
            if (selectedIndex in tabPositions.indices) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    height = 2.dp,
                    color = AccentIndigo,
                )
            }
        },
        divider = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderLight)
            )
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            val count = when (tab) {
                HomeStatusTab.NEEDS_ACTION -> uiState.needsActionReminders.size
                HomeStatusTab.UPCOMING -> uiState.upcomingReminders.size
            }

            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier
                    .height(44.dp)
                    .testTag("tab_${tab.name.lowercase()}"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = tab.title,
                            style = MemoraTypography.body.copy(
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) AccentIndigo else TextCaption,
                        )

                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) AccentIndigo.copy(alpha = 0.12f)
                                        else BorderLight
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MemoraTypography.label.copy(fontSize = 11.sp),
                                    color = if (isSelected) AccentIndigo else TextCaption,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}
