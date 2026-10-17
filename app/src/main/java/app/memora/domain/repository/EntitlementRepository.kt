package app.memora.domain.repository

import app.memora.domain.model.Entitlement
import kotlinx.coroutines.flow.Flow

/**
 * The application's entitlement authority.
 * Determines what the user is allowed to access.
 */
interface EntitlementRepository {
    fun observeEntitlement(): Flow<Entitlement>
    suspend fun getEntitlement(): Entitlement
    suspend fun canScan(): Boolean
    suspend fun canSave(): Boolean
}
