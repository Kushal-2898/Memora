package app.memora

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.memora.data.local.MemoraDatabase
import app.memora.data.local.MemoryDao
import app.memora.data.local.ReminderDao
import app.memora.data.local.toDomain
import app.memora.data.local.toEntity
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
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseTest {

    private lateinit var context: Context
    private lateinit var database: MemoraDatabase
    private lateinit var memoryDao: MemoryDao
    private lateinit var reminderDao: ReminderDao
    private lateinit var memoryRepository: DefaultMemoryRepository
    private lateinit var reminderRepository: DefaultReminderRepository

    private val now = Instant.parse("2026-09-04T12:00:00Z")
    private val eventDate = LocalDate.of(2026, 9, 20)
    private val reminderStartDate = LocalDate.of(2026, 9, 15)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MemoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memoryDao = database.memoryDao()
        reminderDao = database.reminderDao()
        memoryRepository = DefaultMemoryRepository(memoryDao, reminderDao)
        reminderRepository = DefaultReminderRepository(reminderDao, memoryDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `empty database returns empty collections`() = runTest {
        val memories = memoryRepository.observeMemories().first()
        val reminders = reminderRepository.observeReminders().first()
        assertTrue("Memories must be empty initially", memories.isEmpty())
        assertTrue("Reminders must be empty initially", reminders.isEmpty())
    }

    @Test
    fun `create Memory and read returns identical logical data`() = runTest {
        val memory = Memory(
            id = "mem-101",
            title = "Passport Renewal Deadline",
            description = "Renew before international conference",
            eventType = EventType.EXPIRATION,
            eventDate = eventDate,
            eventTime = LocalTime.of(14, 30),
            timeZoneId = "America/New_York",
            createdAt = now,
            updatedAt = now,
        )

        val createResult = memoryRepository.createMemory(memory)
        assertTrue(createResult.isSuccess)

        val retrieved = memoryRepository.getMemory("mem-101")
        assertNotNull(retrieved)
        assertEquals("mem-101", retrieved?.id)
        assertEquals("Passport Renewal Deadline", retrieved?.title)
        assertEquals("Renew before international conference", retrieved?.description)
        assertEquals(EventType.EXPIRATION, retrieved?.eventType)
        assertEquals(eventDate, retrieved?.eventDate)
        assertEquals(LocalTime.of(14, 30), retrieved?.eventTime)
        assertEquals("America/New_York", retrieved?.timeZoneId)
        assertEquals(now, retrieved?.createdAt)
        assertEquals(now, retrieved?.updatedAt)
    }

    @Test
    fun `create Memory with initial Reminder persists and links atomically`() = runTest {
        val memory = Memory(
            id = "mem-102",
            title = "Appliance Warranty",
            description = "Refrigerator 2-year warranty expiry",
            eventType = EventType.WARRANTY_END,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder = Reminder(
            id = "rem-201",
            memoryId = "mem-102",
            reminderStartDate = reminderStartDate,
            reminderTime = LocalTime.of(9, 0),
            status = ReminderStatus.UPCOMING,
            createdAt = now,
            updatedAt = now,
        )

        val result = memoryRepository.createMemory(memory, reminder)
        assertTrue(result.isSuccess)

        val itemWithReminders = memoryRepository.getMemoryWithReminders("mem-102")
        assertNotNull(itemWithReminders)
        assertEquals("Appliance Warranty", itemWithReminders?.memory?.title)
        assertEquals(1, itemWithReminders?.reminders?.size)
        assertEquals("rem-201", itemWithReminders?.reminders?.first()?.id)
        assertEquals(reminderStartDate, itemWithReminders?.reminders?.first()?.reminderStartDate)
    }

    @Test
    fun `foreign key cascade delete removes child reminders when parent memory is deleted`() = runTest {
        val memory = Memory(
            id = "mem-103",
            title = "Flight Check-in",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder1 = Reminder(
            id = "rem-301",
            memoryId = "mem-103",
            reminderStartDate = reminderStartDate,
            createdAt = now,
            updatedAt = now,
        )
        val reminder2 = Reminder(
            id = "rem-302",
            memoryId = "mem-103",
            reminderStartDate = eventDate,
            createdAt = now,
            updatedAt = now,
        )

        memoryRepository.createMemory(memory, reminder1)
        reminderRepository.createReminder(reminder2)

        assertEquals(2, reminderRepository.getRemindersForMemory("mem-103").size)

        // Delete parent memory
        val deleteResult = memoryRepository.deleteMemory("mem-103")
        assertTrue(deleteResult.isSuccess)

        // Parent memory must be gone
        assertNull(memoryRepository.getMemory("mem-103"))
        // Both child reminders must be cascade-deleted
        assertTrue(reminderRepository.getRemindersForMemory("mem-103").isEmpty())
        assertNull(reminderRepository.getReminder("rem-301"))
        assertNull(reminderRepository.getReminder("rem-302"))
    }

    @Test
    fun `complete reminder persists status and completion timestamp in Room`() = runTest {
        val memory = Memory(
            id = "mem-104",
            title = "Utility bill payment",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val reminder = Reminder(
            id = "rem-401",
            memoryId = "mem-104",
            reminderStartDate = reminderStartDate,
            createdAt = now,
            updatedAt = now,
        )

        memoryRepository.createMemory(memory, reminder)

        val completionTime = Instant.parse("2026-09-04T15:30:00Z")
        val completeResult = reminderRepository.completeReminder("rem-401", completionTime)
        assertTrue(completeResult.isSuccess)

        val updated = reminderRepository.getReminder("rem-401")
        assertNotNull(updated)
        assertEquals(ReminderStatus.COMPLETED, updated?.status)
        assertEquals(completionTime, updated?.completedAt)
    }

    @Test
    fun `multiple memories with multiple reminders persist and observe correctly`() = runTest {
        for (i in 1..3) {
            val mem = Memory(
                id = "mem-$i",
                title = "Memory Item $i",
                eventType = EventType.OTHER,
                eventDate = eventDate.plusDays(i.toLong()),
                timeZoneId = "UTC",
                createdAt = now,
                updatedAt = now,
            )
            val remA = Reminder(
                id = "rem-$i-a",
                memoryId = "mem-$i",
                reminderStartDate = eventDate.plusDays(i.toLong() - 2),
                createdAt = now,
                updatedAt = now,
            )
            val remB = Reminder(
                id = "rem-$i-b",
                memoryId = "mem-$i",
                reminderStartDate = eventDate.plusDays(i.toLong()),
                createdAt = now,
                updatedAt = now,
            )
            memoryRepository.createMemory(mem, remA)
            reminderRepository.createReminder(remB)
        }

        val observed = memoryRepository.observeMemories().first()
        assertEquals(3, observed.size)
        observed.forEach { item ->
            assertEquals(2, item.reminders.size)
        }
    }

    @Test
    fun `process restart simulation confirms persistence across database close and reopen`() = runTest {
        val dbFile = File(context.cacheDir, "restart_test.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Open persistent DB file and write data
        var persistentDb = Room.databaseBuilder(context, MemoraDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val memRepo1 = DefaultMemoryRepository(persistentDb.memoryDao(), persistentDb.reminderDao())
        val remRepo1 = DefaultReminderRepository(persistentDb.reminderDao(), persistentDb.memoryDao())

        val testMemory = Memory(
            id = "persist-1",
            title = "Annual Car Registration",
            description = "State DMV renewal",
            eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 11, 1),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val testReminder = Reminder(
            id = "persist-rem-1",
            memoryId = "persist-1",
            reminderStartDate = LocalDate.of(2026, 10, 15),
            createdAt = now,
            updatedAt = now,
        )
        memRepo1.createMemory(testMemory, testReminder)

        // 2. Close database (simulating app process kill)
        persistentDb.close()

        // 3. Re-open database from the exact same SQLite file (simulating app relaunch)
        persistentDb = Room.databaseBuilder(context, MemoraDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val memRepo2 = DefaultMemoryRepository(persistentDb.memoryDao(), persistentDb.reminderDao())
        val remRepo2 = DefaultReminderRepository(persistentDb.reminderDao(), persistentDb.memoryDao())

        val recovered = memRepo2.getMemoryWithReminders("persist-1")
        assertNotNull("Data must survive database closure and reopen", recovered)
        assertEquals("Annual Car Registration", recovered?.memory?.title)
        assertEquals("State DMV renewal", recovered?.memory?.description)
        assertEquals(1, recovered?.reminders?.size)
        assertEquals("persist-rem-1", recovered?.reminders?.first()?.id)

        persistentDb.close()
        dbFile.delete()
    }
    @Test
    fun `create Memory with invalid initial Reminder rolls back the parent insert`() = runTest {
        val memory = Memory(
            id = "mem-rollback",
            title = "Atomicity Test",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val invalidReminder = Reminder(
            id = "rem-invalid",
            memoryId = "different-memory",
            reminderStartDate = reminderStartDate,
            createdAt = now,
            updatedAt = now,
        )

        val result = memoryRepository.createMemory(memory, invalidReminder)

        assertTrue(result.isFailure)
        assertNull(memoryRepository.getMemory(memory.id))
        assertNull(reminderRepository.getReminder(invalidReminder.id))
    }

}
