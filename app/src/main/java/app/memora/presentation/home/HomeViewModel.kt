package app.memora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.ReminderStatus
import app.memora.domain.usecase.CalculateReminderStatusUseCase
import app.memora.domain.usecase.CompleteReminderUseCase
import app.memora.domain.usecase.DeleteMemoryUseCase
import app.memora.domain.usecase.GetMemoryUseCase
import app.memora.platform.notification.NotificationScheduler
import app.memora.domain.usecase.ObserveMemoriesUseCase
import app.memora.domain.usecase.UncompleteReminderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val needsActionReminders: List<MemoryWithReminders> = emptyList(),
    val upcomingReminders: List<MemoryWithReminders> = emptyList(),
    val completedReminders: List<MemoryWithReminders> = emptyList(),
) {
    val isEmpty: Boolean
        get() = needsActionReminders.isEmpty() && upcomingReminders.isEmpty()

    val activeReminders: List<MemoryWithReminders>
        get() = needsActionReminders.filter { it.reminders.any { r -> r.status == ReminderStatus.REMINDER_ACTIVE } }

    val dueTodayReminders: List<MemoryWithReminders>
        get() = needsActionReminders.filter { it.reminders.any { r -> r.status == ReminderStatus.DUE_TODAY } }

    val overdueReminders: List<MemoryWithReminders>
        get() = needsActionReminders.filter { it.reminders.any { r -> r.status == ReminderStatus.OVERDUE } }
}

class HomeViewModel(
    private val observeMemoriesUseCase: ObserveMemoriesUseCase,
    private val calculateReminderStatusUseCase: CalculateReminderStatusUseCase,
    private val completeReminderUseCase: CompleteReminderUseCase,
    private val uncompleteReminderUseCase: UncompleteReminderUseCase,
    private val deleteMemoryUseCase: DeleteMemoryUseCase,
    private val getMemoryUseCase: GetMemoryUseCase,
    private val updateMemoryUseCase: app.memora.domain.usecase.UpdateMemoryUseCase? = null,
    private val notificationScheduler: NotificationScheduler,
    private val snoozeReminderUseCase: app.memora.domain.usecase.SnoozeReminderUseCase? = null,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    val uiState: StateFlow<HomeUiState> = combine(
        observeMemoriesUseCase(),
        _searchQuery
    ) { allItems, query ->
        val needsAction = mutableListOf<MemoryWithReminders>()
        val upcoming = mutableListOf<MemoryWithReminders>()
        val completed = mutableListOf<MemoryWithReminders>()

        val filteredItems = if (query.isBlank()) {
            allItems
        } else {
            val lowerQuery = query.lowercase()
            allItems.filter { item ->
                item.memory.title.lowercase().contains(lowerQuery) ||
                (item.memory.description?.lowercase()?.contains(lowerQuery) == true)
            }
        }

        filteredItems.forEach { item ->
            val updatedReminders = item.reminders.map { reminder ->
                val reminderStatus = calculateReminderStatusUseCase(item.memory, reminder)
                reminder.copy(status = reminderStatus)
            }
            val processedItem = item.copy(reminders = updatedReminders)
            val status = calculateReminderStatusUseCase(processedItem)

            when (status) {
                ReminderStatus.UPCOMING -> upcoming.add(processedItem)
                ReminderStatus.REMINDER_ACTIVE,
                ReminderStatus.DUE_TODAY,
                ReminderStatus.OVERDUE -> needsAction.add(processedItem)
                ReminderStatus.COMPLETED -> completed.add(processedItem)
            }
        }

        HomeUiState(
            isLoading = false,
            needsActionReminders = needsAction,
            upcomingReminders = upcoming,
            completedReminders = completed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun snoozeReminder(reminderId: String, daysToSnooze: Long = 1L) {
        viewModelScope.launch {
            snoozeReminderUseCase?.invoke(reminderId, daysToSnooze)
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

    fun undoCompleteReminder(reminderId: String) {
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
                        reminderTime = restoredReminder.reminderTime ?: java.time.LocalTime.of(9, 0),
                    )
                }
            }
        }
    }

    fun updateMemory(memory: app.memora.domain.model.Memory, newTitle: String, newEventType: app.memora.domain.model.EventType, newEventDate: java.time.LocalDate) {
        viewModelScope.launch {
            val result = updateMemoryUseCase?.invoke(memory, newTitle, newEventType, newEventDate)
                ?: Result.failure(IllegalStateException("No updateMemoryUseCase provided"))
            if (result.isSuccess) {
                val updatedMemory = getMemoryUseCase(memory.id)
                updatedMemory?.reminders?.forEach { reminder ->
                    notificationScheduler.scheduleReminder(
                        memoryId = memory.id,
                        reminderId = reminder.id,
                        title = newTitle,
                        reminderStartDate = reminder.reminderStartDate,
                        eventDate = newEventDate,
                        timeZoneId = memory.timeZoneId,
                        reminderTime = reminder.reminderTime ?: java.time.LocalTime.of(9, 0)
                    )
                }
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            val memory = getMemoryUseCase(memoryId)
            memory?.reminders?.forEach { reminder ->
                notificationScheduler.cancelReminder(reminder.id)
            }
            deleteMemoryUseCase(memoryId)
        }
    }

    class Factory(
        private val observeMemoriesUseCase: ObserveMemoriesUseCase,
        private val calculateReminderStatusUseCase: CalculateReminderStatusUseCase,
        private val completeReminderUseCase: CompleteReminderUseCase,
        private val uncompleteReminderUseCase: UncompleteReminderUseCase,
        private val deleteMemoryUseCase: DeleteMemoryUseCase,
        private val getMemoryUseCase: GetMemoryUseCase,
        private val updateMemoryUseCase: app.memora.domain.usecase.UpdateMemoryUseCase,
        private val notificationScheduler: NotificationScheduler,
        private val snoozeReminderUseCase: app.memora.domain.usecase.SnoozeReminderUseCase? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                observeMemoriesUseCase = observeMemoriesUseCase,
                calculateReminderStatusUseCase = calculateReminderStatusUseCase,
                completeReminderUseCase = completeReminderUseCase,
                uncompleteReminderUseCase = uncompleteReminderUseCase,
                deleteMemoryUseCase = deleteMemoryUseCase,
                getMemoryUseCase = getMemoryUseCase,
                updateMemoryUseCase = updateMemoryUseCase,
                notificationScheduler = notificationScheduler,
                snoozeReminderUseCase = snoozeReminderUseCase,
            ) as T
        }
    }
}
