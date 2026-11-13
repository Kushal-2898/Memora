package app.memora.data.sync

import app.memora.data.local.MemoryDao
import app.memora.data.local.ReminderDao
import app.memora.data.local.ScanEvidenceDao
import app.memora.data.local.ScanEvidenceEntity
import app.memora.data.local.toDomain
import app.memora.data.local.toEntity
import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.repository.AuthRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages bidirectional synchronization of user memories and reminders
 * between local Room SQLite and Cloud Firestore.
 */
class FirestoreSyncManager(
    private val memoryDao: MemoryDao,
    private val reminderDao: ReminderDao,
    private val authRepository: AuthRepository,
    private val notificationScheduler: app.memora.platform.notification.NotificationScheduler? = null,
    private val scanEvidenceDao: ScanEvidenceDao? = null,
    private val firestore: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private fun isValidCloudUser(user: app.memora.domain.model.AuthUser?): Boolean {
        if (user == null || user.isAnonymous || user.uid.isBlank()) return false
        if (user.uid.startsWith("guest") || user.uid.startsWith("email_") || user.uid.startsWith("google_")) return false
        return true
    }

    /**
     * Performs a bidirectional sync between local SQLite and remote Firestore.
     * Only executes when an authenticated user (with real Firebase UID) is signed in.
     */
    suspend fun sync(): Result<Int> = withContext(ioDispatcher) {
        val user = authRepository.currentUser.value ?: return@withContext Result.success(0)
        if (!isValidCloudUser(user)) {
            return@withContext Result.success(0)
        }
        val db = firestore ?: return@withContext Result.success(0)

        runCatching {
            var syncedCount = 0
            val userMemoriesCollection = db.collection("users").document(user.uid).collection("memories")

            // 1. FETCH REMOTE SNAPSHOT FIRST to prevent overwriting newer cloud edits with stale local data
            val remoteDocs = userMemoriesCollection.get().await()
            val remoteUpdatedById = HashMap<String, Long>(remoteDocs.size())

            // 2. PULL: Apply remote memories that are missing locally or newer than local copy
            for (doc in remoteDocs.documents) {
                val id = doc.getString("id") ?: doc.id
                val remoteUpdatedAt = doc.getLong("updatedAtEpochMilli") ?: 0L
                remoteUpdatedById[id] = remoteUpdatedAt

                val existingLocal = memoryDao.getByIdWithReminders(id)
                if (existingLocal == null || remoteUpdatedAt > existingLocal.memory.updatedAtEpochMs) {
                    val title = doc.getString("title")?.trim()?.take(120) ?: continue
                    val eventTypeStr = doc.getString("eventType") ?: "OTHER"
                    val eventType = runCatching { EventType.valueOf(eventTypeStr) }.getOrDefault(EventType.OTHER)
                    val eventDateStr = doc.getString("eventDate") ?: continue
                    val eventDate = runCatching { LocalDate.parse(eventDateStr) }.getOrNull() ?: continue
                    val timeZoneId = doc.getString("timeZoneId") ?: "UTC"
                    val description = doc.getString("description")
                    val createdAt = Instant.ofEpochMilli(doc.getLong("createdAtEpochMilli") ?: System.currentTimeMillis())
                    val updatedAt = Instant.ofEpochMilli(remoteUpdatedAt)

                    val memory = Memory(
                        id = id,
                        title = title,
                        eventType = eventType,
                        eventDate = eventDate,
                        timeZoneId = timeZoneId,
                        description = description,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )

                    @Suppress("UNCHECKED_CAST")
                    val remindersList = doc.get("reminders") as? List<Map<String, Any?>> ?: emptyList()
                    val firstReminderMap = remindersList.firstOrNull()
                    val initialReminder = if (firstReminderMap != null) {
                        val rId = firstReminderMap["id"] as? String ?: java.util.UUID.randomUUID().toString()
                        val rDateStr = firstReminderMap["reminderStartDate"] as? String ?: eventDateStr
                        val rDate = runCatching { LocalDate.parse(rDateStr) }.getOrDefault(eventDate)
                        val rTimeStr = firstReminderMap["reminderTime"] as? String ?: "09:00"
                        val rTime = runCatching { LocalTime.parse(rTimeStr) }.getOrDefault(LocalTime.of(9, 0))
                        val statusStr = firstReminderMap["status"] as? String ?: "UPCOMING"
                        val status = runCatching { ReminderStatus.valueOf(statusStr) }.getOrDefault(ReminderStatus.UPCOMING)
                        val completedAtMilli = firstReminderMap["completedAtEpochMilli"] as? Long
                        val completedAt = completedAtMilli?.let { Instant.ofEpochMilli(it) }
                        Reminder(
                            id = rId,
                            memoryId = id,
                            reminderStartDate = rDate,
                            reminderTime = rTime,
                            status = status,
                            completedAt = completedAt,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                        )
                    } else null

                    if (initialReminder != null) {
                        if (existingLocal == null) {
                            memoryDao.insertMemoryWithReminder(memory.toEntity(), initialReminder.toEntity())
                        } else {
                            memoryDao.updateMemoryWithReminders(memory.toEntity(), listOf(initialReminder.toEntity()))
                        }
                        if (initialReminder.completedAt == null) {
                            notificationScheduler?.scheduleReminder(
                                memoryId = memory.id,
                                reminderId = initialReminder.id,
                                title = memory.title,
                                reminderStartDate = initialReminder.reminderStartDate,
                                eventDate = memory.eventDate,
                                timeZoneId = memory.timeZoneId,
                                reminderTime = initialReminder.reminderTime ?: LocalTime.of(9, 0),
                            )
                        } else {
                            notificationScheduler?.cancelReminder(initialReminder.id)
                        }
                    } else {
                        if (existingLocal == null) {
                            memoryDao.insert(memory.toEntity())
                        } else {
                            memoryDao.update(memory.toEntity())
                        }
                    }
                    syncedCount++
                }
            }

            // 3. PUSH: Upload local memories that are missing remotely or newer than remote copy (chunked <= 450 per Firestore limit)
            val localRelations = memoryDao.observeAllWithReminders().first()
            val toPush = localRelations.filter { relation ->
                val remoteUpdated = remoteUpdatedById[relation.memory.id]
                remoteUpdated == null || relation.memory.updatedAtEpochMs > remoteUpdated
            }
            for (chunk in toPush.chunked(450)) {
                val batch = db.batch()
                for (relation in chunk) {
                    val mem = relation.toDomain()
                    val docRef = userMemoriesCollection.document(mem.memory.id)
                    val data = hashMapOf(
                        "id" to mem.memory.id,
                        "title" to mem.memory.title,
                        "eventType" to mem.memory.eventType.name,
                        "eventDate" to mem.memory.eventDate.toString(),
                        "timeZoneId" to mem.memory.timeZoneId,
                        "description" to (mem.memory.description ?: ""),
                        "createdAtEpochMilli" to mem.memory.createdAt.toEpochMilli(),
                        "updatedAtEpochMilli" to mem.memory.updatedAt.toEpochMilli(),
                        "reminders" to mem.reminders.map { r ->
                            hashMapOf(
                                "id" to r.id,
                                "reminderStartDate" to r.reminderStartDate.toString(),
                                "reminderTime" to (r.reminderTime?.toString() ?: "09:00"),
                                "status" to r.status.name,
                                "completedAtEpochMilli" to r.completedAt?.toEpochMilli(),
                                "createdAtEpochMilli" to r.createdAt.toEpochMilli(),
                                "updatedAtEpochMilli" to r.updatedAt.toEpochMilli(),
                            )
                        }
                    )
                    batch.set(docRef, data)
                    syncedCount++
                }
                batch.commit().await()
            }

            // 4. PUSH SCAN EVIDENCE: Upload any local scan evidence records not yet backed up
            val evidenceList = scanEvidenceDao?.getAll().orEmpty()
            if (evidenceList.isNotEmpty()) {
                val evidenceCollection = db.collection("users").document(user.uid).collection("scan_evidence")
                for (evidence in evidenceList) {
                    runCatching {
                        pushEvidenceInternal(evidenceCollection.document(evidence.memoryId), evidence)
                    }
                }
            }

            syncedCount
        }
    }

    /**
     * Uploads a single [ScanEvidenceEntity] record (metadata, SHA-256 hash, and compressed image blob)
     * to Cloud Firestore under `users/{uid}/scan_evidence/{memoryId}`.
     */
    suspend fun pushScanEvidence(evidence: ScanEvidenceEntity): Result<Unit> = withContext(ioDispatcher) {
        val user = authRepository.currentUser.value ?: return@withContext Result.success(Unit)
        if (!isValidCloudUser(user)) {
            return@withContext Result.success(Unit)
        }
        val db = firestore ?: return@withContext Result.success(Unit)
        runCatching {
            val docRef = db.collection("users")
                .document(user.uid)
                .collection("scan_evidence")
                .document(evidence.memoryId)
            pushEvidenceInternal(docRef, evidence)
        }
    }

    private suspend fun pushEvidenceInternal(
        docRef: com.google.firebase.firestore.DocumentReference,
        evidence: ScanEvidenceEntity,
    ) {
        val data = hashMapOf<String, Any?>(
            "memoryId" to evidence.memoryId,
            "scanId" to evidence.scanId,
            "extractionSource" to evidence.extractionSource,
            "rawOcrText" to evidence.rawOcrText,
            "localFieldsJson" to evidence.localFieldsJson,
            "suggestedTitle" to evidence.suggestedTitle,
            "suggestedEventType" to evidence.suggestedEventType,
            "suggestedEventDate" to evidence.suggestedEventDate,
            "confidence" to evidence.confidence.toDouble(),
            "finalTitle" to evidence.finalTitle,
            "finalEventType" to evidence.finalEventType,
            "finalEventDate" to evidence.finalEventDate,
            "imageSha256" to evidence.imageSha256,
            "compressedImageBlob" to evidence.compressedImageBytes?.let { Blob.fromBytes(it) },
            "createdAtEpochMs" to evidence.createdAtEpochMs,
        )
        docRef.set(data).await()
    }

    /**
     * Deletes a single memory document and its scan evidence from Cloud Firestore when deleted locally,
     * preventing resurrection on the next bidirectional sync.
     */
    suspend fun deleteRemoteMemory(memoryId: String): Result<Unit> = withContext(ioDispatcher) {
        val user = authRepository.currentUser.value ?: return@withContext Result.success(Unit)
        if (!isValidCloudUser(user)) {
            return@withContext Result.success(Unit)
        }
        val db = firestore ?: return@withContext Result.success(Unit)
        runCatching {
            val userDoc = db.collection("users").document(user.uid)
            userDoc.collection("memories").document(memoryId).delete().await()
            runCatching {
                userDoc.collection("scan_evidence").document(memoryId).delete().await()
            }
            Unit
        }
    }

    /**
     * Permanently purges all cloud-synced memories, scan evidence, and the user document in Firestore
     * prior to Firebase Auth account deletion (DPDP Act Section 12 / Play Store Policy).
     */
    suspend fun deleteAllRemoteData(): Result<Unit> = withContext(ioDispatcher) {
        val user = authRepository.currentUser.value ?: return@withContext Result.success(Unit)
        if (!isValidCloudUser(user)) {
            return@withContext Result.success(Unit)
        }
        val db = firestore ?: return@withContext Result.success(Unit)
        runCatching {
            val userDoc = db.collection("users").document(user.uid)
            for (subcollection in listOf("memories", "scan_evidence")) {
                val docs = userDoc.collection(subcollection).get().await()
                if (!docs.isEmpty) {
                    for (chunk in docs.documents.chunked(450)) {
                        val batch = db.batch()
                        for (doc in chunk) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                    }
                }
            }
            userDoc.delete().await()
            Unit
        }
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
