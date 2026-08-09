package com.vaibhav.moneycapture

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object NotificationPoster {

    private val executor =
        Executors.newSingleThreadExecutor()

    @Volatile
    private var networkCallbackRegistered = false

    fun post(
        context: Context,
        endpoint: String,
        secret: String,
        payload: TransactionPayload
    ) {
        val appContext = context.applicationContext

        registerNetworkCallback(appContext)

        executor.execute {

            val success = sendNow(
                endpoint = endpoint,
                secret = secret,
                payload = payload
            )

            if (!success) {

                OfflineCaptureQueue.add(
                    appContext,
                    QueuedTransaction(
                        packageName = payload.packageName,
                        appName = payload.appName,
                        title = payload.title,
                        text = payload.text,
                        postedAt = payload.postedAt,
                        notificationKey = payload.notificationKey
                    )
                )
            }
        }
    }

    private fun sendNow(
        endpoint: String,
        secret: String,
        payload: TransactionPayload
    ): Boolean {

        if (
            endpoint.isBlank() ||
            secret.isBlank()
        ) {
            return false
        }

        var conn: HttpURLConnection? = null

        return try {

            val json = """
                {
                    "secret": ${quote(secret)},
                    "source": "android_notification",
                    "packageName": ${quote(payload.packageName)},
                    "appName": ${quote(payload.appName)},
                    "title": ${quote(payload.title)},
                    "text": ${quote(payload.text)},
                    "postedAt": ${payload.postedAt},
                    "notificationKey": ${quote(payload.notificationKey)}
                }
            """.trimIndent()

            conn =
                (URL(endpoint)
                    .openConnection() as HttpURLConnection).apply {

                    requestMethod = "POST"

                    connectTimeout = 10000
                    readTimeout = 15000

                    doOutput = true

                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )
                }

            conn.outputStream.use {
                it.write(
                    json.toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode =
                conn.responseCode

            responseCode in 200..299

        } catch (
            _: Exception
        ) {

            false

        } finally {

            conn?.disconnect()
        }
    }

    private fun registerNetworkCallback(
        context: Context
    ) {

        if (networkCallbackRegistered) {
            return
        }

        synchronized(this) {

            if (networkCallbackRegistered) {
                return
            }

            try {

                val connectivityManager =
                    context.getSystemService(
                        Context.CONNECTIVITY_SERVICE
                    ) as ConnectivityManager

                val callback =
                    object : ConnectivityManager.NetworkCallback() {

                        override fun onAvailable(
                            network: Network
                        ) {

                            flushQueue(context)
                        }
                    }

                connectivityManager
                    .registerDefaultNetworkCallback(
                        callback
                    )

                networkCallbackRegistered = true

            } catch (
                _: Exception
            ) {
                // Queue will still protect transactions.
            }
        }
    }

    fun flushQueue(
        context: Context
    ) {

        val appContext =
            context.applicationContext

        executor.execute {

            val prefs =
                appContext.getSharedPreferences(
                    "capture",
                    Context.MODE_PRIVATE
                )

            val endpoint =
                prefs.getString(
                    "endpoint",
                    ""
                ).orEmpty()

            val secret =
                prefs.getString(
                    "secret",
                    ""
                ).orEmpty()

            if (
                endpoint.isBlank() ||
                secret.isBlank()
            ) {
                return@execute
            }

            val queued =
                OfflineCaptureQueue.getAll(
                    appContext
                )

            for (
                transaction in queued
            ) {

                val payload =
                    TransactionPayload(
                        packageName =
                            transaction.packageName,

                        appName =
                            transaction.appName,

                        title =
                            transaction.title,

                        text =
                            transaction.text,

                        postedAt =
                            transaction.postedAt,

                        notificationKey =
                            transaction.notificationKey
                    )

                val success =
                    sendNow(
                        endpoint = endpoint,
                        secret = secret,
                        payload = payload
                    )

                if (success) {

                    OfflineCaptureQueue.remove(
                        appContext,
                        transaction.notificationKey
                    )

                } else {

                    break
                }
            }
        }
    }

    private fun quote(
        value: String
    ): String {

        return "\"" +
                value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r") +
                "\""
    }
}
