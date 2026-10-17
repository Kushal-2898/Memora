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
import app.memora.domain.usecase.CreateReminderUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class CreateReminderUseCaseTest {

    private lateinit var database: MemoraDatabase
    private lateinit var memoryRepository: DefaultMemoryRepository
    private lateinit var reminderRepository: DefaultReminderRepository
    private lateinit var createReminderUseCase: CreateReminderUseCase

    private val now = Instant.parse("2026-09-04T10:00:00Z")
    private val today = LocalDate.of(2026, 9, 4)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MemoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memoryRepository = DefaultMemoryRepository(database.memoryDao(), database.reminderDao())
        reminderRepository = DefaultReminderRepository(database.reminderDao(), database.memoryDao())
        createReminderUseCase = CreateReminderUseCase(reminderRepository, memoryRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `creating reminder with blank ID returns validation error`() = runTest {
        val reminder = Reminder(
            id = "",
            memoryId = "mem-1",
            reminderStartDate = today,
            createdAt = now,
            updatedAt = now,
        )

        val result = createReminderUseCase(reminder)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MemoraError.Validation)
        assertEquals("Reminder ID cannot be blank.", (error as MemoraError.Validation).reason)
    }

    @Test
    fun `creating reminder with blank memoryId returns validation error`() = runTest {
        val reminder = Reminder(
            id = "rem-1",
            memoryId = "",
            reminderStartDate = today,
            createdAt = now,
            updatedAt = now,
        )

        val result = createReminderUseCase(reminder)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MemoraError.Validation)
        assertEquals("Memory ID cannot be blank.", (error as MemoraError.Validation).reason)
    }

    @Test
    fun `creating reminder for non-existent memory returns NotFound error`() = runTest {
        val reminder = Reminder(
            id = "rem-orphan",
            memoryId = "non-existent-memory",
            reminderStartDate = today,
            createdAt = now,
            updatedAt = now,
        )

        val result = createReminderUseCase(reminder)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MemoraError.NotFound)
    }

    @Test
    fun `creating reminder with start date after event date returns validation error`() = runTest {
        val eventDate = today.plusDays(3)
        val memory = Memory(
            id = "mem-1",
            title = "Car Insurance",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(memory)

        val invalidReminder = Reminder(
            id = "rem-late",
            memoryId = "mem-1",
            reminderStartDate = eventDate.plusDays(1), // After eventDate
            createdAt = now,
            updatedAt = now,
        )

        val result = createReminderUseCase(invalidReminder)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MemoraError.Validation)
        assertTrue(error?.message?.contains("cannot be after event date") == true)
    }

    @Test
    fun `creating valid reminder persists to Room repository`() = runTest {
        val eventDate = today.plusDays(5)
        val memory = Memory(
            id = "mem-2",
            title = "Boarding pass gate B4",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(memory)

        val validReminder = Reminder(
            id = "rem-valid",
            memoryId = "mem-2",
            reminderStartDate = today.plusDays(2),
            createdAt = now,
            updatedAt = now,
        )

        val result = createReminderUseCase(validReminder)
        assertTrue(result.isSuccess)

        val retrieved = reminderRepository.getReminder("rem-valid")
        assertNotNull(retrieved)
        assertEquals("rem-valid", retrieved?.id)
        assertEquals("mem-2", retrieved?.memoryId)
    }
}
