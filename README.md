# FinTrack: private, on-device personal finance tracker for Android

FinTrack reads bank, UPI and credit-card alert SMS, turns them into transactions, and shows where your money goes. Everything is stored in an encrypted database on your phone. There is no cloud sync, no account, no analytics, and no tracking SDK.

- Kotlin, Jetpack Compose, Material 3
- Room on SQLCipher (AES-256) for storage
- Android 12+ (minSdk 31, targetSdk 35)

## Download

Get the latest signed APK from the [Releases page](https://github.com/Lukey-7/Personal_Finance_app/releases/latest). Each release lists a SHA-256 checksum so you can verify the file.

APKs are split by CPU: take **arm64-v8a** for any phone from the last several years. The `universal`
APK works everywhere but is about three times the size, because the on-device OCR models ship once per
architecture.

## Features

- **Adaptive SMS parsing.** A layered, bank-agnostic parser detects debits and credits from most Indian banks, UPI apps and card networks. There are no per-bank templates. Direction is read from the first verb about *your* account, so alerts that also mention the other side ("Acct debited ...; CAFE credited", "Rs 500 Dr. ... Cr. to x@ybl") are still booked the right way round.
- **Review queue.** Messages the parser is unsure about are never guessed or silently dropped. They go to a review list where you confirm or dismiss them.
- **Rescans that respect you.** *Rescan the last 12 months* re-reads your inbox with the current parser: it recovers messages an older version ignored and corrects rows an older version stored backwards, but never brings back a transaction you deleted, a review item you dismissed, or overwrites a row you edited.
- **Auto-categorisation.** Keyword rules sort transactions into food, shopping, bills, transport and more. You can recategorise any transaction.
- **Manual entry.** Add, edit and delete transactions yourself.
- **Dashboard you can audit.** Net spend = gross spend - refunds, savings = income - net spend, shown with the arithmetic. Transfers, card-bill payments and investments are listed separately and never counted as spend. Every number is tappable and opens the transactions behind it. Period picker, daily average, month-end projection, top merchants, per-account totals.
- **Correct counting.** Amounts are stored as paise (integers). One payment reported by two SMS (bank + UPI app) is stored once, matched by reference number or by amount within ten minutes. Every transaction has an editable *flow* (expense, income, refund, transfer, investment, cash, settlement) that decides how it is counted.
- **SMS log.** Every scanned message with what happened to it and why. Message text is not stored; tapping a row re-reads it from the inbox.
- **Bill split with on-device OCR.** Photograph or pick a bill; ML Kit's bundled recognisers (Latin and Devanagari/Hindi) read it offline. Edit the extracted total and items, add people, split equally / by shares / custom / by item with proportional tax, and only your share counts as your spend. Tracks who owes whom and settlements. See [Splitting a bill](#splitting-a-bill).
- **Insights.** Weekly and monthly trends, category comparisons such as "Food up 20%", and local "reduce spending" suggestions. Suggestions cover recurring subscriptions, frequent small spends, rising categories and budget overspend.
- **Budgets.** Set a monthly limit per category, with progress bars and overspend alerts.
- **Optional AI summary.** Add your own OpenAI API key to get a written monthly summary and saving tips, on demand only.
- **Clean up duplicates.** Finds the same payment stored twice by an older version and shows exactly what it would remove before deleting anything.
- **Your data.** Export to CSV, or wipe everything.

## Security and privacy

| Area | What the app does |
|---|---|
| Database | Encrypted with SQLCipher. A random 256-bit key is created per install and kept in EncryptedSharedPreferences backed by the Android Keystore. `secure_delete` is on, so cleared rows are overwritten. |
| API key | Stored only in EncryptedSharedPreferences using AES-256-GCM with a Keystore master key. Never logged or exported, and never in the source. Published release APKs carry no key; a personal build can have one built in (see `docs/RUNNING.md`), which can be changed or removed in Settings. |
| SMS | Only messages from alphanumeric sender IDs such as `VM-HDFCBK` are read. Personal messages from phone numbers are skipped. Only parsed fields are stored. Raw text is kept only for messages waiting in the review queue, and is deleted when you resolve them. The SMS log stores sender, time, outcome, reason and amount, never the body. |
| Bill photos | Read on the phone by ML Kit Text Recognition with the **bundled** Latin and Devanagari models (`com.google.mlkit:text-recognition`, `com.google.mlkit:text-recognition-devanagari`): no model download, works in airplane mode. The camera capture goes to a temp file in app-private cache and is deleted after recognition, whether or not it succeeded; gallery images are read through the system Photo Picker without a storage permission. No image is stored. It sends nothing — see [Does the OCR phone home?](#does-the-ocr-phone-home) for how that was checked. |
| Network | The only network call in the codebase goes to `https://api.openai.com`, and only when you tap **Generate summary**. Cleartext HTTP is disabled app-wide. |
| Data sent to OpenAI | Category totals, counts, budgets, and this and last month's totals. No merchant names, SMS text, bank names or account numbers. Settings has a **What is sent?** button that shows the exact payload. |
| Backups | `allowBackup=false` and data-extraction rules exclude everything from cloud backup and device-to-device transfer. |
| Screen | `FLAG_SECURE` blocks screenshots, screen recording and the recents preview. |
| Release build | R8 minification is on, and all `android.util.Log` calls are stripped. |
| Screenshots | `FLAG_SECURE` is set in release builds. Debug builds leave it off so the UI can be captured for documentation. |
| Third parties | Only AndroidX and Google libraries, plus SQLCipher. No Firebase, analytics, crash reporting or ads. |
| CSV export | Written to a location you pick through the system file picker. Cells are escaped against spreadsheet formula injection. |

Two things are outside the app's control. An exported CSV is plain text, so treat it like a bank statement. If you use the AI feature, OpenAI's own data policy applies to the aggregated numbers you send.

## Does the OCR phone home?

ML Kit is a Google library, so the fair question is whether reading a bill quietly reports anything. For
this app the answer is no, and it was checked three independent ways rather than taken on trust.

**1. Nothing in the dependencies can log.** None of the ML Kit or Play-services artifacts the app ships
contains Google's telemetry transports — no `clearcut`, `phenotype`, `datatransport` or `firelog` code in
`mlkit:common`, `mlkit:text-recognition`, `mlkit:vision-common`, `play-services-base`,
`play-services-basement` or `play-services-mlkit-text-recognition`.

**2. The built APK has exactly one network endpoint.** Scanning the release APK — both the compiled dex
and all three native libraries, including the 10.6 MB OCR pipeline — turns up a single URL the app could
call:

```
https://api.openai.com/v1/chat/completions
```

Every other URL in the binary is a documentation or bug-tracker string (TensorFlow Lite guides, an
Android reference page, the LLVM toolchain), not an endpoint.

**3. A recognition sends zero bytes.** On a device with networking up, the app's uid was measured through
the framework's own per-uid accounting across a cold launch and a full OCR recognition:

| | rx | tx |
|---|---|---|
| after launch | 0 | 0 |
| after recognising a bill | 0 | 0 |
| *system uid, same moment (control)* | *124,041* | *96,050* |

The control line matters: the counter was demonstrably working and reporting six-figure traffic for
another uid at the same moment this app reported nothing.

**On the manifest flag.** There is no ML Kit logging flag to set, because in this configuration there is
no logging component to switch off. That only becomes a question with the *unbundled* recogniser
(`play-services-mlkit-text-recognition` on its own), which hands the work to Google Play services — a
separate app with its own telemetry, outside any setting this app controls. FinTrack deliberately uses
the bundled model instead, which is also why the APK is larger and why OCR works in airplane mode.

**Scope of this check.** The three checks above were run before the bundled Devanagari (Hindi) recogniser
(`text-recognition-devanagari`) was added. It is the same kind of bundled ML Kit artifact, but the
dependency scan, APK endpoint scan and per-uid traffic measurement should be repeated on the next release
build before this section claims it too.

## Splitting a bill

**Split › New split › Photo** (camera) or **Gallery**. The bill is read on the phone and fills in the
name, date, total, tax / tip / discount and line items; everything stays editable. Or skip the photo and
type the total. Add people (or quick-add 2–5), choose who paid, and split **Equal**, **Shares**, **Custom
amounts** or **By item** (tag people on each item; tax, service and discount are spread in proportion).
The app checks that items + tax − discount = total, and only your share counts as your spending.

**Engine.** Google ML Kit text recognition with bundled models only, Latin plus Devanagari, both inside the
APK. Both read each photo in parallel; the Devanagari reading is used only when the bill actually contains
Hindi script, so English and Hinglish bills keep the stronger Latin model. The photo is discarded after
reading.

**What the bill reader handles:**

- Item / Qty / Amount columns (quantity and unit price), "2 x Item", and four-column Qty / Rate / Amount layouts.
- Tilted photos: rows are straightened using each text line's angle, so prices stay with their items.
- OCR slips in amounts such as `360.0e`, `36O.00`, `1,551. 00` and decimal commas (`120,00`).
- `Rs.`, `INR`, `₹` and `रु.` prefixes, Hindi labels (कुल योग, उप योग, जीएसटी, छूट, सेवा शुल्क) and Devanagari digits.
- Invoice numbers, dates and "You saved…" lines are not mistaken for items or discounts.

**Tested** on 8 photographed receipts (clean, tilted, faded, angled, dim, Hindi, Hinglish) run through the
app on an emulator: every English and Hinglish bill read exactly (items, quantities, totals, discounts).

**Limits.** Bills that print prices in Devanagari digits (३२०.००) are misread by ML Kit, so item prices
must be typed in; the total is usually still found. Crumpled or very low-light photos may need more
corrections. The Devanagari model adds roughly 4 MB per ABI (Google's figure, not yet measured on a
release build). Recognised text is logged only in debug builds, never in release.

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
│   ├── ocr/          Receipt text -> structured bill (row straightening, Latin + Devanagari labels and digits)
│   ├── ai/           OpenAI client and aggregated payload builder
│   └── export/       CSV exporter
└── ui/               Compose theme, charts, navigation, screens
```

## How the SMS parser works

The parser lives in `domain/parser`. It has no Android dependencies, so it runs as a plain JVM unit test.

1. **Filters** drop OTPs, promotions, future or scheduled debits, failed transactions, payment requests and statement reminders, but only when the message reports **no completed money movement**. A real debit with a "never share your OTP" footer, a refund for a "cancelled" order or a reversal of a "failed" payment is kept. A verb after "will be", "to be" or "if amount" does not count as completed. The one rule that always wins is "has not been debited".
2. **Type detection** reads direction from the **first** verb that reports money moving (`debited`, `spent`, `paid`, `sent`, `credited`, `received`, `Dr.`/`Cr.` after an amount...), because that verb is about your account and later mentions describe the other party. "Credited to beneficiary" and "transferred to" are debits; "paid you" and "transferred to your a/c" are credits. Keyword scoring is the fallback when no such verb exists.
3. **Amount extraction** handles `Rs`, `Rs.`, `INR` and `₹` before or after the number, plus Indian digit grouping. It ignores amounts that follow "Avl bal" or "limit", and digits glued to an account or card mask (`XX1234 Rs 750` is ₹750, not ₹1,234).
4. **Account extraction** keeps only the last four digits, from forms like `XX1234`, `a/c **1234` or `card ending 1234`.
5. **Bank detection** reads the sender ID or message body, and falls back to the sender ID itself.
6. **Merchant extraction** understands UPI handles, `UPI/P2M/...`, `Info:`, `at X`, `to X` and `from X`.
7. **Date extraction** reads a date from the body in many formats, and falls back to the SMS timestamp (keeping the SMS time of day when the body has only a date).
8. **Reference extraction** picks up `UPI Ref No`, `UPI:<number>`, `IMPS Ref`, `RRN`, `Txn ID`, `UTR` for duplicate detection.
9. **Flow classification** decides whether the transaction is an expense, income, refund, transfer (incl. credit-card bill payments and self-transfers), investment or cash withdrawal.

Each result gets a confidence score. Results below 60 go to the review queue instead of being saved.

### The SMS regression corpus

`app/src/test/.../SmsCorpus.kt` is a table of real-world SMS shapes, each with the outcome it must produce
(saved with direction, amount, flow, merchant and reference; sent to review; or ignored). `SmsCorpusTest`
runs every row through the parser, categoriser and flow classifier and reports **all** mismatches in one
failure, so a change that fixes one bank and breaks another is caught immediately.

**Fixing a misread message** is always the same two steps:

1. Add the message (card and account digits masked) as a new `CorpusCase` row with the outcome it should have. Run `testDebugUnitTest` and watch it fail.
2. Fix the right layer, usually one line, and re-run until the whole corpus is green:
   - A new ignore rule: append to `TextFilters.ignoreRules` (it yields to completed movements automatically).
   - A new direction verb: add it to `TypeDetector.primaryVerb`.
   - A new merchant shape: add a regex to `MerchantExtractor.patterns` at the right priority.
   - A new bank: append to `BankExtractor.knownIssuers`.
   - A new category keyword: add it to the list on the `Category` enum.

### Reporting a misread SMS

Open **Settings › SMS log**, find the message (every scanned message is listed with its outcome and
reason), tap it to see the text, and open an issue with the text and what it should have been. Mask
account and card digits first. A message marked *ignored* that was really a payment can also be sent to
the review queue from the same screen.

## How the numbers are calculated

- **Spend** counts transactions with flow `EXPENSE` (and `CASH` unless turned off in Settings).
- **Refunds** (`REFUND` flow) are subtracted from spend, and from the matching category when the merchant matches a spend in the same period.
- **Income** counts only `INCOME`. **Transfers**, **investments** and split **settlements** are listed separately.
- **Same message twice:** the live receiver and an inbox scan see one SMS seconds apart; it is stored once. Every message the importer looks at is recorded in the SMS log, which is how it remembers what it has already handled.
- **Identical alerts are separate payments:** two word-for-word identical alerts more than five minutes apart (two ₹180 coffees on one card) are two transactions, not one.
- **One payment, two senders:** a bank alert and a UPI-app alert for the same payment are stored once when their reference numbers match, or when the same amount and direction arrive within ten minutes from a different bank/app (or one has no real merchant). Two references that both exist and differ are always two payments. Same merchant and amount hours apart is two payments.
- **Your edits win:** once you edit a transaction, duplicate merging only fills in missing identifiers and rescans never rewrite it.
- **Splits:** only your share is your expense. If you paid and an SMS debit for the full amount exists, that transaction is trimmed to your share (the bank's original amount is remembered, so a second alert for the full bill is still recognised as the same payment); otherwise your share is recorded as a `SPLIT` expense. Money friends pay back is a `SETTLEMENT`, not income.

Every rule above has a unit test under `app/src/test` (parser corpus, duplicate detection, import memory,
insights, split maths, database migrations). Run them with `testDebugUnitTest`.

### Known limits

- Wording no bank in the corpus uses can still be misread. The defence is the review queue plus one new corpus row per report.
- A "You paid ₹200 ... cashback ₹20 credited" message records the ₹200 spend; the ₹20 cashback is not recorded separately.
- A balance alert that also mentions the last debit is imported. If the bank's real debit alert arrived more than ten minutes apart, **Clean up duplicates** in Settings will merge them.
- Bank alerts sent from ordinary phone numbers (not sender IDs) are skipped, to keep personal SMS private.

## Building

### Requirements

- JDK 17
- Android SDK with platform 35 and build-tools 35. Android Studio Ladybug or newer installs these for you.

### With Android Studio

Open the project folder, let Gradle sync, then use **Build › Build APK(s)**.

### From the command line

On a machine where the Gradle wrapper cannot reach `services.gradle.org`, use the helpers described in
[docs/RUNNING.md](docs/RUNNING.md) (`build.cmd` on Windows, `build.sh` in Git Bash), which point Gradle at
a local JDK 17 and Gradle 8.9. Otherwise the wrapper below works as normal.

Create `local.properties` in the project root pointing at your SDK. This file is git-ignored.

```properties
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
```

Run the unit tests (parser corpus, duplicates, import memory, insights, splits, migrations):

```bash
./gradlew testDebugUnitTest
```

Build debug APKs. One is written per CPU architecture, plus a universal one, to
`app/build/outputs/apk/debug/FinTrack-v<version>-<abi>-debug.apk` (`<abi>` is `arm64-v8a`,
`armeabi-v7a`, `x86_64` or `universal`):

```bash
./gradlew assembleDebug
```

Build release APKs, written to `app/build/outputs/apk/release/FinTrack-v<version>-<abi>-release.apk`:

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
3. Run the tests and `assembleRelease`. The APKs are named `FinTrack-vX.Y.Z-<abi>-release.apk`.
4. Commit, tag `vX.Y.Z`, push, and attach the APK to a GitHub Release for that tag.

### Installing

Enable installing from unknown sources on your phone, then copy the APK over, or run:

```bash
adb install app/build/outputs/apk/release/FinTrack-v1.1.0-arm64-v8a-release.apk
```

## Using the app

1. On first launch, read the SMS explanation, then allow access or skip to manual entry.
2. The app imports the last 12 months of transaction SMS. New messages are imported automatically while auto-import is on.
3. Check the **Activity** tab badge for messages that need review, and the **SMS log** to see what was skipped and why. After updating the app, **Settings › Rescan the last 12 months** applies parser fixes to your existing history.
4. Tap any number on **Home** to see the transactions behind it. Fix a wrong flow or category from the transaction editor.
5. Set monthly limits under **Budgets** (from Home or Insights).
6. Use **Split** to photograph a bill (English, Hinglish or Hindi), check the extracted numbers, add people and save; only your share is counted as spend.
7. Optionally, paste an OpenAI API key in **Settings**, then tap **Generate summary**.
