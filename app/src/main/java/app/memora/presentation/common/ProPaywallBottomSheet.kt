package app.memora.presentation.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memora.domain.gateway.BillingGateway
import app.memora.domain.gateway.BillingProductOffer
import app.memora.domain.gateway.BillingProducts
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.SecondaryText
import app.memora.ui.theme.SurfaceCool
import app.memora.ui.theme.TextCaption
import kotlinx.coroutines.launch

/**
 * Shared Material 3 Pro Paywall sheet backed by Google Play Billing.
 * Displays live localized prices from Play Store (USD, INR, GBP, EUR, etc.)
 * with zero hardcoded currency strings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallBottomSheet(
    billingGateway: BillingGateway?,
    onDismiss: () -> Unit,
    onProUnlocked: () -> Unit,
    headline: String = "Upgrade to Memora Pro",
    subtitle: String = "Unlock unlimited ML Kit & Gemini AI scans every month plus custom categories.",
    onAddManually: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var offers by remember { mutableStateOf<List<BillingProductOffer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedProductId by remember { mutableStateOf(BillingProducts.PRO_ANNUAL) }
    var isPurchasing by remember { mutableStateOf(false) }

    LaunchedEffect(billingGateway) {
        isLoading = true
        loadError = null
        val result = billingGateway?.queryProductOffers()
        if (result == null) {
            loadError = "Google Play Billing is not available on this device."
        } else if (result.isSuccess) {
            val loaded = result.getOrDefault(emptyList())
            offers = loaded
            if (loaded.isNotEmpty() && loaded.none { it.productId == selectedProductId }) {
                selectedProductId = loaded.first().productId
            }
            if (loaded.isEmpty()) {
                loadError = "Subscription plans are loading from Google Play. Please try again shortly."
            }
        } else {
            loadError = result.exceptionOrNull()?.message ?: "Unable to connect to Google Play."
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        modifier = Modifier.testTag("pro_paywall_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF214A34),
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = headline,
                    style = MemoraTypography.title.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                    color = PrimaryNavy,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MemoraTypography.body.copy(fontSize = 14.sp),
                color = TextCaption,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Benefits
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaywallBenefitRow("Unlimited camera & gallery scans (ML Kit + Gemini AI)")
                PaywallBenefitRow("Unlimited custom categories & reminder tags")
                PaywallBenefitRow("Cancel or top up anytime via Google Play")
            }

            Spacer(modifier = Modifier.height(20.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = PrimaryNavy, strokeWidth = 2.5.dp)
                    }
                }
                offers.isNotEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        offers.forEach { offer ->
                            val isSelected = offer.productId == selectedProductId
                            val isAnnual = offer.productId == BillingProducts.PRO_ANNUAL
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProductId = offer.productId }
                                    .testTag("plan_option_${offer.productId}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFF3F7F4) else SurfaceCool,
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF214A34) else BorderLight,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = offer.title,
                                                style = MemoraTypography.headline.copy(fontSize = 15.sp),
                                                color = PrimaryNavy,
                                            )
                                            if (isAnnual) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF214A34),
                                                ) {
                                                    Text(
                                                        text = if (offer.hasFreeTrial) "7-Day Free Trial" else "Best Value",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (offer.isPrepaid) {
                                                "${offer.billingPeriodLabel} · No auto-debit"
                                            } else {
                                                offer.billingPeriodLabel
                                            },
                                            style = MemoraTypography.caption,
                                            color = TextCaption,
                                        )
                                    }

                                    Text(
                                        text = offer.formattedPrice,
                                        style = MemoraTypography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                        color = PrimaryNavy,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCool,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = loadError ?: "Plans unavailable right now.",
                            style = MemoraTypography.caption,
                            color = TextCaption,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val hostActivity = activity
                    if (hostActivity == null || billingGateway == null) {
                        Toast.makeText(context, "Google Play Billing is unavailable.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isPurchasing = true
                    coroutineScope.launch {
                        val result = billingGateway.launchPurchase(hostActivity, selectedProductId)
                        isPurchasing = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Memora Pro activated!", Toast.LENGTH_SHORT).show()
                            onProUnlocked()
                            onDismiss()
                        } else {
                            val msg = result.exceptionOrNull()?.message ?: "Purchase could not be completed."
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isPurchasing && !isLoading && offers.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryNavy,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_paywall_subscribe"),
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    val selected = offers.firstOrNull { it.productId == selectedProductId }
                    Text(
                        text = when {
                            selected?.hasFreeTrial == true -> "Start 7-Day Free Trial"
                            selected != null -> "Continue · ${selected.formattedPrice}"
                            else -> "Continue with Google Play"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val restored = billingGateway?.restorePurchases()
                            if (restored?.isSuccess == true) {
                                Toast.makeText(context, "Memora Pro restored!", Toast.LENGTH_SHORT).show()
                                onProUnlocked()
                                onDismiss()
                            } else {
                                Toast.makeText(
                                    context,
                                    restored?.exceptionOrNull()?.message ?: "No active subscription found.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text("Restore Purchases", color = SecondaryText, fontSize = 13.sp)
                }

                if (onAddManually != null) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onAddManually()
                        },
                    ) {
                        Text("Add Manually", color = PrimaryNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PaywallBenefitRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF214A34),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MemoraTypography.body.copy(fontSize = 13.sp),
            color = PrimaryNavy,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
