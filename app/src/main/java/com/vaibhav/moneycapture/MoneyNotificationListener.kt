package com.vaibhav.moneycapture

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class MoneyNotificationListener : NotificationListenerService() {

    private val seenNotifications =
        ConcurrentHashMap<String, Long>()

    private val recentTransactions =
        ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title =
            extras.getCharSequence(
                Notification.EXTRA_TITLE
            )?.toString()?.trim().orEmpty()

        val textParts = mutableListOf<String>()

        extras.getCharSequence(
            Notification.EXTRA_TEXT
        )?.toString()?.trim()?.let {
            if (it.isNotBlank()) {
                textParts.add(it)
            }
        }

        extras.getCharSequence(
            Notification.EXTRA_BIG_TEXT
        )?.toString()?.trim()?.let {
            if (it.isNotBlank() &&
                !textParts.contains(it)
            ) {
                textParts.add(it)
            }
        }

        val lines =
            extras.getCharSequenceArray(
                Notification.EXTRA_TEXT_LINES
            )

        if (lines != null) {
            for (line in lines) {
                val value =
                    line?.toString()?.trim().orEmpty()

                if (value.isNotBlank() &&
                    !textParts.contains(value)
                ) {
                    textParts.add(value)
                }
            }
        }

        val text =
            textParts.joinToString(" ").trim()

        if (title.isBlank() && text.isBlank()) {
            return
        }

        if (!looksLikeTransaction(title, text)) {
            return
        }

        val prefs =
            getSharedPreferences(
                "capture",
                Context.MODE_PRIVATE
            )

        val endpoint =
            prefs.getString(
                "endpoint",
                ""
            )?.trim().orEmpty()

        val secret =
            prefs.getString(
                "secret",
                ""
            )?.trim().orEmpty()

        if (endpoint.isBlank() ||
            secret.isBlank()
        ) {
            return
        }

        val now =
            System.currentTimeMillis()

        /*
         * Exact notification duplicate protection.
         *
         * Two identical deliveries of the same
         * Android notification will only be posted once.
         */
        val notificationKey =
            buildNotificationKey(
                sbn,
                title,
                text
            )

        cleanupOldEntries(now)

        if (
            seenNotifications.putIfAbsent(
                notificationKey,
                now
            ) != null
        ) {
            return
        }

        /*
         * Cross-source duplicate protection.
         *
         * Some banks send the same transaction through
         * both their app notification and another
         * notification channel.
         *
         * We use amount + debit/credit direction for a
         * short window so the same transaction is not
         * counted twice.
         */
        val amount =
            extractAmount(text)

        val direction =
            transactionDirection(
                title,
                text
            )

        if (amount != null &&
            direction.isNotBlank()
        ) {
            val transactionKey =
                "$amount|$direction"

            val previous =
                recentTransactions[
                    transactionKey
                ]

            if (
                previous != null &&
                now - previous < DUPLICATE_WINDOW_MS
            ) {
                return
            }

            recentTransactions[
                transactionKey
            ] = now
        }

        NotificationPoster.post(
            context = this,
            endpoint = endpoint,
            secret = secret,
            payload = TransactionPayload(
                packageName = sbn.packageName,
                appName = applicationNameFor(
                    sbn.packageName
                ),
                title = title,
                text = text,
                postedAt = sbn.postTime,
                notificationKey = notificationKey
            )
        )
    }

    private fun buildNotificationKey(
        sbn: StatusBarNotification,
        title: String,
        text: String
    ): String {
        return buildString {
            append(sbn.packageName)
            append("|")
            append(sbn.id)
            append("|")
            append(sbn.postTime)
            append("|")
            append(title)
            append("|")
            append(text)
        }
    }

    private fun cleanupOldEntries(
        now: Long
    ) {
        val cutoff =
            now - CACHE_WINDOW_MS

        seenNotifications.entries.removeIf {
            it.value < cutoff
        }

        recentTransactions.entries.removeIf {
            it.value < cutoff
        }
    }

    private fun extractAmount(
        text: String
    ): String? {
        val pattern =
            Pattern.compile(
                "(?:₹|rs\\.?|inr\\s*)\\s*([0-9]+(?:\\.[0-9]{1,2})?)",
                Pattern.CASE_INSENSITIVE
            )

        val matcher =
            pattern.matcher(text)

        if (!matcher.find()) {
            return null
        }

        return matcher.group(1)
            ?.replace(",", "")
    }

    private fun transactionDirection(
        title: String,
        text: String
    ): String {
        val value =
            "$title $text"
                .lowercase()

        return when {
            containsAny(
                value,
                listOf(
                    "debited",
                    "debit",
                    "spent",
                    "paid",
                    "payment sent",
                    "sent"
                )
            ) -> "debit"

            containsAny(
                value,
                listOf(
                    "credited",
                    "credit",
                    "received",
                    "cashback",
                    "refund"
                )
            ) -> "credit"

            else -> ""
        }
    }

    private fun looksLikeTransaction(
        title: String,
        text: String
    ): Boolean {
        val value =
            "$title $text"
                .lowercase()

        if (extractAmount(text) == null) {
            return false
        }

        val transactionWords =
            listOf(
                "debit",
                "debited",
                "credit",
                "credited",
                "received",
                "sent",
                "spent",
                "paid",
                "payment",
                "transaction",
                "txn",
                "upi",
                "bank",
                "transfer",
                "a/c",
                "account"
            )

        return transactionWords.any {
            value.contains(it)
        }
    }

    private fun containsAny(
        text: String,
        words: List<String>
    ): Boolean {
        return words.any {
            text.contains(it)
        }
    }

    private fun applicationNameFor(
        packageName: String
    ): String {
        return try {
            val info =
                packageManager.getApplicationInfo(
                    packageName,
                    0
                )

            packageManager.getApplicationLabel(
                info
            ).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    companion object {
        private const val CACHE_WINDOW_MS =
            15 * 60 * 1000L

        private const val DUPLICATE_WINDOW_MS =
            60 * 1000L
    }
}
