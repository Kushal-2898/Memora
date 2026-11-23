package app.memora.platform.billing

import android.app.Activity
import android.content.Context
import app.memora.data.local.OnboardingPreferences
import app.memora.domain.gateway.BillingGateway
import app.memora.domain.gateway.BillingProductOffer
import app.memora.domain.gateway.BillingProducts
import app.memora.domain.gateway.PurchaseRecord
import app.memora.platform.logging.AppLog
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Production Google Play Billing Library (7.x) implementation of [BillingGateway].
 * Supports auto-renewing subscriptions (USA / Global with free trials) and
 * prepaid UPI subscription passes (India) via Play Console regional pricing.
 */
class PlayBillingGateway(
    context: Context,
    private val onboardingPreferences: OnboardingPreferences,
) : BillingGateway, PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val cachedProducts = ConcurrentHashMap<String, ProductDetails>()

    @Volatile
    private var pendingPurchaseDeferred: CompletableDeferred<Result<Unit>>? = null

    private val _purchases = MutableStateFlow<List<PurchaseRecord>>(emptyList())
    override val purchases: Flow<List<PurchaseRecord>> = _purchases.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    private suspend fun ensureConnected(): Result<Unit> = connectMutex.withLock {
        if (billingClient.isReady) return Result.success(Unit)
        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (!cont.isActive) return
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        cont.resume(Result.success(Unit))
                    } else {
                        cont.resume(
                            Result.failure(
                                IllegalStateException(
                                    "Google Play Billing unavailable (${billingResult.responseCode}): ${billingResult.debugMessage}"
                                )
                            )
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) {
                        cont.resume(Result.failure(IllegalStateException("Google Play Billing service disconnected.")))
                    }
                }
            })
        }
    }

    override suspend fun queryAvailableProducts(): Result<List<String>> {
        return queryProductOffers().map { offers -> offers.map { it.productId } }
    }

    override suspend fun queryProductOffers(): Result<List<BillingProductOffer>> {
        val connected = ensureConnected()
        if (connected.isFailure) return Result.failure(connected.exceptionOrNull()!!)

        val productList = BillingProducts.ALL_SUBSCRIPTIONS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return Result.failure(
                IllegalStateException("Unable to load plans: ${result.billingResult.debugMessage}")
            )
        }

        val detailsList = result.productDetailsList.orEmpty()
        val offers = detailsList.mapNotNull { details ->
            cachedProducts[details.productId] = details
            extractBestOffer(details)
        }.sortedBy { offer ->
            // Always present Annual hero plan before Monthly
            if (offer.productId == BillingProducts.PRO_ANNUAL) 0 else 1
        }

        return Result.success(offers)
    }

    override suspend fun initiatePurchase(productId: String): Result<Unit> {
        return Result.failure(
            IllegalStateException("Use launchPurchase(activity, productId) to launch Google Play checkout.")
        )
    }

    override suspend fun launchPurchase(activity: Activity, productId: String): Result<Unit> {
        val connected = ensureConnected()
        if (connected.isFailure) return Result.failure(connected.exceptionOrNull()!!)

        var details = cachedProducts[productId]
        if (details == null) {
            queryProductOffers()
            details = cachedProducts[productId]
        }
        val productDetails = details ?: return Result.failure(
            IllegalStateException("Subscription plan '$productId' is not yet active on Google Play.")
        )
        val bestOffer = extractBestOffer(productDetails) ?: return Result.failure(
            IllegalStateException("No eligible offer found for '$productId' in your region.")
        )

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(bestOffer.offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val deferred = CompletableDeferred<Result<Unit>>()
        pendingPurchaseDeferred?.cancel()
        pendingPurchaseDeferred = deferred

        val launchResult = withContext(Dispatchers.Main) {
            billingClient.launchBillingFlow(activity, flowParams)
        }

        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseDeferred = null
            return Result.failure(
                IllegalStateException(launchResult.debugMessage.ifBlank { "Unable to launch Google Play checkout." })
            )
        }

        return deferred.await()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val deferred = pendingPurchaseDeferred
        pendingPurchaseDeferred = null

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val activePurchases = purchases.orEmpty()
                scope.launch {
                    val processResult = processPurchases(activePurchases)
                    deferred?.complete(processResult)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deferred?.complete(Result.failure(IllegalStateException("Purchase canceled.")))
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch {
                    val restored = restorePurchases()
                    deferred?.complete(restored)
                }
            }
            else -> {
                val msg = billingResult.debugMessage.ifBlank {
                    "Google Play Billing error (${billingResult.responseCode})"
                }
                AppLog.e("PlayBillingGateway", "onPurchasesUpdated error: $msg")
                deferred?.complete(Result.failure(IllegalStateException(msg)))
            }
        }
    }

    override suspend fun restorePurchases(): Result<Unit> {
        val connected = ensureConnected()
        if (connected.isFailure) return Result.failure(connected.exceptionOrNull()!!)

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return Result.failure(
                IllegalStateException("Failed to restore purchases: ${result.billingResult.debugMessage}")
            )
        }

        val purchased = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) {
            onboardingPreferences.setProUser(false)
            _purchases.value = emptyList()
            return Result.failure(IllegalStateException("No active Memora Pro subscription found."))
        }

        return processPurchases(purchased)
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> {
        val connected = ensureConnected()
        if (connected.isFailure) return Result.failure(connected.exceptionOrNull()!!)

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        val ackResult = billingClient.acknowledgePurchase(params)
        return if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to acknowledge purchase: ${ackResult.debugMessage}"))
        }
    }

    private suspend fun processPurchases(purchaseList: List<Purchase>): Result<Unit> {
        val records = mutableListOf<PurchaseRecord>()
        var hasActivePro = false

        for (purchase in purchaseList) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                var acknowledged = purchase.isAcknowledged
                if (!acknowledged) {
                    val ack = acknowledgePurchase(purchase.purchaseToken)
                    if (ack.isSuccess) {
                        acknowledged = true
                    } else {
                        AppLog.e("PlayBillingGateway", "Acknowledge failed: ${ack.exceptionOrNull()?.message}")
                    }
                }
                val productId = purchase.products.firstOrNull().orEmpty()
                if (productId in BillingProducts.ALL_SUBSCRIPTIONS) {
                    hasActivePro = true
                }
                records.add(
                    PurchaseRecord(
                        productId = productId,
                        purchaseToken = purchase.purchaseToken,
                        isAcknowledged = acknowledged,
                        isAutoRenewing = purchase.isAutoRenewing,
                    )
                )
            }
        }

        _purchases.value = records
        if (hasActivePro) {
            onboardingPreferences.setProUser(true)
            return Result.success(Unit)
        }
        return Result.failure(IllegalStateException("Purchase is pending confirmation from Google Play."))
    }

    private fun extractBestOffer(details: ProductDetails): BillingProductOffer? {
        val offers = details.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return null

        // Prefer an offer with a free trial phase (e.g. USA 7-day trial); otherwise take the primary base plan
        val chosen = offers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        } ?: offers.first()

        val phases = chosen.pricingPhases.pricingPhaseList
        val hasFreeTrial = phases.any { it.priceAmountMicros == 0L }
        val paidPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.lastOrNull() ?: return null
        val isPrepaid = paidPhase.recurrenceMode == ProductDetails.RecurrenceMode.NON_RECURRING

        val periodLabel = when (paidPhase.billingPeriod) {
            "P1M" -> if (isPrepaid) "1 month prepaid" else "per month"
            "P1Y" -> if (isPrepaid) "1 year prepaid" else "per year"
            "P3M" -> if (isPrepaid) "3 months prepaid" else "every 3 months"
            else -> if (isPrepaid) "prepaid pass" else "recurring"
        }

        val displayTitle = when (details.productId) {
            BillingProducts.PRO_ANNUAL -> "Memora Pro Annual"
            BillingProducts.PRO_MONTHLY -> "Memora Pro Monthly"
            else -> details.name.ifBlank { details.productId }
        }

        return BillingProductOffer(
            productId = details.productId,
            title = displayTitle,
            formattedPrice = paidPhase.formattedPrice,
            billingPeriodLabel = periodLabel,
            hasFreeTrial = hasFreeTrial,
            isPrepaid = isPrepaid,
            offerToken = chosen.offerToken,
        )
    }
}
