package app.memora

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.memora.data.local.MemoraDatabase
import app.memora.data.repository.DefaultMemoryRepository
import app.memora.data.repository.DefaultReminderRepository
import app.memora.domain.model.EventType
import app.memora.domain.model.MemoraError
import app.memora.domain.model.Memory
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.usecase.UncompleteReminderUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UncompleteReminderUseCaseTest {

    private lateinit var database: MemoraDatabase
    private lateinit var memoryRepository: DefaultMemoryRepository
    private lateinit var reminderRepository: DefaultReminderRepository
    private lateinit var uncompleteReminderUseCase: UncompleteReminderUseCase

    private val now = Instant.parse("2026-09-23T10:00:00Z")
    private val today = LocalDate.of(2026, 9, 23)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MemoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memoryRepository = DefaultMemoryRepository(database.memoryDao(), database.reminderDao())
        reminderRepository = DefaultReminderRepository(database.reminderDao(), database.memoryDao())
        uncompleteReminderUseCase = UncompleteReminderUseCase(reminderRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun blankId_returnsValidationError() = runTest {
        val result = uncompleteReminderUseCase("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MemoraError.Validation)
    }

    @Test
    fun nonExistentId_returnsNotFoundError() = runTest {
        val result = uncompleteReminderUseCase("non-existent-reminder-id")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MemoraError.NotFound)
    }

    @Test
    fun completedReminder_isSuccessfullyRestoredToUpcoming() = runTest {
        // Setup memory and completed reminder in database
        val memory = Memory(
            id = "mem-1",
            title = "Electricity Bill",
            eventType = EventType.DUE_DATE,
            eventDate = today.plusDays(5),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder = Reminder(
            id = "rem-1",
            memoryId = "mem-1",
            reminderStartDate = today.plusDays(2),
            status = ReminderStatus.COMPLETED,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(memory, reminder)

        // Verify it is completed initially
        val initialReminder = reminderRepository.getReminder("rem-1")
        assertNotNull(initialReminder)
        assertNotNull(initialReminder?.completedAt)

        // Perform uncomplete
        val result = uncompleteReminderUseCase("rem-1")
        assertTrue(result.isSuccess)

        // Verify reminder is restored with null completedAt
        val updatedReminder = reminderRepository.getReminder("rem-1")
        assertNotNull(updatedReminder)
        assertNull(updatedReminder?.completedAt)
        assertEquals(ReminderStatus.UPCOMING, updatedReminder?.status)
    }
}
