# Build the APK without Android Studio

This project includes a GitHub Actions workflow that builds a debug APK in the cloud.

## Steps

1. Create/sign in to a GitHub account.
2. Create a new repository, e.g. `MoneyTransactionCapture`.
3. Upload the **contents of this project folder** to the repository (not the outer ZIP folder).
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab.
6. Select **Build Android APK**.
7. Click **Run workflow** and run it on `main`.
8. Wait for the workflow to finish with a green check.
9. Open the completed workflow run.
10. At the bottom, under **Artifacts**, download `MoneyTransactionCapture-debug-apk`.
11. Unzip the downloaded artifact and install `app-debug.apk` on the phone.

The workflow uses GitHub's hosted Linux runner, Java 17, Gradle 8.10.2, and the Android SDK already available on the runner.

## Important

This produces a **debug APK** for testing. It is not a Play Store release build.

Do not put your Gemini API key in the Android project.
