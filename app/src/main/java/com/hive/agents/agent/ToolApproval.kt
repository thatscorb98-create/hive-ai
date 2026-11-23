package com.hive.agents.agent

import org.json.JSONObject

/** Human-readable approval copy for risky tools. */
object ToolApproval {

    fun title(name: String, args: JSONObject): String = when (name) {
        "send_email" -> "Send email to ${args.optString("to")}"
        "create_payment_link" -> "Create a Stripe link for $${args.optLong("amount_cents") / 100.0}"
        "create_checkout" -> "Create a checkout link for ${args.optString("product")}"
        else -> "Run $name"
    }

    fun detail(name: String, args: JSONObject): String = when (name) {
        "send_email" -> "Subject: ${args.optString("subject")}\n\n${args.optString("body").take(600)}"
        "create_payment_link" ->
            "Amount: $${args.optLong("amount_cents") / 100.0}\nFor: ${args.optString("description")}\n\n" +
                "Stripe links are created only when a buyer is ready to pay."
        "create_checkout" ->
            "Product: ${args.optString("product")}\n\n" +
                "A checkout link is created only when a buyer is ready to pay."
        else -> args.toString()
    }

}
