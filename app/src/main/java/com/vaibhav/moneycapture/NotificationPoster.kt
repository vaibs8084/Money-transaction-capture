package com.vaibhav.moneycapture

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object NotificationPoster {
    private val executor = Executors.newSingleThreadExecutor()

    fun post(
        context: Context,
        endpoint: String,
        secret: String,
        payload: TransactionPayload
    ) {
        executor.execute {
            try {
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

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                    it.write(json.toByteArray(Charsets.UTF_8))
                }

                conn.inputStream.use {
                    it.readBytes()
                }

                conn.disconnect()
            } catch (_: Exception) {
                // First version is deliberately fail-silent:
                // no transaction is created if the endpoint is unreachable.
            }
        }
    }

    private fun quote(value: String): String {
        return "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") +
            "\""
    }
}
