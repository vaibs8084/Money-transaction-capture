package com.vaibhav.moneycapture

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class MoneyNotificationListener : NotificationListenerService() {

    private val seen = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!lines.isNullOrEmpty()) {
                append(" ")
                append(lines.joinToString(" ") { it.toString() })
            }
            val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            if (big.isNotBlank()) {
                append(" ")
                append(big)
            }
        }.trim()

        if (!looksLikeTransaction(title, text)) return

        val key = "${sbn.packageName}|${sbn.id}|${sbn.postTime}|${title}|${text}"
        if (seen.putIfAbsent(key, System.currentTimeMillis()) != null) return

        // Keep only a bounded in-memory cache.
        if (seen.size > 300) {
            val cutoff = System.currentTimeMillis() - 15 * 60 * 1000L
            seen.entries.removeIf { it.value < cutoff }
        }

        val prefs = getSharedPreferences("capture", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "").orEmpty()
        val secret = prefs.getString("secret", "").orEmpty()
        if (endpoint.isBlank() || secret.isBlank()) return

        NotificationPoster.post(
            context = this,
            endpoint = endpoint,
            secret = secret,
            payload = TransactionPayload(
                packageName = sbn.packageName,
                appName = title.ifBlank { sbn.packageName },
                title = title,
                text = text,
                postedAt = sbn.postTime,
                notificationKey = key
            )
        )
    }

    private fun looksLikeTransaction(title: String, text: String): Boolean {
        val s = (title + " " + text).lowercase()

        val amount =
            Pattern.compile(
                """(?:₹|rs\.?|inr)\s*[\d,]+(?:\.\d{1,2})?""",
                Pattern.CASE_INSENSITIVE
            ).matcher(s).find()

        if (!amount) return false

        val moneyWords = listOf(
            "debited", "credited", "debit", "credit", "paid", "payment",
            "received", "sent", "spent", "transaction", "upi", "bank",
            "account", "a/c", "transferred", "transfer"
        )

        return moneyWords.any { s.contains(it) }
    }
}
