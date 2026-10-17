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
import app.memora.domain.usecase.CreateMemoryUseCase
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
class CreateMemoryUseCaseTest {

    private lateinit var database: MemoraDatabase
    private lateinit var memoryRepository: DefaultMemoryRepository
    private lateinit var createMemoryUseCase: CreateMemoryUseCase

    private val now = Instant.parse("2026-09-04T10:00:00Z")
    private val today = LocalDate.of(2026, 9, 4)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MemoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memoryRepository = DefaultMemoryRepository(database.memoryDao(), database.reminderDao())
        createMemoryUseCase = CreateMemoryUseCase(memoryRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `creating memory with blank title fails with validation error`() = runTest {
        val memory = Memory(
            id = "mem-blank",
            title = "   ",
            eventType = EventType.OTHER,
            eventDate = today.plusDays(5),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )

        val result = createMemoryUseCase(memory)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MemoraError.Validation)
        assertEquals("Memory title cannot be blank.", (error as MemoraError.Validation).reason)
    }

    @Test
    fun `creating memory with blank ID fails with validation error`() = runTest {
        val memory = Memory(
            id = "",
            title = "Valid Title",
            eventType = EventType.OTHER,
            eventDate = today.plusDays(5),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )

        val result = createMemoryUseCase(memory)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MemoraError.Validation)
        assertEquals("Memory ID cannot be blank.", (error as MemoraError.Validation).reason)
    }

    @Test
    fun `creating memory with reminder start date after event date fails validation`() = runTest {
        val eventDate = today.plusDays(3)
        val invalidReminderStartDate = today.plusDays(5) // After event date!
        val memory = Memory(
            id = "mem-invalid-dates",
            title = "Invoice Due",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder = Reminder(
            id = "rem-invalid",
            memoryId = "mem-invalid-dates",
            reminderStartDate = invalidReminderStartDate,
            createdAt = now,
            updatedAt = now,
        )

        val result = createMemoryUseCase(memory, reminder)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is MemoraError.Validation)
        assertTrue(error?.message?.contains("cannot be after event date") == true)
    }

    @Test
    fun `creating memory with reminder start date equal to event date succeeds`() = runTest {
        val eventDate = today.plusDays(3)
        val memory = Memory(
            id = "mem-same-dates",
            title = "Concert Ticket",
            eventType = EventType.OTHER,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder = Reminder(
            id = "rem-same",
            memoryId = "mem-same-dates",
            reminderStartDate = eventDate,
            createdAt = now,
            updatedAt = now,
        )

        val result = createMemoryUseCase(memory, reminder)
        assertTrue(result.isSuccess)

        val retrieved = memoryRepository.getMemoryWithReminders("mem-same-dates")
        assertNotNull(retrieved)
        assertEquals("Concert Ticket", retrieved?.memory?.title)
        assertEquals(1, retrieved?.reminders?.size)
    }

    @Test
    fun `creating valid memory without reminder persists successfully`() = runTest {
        val memory = Memory(
            id = "mem-valid",
            title = "Passport Renewal",
            eventType = EventType.EXPIRATION,
            eventDate = today.plusMonths(6),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )

        val result = createMemoryUseCase(memory)
        assertTrue(result.isSuccess)

        val retrieved = memoryRepository.getMemory("mem-valid")
        assertNotNull(retrieved)
        assertEquals("Passport Renewal", retrieved?.title)
    }
}
