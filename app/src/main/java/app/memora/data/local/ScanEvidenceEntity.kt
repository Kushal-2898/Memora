package app.memora.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Audit evidence record persisted whenever a scan is saved as a memory.
 *
 * Linked to [MemoryEntity] with ON DELETE CASCADE so deleting the memory
 * automatically purges its local audit record.
 */
@Entity(
    tableName = "scan_evidence",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ]
)
data class ScanEvidenceEntity(
    @PrimaryKey val memoryId: String,
    val scanId: String,
    val extractionSource: String,
    val rawOcrText: String,
    val localFieldsJson: String,
    val suggestedTitle: String?,
    val suggestedEventType: String?,
    val suggestedEventDate: String?,
    val confidence: Float,
    val finalTitle: String,
    val finalEventType: String,
    val finalEventDate: String,
    val compressedImageBytes: ByteArray?,
    val imageSha256: String?,
    val createdAtEpochMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanEvidenceEntity) return false
        return memoryId == other.memoryId &&
            scanId == other.scanId &&
            extractionSource == other.extractionSource &&
            rawOcrText == other.rawOcrText &&
            localFieldsJson == other.localFieldsJson &&
            suggestedTitle == other.suggestedTitle &&
            suggestedEventType == other.suggestedEventType &&
            suggestedEventDate == other.suggestedEventDate &&
            confidence == other.confidence &&
            finalTitle == other.finalTitle &&
            finalEventType == other.finalEventType &&
            finalEventDate == other.finalEventDate &&
            (compressedImageBytes?.contentEquals(other.compressedImageBytes) ?: (other.compressedImageBytes == null)) &&
            imageSha256 == other.imageSha256 &&
            createdAtEpochMs == other.createdAtEpochMs
    }

    override fun hashCode(): Int {
        var result = memoryId.hashCode()
        result = 31 * result + scanId.hashCode()
        result = 31 * result + (imageSha256?.hashCode() ?: 0)
        return result
    }
}

@Dao
interface ScanEvidenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScanEvidenceEntity)

    @Query("SELECT * FROM scan_evidence WHERE memoryId = :memoryId LIMIT 1")
    suspend fun getByMemoryId(memoryId: String): ScanEvidenceEntity?

    @Query("SELECT * FROM scan_evidence")
    suspend fun getAll(): List<ScanEvidenceEntity>

    @Query("DELETE FROM scan_evidence WHERE memoryId = :memoryId")
    suspend fun deleteByMemoryId(memoryId: String)
}
