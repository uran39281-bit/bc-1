# Black Ice Budget

Standalone, offline Android budget tracker. The launcher package is com.blackice.budget. Android 8+ and OpenGL ES 3.0 are required. The app stores its ledger in private device preferences. Clearing app data or uninstalling removes the ledger.

Add an account with its starting balance, then enter a subscription, payday, or expense. Select a calendar date to record a payment. The Budget screen supports a reserve for each period. Available cash includes all account balances less outstanding bills through the cutoff and the selected reserve. Expected income is displayed separately until marked received. Monthly renewals use the original day and clamp to the end of shorter months. A removed schedule retains its posted history.

The monthly charge chart runs through the BC-1 OpenGL ES 3.0 renderer and shaders from commit 969700ab65045f46323f01fdbdc9e732ce4a9161. Its viewport fills the chart strip. Android WebView renders the accessible forms and ledger. The app has no Internet permission. Reminders use approximate local 9 a.m. alarms and require Android notification permission.

## Build

Open the Budget directory in Android Studio, or use Gradle 8.11.1, JDK 17 and Android SDK 35. Run `gradle assembleDebug` from this directory. The debug APK is generated in build/outputs/apk/debug. Run `node verify.cjs` for ledger checks.
