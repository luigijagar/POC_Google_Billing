package com.example.poc_google_billing
import com.sample.poc_google_billing.R
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.android.billingclient.api.*
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.io.IOException

class Consumable : AppCompatActivity() {
    lateinit var billingClient: BillingClient
    val activity = this
    private lateinit var txtItemName : TextView
    private lateinit var txtItemPrice : TextView
    private lateinit var btnItemBuy : Button
    private val PRODUCT_ID = "turbo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consumable)

        txtItemName = findViewById(R.id.txtItemName)
        txtItemPrice = findViewById(R.id.txtItemPrice)
        btnItemBuy = findViewById(R.id.btnItemBuy)

        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases()
            .setListener(purchaseUpdateListener)
            .build()

        connectToGooglePlayBilling()
    }

    private val purchaseUpdateListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            /*for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                    verifyPurchase(purchase) // Server side

                }
            }*/
            handlePurchases(purchases)
        }
        else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Toast.makeText(activity, "Already owned", Toast.LENGTH_SHORT).show()
        }
        else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(activity, "Purchase canceled", Toast.LENGTH_SHORT).show()
        }
        else {
            Toast.makeText(applicationContext, "Error " + billingResult.debugMessage, Toast.LENGTH_SHORT).show()
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

    private fun verifyPurchase(purchase: Purchase) {
        val requestUrl = "https://us-central1-pocplaybilling.cloudfunctions.net/verifyPurchases?" +
                "purchaseToken=" + purchase.purchaseToken + "&" +
                "purchaseTime=" + purchase.purchaseTime + "&" +
                "orderId=" + purchase.orderId

        val stringRequest = StringRequest(
            Request.Method.POST,
            requestUrl,
            Response.Listener { },
            Response.ErrorListener { })

        Volley.newRequestQueue(this).add(stringRequest)
    }

    private fun getProductsDetails() {
        val productIds: List<String> = arrayListOf(PRODUCT_ID)
        val getProductDetailsQuery = SkuDetailsParams.newBuilder()
            .setSkusList(productIds)
            .setType(BillingClient.SkuType.INAPP)
            .build()
        billingClient.querySkuDetailsAsync(
            getProductDetailsQuery,
            SkuDetailsResponseListener { billingResult, mutableList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && mutableList != null) {

                    var itemInfo: SkuDetails = mutableList[0]
                    txtItemName.text = itemInfo.title
                    txtItemPrice.text = itemInfo.price

                    btnItemBuy.setOnClickListener {
                        billingClient.launchBillingFlow(
                            activity,
                            BillingFlowParams.newBuilder().setSkuDetails(itemInfo).build()
                        )
                    }
                }
            }
        )
    }

    //------ Verify client side

    private fun verifyValidSignature(signedData: String, signature: String): Boolean {
        return try {
            // To get key go to Developer Console > Select your app > Development Tools > Services & APIs.
            val base64Key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtCfJBDhCvGZ/R9WdHnNBiGZZGDh0jnMPlcoWHSEmM5wm72T90VZw+x+iGLG40Ds0QWURByapr0+1m1wIeLgsc5ZdqG3MT2sSJOqPv0jJhyrXjRYEglDt3aWMDB1hZ6YtnwFAMrnLZGleNmRcT5b/l9YIbBinsMWTGKYdHy07zFUAzWFSovpghUoBzw7c34PEj67e9G6wNm86TbAxl21BfjnyCvUKLeQeqB8PJHZY6DL0jFoC4tpvNWM06CR/oFA4Nf/KwWQ0ODLpDwIYcgIIIRFRWg3vUR5KEttYQUN+E46XhQYIJW/WjZMuQPsprQjg2f6StoZEQL7LvlgTTkxcwQIDAQAB"
            Security.verifyPurchase(base64Key, signedData, signature)
        } catch (e: IOException) {
            false
        }
    }

    fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            //if item is purchased
            var purchaseSku : String? = null
            if(purchase.skus.size == 1){
                Log.d("handlePurchases", "purcharse sku ${purchase.skus[0]}")
                purchaseSku = purchase.skus[0]
            }

            if(purchaseSku!=null) {
                if (PRODUCT_ID == purchaseSku && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
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
                        val consumeParams = ConsumeParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient!!.consumeAsync(consumeParams, consumeListener)
                    }
                }
                //if purchase is pending
                else if (PRODUCT_ID == purchaseSku && purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    Toast.makeText(
                        applicationContext,
                        "Purchase is Pending. Please complete Transaction", Toast.LENGTH_SHORT
                    ).show()
                }
                //if purchase is refunded or unknown
                else if (PRODUCT_ID == purchaseSku && purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                    Toast.makeText(
                        applicationContext,
                        "Purchase Status Unknown",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    var consumeListener = ConsumeResponseListener { billingResult, purchaseToken ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Toast.makeText(applicationContext, "Item Consumed", Toast.LENGTH_SHORT).show()
        }
    }

}