package it.venturini.poppy

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams

/** Mance (prodotti consumabili): tip_small, tip_medium, tip_large da creare in Play Console. */
class Billing(private val activity: Activity, private val onThanks: () -> Unit) : PurchasesUpdatedListener {

    var tips by mutableStateOf<List<ProductDetails>>(emptyList())
        private set

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) loadTips()
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun loadTips() {
        val products = listOf("tip_small", "tip_medium", "tip_large").map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                tips = list.sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L }
            }
        }
    }

    fun buy(p: ProductDetails) {
        val pp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(p).build()
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pp)).build()
        client.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        for (p in purchases) {
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val cp = ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                client.consumeAsync(cp) { res, _ ->
                    if (res.responseCode == BillingClient.BillingResponseCode.OK) onThanks()
                }
            }
        }
    }

    fun close() {
        client.endConnection()
    }
}
