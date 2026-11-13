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
        ScanEvidenceEntity::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class MemoraDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun scanEvidenceDao(): ScanEvidenceDao

    companion object {
        private const val DATABASE_NAME = "memora_v1.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_evidence` (
                        `memoryId` TEXT NOT NULL,
                        `scanId` TEXT NOT NULL,
                        `extractionSource` TEXT NOT NULL,
                        `rawOcrText` TEXT NOT NULL,
                        `localFieldsJson` TEXT NOT NULL,
                        `suggestedTitle` TEXT,
                        `suggestedEventType` TEXT,
                        `suggestedEventDate` TEXT,
                        `confidence` REAL NOT NULL,
                        `finalTitle` TEXT NOT NULL,
                        `finalEventType` TEXT NOT NULL,
                        `finalEventDate` TEXT NOT NULL,
                        `compressedImageBytes` BLOB,
                        `imageSha256` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`memoryId`),
                        FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: MemoraDatabase? = null

        fun getInstance(context: Context): MemoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoraDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                // Destructive migration is strictly disabled to prevent accidental data loss.
                .fallbackToDestructiveMigration(false)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // PRAGMA is a safe no-op inside a transaction, unlike setForeignKeyConstraintsEnabled().
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build().also { instance = it }
            }
        }
    }
}
