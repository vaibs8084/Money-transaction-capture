# Money Transaction Capture — Android companion

## What this first version does

1. Uses Android `NotificationListenerService`.
2. Reads notification title/body only after you grant Notification Access.
3. Locally filters for notifications containing a rupee/INR amount plus transaction-like words.
4. De-duplicates notifications in memory.
5. Sends the candidate notification to your Money Tracker Apps Script endpoint.
6. It does NOT save a transaction directly from Android.

## Setup

1. Open this project in Android Studio.
2. Let Gradle sync.
3. Build/install on the Android phone.
4. Open the app.
5. Enter your deployed Money Tracker Web App URL.
6. Enter the shared capture secret configured in Apps Script.
7. Save settings.
8. Tap `ENABLE NOTIFICATION ACCESS`.
9. Enable `Money Transaction Capture`.
10. Use `SEND TEST TRANSACTION` after the Apps Script endpoint is ready.

## Security

Do not put your Gemini API key in this Android app. The Android app sends only the notification candidate to your Apps Script backend. Gemini stays server-side.

## Current scope

This is the capture layer first. It does not yet automatically categorize or save transactions. The next backend layer will validate the notification, run the existing Gemma 4 transaction understanding, perform duplicate protection, and present a confirmation in the Money Tracker UI.
