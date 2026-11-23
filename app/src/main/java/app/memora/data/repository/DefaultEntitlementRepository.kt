package app.memora.data.repository

import app.memora.data.local.OnboardingPreferences
import app.memora.domain.model.Entitlement
import app.memora.domain.model.EntitlementStatus
import app.memora.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DefaultEntitlementRepository(
    private val onboardingPreferences: OnboardingPreferences? = null,
) : EntitlementRepository {

    private fun resolveEntitlement(): Entitlement {
        val isPro = onboardingPreferences?.isProUser() ?: false
        val remainingScans = onboardingPreferences?.getRemainingMonthlyScans(OnboardingPreferences.FREE_MONTHLY_SCAN_LIMIT)
            ?: OnboardingPreferences.FREE_MONTHLY_SCAN_LIMIT
        return Entitlement(
            status = if (isPro) EntitlementStatus.PREMIUM else EntitlementStatus.FREE,
            canScan = isPro || remainingScans > 0,
            canSave = true,
            productId = if (isPro) "memora_pro_tier" else null,
            source = if (isPro) "google_play_billing" else "free_tier"
        )
    }

    override fun observeEntitlement(): Flow<Entitlement> = flow {
        emit(resolveEntitlement())
    }

    override suspend fun getEntitlement(): Entitlement {
        return resolveEntitlement()
    }

    override suspend fun canScan(): Boolean {
        return resolveEntitlement().canScan
    }

    override suspend fun canSave(): Boolean {
        return true
    }
}
