# FinTrack: private, on-device personal finance tracker for Android

FinTrack reads bank, UPI and credit-card alert SMS, turns them into transactions, and shows where your money goes. Everything is stored in an encrypted database on your phone. There is no cloud sync, no account, no analytics, and no tracking SDK.

- Kotlin, Jetpack Compose, Material 3
- Room on SQLCipher (AES-256) for storage
- Android 12+ (minSdk 31, targetSdk 35)

## Features

- **Adaptive SMS parsing.** A layered, bank-agnostic parser detects debits and credits from most Indian banks, UPI apps and card networks. There are no per-bank templates.
- **Review queue.** Messages the parser is unsure about are never guessed or silently dropped. They go to a review list where you confirm or dismiss them.
- **Auto-categorisation.** Keyword rules sort transactions into food, shopping, bills, transport and more. You can recategorise any transaction.
- **Manual entry.** Add, edit and delete transactions yourself.
- **Dashboard.** Monthly spend, a category donut chart, income vs expense, budgets and recent activity.
- **Insights.** Weekly and monthly trends, category comparisons such as "Food up 20%", and local "reduce spending" suggestions. Suggestions cover recurring subscriptions, frequent small spends, rising categories and budget overspend.
- **Budgets.** Set a monthly limit per category, with progress bars and overspend alerts.
- **Optional AI summary.** Add your own OpenAI API key to get a written monthly summary and saving tips, on demand only.
- **Your data.** Export to CSV, or wipe everything.

## Security and privacy

| Area | What the app does |
|---|---|
| Database | Encrypted with SQLCipher. A random 256-bit key is created per install and kept in EncryptedSharedPreferences backed by the Android Keystore. `secure_delete` is on, so cleared rows are overwritten. |
| API key | Stored only in EncryptedSharedPreferences using AES-256-GCM with a Keystore master key. Never hardcoded, logged or exported. |
| SMS | Only messages from alphanumeric sender IDs such as `VM-HDFCBK` are read. Personal messages from phone numbers are skipped. Only parsed fields are stored. Raw text is kept only for messages waiting in the review queue, and is deleted when you resolve them. |
| Network | The only network call in the codebase goes to `https://api.openai.com`, and only when you tap **Generate summary**. Cleartext HTTP is disabled app-wide. |
| Data sent to OpenAI | Category totals, counts, budgets, and this and last month's totals. No merchant names, SMS text, bank names or account numbers. Settings has a **What is sent?** button that shows the exact payload. |
| Backups | `allowBackup=false` and data-extraction rules exclude everything from cloud backup and device-to-device transfer. |
| Screen | `FLAG_SECURE` blocks screenshots, screen recording and the recents preview. |
| Release build | R8 minification is on, and all `android.util.Log` calls are stripped. |
| Third parties | Only AndroidX and Google libraries, plus SQLCipher. No Firebase, analytics, crash reporting or ads. |
| CSV export | Written to a location you pick through the system file picker. Cells are escaped against spreadsheet formula injection. |

Two things are outside the app's control. An exported CSV is plain text, so treat it like a bank statement. If you use the AI feature, OpenAI's own data policy applies to the aggregated numbers you send.

## Project structure

```
app/src/main/java/com/pft/financetracker/
├── data/
│   ├── local/        Room entities, DAOs, encrypted database, mappers
│   ├── prefs/        Settings and encrypted API key storage
│   ├── repository/   Transaction and budget repositories
│   └── sms/          Inbox reader, live SMS receiver, import pipeline
├── domain/
│   ├── model/        Transaction, Category (with keyword lists), Budget
│   ├── parser/       Layered SMS parser (pure Kotlin, unit tested)
│   ├── categorize/   Rule-based categoriser
│   ├── insights/     Summaries, trends, suggestions, budget status
│   ├── ai/           OpenAI client and aggregated payload builder
│   └── export/       CSV exporter
└── ui/               Compose theme, charts, navigation, screens
```

## How the SMS parser works

The parser lives in `domain/parser`. It has no Android dependencies, so it runs as a plain JVM unit test.

1. **Filters** drop OTPs, promotions, future or scheduled debits, failed transactions, payment requests and statement reminders.
2. **Type detection** scores debit and credit keywords such as "debited", "spent", "paid to", "credited" and "refund".
3. **Amount extraction** handles `Rs`, `Rs.`, `INR` and `₹` before or after the number, plus Indian digit grouping. It ignores amounts that follow "Avl bal" or "limit".
4. **Account extraction** keeps only the last four digits, from forms like `XX1234`, `a/c **1234` or `card ending 1234`.
5. **Bank detection** reads the sender ID or message body, and falls back to the sender ID itself.
6. **Merchant extraction** understands UPI handles, `UPI/P2M/...`, `Info:`, `at X`, `to X` and `from X`.
7. **Date extraction** reads a date from the body in many formats, and falls back to the SMS timestamp.

Each result gets a confidence score. Results below 60 go to the review queue instead of being saved.

**Adding a new edge case** usually means one line in the right layer:

- A new ignore rule: append to `TextFilters.ignoreRules`.
- A new merchant shape: add a regex to `MerchantExtractor.patterns` at the right priority.
- A new bank: append to `BankExtractor.knownIssuers`.
- A new category keyword: add it to the list on the `Category` enum.

Add a matching case in `app/src/test/.../SmsParserTest.kt` whenever you add a pattern.

## Building

### Requirements

- JDK 17
- Android SDK with platform 35 and build-tools 35. Android Studio Ladybug or newer installs these for you.

### With Android Studio

Open the project folder, let Gradle sync, then use **Build › Build APK(s)**.

### From the command line

Create `local.properties` in the project root pointing at your SDK. This file is git-ignored.

```properties
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
```

Run the parser tests:

```bash
./gradlew testDebugUnitTest
```

Build a debug APK, written to `app/build/outputs/apk/debug/app-debug.apk`:

```bash
./gradlew assembleDebug
```

Build a release APK, written to `app/build/outputs/apk/release/app-release.apk`:

```bash
./gradlew assembleRelease
```

On Windows use `gradlew.bat` instead of `./gradlew`.

### Release signing

Out of the box, the release build is signed with the debug key so it installs for personal use. To distribute it, create your own keystore and replace `signingConfig` in `app/build.gradle.kts` with a release config. Never commit keystores. `*.jks`, `*.keystore` and `keystore.properties` are already git-ignored.

### Installing

Enable installing from unknown sources on your phone, then copy the APK over, or run:

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## Using the app

1. On first launch, read the SMS explanation, then allow access or skip to manual entry.
2. The app imports the last 12 months of transaction SMS. New messages are imported automatically while auto-import is on.
3. Check the **Activity** tab badge for messages that need review.
4. Set monthly limits in **Budgets**.
5. Optionally, paste an OpenAI API key in **Settings**, then tap **Generate summary**.
