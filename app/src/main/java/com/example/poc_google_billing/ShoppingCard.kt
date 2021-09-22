package com.example.poc_google_billing

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.*
import com.example.poc_google_billing.adapters.Product
import com.example.poc_google_billing.adapters.ProductAdapter
import com.sample.poc_google_billing.R
import java.io.IOException

class ShoppingCard : AppCompatActivity() {
    var rv_inapp_products: RecyclerView? = null
    var lsProducts: MutableList<Product> = ArrayList()
    var adapter: ProductAdapter? = null
    val activity = this
    lateinit var billingClient: BillingClient
    var mapSkuDetails: MutableMap<String, SkuDetails> = mutableMapOf()

    var currentProductId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_card)

        rv_inapp_products = findViewById(R.id.rv_inapp_products)
        adapter = ProductAdapter(lsProducts)
        adapter?.setAdapterListener(listenerAdapter)

        var layoutManager = LinearLayoutManager(activity)

        rv_inapp_products?.layoutManager = layoutManager
        rv_inapp_products?.adapter = adapter
        rv_inapp_products?.itemAnimator = DefaultItemAnimator()

        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases()
            .setListener(purchaseUpdateListener)
            .build()

        connectToGooglePlayBilling()
    }

    private val purchaseUpdateListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Toast.makeText(activity, "Already owned", Toast.LENGTH_SHORT).show()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(activity, "Purchase canceled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                applicationContext,
                "Error " + billingResult.debugMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun connectToGooglePlayBilling() {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    connectToGooglePlayBilling()
                }

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        getProductsDetails()
                    }
                }
            }
        )
    }

    private fun getProductsDetails() {
        val productIds: List<String> = arrayListOf("destacado_especial", "aviso_adicional", "turbo")
        val getProductDetailsQuery = SkuDetailsParams.newBuilder()
            .setSkusList(productIds)
            .setType(BillingClient.SkuType.INAPP)
            .build()
        billingClient.querySkuDetailsAsync(
            getProductDetailsQuery,
            SkuDetailsResponseListener { billingResult, mutableList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && mutableList != null) {
                    for (skuDetail in mutableList) {
                        mapSkuDetails?.put(skuDetail.sku, skuDetail)
                        lsProducts.add(Product(skuDetail.title, skuDetail.sku, skuDetail.price))
                    }
                    adapter?.notifyDataSetChanged()
                }
            }
        )
    }

    var listenerAdapter: ProductAdapter.AdapterListener = object : ProductAdapter.AdapterListener {
        override fun onClick(item: Product) {
            var skuDetails = mapSkuDetails?.get(item.sku)
            if (skuDetails != null) {
                //Toast.makeText(activity, item.sku, Toast.LENGTH_SHORT).show()
                currentProductId = item.sku
                billingClient.launchBillingFlow(
                    activity,
                    BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build()
                )
            }
        }
    }

    //------ Verify client side

    private fun verifyValidSignature(signedData: String, signature: String): Boolean {
        return try {
            // To get key go to Developer Console > Select your app > Development Tools > Services & APIs.
            val base64Key =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtCfJBDhCvGZ/R9WdHnNBiGZZGDh0jnMPlcoWHSEmM5wm72T90VZw+x+iGLG40Ds0QWURByapr0+1m1wIeLgsc5ZdqG3MT2sSJOqPv0jJhyrXjRYEglDt3aWMDB1hZ6YtnwFAMrnLZGleNmRcT5b/l9YIbBinsMWTGKYdHy07zFUAzWFSovpghUoBzw7c34PEj67e9G6wNm86TbAxl21BfjnyCvUKLeQeqB8PJHZY6DL0jFoC4tpvNWM06CR/oFA4Nf/KwWQ0ODLpDwIYcgIIIRFRWg3vUR5KEttYQUN+E46XhQYIJW/WjZMuQPsprQjg2f6StoZEQL7LvlgTTkxcwQIDAQAB"
            Security.verifyPurchase(base64Key, signedData, signature)
        } catch (e: IOException) {
            false
        }
    }

    fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            //if item is purchased
            Log.d("handlePurchases", "purcharse $purchase id: ${purchase.skus}");
            var purchaseSku: String? = null
            if (purchase.skus.size == 1) {
                purchaseSku = purchase.skus[0]
            }

            if (purchaseSku != null) {
                if (currentProductId == purchaseSku && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                        // Invalid purchase
                        // show error to user

                        Toast.makeText(
                            applicationContext,
                            "Error : Invalid Purchase",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    // else purchase is valid
                    //if item is purchased and not acknowledged
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient!!.acknowledgePurchase(acknowledgePurchaseParams, ackPurchase)
                    }
                }
                //if purchase is pending
                else if (currentProductId == purchaseSku && purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    Toast.makeText(
                        applicationContext,
                        "Purchase is Pending. Please complete Transaction", Toast.LENGTH_SHORT
                    ).show()
                }
                //if purchase is refunded or unknown
                else if (currentProductId == purchaseSku && purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                    Toast.makeText(
                        applicationContext,
                        "Purchase Status Unknown",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    var ackPurchase = AcknowledgePurchaseResponseListener { billingResult ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            //if purchase is acknowledged
            // Grant entitlement to the user. and restart activity
            Toast.makeText(applicationContext, "Item Purchased", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }
}