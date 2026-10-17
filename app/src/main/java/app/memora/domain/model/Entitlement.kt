package app.memora.domain.model

import java.time.Instant

enum class EntitlementStatus {
    FREE,
    PREMIUM,
    EXPIRED
}

/**
 * Core domain representation of the user's current monetization and access state.
 * This class must remain isolated from any specific payment provider SDK.
 */
data class Entitlement(
    val status: EntitlementStatus,
    val canScan: Boolean,
    val canSave: Boolean,
    val productId: String? = null,
    val expiresAt: Instant? = null,
    val source: String? = null
)
