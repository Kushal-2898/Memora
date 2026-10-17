package app.memora.domain.extraction

import app.memora.domain.model.EventType
import java.time.LocalDate

/** Temporal status of a date candidate relative to current reference date (today). */
enum class TemporalStatus {
    PAST,
    TODAY,
    NEAR_FUTURE, // within 30 days
    FUTURE,      // within 1-5 years
    FAR_FUTURE,  // > 5 years
    UNKNOWN
}

/** Breakdown of candidate scoring for full auditability and explainability. */
data class CandidateScoreComponents(
    val semanticBaseScore: Float = 0f,
    val temporalPlausibilityScore: Float = 0f,
    val precisionBonus: Float = 0f,
    val domainPrecedenceBonus: Float = 0f,
    val penalty: Float = 0f,
    val totalScore: Float = 0f,
)

/** Domain role of a date candidate distinguishing actionable reminders from historical context. */
enum class DateRole(val isActionableReminder: Boolean) {
    ACTIONABLE_EXPIRATION(true),
    ACTIONABLE_BEST_BEFORE(true),
    ACTIONABLE_DUE_DATE(true),
    ACTIONABLE_WARRANTY_END(true),
    ACTIONABLE_RETURN_DEADLINE(true),
    ACTIONABLE_SUBSCRIPTION_END(true),
    HISTORICAL_MANUFACTURE(false),
    HISTORICAL_PURCHASE(false),
    HISTORICAL_ISSUANCE_BILL(false),
    UNLABELLED_DATE(true),
    OTHER(false);

    companion object {
        fun fromEventType(eventType: EventType): DateRole = when (eventType) {
            EventType.DUE_DATE -> ACTIONABLE_DUE_DATE
            EventType.EXPIRATION -> ACTIONABLE_EXPIRATION
            EventType.BEST_BEFORE -> ACTIONABLE_BEST_BEFORE
            EventType.WARRANTY_END -> ACTIONABLE_WARRANTY_END
            EventType.RETURN_DEADLINE -> ACTIONABLE_RETURN_DEADLINE
            EventType.SUBSCRIPTION_END -> ACTIONABLE_SUBSCRIPTION_END
            EventType.OTHER -> OTHER
        }
    }
}

/** A parsed date candidate with semantic role, evidence, geometry, temporal status, and score components. */
data class ExtractedDateCandidate(
    val eventType: EventType,
    val eventDate: LocalDate?,
    val confidence: Float,
    val context: String,
    val hasExplicitDay: Boolean,
    val role: DateRole = DateRole.fromEventType(eventType),
    val evidenceText: String = context,
    val boundingBox: android.graphics.Rect? = null,
    val lineIndex: Int? = null,
    val temporalStatus: TemporalStatus = TemporalStatus.UNKNOWN,
    val scoreComponents: CandidateScoreComponents = CandidateScoreComponents(),
    val explanation: String = "",
)

enum class ExtractionDisposition {
    HIGH_CONFIDENCE,
    LOW_CONFIDENCE,
    AMBIGUOUS,
    NO_ACTIONABLE_DATE,
}

enum class PackagingType(val displayName: String) {
    TUBE("Tube"),
    CAN("Can / Tin"),
    BOTTLE("Bottle / Jug"),
    BLISTER_PACK("Blister Pack"),
    CARTON_BOX("Carton Box"),
    POUCH("Flexible Pouch"),
    DOCUMENT("Document / Receipt"),
    UNKNOWN("Standard Package"),
}

enum class DateLocation(val displayName: String) {
    CRIMP_SEAL("Top Crimp Strip"),
    CAN_BOTTOM("Underside Rim"),
    BOTTLE_NECK("Bottle Neck / Shoulder"),
    END_FLAP("Box End Flap"),
    LABEL("Printed Label"),
    UNKNOWN("Package Body"),
}

enum class MarkingStyle(val displayName: String) {
    EMBOSSED_NO_INK("Embossed into plastic (no ink)"),
    DOT_MATRIX("Dot-matrix inkjet"),
    LASER_ETCHED("Laser-etched"),
    PRINTED("Printed typeset"),
    UNKNOWN("Standard marking"),
}

/** Pure OCR text result separated from local semantic extraction. */
data class OcrResult(
    val rawText: String,
    val confidence: Float? = null,
)

/** Structured, deterministic on-device extraction result presented to user before AI confirmation. */
data class LocalExtractionResult(
    val title: String?,
    val eventType: EventType?,
    val selectedEventDate: LocalDate?,
    val dateType: String?,
    val dateCandidates: List<ExtractedDateCandidate>,
    val fields: Map<String, String>,
    val confidence: Float,
    val isAmbiguous: Boolean,
    val packagingType: PackagingType = PackagingType.UNKNOWN,
    val dateLocation: DateLocation = DateLocation.UNKNOWN,
    val markingStyle: MarkingStyle = MarkingStyle.UNKNOWN,
    val explanation: String? = null,
)

/** Combined OCR and local extraction output from on-device analysis. */
data class LocalScanOutput(
    val ocrResult: OcrResult,
    val localResult: LocalExtractionResult,
)

/** Complete Stage 3 output; contains no image bytes or durable image reference. */
data class Stage3ExtractionResult(
    val candidate: ExtractedMemoryCandidate,
    val rawOcrText: String,
    val normalizedText: String,
    val disposition: ExtractionDisposition,
    val alternatives: List<ExtractedDateCandidate> = emptyList(),
)

/** Extraction candidate holding structured metadata parsed from OCR text. */
data class ExtractedMemoryCandidate(
    val suggestedTitle: String? = null,
    val suggestedEventType: EventType = EventType.OTHER,
    val suggestedEventDate: LocalDate? = null,
    val confidence: Float = 0f,
    val rawTextSnippet: String? = null,
    val requiresConfirmation: Boolean = true,
    val alternatives: List<ExtractedDateCandidate> = emptyList(),
)

interface MemoryExtractor {
    suspend fun extractCandidate(rawText: String, imageUri: android.net.Uri? = null): Result<ExtractedMemoryCandidate>
    suspend fun extractCandidate(rawText: String, imageUri: android.net.Uri? = null, scanId: String? = null): Result<ExtractedMemoryCandidate> =
        extractCandidate(rawText, imageUri)
}
