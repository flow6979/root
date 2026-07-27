package com.rootapp.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.rootapp.data.SettingsStore

/**
 * Play Billing wrapper for the Root Premium subscription.
 *
 * Lifecycle: create with remember { BillingManager(ctx) { ... } }, call [start] from a
 * DisposableEffect and [close] on dispose. It exposes Compose state ([available],
 * [priceText]) so the UI can show the real price and enable the buy button only when
 * Google Play + the configured product are actually reachable.
 *
 * Security note (MVP): a successful, acknowledged purchase flips the LOCAL premium flag
 * (SettingsStore.premium), which the whole app reads. It only ever GRANTS here and never
 * force-revokes, so it can't clobber an admin/server grant. For production, verify the
 * purchase token server-side (or via RevenueCat) before granting, and mirror the result
 * into the Supabase entitlements table so admins see Play subscribers too.
 */
class BillingManager(
    private val context: Context,
    private val onPremiumGranted: () -> Unit = {},
) {
    /** True once Play is connected and the subscription product is available to buy. */
    var available by mutableStateOf(false)
        private set

    /** Localised price of the base subscription offer, e.g. "$2.99", once known. */
    var priceText by mutableStateOf<String?>(null)
        private set

    private val settings = SettingsStore(context)
    private var productDetails: ProductDetails? = null
    private var offerToken: String? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private val client = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    fun start() {
        if (client.isReady) { queryProduct(); restorePurchases(); return }
        runCatching {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryProduct()
                        restorePurchases()
                    } else {
                        Log.w(TAG, "setup ${result.responseCode}: ${result.debugMessage}")
                        available = false
                    }
                }

                override fun onBillingServiceDisconnected() { available = false }
            })
        }.onFailure { Log.w(TAG, "startConnection failed: ${it.message}"); available = false }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            ).build()
        client.queryProductDetailsAsync(params) { result, list ->
            val pd = list.firstOrNull()
            if (result.responseCode == BillingClient.BillingResponseCode.OK && pd != null) {
                productDetails = pd
                val offer = pd.subscriptionOfferDetails?.firstOrNull()
                offerToken = offer?.offerToken
                priceText = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                available = offerToken != null
            } else {
                Log.w(TAG, "product query ${result.responseCode}; is '$PRODUCT_ID' configured?")
                available = false
            }
        }
    }

    /** Restore an existing active subscription (e.g. after reinstall / new device). */
    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { _, purchases -> purchases.forEach { handlePurchase(it) } }
    }

    fun purchase(activity: Activity) {
        val pd = productDetails ?: return
        val token = offerToken ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .setOfferToken(token)
                        .build(),
                ),
            ).build()
        runCatching { client.launchBillingFlow(activity, params) }
            .onFailure { Log.w(TAG, "launchBillingFlow failed: ${it.message}") }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // Grant immediately so the app unlocks, then acknowledge so Google doesn't auto-refund.
        settings.premium = true
        onPremiumGranted()
        if (!purchase.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(ack) { r ->
                if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "acknowledge failed ${r.responseCode}")
                }
            }
        }
    }

    fun close() { runCatching { client.endConnection() } }

    companion object {
        /** Must match the subscription product id created in Play Console -> Monetize. */
        const val PRODUCT_ID = "root_premium_monthly"
        private const val TAG = "Billing"
    }
}
