package app.memora.domain.gateway

import kotlinx.coroutines.flow.Flow

/**
 * Represents an abstracted purchase transaction/receipt.
 */
data class PurchaseRecord(
    val productId: String,
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean
)

/**
 * Localized subscription offer details returned from Google Play Billing.
 */
data class BillingProductOffer(
    val productId: String,
    val title: String,
    val formattedPrice: String,
    val billingPeriodLabel: String,
    val hasFreeTrial: Boolean = false,
    val isPrepaid: Boolean = false,
    val offerToken: String = "",
)

object BillingProducts {
    const val PRO_MONTHLY = "memora_pro_monthly"
    const val PRO_ANNUAL = "memora_pro_annual"
    val ALL_SUBSCRIPTIONS = listOf(PRO_ANNUAL, PRO_MONTHLY)
}

/**
 * Platform-independent billing abstraction.
 * Represents Memora-level billing operations without depending on 
 * Google Play Billing SDK classes or Android UI elements.
 */
interface BillingGateway {
    val purchases: Flow<List<PurchaseRecord>>
    
    suspend fun queryAvailableProducts(): Result<List<String>>
    suspend fun queryProductOffers(): Result<List<BillingProductOffer>> = Result.success(emptyList())
    suspend fun initiatePurchase(productId: String): Result<Unit>
    suspend fun launchPurchase(activity: android.app.Activity, productId: String): Result<Unit> =
        initiatePurchase(productId)
    suspend fun restorePurchases(): Result<Unit>
    suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit>
}
