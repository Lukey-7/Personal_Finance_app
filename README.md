# FinTrack: private, on-device personal finance tracker for Android

FinTrack reads bank, UPI and credit-card alert SMS, turns them into transactions, and shows where your money goes. Everything is stored in an encrypted database on your phone. There is no cloud sync, no account, no analytics, and no tracking SDK.

- Kotlin, Jetpack Compose, Material 3
- Room on SQLCipher (AES-256) for storage
- Android 12+ (minSdk 31, targetSdk 35)

## Download

Get the latest signed APK from the [Releases page](https://github.com/Lukey-7/Personal_Finance_app/releases/latest). Each release lists a SHA-256 checksum so you can verify the file.

APKs are split by CPU: take **arm64-v8a** for any phone from the last several years (~22 MB). The
`universal` APK works everywhere but is three times the size, because the on-device OCR model ships once
per architecture.

## Features

- **Adaptive SMS parsing.** A layered, bank-agnostic parser detects debits and credits from most Indian banks, UPI apps and card networks. There are no per-bank templates.
- **Review queue.** Messages the parser is unsure about are never guessed or silently dropped. They go to a review list where you confirm or dismiss them.
- **Auto-categorisation.** Keyword rules sort transactions into food, shopping, bills, transport and more. You can recategorise any transaction.
- **Manual entry.** Add, edit and delete transactions yourself.
- **Dashboard you can audit.** Net spend = gross spend - refunds, savings = income - net spend, shown with the arithmetic. Transfers, card-bill payments and investments are listed separately and never counted as spend. Every number is tappable and opens the transactions behind it. Period picker, daily average, month-end projection, top merchants, per-account totals.
- **Correct counting.** Amounts are stored as paise (integers). One payment reported by two SMS (bank + UPI app) is stored once, matched by reference number or by amount within ten minutes. Every transaction has an editable *flow* (expense, income, refund, transfer, investment, cash, settlement) that decides how it is counted.
- **SMS log.** Every scanned message with what happened to it and why. Message text is not stored; tapping a row re-reads it from the inbox.
- **Bill split with on-device OCR.** Photograph or pick a bill; ML Kit's bundled text recogniser reads it offline. Edit the extracted total and items, add people, split equally / by shares / custom / by item with proportional tax, and only your share counts as your spend. Tracks who owes whom and settlements.
- **Insights.** Weekly and monthly trends, category comparisons such as "Food up 20%", and local "reduce spending" suggestions. Suggestions cover recurring subscriptions, frequent small spends, rising categories and budget overspend.
- **Budgets.** Set a monthly limit per category, with progress bars and overspend alerts.
- **Optional AI summary.** Add your own OpenAI API key to get a written monthly summary and saving tips, on demand only.
- **Clean up duplicates.** Finds the same payment stored twice by an older version and shows exactly what it would remove before deleting anything.
- **Your data.** Export to CSV, or wipe everything.

## Security and privacy

| Area | What the app does |
|---|---|
| Database | Encrypted with SQLCipher. A random 256-bit key is created per install and kept in EncryptedSharedPreferences backed by the Android Keystore. `secure_delete` is on, so cleared rows are overwritten. |
| API key | Stored only in EncryptedSharedPreferences using AES-256-GCM with a Keystore master key. Never hardcoded, logged or exported. |
| SMS | Only messages from alphanumeric sender IDs such as `VM-HDFCBK` are read. Personal messages from phone numbers are skipped. Only parsed fields are stored. Raw text is kept only for messages waiting in the review queue, and is deleted when you resolve them. The SMS log stores sender, time, outcome, reason and amount, never the body. |
| Bill photos | Read on the phone by ML Kit Text Recognition with the **bundled** model (`com.google.mlkit:text-recognition`): no model download, works in airplane mode. The camera capture goes to a temp file in app-private cache and is deleted after recognition; gallery images are read through the system Photo Picker without a storage permission. No image is stored. ML Kit is a Google library; recognition needs no network, but like any Google Play services component it may report usage metrics through Play services if your device allows that. |
| Network | The only network call in the codebase goes to `https://api.openai.com`, and only when you tap **Generate summary**. Cleartext HTTP is disabled app-wide. |
| Data sent to OpenAI | Category totals, counts, budgets, and this and last month's totals. No merchant names, SMS text, bank names or account numbers. Settings has a **What is sent?** button that shows the exact payload. |
| Backups | `allowBackup=false` and data-extraction rules exclude everything from cloud backup and device-to-device transfer. |
| Screen | `FLAG_SECURE` blocks screenshots, screen recording and the recents preview. |
| Release build | R8 minification is on, and all `android.util.Log` calls are stripped. |
| Screenshots | `FLAG_SECURE` is set in release builds. Debug builds leave it off so the UI can be captured for documentation. |
| Third parties | Only AndroidX and Google libraries, plus SQLCipher. No Firebase, analytics, crash reporting or ads. |
| CSV export | Written to a location you pick through the system file picker. Cells are escaped against spreadsheet formula injection. |

Two things are outside the app's control. An exported CSV is plain text, so treat it like a bank statement. If you use the AI feature, OpenAI's own data policy applies to the aggregated numbers you send.

## Design

The interface follows the Buro reference kept in `stitch_buro_fintech_app/`: a warm off-white page,
white cards separated by hairlines rather than shadows, a single saturated blue for anything actionable,
and figures treated as the hero element with tight tracking. Colours were sampled from those screens.
[Inter](https://rsms.me/inter/) is bundled under the SIL Open Font License (`licenses/Inter-OFL.txt`).

## Project structure

```
app/src/main/java/com/pft/financetracker/
├── data/
│   ├── local/        Room entities, DAOs, encrypted database + migrations, mappers
│   ├── prefs/        Settings and encrypted API key storage
│   ├── repository/   Transaction and budget repositories
│   └── sms/          Inbox reader, live SMS receiver, import pipeline
├── domain/
│   ├── model/        Transaction, Category (with keyword lists), Budget
│   ├── parser/       Layered SMS parser incl. reference + flow classification (pure Kotlin, unit tested)
│   ├── categorize/   Rule-based categoriser
│   ├── insights/     Net/gross/refund summaries, drill-downs, trends, suggestions, budget status
│   ├── split/        Bill split models and integer-paise calculator
│   ├── ocr/          Receipt text -> structured bill parser
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
7. **Date extraction** reads a date from the body in many formats, and falls back to the SMS timestamp (keeping the SMS time of day when the body has only a date).
8. **Reference extraction** picks up `UPI Ref No`, `IMPS Ref`, `RRN`, `Txn ID`, `UTR` for duplicate detection.
9. **Flow classification** decides whether the transaction is an expense, income, refund, transfer (incl. credit-card bill payments and self-transfers), investment or cash withdrawal.

Each result gets a confidence score. Results below 60 go to the review queue instead of being saved.

**Adding a new edge case** usually means one line in the right layer:

- A new ignore rule: append to `TextFilters.ignoreRules`.
- A new merchant shape: add a regex to `MerchantExtractor.patterns` at the right priority.
- A new bank: append to `BankExtractor.knownIssuers`.
- A new category keyword: add it to the list on the `Category` enum.

Add a matching case in `app/src/test/.../SmsParserTest.kt` whenever you add a pattern.

## How the numbers are calculated

- **Spend** counts transactions with flow `EXPENSE` (and `CASH` unless turned off in Settings).
- **Refunds** (`REFUND` flow) are subtracted from spend, and from the matching category when the merchant matches a spend in the same period.
- **Income** counts only `INCOME`. **Transfers**, **investments** and split **settlements** are listed separately.
- **Duplicates:** an SMS is skipped if its hash (sender + normalised body + day) was seen, if its reference number matches a stored transaction of the same direction, or if the same amount and direction was stored within ten minutes by a different bank/app. The richer record (merchant, account, reference) is kept and the other is logged as a duplicate.
- **Splits:** only your share is your expense. If you paid and an SMS debit for the full amount exists, that transaction is trimmed to your share; otherwise your share is recorded as a `SPLIT` expense. Money friends pay back is a `SETTLEMENT`, not income.

Every rule above has a unit test under `app/src/test`. Run them with `testDebugUnitTest`.

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

Build a debug APK, written to `app/build/outputs/apk/debug/FinTrack-v1.1.0-debug.apk`:

```bash
./gradlew assembleDebug
```

Build a release APK, written to `app/build/outputs/apk/release/FinTrack-v1.1.0-release.apk`:

```bash
./gradlew assembleRelease
```

On Windows use `gradlew.bat` instead of `./gradlew`.

### Release signing

`assembleRelease` reads signing details from `keystore.properties` in the project root. Without that file it falls back to the debug key.

```properties
storeFile=C:/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Never commit the keystore or this file. `*.jks`, `*.keystore` and `keystore.properties` are git-ignored. Back the keystore up somewhere safe: Android only installs an update over an existing app if both are signed with the same key.

### Cutting a new version

1. Bump `appVersionCode` by one and set `appVersionName` in `app/build.gradle.kts`.
2. Add a section to `CHANGELOG.md`.
3. Run the tests and `assembleRelease`. The APK is named `FinTrack-vX.Y.Z-release.apk`.
4. Commit, tag `vX.Y.Z`, push, and attach the APK to a GitHub Release for that tag.

### Installing

Enable installing from unknown sources on your phone, then copy the APK over, or run:

```bash
adb install app/build/outputs/apk/release/FinTrack-v1.1.0-release.apk
```

## Using the app

1. On first launch, read the SMS explanation, then allow access or skip to manual entry.
2. The app imports the last 12 months of transaction SMS. New messages are imported automatically while auto-import is on.
3. Check the **Activity** tab badge for messages that need review, and the **SMS log** to see what was skipped and why.
4. Tap any number on **Home** to see the transactions behind it. Fix a wrong flow or category from the transaction editor.
5. Set monthly limits under **Budgets** (from Home or Insights).
6. Use **Split** to photograph a bill, check the extracted numbers, add people and save; only your share is counted as spend.
7. Optionally, paste an OpenAI API key in **Settings**, then tap **Generate summary**.
