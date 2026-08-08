package com.vaibhav.moneycapture

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("capture", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Money Transaction Capture"
            textSize = 24f
            setPadding(0, 0, 0, 20)
        }
        root.addView(title)

        val info = TextView(this).apply {
            text = "This app watches notifications from banks/UPI apps, filters likely transaction alerts locally, removes duplicates, and sends only transaction candidates to your Money Tracker."
            textSize = 15f
            setPadding(0, 0, 0, 20)
        }
        root.addView(info)

        val endpoint = EditText(this).apply {
            hint = "Money Tracker Web App URL"
            setSingleLine(true)
            setText(prefs.getString("endpoint", ""))
        }
        root.addView(endpoint)

        val secret = EditText(this).apply {
            hint = "Capture secret"
            setSingleLine(true)
            setInputType(0x00000081)
            setText(prefs.getString("secret", ""))
        }
        root.addView(secret)

        val save = Button(this).apply { text = "SAVE SETTINGS" }
        save.setOnClickListener {
            prefs.edit()
                .putString("endpoint", endpoint.text.toString().trim())
                .putString("secret", secret.text.toString().trim())
                .apply()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }
        root.addView(save)

        val access = Button(this).apply { text = "ENABLE NOTIFICATION ACCESS" }
        access.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        root.addView(access)

        val test = Button(this).apply { text = "SEND TEST TRANSACTION" }
        test.setOnClickListener {
            val url = endpoint.text.toString().trim()
            val token = secret.text.toString().trim()
            if (url.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "Enter URL and secret first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            NotificationPoster.post(
                context = this,
                endpoint = url,
                secret = token,
                payload = TransactionPayload(
                    packageName = "manual.test",
                    appName = "Money Capture Test",
                    title = "Test transaction",
                    text = "₹1 test transaction",
                    postedAt = System.currentTimeMillis(),
                    notificationKey = "manual-${System.currentTimeMillis()}"
                )
            )
            Toast.makeText(this, "Test queued", Toast.LENGTH_SHORT).show()
        }
        root.addView(test)

        val status = TextView(this).apply {
            text = "Status: notification listener runs only after Android grants Notification Access."
            textSize = 13f
            setPadding(0, 20, 0, 0)
        }
        root.addView(status)

        setContentView(root)
    }
}
