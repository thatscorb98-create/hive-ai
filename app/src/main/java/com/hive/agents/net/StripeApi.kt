package com.hive.agents.net

import com.hive.agents.data.KeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

/**
 * Stripe, used one way only: a payment link is created at the moment a buyer is ready to pay.
 * There is deliberately no "pre-generate links" method, and the agent tool that calls this
 * always goes through an approval card first.
 */
class StripeApi(private val vault: KeyVault) {

    suspend fun createPaymentLink(
        amountCents: Long,
        currency: String,
        description: String,
        customerEmail: String = ""
    ): String {
        val key = vault.load().stripeSecretKey
        require(key.isNotBlank()) { "No Stripe key. Add one in Settings." }
        require(amountCents > 0) { "Amount must be positive." }

        val price = post(
            path = "/v1/prices",
            key = key,
            form = FormBody.Builder()
                .add("currency", currency.lowercase(Locale.US))
                .add("unit_amount", amountCents.toString())
                .add("product_data[name]", description.take(120))
                .build()
        )
        val priceId = JSONObject(price).optString("id")
        require(priceId.isNotBlank()) { "Stripe did not return a price id." }

        val link = post(
            path = "/v1/payment_links",
            key = key,
            form = FormBody.Builder()
                .add("line_items[0][price]", priceId)
                .add("line_items[0][quantity]", "1")
                .apply { if (customerEmail.isNotBlank()) add("customer_creation", "always") }
                .build()
        )
        val url = JSONObject(link).optString("url")
        require(url.isNotBlank()) { "Stripe did not return a link." }
        return url
    }

    private suspend fun post(path: String, key: String, form: FormBody): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.stripe.com$path")
                .header("Authorization", "Bearer $key")
                .post(form)
                .build()
            Http.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw HttpException(response.code, text)
                text
            }
        }
}
