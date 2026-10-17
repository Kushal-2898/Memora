package app.memora.presentation.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.IconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memora.R
import app.memora.domain.model.MemoryWithReminders
import app.memora.presentation.common.EventTypeChip
import app.memora.presentation.common.MemoraEmptyState
import app.memora.presentation.common.StatusBadge
import app.memora.presentation.common.formatDaysRelative
import app.memora.presentation.common.toColor
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter


@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    HistoryScreen(
        uiState = uiState,
        searchQuery = searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onRestoreReminder = viewModel::restoreReminder,
        onUndoRestoreReminder = viewModel::completeReminder,
        modifier = modifier,
    )
}

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onRestoreReminder: (String) -> Unit = {},
    onUndoRestoreReminder: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current

    val handleRestore: (MemoryWithReminders) -> Unit = { item ->
        val reminderId = item.reminders.firstOrNull { it.completedAt != null }?.id
            ?: item.activeReminder?.id
            ?: item.reminders.firstOrNull()?.id
        if (reminderId != null) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            onRestoreReminder(reminderId)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = "\"${item.memory.title}\" restored to active reminders",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onUndoRestoreReminder(reminderId)
                }
            }
        }
    }

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

            // Screen Header: Title (22sp weight 600) + Caption (13sp #6B7280)
            Text(
                text = stringResource(R.string.history_title),
                style = MemoraTypography.title,
                color = PrimaryNavy,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Archived and completed date reminders",
                style = MemoraTypography.caption,
                color = TextCaption,
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search history...", color = TextCaption) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextCaption) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextCaption)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderLight,
                    focusedBorderColor = AccentIndigo,
                    unfocusedContainerColor = CardWhite,
                    focusedContainerColor = CardWhite,
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = when {
                    uiState.isLoading -> 0
                    uiState.isEmpty -> 1
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
                label = "HistoryScreenContent"
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
                            icon = Icons.Outlined.CheckCircle,
                            message = if (searchQuery.isNotBlank()) "No matching history found for \"$searchQuery\"." else "No completed reminders in your history yet.",
                            actionLabel = if (searchQuery.isNotBlank()) "Clear search" else "Back to home",
                            onAction = { 
                                if (searchQuery.isNotBlank()) onSearchQueryChange("")
                            },
                            modifier = Modifier.fillMaxSize(),
                            testTag = "history_empty_state",
                        )
                    }
                    2 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("history_list"),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(uiState.completedReminders, key = { it.memory.id }) { item ->
                                SwipeableHistoryItem(
                                    item = item,
                                    onRestore = handleRestore,
                                    onClick = { /* Detail */ },
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

        // Floating Snackbar Host for Restore / Undo actions
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                .testTag("history_snackbar_host"),
            snackbar = { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = PrimaryNavy,
                    contentColor = Color.White,
                    actionColor = Color(0xFFFFD54F),
                    actionContentColor = Color(0xFFFFD54F),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableHistoryItem(
    item: MemoryWithReminders,
    onRestore: (MemoryWithReminders) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem by rememberUpdatedState(item)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onRestore(currentItem)
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .testTag("history_swipe_item_${item.memory.id}"),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                AccentIndigo.copy(alpha = 0.9f)
            } else {
                Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = stringResource(R.string.action_restore),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_restore),
                        color = Color.White,
                        style = MemoraTypography.body.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        },
        content = {
            HistoryMemoryCard(
                item = item,
                onRestore = { onRestore(currentItem) },
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    )
}

@Composable
fun HistoryMemoryCard(
    item: MemoryWithReminders,
    onRestore: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "memory_card_${item.memory.id}",
) {
    val status = item.effectiveStatus
    val statusColor = status.toColor()
    val relativeDays = formatDaysRelative(item.memory.eventDate)
    val formattedDate = item.memory.eventDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite,
        ),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Left status accent bar (4dp wide)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor),
            )

            // Content Container with 16dp internal padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                // Top row: Title and Prominent Days Until/Since
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = item.memory.title,
                        style = MemoraTypography.headline,
                        color = PrimaryNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )

                    Text(
                        text = relativeDays,
                        style = MemoraTypography.headline.copy(fontSize = 15.sp),
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (!item.memory.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.memory.description,
                        style = MemoraTypography.body.copy(fontSize = 14.sp),
                        color = TextCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom row: Event type chip, Date text, Status badge, and Restore action button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EventTypeChip(eventType = item.memory.eventType)
                        Text(
                            text = formattedDate,
                            style = MemoraTypography.caption,
                            color = TextCaption,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusBadge(status = status)

                        OutlinedButton(
                            onClick = onRestore,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentIndigo),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardWhite,
                                contentColor = AccentIndigo,
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("restore_button_${item.memory.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = AccentIndigo,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_restore),
                                style = MemoraTypography.label.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Medium,
                                color = AccentIndigo,
                            )
                        }
                    }
                }
            }
        }
    }
}
