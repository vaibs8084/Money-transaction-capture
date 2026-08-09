package com.vaibhav.moneycapture

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class MoneyNotificationListener : NotificationListenerService() {

    /*
     * Short-term duplicate protection for the same transaction.
     */
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

        val textParts =
            mutableListOf<String>()

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
            if (
                it.isNotBlank() &&
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

                if (
                    value.isNotBlank() &&
                    !textParts.contains(value)
                ) {
                    textParts.add(value)
                }
            }
        }

        val text =
            textParts.joinToString(" ").trim()

        if (
            title.isBlank() &&
            text.isBlank()
        ) {
            return
        }

        /*
         * Ignore notifications that do not look
         * like bank/payment transactions.
         */
        if (
            !looksLikeTransaction(
                title,
                text
            )
        ) {
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

        if (
            endpoint.isBlank() ||
            secret.isBlank()
        ) {
            return
        }

        /*
         * Create a stable identifier for this notification.
         *
         * sbn.key is the Android notification key.
         */
        val notificationKey =
            buildNotificationKey(
                sbn,
                title,
                text
            )

        /*
         * IMPORTANT:
         *
         * This check is persistent.
         *
         * Therefore:
         *
         * notification arrives
         * -> processed
         *
         * notification remains unread
         * -> Android posts it again
         * -> ignored
         *
         * app restarts
         * -> still ignored
         *
         * phone restarts
         * -> still ignored
         */
        if (
            wasAlreadyProcessed(
                notificationKey
            )
        ) {
            return
        }

        val now =
            System.currentTimeMillis()

        cleanupOldEntries(now)

        /*
         * Cross-source duplicate protection.
         *
         * Example:
         * Same ₹10 transaction arrives from
         * two notification sources.
         */
        val amount =
            extractAmount(text)

        val direction =
            transactionDirection(
                title,
                text
            )

        if (
            amount != null &&
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

                /*
                 * Even though this notification is
                 * a duplicate transaction, remember
                 * that this notification itself was
                 * already handled.
                 */
                markAsProcessed(
                    notificationKey
                )

                return
            }

            recentTransactions[
                transactionKey
            ] = now
        }

        /*
         * Mark the notification as processed BEFORE
         * sending it.
         *
         * NotificationPoster / OfflineCaptureQueue
         * handles the internet-offline situation.
         */
        markAsProcessed(
            notificationKey
        )

        NotificationPoster.post(
            context = this,
            endpoint = endpoint,
            secret = secret,
            payload = TransactionPayload(
                packageName = sbn.packageName,
                appName =
                    applicationNameFor(
                        sbn.packageName
                    ),
                title = title,
                text = text,
                postedAt = sbn.postTime,
                notificationKey =
                    notificationKey
            )
        )
    }

    /*
     * Build a stable notification identifier.
     */
    private fun buildNotificationKey(
        sbn: StatusBarNotification,
        title: String,
        text: String
    ): String {

        return buildString {

            append(sbn.key)

            append("|")

            append(sbn.packageName)

            append("|")

            append(title)

            append("|")

            append(text)
        }
    }

    /*
     * Check whether this notification was
     * already processed.
     */
    private fun wasAlreadyProcessed(
        key: String
    ): Boolean {

        val prefs =
            getSharedPreferences(
                PROCESSED_PREFS,
                Context.MODE_PRIVATE
            )

        val processed =
            prefs.getStringSet(
                PROCESSED_KEYS,
                emptySet()
            ) ?: emptySet()

        return processed.contains(key)
    }

    /*
     * Permanently remember that this notification
     * has already been processed.
     *
     * This uses SharedPreferences, so the information
     * survives app restart and phone restart.
     */
    private fun markAsProcessed(
        key: String
    ) {

        val prefs =
            getSharedPreferences(
                PROCESSED_PREFS,
                Context.MODE_PRIVATE
            )

        val processed =
            prefs.getStringSet(
                PROCESSED_KEYS,
                emptySet()
            )?.toMutableSet()
                ?: mutableSetOf()

        processed.add(key)

        prefs.edit()
            .putStringSet(
                PROCESSED_KEYS,
                processed
            )
            .apply()
    }

    /*
     * Remove only short-term transaction
     * duplicate records.
     *
     * Persistent notification records are NOT
     * removed here.
     */
    private fun cleanupOldEntries(
        now: Long
    ) {

        val cutoff =
            now - CACHE_WINDOW_MS

        recentTransactions.entries.removeIf {
            it.value < cutoff
        }
    }

    /*
     * Extract transaction amount.
     *
     * Supports:
     * ₹10
     * Rs 10
     * Rs.10
     * INR 10
     */
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

        return matcher
            .group(1)
            ?.replace(",", "")
    }

    /*
     * Determine whether transaction is debit
     * or credit.
     */
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

    /*
     * Decide whether notification looks like
     * a financial transaction.
     */
    private fun looksLikeTransaction(
        title: String,
        text: String
    ): Boolean {

        val value =
            "$title $text"
                .lowercase()

        /*
         * A transaction must contain an amount.
         */
        if (
            extractAmount(text) == null
        ) {
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

    /*
     * Get the readable application name.
     */
    private fun applicationNameFor(
        packageName: String
    ): String {

        return try {

            val info =
                packageManager.getApplicationInfo(
                    packageName,
                    0
                )

            packageManager
                .getApplicationLabel(info)
                .toString()

        } catch (_: Exception) {

            packageName
        }
    }

    companion object {

        /*
         * How long short-term transaction
         * duplicate protection should remain.
         */
        private const val CACHE_WINDOW_MS =
            15 * 60 * 1000L

        /*
         * Same amount + same direction within
         * 60 seconds is treated as duplicate.
         */
        private const val DUPLICATE_WINDOW_MS =
            60 * 1000L

        /*
         * SharedPreferences used for permanent
         * notification duplicate protection.
         */
        private const val PROCESSED_PREFS =
            "processed_notifications"

        private const val PROCESSED_KEYS =
            "keys"
    }
}
