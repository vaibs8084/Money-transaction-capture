package com.vaibhav.moneycapture

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QueuedTransaction(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val notificationKey: String
)

object OfflineCaptureQueue {

    private const val PREFS_NAME = "offline_capture_queue"
    private const val QUEUE_KEY = "transactions"

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    @Synchronized
    fun add(
        context: Context,
        transaction: QueuedTransaction
    ) {
        val current =
            getAll(context).toMutableList()

        /*
         * Never add the same notification twice.
         */
        if (
            current.any {
                it.notificationKey ==
                    transaction.notificationKey
            }
        ) {
            return
        }

        current.add(transaction)

        saveAll(
            context,
            current
        )
    }

    @Synchronized
    fun remove(
        context: Context,
        notificationKey: String
    ) {
        val remaining =
            getAll(context)
                .filter {
                    it.notificationKey !=
                        notificationKey
                }

        saveAll(
            context,
            remaining
        )
    }

    @Synchronized
    fun getAll(
        context: Context
    ): List<QueuedTransaction> {

        val raw =
            prefs(context)
                .getString(
                    QUEUE_KEY,
                    "[]"
                )
                ?: "[]"

        return try {

            val array =
                JSONArray(raw)

            val result =
                mutableListOf<QueuedTransaction>()

            for (
                i in 0 until array.length()
            ) {

                val item =
                    array.getJSONObject(i)

                result.add(
                    QueuedTransaction(
                        packageName =
                            item.optString(
                                "packageName"
                            ),

                        appName =
                            item.optString(
                                "appName"
                            ),

                        title =
                            item.optString(
                                "title"
                            ),

                        text =
                            item.optString(
                                "text"
                            ),

                        postedAt =
                            item.optLong(
                                "postedAt"
                            ),

                        notificationKey =
                            item.optString(
                                "notificationKey"
                            )
                    )
                )
            }

            result

        } catch (
            _: Exception
        ) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveAll(
        context: Context,
        transactions: List<QueuedTransaction>
    ) {

        val array =
            JSONArray()

        for (
            transaction in transactions
        ) {

            val item =
                JSONObject()

            item.put(
                "packageName",
                transaction.packageName
            )

            item.put(
                "appName",
                transaction.appName
            )

            item.put(
                "title",
                transaction.title
            )

            item.put(
                "text",
                transaction.text
            )

            item.put(
                "postedAt",
                transaction.postedAt
            )

            item.put(
                "notificationKey",
                transaction.notificationKey
            )

            array.put(item)
        }

        prefs(context)
            .edit()
            .putString(
                QUEUE_KEY,
                array.toString()
            )
            .apply()
    }

    fun count(
        context: Context
    ): Int {
        return getAll(context).size
    }
}
