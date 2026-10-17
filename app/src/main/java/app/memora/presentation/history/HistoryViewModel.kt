package app.memora.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.ReminderStatus
import app.memora.domain.usecase.CompleteReminderUseCase
import app.memora.domain.usecase.GetMemoryUseCase
import app.memora.domain.usecase.ObserveMemoriesUseCase
import app.memora.domain.usecase.UncompleteReminderUseCase
import app.memora.platform.notification.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class HistoryUiState(
    val isLoading: Boolean = false,
    val completedReminders: List<MemoryWithReminders> = emptyList(),
) {
    val isEmpty: Boolean
        get() = completedReminders.isEmpty()
}

class HistoryViewModel(
    private val observeMemoriesUseCase: ObserveMemoriesUseCase,
    private val uncompleteReminderUseCase: UncompleteReminderUseCase,
    private val completeReminderUseCase: CompleteReminderUseCase,
    private val getMemoryUseCase: GetMemoryUseCase,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        observeMemoriesUseCase(),
        _searchQuery
    ) { allMemories, query ->
        val completed = allMemories.filter { it.effectiveStatus == ReminderStatus.COMPLETED }
        
        val filtered = if (query.isBlank()) {
            completed
        } else {
            val lowerQuery = query.lowercase()
            completed.filter { item ->
                item.memory.title.lowercase().contains(lowerQuery) ||
                (item.memory.description?.lowercase()?.contains(lowerQuery) == true)
            }
        }
        
        HistoryUiState(
            isLoading = false,
            completedReminders = filtered,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun restoreReminder(reminderId: String) {
        viewModelScope.launch {
            val result = uncompleteReminderUseCase(reminderId)
            val restoredReminder = result.getOrNull()
            if (restoredReminder != null) {
                val memoryWithReminders = getMemoryUseCase(restoredReminder.memoryId)
                if (memoryWithReminders != null) {
                    notificationScheduler.scheduleReminder(
                        memoryId = memoryWithReminders.memory.id,
                        reminderId = restoredReminder.id,
                        title = memoryWithReminders.memory.title,
                        reminderStartDate = restoredReminder.reminderStartDate,
                        eventDate = memoryWithReminders.memory.eventDate,
                        timeZoneId = memoryWithReminders.memory.timeZoneId,
                        reminderTime = restoredReminder.reminderTime ?: LocalTime.of(9, 0),
                    )
                }
            }
        }
    }

    fun completeReminder(reminderId: String) {
        viewModelScope.launch {
            val result = completeReminderUseCase(reminderId)
            if (result.isSuccess) {
                notificationScheduler.cancelReminder(reminderId)
            }
        }
    }

    class Factory(
        private val observeMemoriesUseCase: ObserveMemoriesUseCase,
        private val uncompleteReminderUseCase: UncompleteReminderUseCase,
        private val completeReminderUseCase: CompleteReminderUseCase,
        private val getMemoryUseCase: GetMemoryUseCase,
        private val notificationScheduler: NotificationScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(
                observeMemoriesUseCase = observeMemoriesUseCase,
                uncompleteReminderUseCase = uncompleteReminderUseCase,
                completeReminderUseCase = completeReminderUseCase,
                getMemoryUseCase = getMemoryUseCase,
                notificationScheduler = notificationScheduler,
            ) as T
        }
    }
}
