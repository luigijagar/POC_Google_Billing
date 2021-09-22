package com.example.poc_google_billing

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.android.billingclient.api.*
import com.sample.poc_google_billing.R
import java.io.IOException

class MainActivity : AppCompatActivity() {
    lateinit var billingClient: BillingClient
    val activity = this
    private lateinit var txtItemName : TextView
    private lateinit var txtItemPrice : TextView
    private lateinit var btnItemBuy : Button
    private lateinit var btnGoToShoppingCard : Button

    private lateinit var btnConsume : Button

    val PRODUCT_ID = "aviso_adicional"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtItemName = findViewById(R.id.txtItemName)
        txtItemPrice = findViewById(R.id.txtItemPrice)
        btnItemBuy = findViewById(R.id.btnItemBuy)

        btnGoToShoppingCard = findViewById(R.id.btnGoToConsumable)

        btnConsume = findViewById(R.id.btnConsume)

        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases()
            .setListener(purchaseUpdateListener)
            .build()

        connectToGooglePlayBilling()

        btnGoToShoppingCard.setOnClickListener(View.OnClickListener {
            val intent = Intent(this, ShoppingCard::class.java)
            startActivity(intent)
        })

        btnConsume.setOnClickListener(View.OnClickListener {
            consumeProducts()
        })
    }

    private fun consumeProducts(){
        if(billingClient.isReady){
            billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, purchaseResponseListener)
        }
        else{
            Toast.makeText(this, "Product Not consumed, please try again!", Toast.LENGTH_LONG).show()
            connectToGooglePlayBilling()
        }
    }

    val purchaseResponseListener = PurchasesResponseListener { billingResult, purchases ->
        for(purchase in purchases) {
            Log.d("responseListener", " name ${purchase.orderId} estate: ${purchase.purchaseState}")
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient!!.consumeAsync(consumeParams, consumeListener)
        }
    }

    var consumeListener = ConsumeResponseListener { billingResult, purchaseToken ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Toast.makeText(applicationContext, "Item Consumed", Toast.LENGTH_SHORT).show()
        }
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

    /*private fun verifyPurchase(purchase: Purchase) {
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
    }*/

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
                    txtItemPrice.text = "Price: " + itemInfo.price
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
            //Log.d("handlePurchases", "purcharse $purchase id: ${purchase.skus}");
            var purchaseSku : String? = null
            if(purchase.skus.size == 1){
                purchaseSku = purchase.skus[0]
                Log.d("handlePurchases", "purcharse sku $purchaseSku")
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
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient!!.acknowledgePurchase(acknowledgePurchaseParams, ackPurchase)
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

    var ackPurchase = AcknowledgePurchaseResponseListener { billingResult ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            //if purchase is acknowledged
            // Grant entitlement to the user. and restart activity
            Toast.makeText(applicationContext, "Item Purchased", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

}