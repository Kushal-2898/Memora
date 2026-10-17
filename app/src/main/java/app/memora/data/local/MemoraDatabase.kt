package app.memora.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Authoritative Room SQLite database for Memora V1.
 *
 * Schema:
 * Version: 1
 *
 * Entities:
 * - [MemoryEntity]: Durable business objects representing the event date, event type, and core details.
 * - [ReminderEntity]: Child entities representing reminder configuration referencing a parent Memory via foreign key.
 *
 * Foreign Key constraints:
 * - Parent: memories(id)
 * - Child: reminders(memoryId)
 * - OnDelete: CASCADE
 * - OnUpdate: CASCADE
 *
 * Migration policy:
 * - Version 1 establishes the foundational schema.
 * - Destructive migration fallback is disabled in production to protect user data. Subsequent schema versions
 *   MUST register explicit [androidx.room.migration.Migration] instances.
 * - exportSchema = true: Room KSP exports a versioned JSON schema snapshot to the configured
 *   schemas/ directory (defined in app/build.gradle.kts via ksp.arg). This snapshot must be committed
 *   to version control and is required for Room AutoMigration support in future versions.
 */
@Database(
    entities = [
        MemoryEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class MemoraDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "memora_v1.db"

        @Volatile
        private var instance: MemoraDatabase? = null

        fun getInstance(context: Context): MemoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoraDatabase::class.java,
                    DATABASE_NAME
                )
                // Destructive migration is strictly disabled to prevent accidental data loss.
                .fallbackToDestructiveMigration(false)
                .build().also { instance = it }
            }
        }
    }
}
