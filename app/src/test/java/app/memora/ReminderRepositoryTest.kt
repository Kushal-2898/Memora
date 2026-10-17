package app.memora

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.memora.data.local.MemoraDatabase
import app.memora.data.repository.DefaultMemoryRepository
import app.memora.data.repository.DefaultReminderRepository
import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import kotlinx.coroutines.flow.first
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

/**
 * Unit tests verifying Reminder repository operations backed by Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderRepositoryTest {

    private lateinit var database: MemoraDatabase
    private lateinit var memoryRepository: DefaultMemoryRepository
    private lateinit var reminderRepository: DefaultReminderRepository
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `repository initializes empty with no fabricated demo reminders`() = runTest {
        val initialReminders = reminderRepository.observeReminders().first()
        assertTrue("Repository must start with empty list", initialReminders.isEmpty())
    }

    @Test
    fun `create and retrieve reminder`() = runTest {
        val memory = Memory(
            id = "mem-101",
            title = "Follow up on tax invoice",
            eventType = EventType.DUE_DATE,
            eventDate = today.plusDays(3),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(memory)

        val reminder = Reminder(
            id = "rem-101",
            memoryId = "mem-101",
            reminderStartDate = today.plusDays(1),
            createdAt = now,
            updatedAt = now,
        )

        val createResult = reminderRepository.createReminder(reminder)
        assertTrue(createResult.isSuccess)

        val fetched = reminderRepository.getReminder("rem-101")
        assertNotNull(fetched)
        assertEquals("rem-101", fetched?.id)
        assertEquals("mem-101", fetched?.memoryId)
        assertEquals(ReminderStatus.UPCOMING, fetched?.status)
    }

    @Test
    fun `complete reminder transitions status and sets timestamp in Room`() = runTest {
        val memory = Memory(
            id = "mem-102",
            title = "Passport renewal document",
            eventType = EventType.EXPIRATION,
            eventDate = today.plusDays(5),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder = Reminder(
            id = "rem-102",
            memoryId = "mem-102",
            reminderStartDate = today.plusDays(2),
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(memory, reminder)

        val completionTime = Instant.parse("2026-09-04T11:30:00Z")
        val completeResult = reminderRepository.completeReminder("rem-102", completionTime)
        assertTrue(completeResult.isSuccess)

        val updated = reminderRepository.getReminder("rem-102")
        assertEquals(ReminderStatus.COMPLETED, updated?.status)
        assertEquals(completionTime, updated?.completedAt)
    }

    @Test
    fun `delete reminder removes it from observation stream`() = runTest {
        val memory = Memory(
            id = "mem-103",
            title = "Receipt capture",
            eventType = EventType.OTHER,
            eventDate = today.plusDays(1),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder = Reminder(
            id = "rem-103",
            memoryId = "mem-103",
            reminderStartDate = today,
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(memory, reminder)

        val deleteResult = reminderRepository.deleteReminder("rem-103")
        assertTrue(deleteResult.isSuccess)

        val fetched = reminderRepository.getReminder("rem-103")
        assertNull(fetched)

        val currentList = reminderRepository.observeReminders().first()
        assertTrue(currentList.isEmpty())
    }

    @Test
    fun `completing non-existent reminder returns failure`() = runTest {
        val completeResult = reminderRepository.completeReminder("does-not-exist", now)
        assertTrue(completeResult.isFailure)
    }
}
