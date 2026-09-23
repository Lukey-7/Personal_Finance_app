# SMS Accuracy Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the nine SMS/ledger bugs found in the 2026-09-23 audit at their root, so that a message is either parsed correctly, sent to the review queue, or ignored for a stated reason. It must never be silently booked the wrong way round, with the wrong amount, twice, or not at all.

**Architecture:** Three layers of defence. (1) *Parser*: direction comes from the first verb that describes the customer's own account, not from keyword totals; amounts glued to an account mask are rejected; ignore rules may drop a message only when it reports no completed money movement. (2) *Import memory*: the SMS log is the importer's record of what it has seen, so identical alerts become separate occurrences, user decisions survive a rescan, and messages that were only ignored by the parser get re-parsed. (3) *Regression corpus*: one table-driven test holds every real-world SMS shape; each future bug report becomes one more row.

**Tech Stack:** Kotlin, Room 2 (SQLCipher at runtime; plain in-memory Room in tests), JUnit 4 + Robolectric (sdk 33), Gradle 8.9 via `./build.sh`.

**Spec:** The audit findings in this session (bugs #1 to #9), restated under "Bug index" below. There is no separate spec file.

## Bug index (what "done" means)

| # | Symptom | Fixed in |
|---|---|---|
| 1 | ICICI "Acct debited …; X credited" stored as income | Task 4 |
| 2 | "Rs.500 Dr. … Cr. to x@ybl" stored as income | Task 4 |
| 3 | "You paid Rs 200 … Cashback of Rs 20 credited" stored as a ₹200 refund | Task 4 |
| 4 | "A/c XX1234 Rs 750 debited" stored as ₹1,234 | Task 5 |
| 5 | Real debit dropped for an "OTP" footer, or a message that starts "Avl Bal" | Task 6 |
| 6 | Refund/reversal dropped because it says "cancelled"/"failed" | Task 6 |
| 7 | Two identical same-day card alerts stored once | Tasks 3 and 7 |
| 8 | "Rescan the last 12 months" resurrects deleted rows and dismissed review items | Task 7 |
| 9 | Split-shrunk debit plus a second-sender alert for the full amount are both counted | Task 3 |
| - | Rows already stored wrongly by the old parser stay wrong | Task 8 |

## Global Constraints

- Build only through `./build.sh --offline -q <tasks>` from the repo root (Git Bash). The Gradle wrapper is broken on this machine.
- **Shared working tree and emulator.** Another session (the OCR/bill-split chat) uses the same checkout and `emulator-5554`. Before every Gradle run, message that session "starting <tasks>"; when it ends, send "done". Never clear app data or reseed the emulator inbox.
- **Stage explicit paths only.** Never use `git add -A`, `git add .` or `git commit -a`. The other session has uncommitted files (`BillParser.kt`, `OcrEngine.kt`, `OcrRows.kt`, `OcrRowsTest.kt`, `BillParserRealOcrTest.kt`, `NewSplitScreen.kt`) that must not be swept in.
- `AppViewModel.saveSplit` and `markAsSettlement` belong to the split feature. Message the other session before editing them (Task 3 and Task 2).
- Never print or commit `secrets.properties`.
- Money is whole paise (`Long`) everywhere. No `Double` crosses a storage or comparison boundary.
- Commit message style: one plain sentence in the imperative ("Read the direction from the first account verb"), no `feat:` prefixes, ending with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- `-q` Gradle runs print nothing on success. Success = exit code 0. On failure, Gradle prints the failing test names.

## File map

| File | Responsibility | Tasks |
|---|---|---|
| `app/src/test/java/com/pft/financetracker/SmsCorpus.kt` (new) | The table of real-world SMS shapes and their expected outcome | 1, 4, 5, 6 |
| `app/src/test/java/com/pft/financetracker/SmsCorpusTest.kt` (new) | Runs every corpus row through parser + categorizer + flow classifier, reports all mismatches at once | 1 |
| `app/src/main/java/com/pft/financetracker/domain/parser/Extractors.kt` | Parser layers (filters, direction, amount, merchant, ref) | 4, 5, 6 |
| `app/src/main/java/com/pft/financetracker/data/local/Entities.kt`, `Mappers.kt`, `AppDatabase.kt`, `domain/model/Models.kt` | `originalAmountPaise` + `userEdited` columns, DB v3 | 2 |
| `app/schemas/com.pft.financetracker.data.local.AppDatabase/3.json` (generated) | Exported schema for v3 | 2 |
| `app/src/main/java/com/pft/financetracker/data/local/Daos.kt` | `findSimilar` matches either amount and either direction; `SmsLogDao.getByHash` | 3, 7 |
| `app/src/main/java/com/pft/financetracker/data/repository/Repositories.kt` | Duplicate rules; `SmsLogRepository.getByHash` | 3, 7 |
| `app/src/main/java/com/pft/financetracker/data/sms/SmsImporter.kt` | Occurrence-aware "seen" check, user decisions, repair of stale rows, merge that respects user edits | 3, 7, 8 |
| `app/src/main/java/com/pft/financetracker/ui/AppViewModel.kt` | Mark user edits; delete/dismiss call the importer; split keeps the bank amount | 2, 3, 7 |
| `app/src/main/java/com/pft/financetracker/ui/screens/edit/EditTransactionScreen.kt` | Carry `originalAmountPaise` through an edit | 2 |
| `app/src/test/java/com/pft/financetracker/MigrationTest.kt`, `DuplicateDetectionTest.kt` | Existing tests, extended | 2, 3 |
| `app/src/test/java/com/pft/financetracker/ImportMemoryTest.kt` (new) | Import memory + repair scenarios against an in-memory DB | 7, 8 |
| `CHANGELOG.md`, `docs/DEVICE-TEST-v1.1.md` | Release notes and device verification record | 9 |

---

### Task 1: Regression corpus harness (baseline, green)

**Files:**
- Create: `app/src/test/java/com/pft/financetracker/SmsCorpus.kt`
- Create: `app/src/test/java/com/pft/financetracker/SmsCorpusTest.kt`

**Interfaces:**
- Produces: `SmsCorpus.cases: List<CorpusCase>`; `CorpusCase(name, sender, body, expect)`; `Expect.Saved(type: TransactionType?, paise: Long, flow: Flow? = null, merchantContains: String? = null, ref: String? = null)`, `Expect.Ignored`, `Expect.Review`. Later tasks append rows to `SmsCorpus.cases`.

- [ ] **Step 1: Create the corpus with shapes that already parse correctly**

```kotlin
package com.pft.financetracker

import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.model.TransactionType.DEBIT

/** What a message must turn into. [Saved.type] null means "either direction is acceptable". */
sealed class Expect {
    data class Saved(val type: TransactionType?, val paise: Long, val flow: Flow? = null, val merchantContains: String? = null, val ref: String? = null) : Expect()
    data object Ignored : Expect()
    data object Review : Expect()
}

data class CorpusCase(val name: String, val sender: String, val body: String, val expect: Expect)

/**
 * Every real-world SMS shape the parser must handle, with the outcome it must produce. A bug report about a
 * misread message becomes one more row here, so the same shape can never regress.
 */
object SmsCorpus {
    val cases: List<CorpusCase> = listOf(
        // ---- baseline: shapes that were already right on 2026-09-23 ----
        CorpusCase("hdfc_upi_debit", "VM-HDFCBK", "Rs.250.00 debited from a/c **1234 on 12-08-24 to VPA swiggy.upi@axisbank (UPI Ref No 422312345678). Not you? Call 18002586161", Expect.Saved(DEBIT, 25_000, Flow.EXPENSE, "swiggy", "422312345678")),
        CorpusCase("hrs_before_amount", "VM-CANBNK", "At 10:30 Hrs, Rs.500.00 debited from a/c XX1234 to Uber. Ref 422312345678", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "uber")),
        CorpusCase("indian_grouping", "VM-HDFCBK", "Rs.1,23,456.78 debited from a/c **1234 to VPA landlord@okaxis (UPI Ref No 422312345699).", Expect.Saved(DEBIT, 12_345_678, Flow.EXPENSE)),
        CorpusCase("rupee_symbol_space", "VM-HDFCBK", "₹ 99 debited from a/c **1234 to VPA spotify@ybl (UPI Ref No 422312345611).", Expect.Saved(DEBIT, 9_900, Flow.EXPENSE, "spotify")),
        CorpusCase("received_from_person", "VM-KOTAKB", "Received Rs.500.00 in your Kotak Bank AC X1234 from rahul@okicici on 12-08-24.UPI Ref:422312345622.", Expect.Saved(CREDIT, 50_000, Flow.INCOME, "rahul")),
        CorpusCase("atm", "VM-SBIINB", "Rs.2000 withdrawn at ATM S1AN0123 from A/c XX1234 on 12Aug24. Avl Bal Rs 8000 -SBI", Expect.Saved(DEBIT, 200_000, Flow.CASH)),
        CorpusCase("autopay_executed", "VM-HDFCBK", "Rs 649.00 debited from A/c XX1234 for UPI AutoPay to Netflix. Ref 422312345633", Expect.Saved(DEBIT, 64_900, Flow.EXPENSE, "netflix")),
        CorpusCase("salary_neft", "VM-HDFCBK", "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-AUG-24 for NEFT Cr-ACME CORP SALARY.", Expect.Saved(CREDIT, 8_500_000, Flow.INCOME)),
        CorpusCase("self_transfer", "VM-SBIINB", "Rs 5000 debited from A/c XX1234 transferred to own account XX9876. Ref 422312345644", Expect.Saved(DEBIT, 500_000, Flow.TRANSFER)),
        CorpusCase("gpay_sent_short", "VM-GPAYIN", "Sent Rs.120 to chaiwala@ybl", Expect.Saved(DEBIT, 12_000, Flow.EXPENSE, "chaiwala")),
        CorpusCase("card_bill_payment", "AX-ICICIB", "Payment of Rs 15,000.00 received towards your ICICI Bank Credit Card XX4455. Thank you.", Expect.Saved(null, 1_500_000, Flow.TRANSFER)),
        CorpusCase("declined", "VM-HDFCBK", "Txn of Rs 500 on HDFC Card XX1234 at Amazon declined due to insufficient balance.", Expect.Ignored),
        CorpusCase("mandate_setup", "VM-HDFCBK", "UPI AutoPay mandate of Rs 649 for Netflix registered successfully on A/c XX1234.", Expect.Ignored),
        CorpusCase("pure_otp", "VM-HDFCBK", "123456 is your OTP for txn of Rs.5000 at Flipkart. Do not share.", Expect.Ignored),
        CorpusCase("future_debit", "VM-HDFCBK", "Rs.999 will be debited from your a/c on 20-09-26 for Netflix autopay.", Expect.Ignored),
        CorpusCase("pure_promo", "BZ-OFFERS", "Get up to Rs.500 cashback on your next order! Apply now. T&C apply.", Expect.Ignored),
        CorpusCase("ambiguous_to_review", "VM-XYZBNK", "Transaction alert: Rs 300 on your account XX1234.", Expect.Review),
    )
}
```

- [ ] **Step 2: Create the runner that reports every mismatch in one failure**

```kotlin
package com.pft.financetracker

import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.fail
import org.junit.Test

class SmsCorpusTest {
    private val parser = SmsParser()
    private val now = System.currentTimeMillis()

    private fun check(c: CorpusCase): String? {
        val r = parser.parse(SmsMessage(c.sender, c.body, now))
        return when (val e = c.expect) {
            Expect.Ignored -> if (r is ParseResult.Ignored) null else "expected Ignored, got $r"
            Expect.Review -> if (r is ParseResult.NeedsReview) null else "expected Review, got $r"
            is Expect.Saved -> {
                val t = (r as? ParseResult.Success)?.transaction ?: return "expected Saved, got $r"
                val cat = Categorizer.categorize(t.merchant, t.type, t.bankName)
                val flow = FlowClassifier.classify(t.type, c.body, t.merchant, cat)
                listOfNotNull(
                    if (e.type != null && t.type != e.type) "type ${t.type} != ${e.type}" else null,
                    if (t.amountPaise != e.paise) "paise ${t.amountPaise} != ${e.paise}" else null,
                    if (e.flow != null && flow != e.flow) "flow $flow != ${e.flow}" else null,
                    if (e.merchantContains != null && !t.merchant.lowercase().contains(e.merchantContains)) "merchant '${t.merchant}' lacks '${e.merchantContains}'" else null,
                    if (e.ref != null && t.refNumber != e.ref) "ref ${t.refNumber} != ${e.ref}" else null,
                ).joinToString("; ").ifEmpty { null }
            }
        }
    }

    @Test
    fun everyCorpusMessageParsesAsExpected() {
        val failures = SmsCorpus.cases.mapNotNull { c -> check(c)?.let { "${c.name}: $it" } }
        if (failures.isNotEmpty()) fail("${failures.size} of ${SmsCorpus.cases.size} corpus messages wrong:\n" + failures.joinToString("\n"))
    }
}
```

- [ ] **Step 3: Run it (announce the Gradle run to the other session first)**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.SmsCorpusTest"`
Expected: exit 0 (all 17 baseline rows pass). If a baseline row fails, the row is wrong, not the parser: re-check it against the audit output, fix the row, re-run.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/pft/financetracker/SmsCorpus.kt app/src/test/java/com/pft/financetracker/SmsCorpusTest.kt
git commit -m "Add a regression corpus of real SMS shapes and their expected outcome

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Database v3: remember the bank amount and user edits

Two facts the ledger currently forgets. `originalAmountPaise` is the amount the bank reported before a split shrank the row to "my share" (bug #9). `userEdited` is true once a person has corrected a row, so no automatic process overwrites it (Tasks 3 and 8).

**Files:**
- Modify: `app/src/main/java/com/pft/financetracker/data/local/Entities.kt` (`TransactionEntity`)
- Modify: `app/src/main/java/com/pft/financetracker/domain/model/Models.kt` (`Transaction`)
- Modify: `app/src/main/java/com/pft/financetracker/data/local/Mappers.kt` (`toDomain`, `toEntity`)
- Modify: `app/src/main/java/com/pft/financetracker/data/local/AppDatabase.kt` (version, `MIGRATION_2_3`, `ALL_MIGRATIONS`)
- Modify: `app/src/main/java/com/pft/financetracker/ui/AppViewModel.kt` (`save`, `resolveReview`, `markAsSettlement`)
- Modify: `app/src/main/java/com/pft/financetracker/ui/screens/edit/EditTransactionScreen.kt` (`build()`)
- Test: `app/src/test/java/com/pft/financetracker/MigrationTest.kt`
- Generated: `app/schemas/com.pft.financetracker.data.local.AppDatabase/3.json`

**Interfaces:**
- Produces: `Transaction.originalAmountPaise: Long? = null`, `Transaction.userEdited: Boolean = false`; entity columns of the same names; `AppDatabase.MIGRATION_2_3`.

- [ ] **Step 1: Write the failing migration test** (append inside `MigrationTest`)

```kotlin
    @Test
    fun migrate2To3AddsOriginalAmountAndUserEdited() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO transactions (id, amountPaise, type, merchant, category, timestamp, bankName, accountRef, source, flow, note, smsHash, refNumber, confidence, needsReview, createdAt) VALUES (1, 25050, 'DEBIT', 'Swiggy', 'FOOD', 1700000000000, 'HDFC Bank', '1234', 'SMS', 'EXPENSE', NULL, 'hash1', '4223', 90, 0, 1700000000000)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)
        db.query("SELECT amountPaise, originalAmountPaise, userEdited FROM transactions WHERE id = 1").use { c ->
            c.moveToNext()
            assertEquals(25_050L, c.getLong(0))
            assertTrue(c.isNull(1))
            assertEquals(0, c.getInt(2))
        }
        db.close()
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.MigrationTest"`
Expected: compile failure, `Unresolved reference: MIGRATION_2_3`.

- [ ] **Step 3: Add the columns, mapping and migration**

`Entities.kt`, add `import androidx.room.ColumnInfo`, then add these two properties to `TransactionEntity` after `needsReview`:

```kotlin
    /** The amount the bank reported, when a split later reduced [amountPaise] to my share. Used to spot the same payment. */
    val originalAmountPaise: Long? = null,
    /** True once a person corrected this row. Automatic re-parsing and duplicate merging never overwrite it. */
    @ColumnInfo(defaultValue = "0") val userEdited: Boolean = false,
```

`Models.kt`, add to `Transaction` after `needsReview`:

```kotlin
    /** Bank-reported amount before a split shrank this row to my share; null when never shrunk. */
    val originalAmountPaise: Long? = null,
    /** A person corrected this row; automatic processes must leave it alone. */
    val userEdited: Boolean = false,
```

`Mappers.kt`, add `originalAmountPaise = originalAmountPaise,` and `userEdited = userEdited,` as the last arguments of both `TransactionEntity.toDomain()`'s `Transaction(...)` and `Transaction.toEntity()`'s `TransactionEntity(...)`.

`AppDatabase.kt`: set `version = 3`, add below `MIGRATION_1_2`:

```kotlin
        /** v2 -> v3: transactions remember the bank's amount before a split, and whether a person edited them. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN originalAmountPaise INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }
```

and change `ALL_MIGRATIONS` to `arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3)`.

- [ ] **Step 4: Mark human edits and keep the bank amount through an edit**

Message the other session first: "Editing AppViewModel.markAsSettlement (one `.copy(userEdited = true)`) for the SMS plan, Task 2."

`AppViewModel.kt`:

```kotlin
    fun save(t: Transaction, onDone: () -> Unit = {}) = viewModelScope.launch {
        // An existing row saved from the editor was corrected by a person: protect it from automatic rewrites.
        if (t.id == 0L) c.transactions.insert(t) else c.transactions.update(t.copy(userEdited = true))
        onDone()
    }
```

In `resolveReview`, change `val id = c.transactions.insert(t)` to `val id = c.transactions.insert(t.copy(userEdited = true))`.
In `markAsSettlement`, change `t.copy(flow = Flow.SETTLEMENT)` to `t.copy(flow = Flow.SETTLEMENT, userEdited = true)`.

`EditTransactionScreen.kt` `build()`: add `originalAmountPaise = existing?.originalAmountPaise,` after `needsReview = false,`.

- [ ] **Step 5: Run the migration test and the full suite (the build generates 3.json)**

Run: `./build.sh --offline -q testDebugUnitTest`
Expected: exit 0. `app/schemas/com.pft.financetracker.data.local.AppDatabase/3.json` now exists (`ls` it). If `MigrationTest` says it cannot find schema version 3, KSP wrote `3.json` after the test assets were merged: run the same command once more. If `runMigrationsAndValidate` reports a schema mismatch on `userEdited`, the entity is missing `@ColumnInfo(defaultValue = "0")`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pft/financetracker/data/local/Entities.kt app/src/main/java/com/pft/financetracker/domain/model/Models.kt app/src/main/java/com/pft/financetracker/data/local/Mappers.kt app/src/main/java/com/pft/financetracker/data/local/AppDatabase.kt app/src/main/java/com/pft/financetracker/ui/AppViewModel.kt app/src/main/java/com/pft/financetracker/ui/screens/edit/EditTransactionScreen.kt app/src/test/java/com/pft/financetracker/MigrationTest.kt app/schemas/com.pft.financetracker.data.local.AppDatabase/3.json
git commit -m "Remember the bank's amount before a split and which rows a person edited

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Duplicate rules that keep repeat payments and respect edits (#7 part 1, #9)

Today, two same-amount payments to the same merchant hours apart on one day are merged. That rule exists only for rows imported by v1.0.0, which sit at exactly midnight, so this task narrows it to those rows. Those legacy rows may also carry the wrong direction, which Task 4 will correct, so the legacy match accepts either direction. A split-shrunk row is matched by its original amount. The merge never overwrites a person's edits or a split's amount.

**Files:**
- Modify: `app/src/main/java/com/pft/financetracker/data/local/Daos.kt` (`TransactionDao.findSimilar`)
- Modify: `app/src/main/java/com/pft/financetracker/data/repository/Repositories.kt` (`findLikelyDuplicate`)
- Modify: `app/src/main/java/com/pft/financetracker/data/sms/SmsImporter.kt` (duplicate branch of `process`)
- Modify: `app/src/main/java/com/pft/financetracker/ui/AppViewModel.kt` (`saveSplit`, one line)
- Test: `app/src/test/java/com/pft/financetracker/DuplicateDetectionTest.kt`

**Interfaces:**
- Consumes: `Transaction.originalAmountPaise`, `Transaction.userEdited` (Task 2).
- Produces: `TransactionDao.findSimilar(amountPaise: Long, from: Long, to: Long): List<TransactionEntity>` (the `type` parameter is removed).

- [ ] **Step 1: Write the failing tests** (append inside `DuplicateDetectionTest`)

```kotlin
    /** Two identical card alerts for two coffees, hours apart, no references: both are real payments. */
    @Test fun sameMerchantSameDayHoursApartWithoutRefsAreBothKept() = runBlocking {
        val at = startOfDay(now) + 9 * 3600_000
        repo.insert(tx(18_000, "Starbucks", "HDFC Bank", at))
        assertNull(repo.findLikelyDuplicate(tx(18_000, "Starbucks", "HDFC Bank", at + 5 * 3600_000)))
    }

    /** A v1.0.0 row stored with the wrong direction is still the same payment when the fixed parser re-reads it. */
    @Test fun legacyMidnightRowMatchesEitherDirection() = runBlocking {
        val midnight = startOfDay(now)
        repo.insert(tx(1_250_000, "Payment (HDFC Bank)", "HDFC Bank", midnight, hash = "legacy"))
        val credit = tx(1_250_000, "Credit (HDFC Bank)", "HDFC Bank", midnight + 18 * 3600_000).copy(type = TransactionType.CREDIT)
        assertEquals("legacy", repo.findLikelyDuplicate(credit)?.smsHash)
    }

    /** Bug #9: a split shrank the bank debit to my share; the UPI app's full-amount alert is the same payment. */
    @Test fun splitShrunkRowIsMatchedByItsOriginalAmount() = runBlocking {
        repo.insert(tx(40_000, "Barbeque", "HDFC Bank", now).copy(originalAmountPaise = 120_000))
        assertNotNull(repo.findLikelyDuplicate(tx(120_000, "Barbeque Nation", "Google Pay", now + 60_000)))
    }

    /** Merging a second alert into a split-shrunk, user-edited row must not undo the split or the edit. */
    @Test fun mergeKeepsSplitAmountAndUserEdits() = runBlocking {
        val importer = SmsImporter(
            ApplicationProvider.getApplicationContext(), SmsParser(), repo,
            SmsLogRepository(db.smsLogDao()), SettingsRepository(ApplicationProvider.getApplicationContext()),
        )
        repo.insert(tx(40_000, "Barbeque", "HDFC Bank", now).copy(originalAmountPaise = 120_000, category = Category.SHOPPING, userEdited = true, note = "team dinner"))
        val outcome = importer.process(SmsMessage("VM-GPAYIN", "You paid Rs.1200 to Barbeque Nation using Google Pay", now + 60_000))
        assertEquals(SmsImporter.Outcome.DUPLICATE, outcome)
        val row = repo.getAll().single()
        assertEquals(40_000L, row.amountPaise)
        assertEquals(Category.SHOPPING, row.category)
        assertEquals("team dinner", row.note)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.DuplicateDetectionTest"`
Expected: FAIL on the four new tests (the first returns a match, the second and third return null, the fourth changes amount/category).

- [ ] **Step 3: Widen the DAO query**

`Daos.kt`, replace `findSimilar`:

```kotlin
    /**
     * Candidates for the "same payment, two SMS" check: same amount (or the bank amount a split shrank) within a
     * time window. Direction is filtered by the caller, because a v1.0.0 row may carry the wrong one.
     */
    @Query("SELECT * FROM transactions WHERE (amountPaise = :amountPaise OR originalAmountPaise = :amountPaise) AND timestamp BETWEEN :from AND :to AND source = 'SMS'")
    suspend fun findSimilar(amountPaise: Long, from: Long, to: Long): List<TransactionEntity>
```

- [ ] **Step 4: Rewrite the matching rule**

`Repositories.kt`, replace the body of `findLikelyDuplicate` after the `findByRef` line:

```kotlin
        // Search the whole calendar day as well as the window: a transaction imported by v1.0.0 sits at
        // midnight (its parser dropped the time of day), so the same message re-parsed now lands hours away.
        val dayStart = startOfDay(candidate.timestamp)
        val dayEnd = dayStart + 86_400_000L
        val from = minOf(dayStart, candidate.timestamp - windowMillis)
        val to = maxOf(dayEnd, candidate.timestamp + windowMillis)

        return dao.findSimilar(candidate.amountPaise, from, to).map { it.toDomain() }.firstOrNull { existing ->
            // Two references that both exist and disagree mean two genuinely different payments.
            if (existing.refNumber != null && candidate.refNumber != null && existing.refNumber != candidate.refNumber) return@firstOrNull false
            val sameMerchant = InsightsEngine.normalizeMerchant(existing.merchant) == InsightsEngine.normalizeMerchant(candidate.merchant)
            val genericMerchant = isGeneric(existing.merchant) || isGeneric(candidate.merchant)
            when {
                // A v1.0.0 row: exactly midnight on this day, possibly with the direction the old parser guessed.
                existing.timestamp == startOfDay(existing.timestamp) && existing.timestamp in dayStart until dayEnd ->
                    sameMerchant || genericMerchant
                // Minutes apart, same direction: a second sender reporting the same payment, or a generic alert.
                existing.type == candidate.type && kotlin.math.abs(existing.timestamp - candidate.timestamp) <= windowMillis ->
                    sameMerchant || existing.bankName != candidate.bankName || genericMerchant
                // Hours apart is two payments, even to the same merchant: two coffees are two coffees.
                else -> false
            }
        }
```

- [ ] **Step 5: Make the importer's merge respect edits and splits**

`SmsImporter.kt`, in the `existing != null` branch of `process`, replace the two lines starting `val best = ...` and `val merged = ...` with:

```kotlin
                    val merged = if (existing.userEdited) {
                        // A person corrected this row: only fill in identifiers it lacks.
                        existing.copy(refNumber = existing.refNumber ?: candidate.refNumber, accountRef = existing.accountRef ?: candidate.accountRef)
                    } else {
                        // Keep the richer descriptive fields, take this parse's direction/flow/category (we hold the
                        // full SMS body), but never the amount or note: a split may have shrunk the amount on purpose.
                        repo.richer(existing, candidate).copy(
                            id = existing.id, smsHash = existing.smsHash, type = candidate.type, flow = candidate.flow, category = candidate.category,
                            amountPaise = existing.amountPaise, originalAmountPaise = existing.originalAmountPaise, note = existing.note,
                        )
                    }
```

(The following `if (merged != existing) repo.update(merged)` line stays.)

- [ ] **Step 6: Make splits record the bank amount**

Message the other session: "Editing AppViewModel.saveSplit (one line) for SMS plan Task 3: the linked debit keeps originalAmountPaise." Then in `saveSplit`, change

```kotlin
                c.transactions.update(t.copy(amountPaise = me, category = category, note = ...
```

to

```kotlin
                c.transactions.update(t.copy(amountPaise = me, originalAmountPaise = t.originalAmountPaise ?: t.amountPaise, category = category, note = ...
```

(leaving the rest of that line unchanged).

- [ ] **Step 7: Run the duplicate tests, then the full suite**

Run: `./build.sh --offline -q testDebugUnitTest`
Expected: exit 0. In particular `legacyMidnightRowIsMatchedOnRescan`, `rescanCorrectsTheFlowOfALegacyRow` and `sameAmountSameBankDifferentMerchantIsKept` still pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/pft/financetracker/data/local/Daos.kt app/src/main/java/com/pft/financetracker/data/repository/Repositories.kt app/src/main/java/com/pft/financetracker/data/sms/SmsImporter.kt app/src/main/java/com/pft/financetracker/ui/AppViewModel.kt app/src/test/java/com/pft/financetracker/DuplicateDetectionTest.kt
git commit -m "Keep same-day repeat payments, match split-shrunk rows, and never merge over a person's edits

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Read direction from the first account verb (#1, #2, #3)

**Files:**
- Modify: `app/src/main/java/com/pft/financetracker/domain/parser/Extractors.kt` (`TypeDetector`, `MerchantExtractor.patterns`, `RefExtractor.patterns`)
- Test: `app/src/test/java/com/pft/financetracker/SmsCorpus.kt`

**Interfaces:**
- Produces: `TypeDetector.primaryDirection(body: String): TransactionType?` (public, used only by `detect`).

- [ ] **Step 1: Add the failing corpus rows** (append to `SmsCorpus.cases`, under a `// ---- Task 4: direction ----` comment)

```kotlin
        CorpusCase("icici_upi_payee_credited", "AX-ICICIB", "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Mar-24; DAKSHIN CAFE credited. UPI:408812345678. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.", Expect.Saved(DEBIT, 24_000, Flow.EXPENSE, "dakshin", "408812345678")),
        CorpusCase("bob_dr_cr_abbrev", "VK-BOBTXN", "Rs.500 Dr. from A/C XXXXXX1234 and Cr. to swiggy@ybl. Ref:422312345678. AvlBal:Rs10000.00", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "swiggy")),
        CorpusCase("paid_with_cashback", "VM-AMZNPY", "You paid Rs 200 to Blinkit using Amazon Pay. Cashback of Rs 20 credited to your balance.", Expect.Saved(DEBIT, 20_000, Flow.EXPENSE, "blinkit")),
        CorpusCase("card_bill_payment_is_credit", "AX-ICICIB", "Payment of Rs 15,000.00 received towards your ICICI Bank Credit Card XX4455. Thank you.", Expect.Saved(CREDIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("credited_to_beneficiary", "VM-SBIINB", "INR 500.00 credited to beneficiary A/c XX9999 (JOHN) from your A/c XX1234 via IMPS. Ref 422312345655", Expect.Saved(DEBIT, 50_000)),
        CorpusCase("transferred_into_your_account", "VM-SBIINB", "Rs 2,000 transferred to your a/c XX1234 from RAHUL via IMPS. Ref 422312345666", Expect.Saved(CREDIT, 200_000)),
        CorpusCase("someone_paid_you", "VM-PHONPE", "Rahul paid you Rs 500 on PhonePe. UPI Ref 422312345677", Expect.Saved(CREDIT, 50_000)),
```

- [ ] **Step 2: Run to verify they fail**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.SmsCorpusTest"`
Expected: FAIL listing at least `icici_upi_payee_credited`, `bob_dr_cr_abbrev`, `paid_with_cashback`, `card_bill_payment_is_credit`, `credited_to_beneficiary`, `someone_paid_you`.

- [ ] **Step 3: Implement primary-verb direction**

`Extractors.kt`, inside `object TypeDetector`, add above `fun detect`:

```kotlin
    /**
     * The first verb that reports money moving is about the customer's own account; later mentions describe the
     * other side ("Acct debited ...; DAKSHIN CAFE credited", "Rs.500 Dr. ... Cr. to x@ybl", "You paid Rs 200 ...
     * Cashback of Rs 20 credited"). Counting keywords let those later mentions outvote the real direction.
     */
    private val primaryVerb = Regex(
        """\b(debited|spent|withdrawn|deducted|paid|sent|charged|transferred|credited|received|deposited|refunded|refund)\b|(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?\s*(dr|cr)\b""",
        RegexOption.IGNORE_CASE
    )
    private val toCounterparty = Regex("""^\s*to\s+(?:the\s+)?(?:beneficiary|payee|[\w.\-]+@[a-z]+)""", RegexOption.IGNORE_CASE)
    private val intoOwnAccount = Regex("""^\s*(?:to|into|in)\s+(?:your|ur)\b""", RegexOption.IGNORE_CASE)
    private val toYou = Regex("""^\s*you\b""", RegexOption.IGNORE_CASE)

    fun primaryDirection(body: String): TransactionType? {
        val m = primaryVerb.find(body) ?: return null
        val word = (m.groups[1]?.value ?: m.groups[2]?.value ?: return null).lowercase(Locale.ROOT)
        val after = body.substring(m.range.last + 1, minOf(body.length, m.range.last + 41))
        return when (word) {
            "paid" -> if (toYou.containsMatchIn(after)) TransactionType.CREDIT else TransactionType.DEBIT
            "debited", "spent", "withdrawn", "deducted", "sent", "charged", "dr" -> TransactionType.DEBIT
            "transferred" -> if (intoOwnAccount.containsMatchIn(after)) TransactionType.CREDIT else TransactionType.DEBIT
            "credited" -> if (toCounterparty.containsMatchIn(after)) TransactionType.DEBIT else TransactionType.CREDIT
            else -> TransactionType.CREDIT // received, deposited, refunded, refund, cr
        }
    }
```

In `detect`, insert this line immediately before the final `return when {`:

```kotlin
        primaryDirection(body)?.let { dir ->
            val margin = if (dir == TransactionType.DEBIT) debit - credit else credit - debit
            return Result(dir, maxOf(3, margin))
        }
```

- [ ] **Step 4: Name the ICICI payee and read "UPI:<ref>"**

`MerchantExtractor.patterns`: insert as the **first** element:

```kotlin
        // ICICI: "Acct XX123 debited for Rs 240.00 on 28-Mar-24; DAKSHIN CAFE credited." The payee precedes "credited".
        Regex(""";\s*([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)\s+credited\b"""),
```

`RefExtractor.patterns`: replace the single regex with:

```kotlin
        Regex("""\b(?:upi|imps|neft|rtgs)?\s*(?:ref(?:erence)?(?:\s*no\.?|\s*number|\s*id)?|rrn|txn\s*id|transaction\s*id|utr|upi)\s*[:#.\-]?\s*([A-Za-z0-9]{6,22})\b""", RegexOption.IGNORE_CASE),
```

- [ ] **Step 5: Run the corpus, then the full suite**

Run: `./build.sh --offline -q testDebugUnitTest`
Expected: exit 0. If an existing `SmsParserTest`/`ParserAccuracyTest` case now fails, read its body: if the first verb really is the customer's side, the old expectation was wrong and must be discussed with the user before changing it. Do not weaken `primaryDirection` to make it pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pft/financetracker/domain/parser/Extractors.kt app/src/test/java/com/pft/financetracker/SmsCorpus.kt
git commit -m "Read direction from the first verb about the customer's own account

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Never read an account number as the amount (#4)

**Files:**
- Modify: `app/src/main/java/com/pft/financetracker/domain/parser/Extractors.kt` (`AmountExtractor`)
- Test: `app/src/test/java/com/pft/financetracker/SmsCorpus.kt`

- [ ] **Step 1: Add the failing corpus rows** (`// ---- Task 5: amount ----`)

```kotlin
        CorpusCase("masked_acct_then_rs", "VM-KOTAKB", "A/c XX1234 Rs 750.00 debited to Swiggy on 12-08-24. UPI Ref 422312345678", Expect.Saved(DEBIT, 75_000, Flow.EXPENSE, "swiggy")),
        CorpusCase("plain_acct_then_rs", "VM-KOTAKB", "A/c 1234 Rs 750.00 debited to Swiggy. UPI Ref 422312345679", Expect.Saved(DEBIT, 75_000)),
        CorpusCase("card_word_before_rs", "VM-HDFCBK", "Rs.500 paid to card XX1234 towards Amazon. Ref 422312345680", Expect.Saved(DEBIT, 50_000)),
```

- [ ] **Step 2: Run to verify the first two fail**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.SmsCorpusTest"`
Expected: FAIL with `masked_acct_then_rs: paise 123400 != 75000` and `plain_acct_then_rs: paise 123400 != 75000`.

- [ ] **Step 3: Implement the guard**

In `AmountExtractor`, change the first pattern so a currency word inside another word ("Hrs", "Users") never starts an amount:

```kotlin
        Regex("""(?<![a-z])(?:inr|rs\.?|₹|rupees?)\s*:?\s*$NUM""", RegexOption.IGNORE_CASE),
```

Add below `balanceContext`:

```kotlin
    /** A number glued to a card/account mask ("XX1234 Rs 750", "A/c 1234 Rs 750") is the account, not the amount. */
    private val accountLead = Regex("""(?:[x*]|(?:a/?c|acct|account|card)(?:\s*(?:no\.?|number))?\s*[:#\-]?\s*)$""", RegexOption.IGNORE_CASE)
```

In `candidates`, directly after `for (m in rx.findAll(body)) {`, add:

```kotlin
                // Judge the digits themselves (group 1), not the currency word in front of them.
                val numStart = m.groups[1]!!.range.first
                if (accountLead.containsMatchIn(body.substring(maxOf(0, numStart - 20), numStart))) continue
```

- [ ] **Step 4: Run the full suite**

Run: `./build.sh --offline -q testDebugUnitTest`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pft/financetracker/domain/parser/Extractors.kt app/src/test/java/com/pft/financetracker/SmsCorpus.kt
git commit -m "Never take a masked or plain account number as the amount

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Ignore rules yield to a completed money movement (#5, #6)

Only one statement is final: "money did not move" ("has not been debited"). Every other ignore rule (OTP, balance-only, failed, future, promo, and so on) may drop a message only when it reports no *completed* movement with an amount. A verb preceded by "will be", "to be" or "if (the) amount" is not completed.

**Files:**
- Modify: `app/src/main/java/com/pft/financetracker/domain/parser/Extractors.kt` (`TextFilters`)
- Test: `app/src/test/java/com/pft/financetracker/SmsCorpus.kt`

- [ ] **Step 1: Add the failing corpus rows** (`// ---- Task 6: ignore rules ----`)

```kotlin
        CorpusCase("otp_footer_on_debit", "VM-HDFCBK", "Rs.500.00 debited from a/c **1234 to VPA zomato@hdfcbank (UPI Ref No 422312345678). Never share your OTP with anyone.", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "zomato")),
        CorpusCase("balance_leads", "VM-AXISBK", "Avl Bal Rs 10,000.00 after Rs 500.00 debited from A/c XX1234 at Zepto. Ref 422312345678", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE)),
        CorpusCase("refund_cancelled_order", "AD-HDFCBK", "Refund of Rs 499.00 for your cancelled order has been credited to your A/c XX1234. Ref 998877665544", Expect.Saved(CREDIT, 49_900, Flow.REFUND)),
        CorpusCase("reversal_of_failed_txn", "JD-SBIINB", "Your a/c XX1234 is credited with Rs 500.00 towards reversal of failed UPI txn Ref 123456789012 -SBI", Expect.Saved(CREDIT, 50_000, Flow.REFUND)),
        CorpusCase("debited_then_failed", "VM-HDFCBK", "Rs 500.00 debited from A/c XX1234 to swiggy@ybl but the txn failed. Amount will be reversed in 48 hrs. Ref 422312345677", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE)),
        CorpusCase("cashback_will_be_credited", "VM-PAYTMB", "Paid Rs 300 to Zomato via UPI. Cashback will be credited in 3 days.", Expect.Saved(DEBIT, 30_000, Flow.EXPENSE)),
        CorpusCase("not_debited", "VM-HDFCBK", "UPI txn of Rs 500 to Swiggy failed. Your account has not been debited.", Expect.Ignored),
        CorpusCase("failed_if_debited", "VM-HDFCBK", "UPI txn of Rs 500 failed. If amount debited, it will be reversed within 48 hrs.", Expect.Ignored),
        CorpusCase("refund_promised", "VM-HDFCBK", "Txn of Rs 500 failed. Amount will be refunded in 5-7 days.", Expect.Ignored),
```

- [ ] **Step 2: Run to verify the first six fail**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.SmsCorpusTest"`
Expected: FAIL listing `otp_footer_on_debit`, `balance_leads`, `refund_cancelled_order`, `reversal_of_failed_txn`, `debited_then_failed`, `cashback_will_be_credited` as "expected Saved, got Ignored(...)".

- [ ] **Step 3: Implement**

In `TextFilters.ignoreRules`, add as the **first** entry:

```kotlin
        "not_moved" to Regex("""\b(?:not|never)\s+(?:been\s+)?(?:debited|credited|deducted|charged|processed)\b|\bwasn'?t\s+(?:debited|charged)\b""", RegexOption.IGNORE_CASE),
```

and change the `"future"` entry's first group to `will be (debited|credited|deducted|refunded|reversed)`.

Replace `completedVerb`, `softRules` and `ignoreReason` with:

```kotlin
    /** A verb that only appears once money has actually moved. */
    val completedVerb = Regex("""\b(debited|credited|spent|withdrawn|deducted|paid|received|transferred|refunded|reversed)\b""", RegexOption.IGNORE_CASE)

    /** "will be debited", "to be refunded", "if amount debited": the verb describes something that has not happened. */
    private val notYet = Regex("""\b(?:will|shall|would|to\s+be|if|in\s+case)\b[^.;,]{0,20}$""", RegexOption.IGNORE_CASE)

    /** Only "money did not move" is final. Every other rule yields to a message that reports a completed movement. */
    private val hardRules = setOf("not_moved")

    fun hasCompletedMove(body: String): Boolean =
        completedVerb.findAll(body).any { m -> !notYet.containsMatchIn(body.substring(maxOf(0, m.range.first - 30), m.range.first)) } &&
            AmountExtractor.candidates(body).any { !it.isBalance }

    fun ignoreReason(body: String): String? {
        val completed = hasCompletedMove(body)
        for ((reason, rx) in ignoreRules) {
            if (rx.containsMatchIn(body)) {
                // Real transactions carry OTP footers, "failed ... reversed", "cancelled order" refunds, promo and
                // login wording. Never drop a message that clearly reports money moving; let scoring decide.
                if (reason !in hardRules && completed) continue
                return reason
            }
        }
        return null
    }
```

- [ ] **Step 4: Run the full suite**

Run: `./build.sh --offline -q testDebugUnitTest`
Expected: exit 0, including the existing `genuineFutureDebitStillIgnored`, `pureOtpStillIgnored`, `purePromoStillIgnored`, `failedTxnIsIgnored`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pft/financetracker/domain/parser/Extractors.kt app/src/test/java/com/pft/financetracker/SmsCorpus.kt
git commit -m "Let ignore rules drop a message only when no money has moved

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Import memory: identical alerts, rescans and user decisions (#7 part 2, #8)

The SMS log becomes the importer's record of what it has seen. Identical bodies on one day share `Hashing.smsHash` (unchanged, so the live receiver and inbox scan still agree). The first occurrence is stored under that hash; a later identical alert more than 5 minutes from every earlier one is stored as `hash#2`, `hash#3`, and so on. A log row the parser merely ignored is processed again, so a fixed parser recovers old misses on "Rescan the last 12 months". A row the user deleted or dismissed stays settled.

**Files:**
- Modify: `app/src/main/java/com/pft/financetracker/data/local/Daos.kt` (`SmsLogDao.getByHash`)
- Modify: `app/src/main/java/com/pft/financetracker/data/repository/Repositories.kt` (`SmsLogRepository.getByHash`)
- Modify: `app/src/main/java/com/pft/financetracker/data/sms/SmsImporter.kt` (`Outcomes`, `seen`, `process` head, `forgetDeleted`, `recordDismissed`)
- Modify: `app/src/main/java/com/pft/financetracker/ui/AppViewModel.kt` (`delete`, `dismissReview`)
- Create: `app/src/test/java/com/pft/financetracker/ImportMemoryTest.kt`

**Interfaces:**
- Produces: `Outcomes.DISMISSED_BY_USER`, `Outcomes.DELETED_BY_USER`, `Outcomes.USER_DECISIONS: Set<String>`; `SmsImporter.forgetDeleted(t: Transaction)`, `SmsImporter.recordDismissed(hash: String)`; `SmsLogRepository.getByHash(hash: String): SmsLogEntity?`; private `SmsImporter.seen(base, at): Seen` with `Seen.New(hash)` / `Seen.Settled(hash, log: SmsLogEntity?)` (Task 8 extends the `Settled` branch).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.data.sms.SmsImporter.Outcome
import com.pft.financetracker.domain.parser.Hashing
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The importer remembers what it has seen: repeats, rescans and the user's own decisions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class) // plain Application: FinanceApp would load SQLCipher natives
class ImportMemoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var log: SmsLogRepository
    private lateinit var importer: SmsImporter
    // Midday, so "five hours later" stays on the same calendar day.
    private val noon = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 12); set(java.util.Calendar.MINUTE, 0) }.timeInMillis

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        log = SmsLogRepository(db.smsLogDao())
        importer = SmsImporter(ApplicationProvider.getApplicationContext(), SmsParser(), repo, log, SettingsRepository(ApplicationProvider.getApplicationContext()))
    }

    @After fun tearDown() { db.close() }

    private val card = "Spent Rs.180 On HDFC Bank Card 1234 At Starbucks. Not You? Call 18002586161"
    private fun sms(body: String, at: Long, sender: String = "VM-HDFCBK") = SmsMessage(sender, body, at)

    @Test fun sameMessageSeenTwiceSecondsApartIsOneRow() = runBlocking {
        assertEquals(Outcome.INSERTED, importer.process(sms(card, noon)))
        assertEquals(Outcome.DUPLICATE, importer.process(sms(card, noon + 20_000)))
        assertEquals(1, repo.getAll().size)
    }

    @Test fun identicalAlertsHoursApartAreTwoPaymentsAndStayTwoOnRescan() = runBlocking {
        assertEquals(Outcome.INSERTED, importer.process(sms(card, noon - 3 * 3600_000)))
        assertEquals(Outcome.INSERTED, importer.process(sms(card, noon + 2 * 3600_000)))
        // "Rescan the last 12 months" feeds both again, oldest first.
        assertEquals(Outcome.DUPLICATE, importer.process(sms(card, noon - 3 * 3600_000)))
        assertEquals(Outcome.DUPLICATE, importer.process(sms(card, noon + 2 * 3600_000)))
        assertEquals(2, repo.getAll().size)
    }

    @Test fun deletedTransactionStaysDeletedOnRescan() = runBlocking {
        val body = "Rs.999.00 debited from a/c **1234 to VPA myntra@ybl (UPI Ref No 422399999999)."
        importer.process(sms(body, noon))
        val row = repo.getAll().single()
        repo.delete(row); importer.forgetDeleted(row)
        assertEquals(Outcome.DUPLICATE, importer.process(sms(body, noon)))
        assertEquals(0, repo.getAll().size)
    }

    @Test fun dismissedReviewItemStaysDismissedOnRescan() = runBlocking {
        val body = "Transaction alert: Rs 300 on your account XX1234."
        assertEquals(Outcome.REVIEW, importer.process(sms(body, noon, "VM-XYZBNK")))
        val item = repo.reviewQueue.first().single()
        repo.resolveReview(item.id); importer.recordDismissed(item.smsHash)
        assertEquals(Outcome.DUPLICATE, importer.process(sms(body, noon, "VM-XYZBNK")))
        assertEquals(0, repo.reviewQueue.first().size)
    }

    /** An older parser ignored this real debit (OTP footer). A rescan with the fixed parser must recover it. */
    @Test fun messageIgnoredByAnOlderParserIsRecoveredOnRescan() = runBlocking {
        val body = "Rs.500.00 debited from a/c **1234 to VPA zomato@hdfcbank (UPI Ref No 422312345678). Never share your OTP with anyone."
        log.log(SmsLogEntity(sender = "VM-HDFCBK", receivedAt = noon, outcome = "IGNORED", reason = "otp", amountPaise = null, type = null, transactionId = null, smsHash = Hashing.smsHash("VM-HDFCBK", body, noon), runId = 0))
        assertEquals(Outcome.INSERTED, importer.process(sms(body, noon)))
        assertEquals(50_000L, repo.getAll().single().amountPaise)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.ImportMemoryTest"`
Expected: compile failure on `forgetDeleted` / `recordDismissed`.

- [ ] **Step 3: Add the log lookup**

`Daos.kt`, in `SmsLogDao`:

```kotlin
    @Query("SELECT * FROM sms_log WHERE smsHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): SmsLogEntity?
```

`Repositories.kt`, in `SmsLogRepository`: `suspend fun getByHash(hash: String) = dao.getByHash(hash)`

- [ ] **Step 4: Implement the importer memory**

`SmsImporter.kt`: in `object Outcomes` add:

```kotlin
    /** Reasons recording the user's own decision. A rescan never overrides these. */
    const val DISMISSED_BY_USER = "dismissed_by_user"
    const val DELETED_BY_USER = "deleted_by_user"
    val USER_DECISIONS = setOf(DISMISSED_BY_USER, DELETED_BY_USER)
```

Inside `class SmsImporter`, add above `process`:

```kotlin
    /** The live receiver and an inbox scan see one message within seconds; identical alerts further apart are separate payments. */
    private val sameMessageWindowMs = 5 * 60_000L

    private sealed interface Seen {
        /** Not handled yet (or only ignored by the parser): process it and store it under [hash]. */
        data class New(val hash: String) : Seen
        /** Handled and settled: saved, queued, a duplicate, or decided by the user. */
        data class Settled(val hash: String, val log: SmsLogEntity?) : Seen
    }

    /**
     * Identical bodies on one day share [Hashing.smsHash]. The first is stored under it; the n-th distinct occurrence
     * (more than [sameMessageWindowMs] from the earlier ones) under "hash#n", so two identical card alerts are two
     * payments. A message the parser only ignored is new again, so a better parser recovers it on rescan.
     */
    private suspend fun seen(base: String, at: Long): Seen {
        var n = 1
        while (true) {
            val hash = if (n == 1) base else "$base#$n"
            // No log row: new, unless a pre-log (v1.0.0) transaction or review item already holds this hash.
            val logged = log.getByHash(hash) ?: return if (n == 1 && repo.hashSeen(hash)) Seen.Settled(hash, null) else Seen.New(hash)
            if (kotlin.math.abs(logged.receivedAt - at) <= sameMessageWindowMs) {
                val onlyParserIgnored = logged.outcome == Outcomes.IGNORED && logged.reason !in Outcomes.USER_DECISIONS
                return if (onlyParserIgnored) Seen.New(hash) else Seen.Settled(hash, logged)
            }
            n++
        }
    }

    /** The user deleted [t]. Record it so a rescan does not import the same SMS again. */
    suspend fun forgetDeleted(t: Transaction) {
        t.smsHash?.let { log.updateOutcome(it, Outcomes.IGNORED, Outcomes.DELETED_BY_USER, null) }
    }

    /** The user said a review item is not a transaction. */
    suspend fun recordDismissed(hash: String) = log.updateOutcome(hash, Outcomes.IGNORED, Outcomes.DISMISSED_BY_USER, null)
```

In `process`, replace the first two statements (`val hash = Hashing.smsHash(...)` and the `if (repo.hashSeen(hash)) return Outcome.DUPLICATE` line and its comment) with:

```kotlin
        val hash = when (val s = seen(Hashing.smsHash(sms.sender, sms.body, sms.receivedAt), sms.receivedAt)) {
            is Seen.New -> s.hash
            is Seen.Settled -> return Outcome.DUPLICATE
        }
```

- [ ] **Step 5: Route the UI through it**

`AppViewModel.kt`:

```kotlin
    fun delete(t: Transaction) = viewModelScope.launch {
        c.transactions.delete(t)
        c.importer.forgetDeleted(t)
    }
```

In `dismissReview`, replace `r?.let { c.smsLog.updateOutcome(it.smsHash, "IGNORED", "dismissed_by_user", null) }` with `r?.let { c.importer.recordDismissed(it.smsHash) }`.

- [ ] **Step 6: Run the full suite**

Run: `./build.sh --offline -q testDebugUnitTest`
Expected: exit 0, including `DuplicateDetectionTest.rescanCorrectsTheFlowOfALegacyRow` (its legacy row has hash "legacy" and no log row, so the SMS is `New` and reaches the duplicate merge as before).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pft/financetracker/data/local/Daos.kt app/src/main/java/com/pft/financetracker/data/repository/Repositories.kt app/src/main/java/com/pft/financetracker/data/sms/SmsImporter.kt app/src/main/java/com/pft/financetracker/ui/AppViewModel.kt app/src/test/java/com/pft/financetracker/ImportMemoryTest.kt
git commit -m "Remember every SMS decision: repeat alerts count, deletions and dismissals survive a rescan

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Repair rows the old parser stored wrongly

Users already have ICICI debits stored as income. On rescan, a settled SMS whose saved row disagrees with the fixed parser on direction or amount gets corrected in place. This never applies to a row a person edited or a split owns.

**Files:**
- Modify: `app/src/main/java/com/pft/financetracker/data/sms/SmsImporter.kt` (`repairIfWrong`, `Seen.Settled` branch)
- Test: `app/src/test/java/com/pft/financetracker/ImportMemoryTest.kt`

**Interfaces:**
- Consumes: `Seen.Settled(hash, log)` (Task 7), `Transaction.userEdited` / `originalAmountPaise` (Task 2).

- [ ] **Step 1: Write the failing tests** (append inside `ImportMemoryTest`; add imports `com.pft.financetracker.domain.model.Category`, `com.pft.financetracker.domain.model.Flow`, `com.pft.financetracker.domain.model.Transaction`, `com.pft.financetracker.domain.model.TransactionType`)

```kotlin
    private val icici = "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Mar-24; DAKSHIN CAFE credited. UPI:408812345678. Call 18002662 for dispute."

    /** What the pre-fix parser stored for [icici]: a ₹240 "income" from "dispute". */
    private suspend fun storeStaleIcici(userEdited: Boolean): Long {
        val hash = Hashing.smsHash("AX-ICICIB", icici, noon)
        val id = repo.insert(Transaction(amountPaise = 24_000, type = TransactionType.CREDIT, merchant = "dispute", category = Category.INCOME, timestamp = noon,
            bankName = "ICICI Bank", accountRef = "123", source = Transaction.Source.SMS, flow = Flow.INCOME, smsHash = hash, userEdited = userEdited))
        log.log(SmsLogEntity(sender = "AX-ICICIB", receivedAt = noon, outcome = "SAVED", reason = "dispute", amountPaise = 24_000, type = "CREDIT", transactionId = id, smsHash = hash, runId = 0))
        return id
    }

    @Test fun rescanRepairsARowTheOldParserGotBackwards() = runBlocking {
        val id = storeStaleIcici(userEdited = false)
        assertEquals(Outcome.DUPLICATE, importer.process(sms(icici, noon, "AX-ICICIB")))
        val t = repo.getById(id)!!
        assertEquals(TransactionType.DEBIT, t.type)
        assertEquals(Flow.EXPENSE, t.flow)
        assertEquals(24_000L, t.amountPaise)
        assertEquals(1, repo.getAll().size)
    }

    @Test fun rescanNeverRepairsARowAPersonEdited() = runBlocking {
        val id = storeStaleIcici(userEdited = true)
        importer.process(sms(icici, noon, "AX-ICICIB"))
        assertEquals(TransactionType.CREDIT, repo.getById(id)!!.type)
    }
```

- [ ] **Step 2: Run to verify the first fails**

Run: `./build.sh --offline -q testDebugUnitTest --tests "com.pft.financetracker.ImportMemoryTest"`
Expected: FAIL in `rescanRepairsARowTheOldParserGotBackwards`: `expected:<DEBIT> but was:<CREDIT>`.

- [ ] **Step 3: Implement**

`SmsImporter.kt`, add below `recordDismissed`:

```kotlin
    /**
     * A settled SMS whose saved row an older parser got wrong (e.g. an ICICI UPI debit stored as income). Re-parse it and
     * correct direction, amount, merchant and flow in place, unless a person edited the row or a split owns its amount.
     */
    private suspend fun repairIfWrong(sms: SmsMessage, logged: SmsLogEntity) {
        if (logged.outcome != Outcomes.SAVED) return
        val existing = logged.transactionId?.let { repo.getById(it) } ?: return
        if (existing.userEdited || existing.originalAmountPaise != null || existing.source != Transaction.Source.SMS) return
        val p = (parser.parse(sms) as? ParseResult.Success)?.transaction ?: return
        if (p.type == existing.type && p.amountPaise == existing.amountPaise) return
        val category = Categorizer.categorize(p.merchant, p.type, p.bankName)
        repo.update(
            existing.copy(
                amountPaise = p.amountPaise, type = p.type, merchant = p.merchant, category = category,
                flow = FlowClassifier.classify(p.type, sms.body, p.merchant, category), refNumber = existing.refNumber ?: p.refNumber,
            )
        )
        log.updateOutcome(logged.smsHash, Outcomes.SAVED, "repaired_${p.merchant}", existing.id)
    }
```

In `process`, change the `Settled` branch to:

```kotlin
            is Seen.Settled -> { s.log?.let { repairIfWrong(sms, it) }; return Outcome.DUPLICATE }
```

- [ ] **Step 4: Run the full suite**

Run: `./build.sh --offline -q testDebugUnitTest`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pft/financetracker/data/sms/SmsImporter.kt app/src/test/java/com/pft/financetracker/ImportMemoryTest.kt
git commit -m "Correct rows an older parser stored backwards when their SMS is rescanned

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Whole-app verification and release notes

**Files:**
- Modify: `CHANGELOG.md` (new `## [Unreleased]` section above `## [1.1.0]`)
- Modify: `docs/DEVICE-TEST-v1.1.md` (append a dated "SMS accuracy hardening" section)

- [ ] **Step 1: Full test suite and debug build** (announce to the other session)

Run: `./build.sh --offline -q testDebugUnitTest assembleDebug`
Expected: exit 0. Record the total from `app/build/test-results/testDebugUnitTest/*.xml`: sum of `tests=`, 0 failures.

- [ ] **Step 2: Device check on the shared emulator** (ask the other session for the emulator first; do NOT clear data or reseed)

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
```

(If the per-ABI APK name differs, `ls app/build/outputs/apk/debug/` and use the x86_64 one.) Launch `com.pft.financetracker.debug`, open Settings, then "Rescan the last 12 months". Read the dashboard with `adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml` + `adb -s emulator-5554 pull /sdcard/ui.xml` (FLAG_SECURE blanks screenshots).
Expected: net spend for September 2026 is still **₹4,218**, and the row counts are unchanged: no seeded message is re-imported, duplicated or lost. Seeded #2 (card bill payment) may now show as a *transfer in* rather than a transfer out. That is correct, and net spend is unaffected.

- [ ] **Step 3: Release notes**

Add to `CHANGELOG.md` above `## [1.1.0]`:

```markdown
## [Unreleased]

### SMS accuracy
- Direction comes from the first verb about your own account, so "Acct debited ...; SHOP credited" (ICICI), "Rs 500 Dr. ... Cr. to x@ybl" (Bank of Baroda) and "You paid ... cashback credited" are spends, not income or refunds.
- An account number next to "Rs" ("A/c XX1234 Rs 750") is never taken as the amount.
- Real debits with an OTP footer, balance-first alerts, refunds for cancelled orders and reversals of failed payments are no longer dropped. "Has not been debited" is still ignored.
- Two identical card alerts on one day are two payments. A payment reported by a second app after you split it is recognised as the same payment.
- "Rescan the last 12 months" no longer brings back transactions you deleted or review items you dismissed. It recovers messages an older version ignored, and corrects rows an older version stored backwards, except rows you edited yourself.
- Database version 3 (adds two columns; existing data migrates in place).
```

Append to `docs/DEVICE-TEST-v1.1.md` a `## SMS accuracy hardening (<date>)` section with the test total from Step 1 and the dashboard figures read in Step 2.

- [ ] **Step 4: Commit**

```bash
git add CHANGELOG.md docs/DEVICE-TEST-v1.1.md
git commit -m "Record the SMS accuracy hardening in the changelog and device test

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Known limits (deliberately not fixed)

- Wording no bank in the corpus uses can still be misread. The defence is the review queue plus one new corpus row per report.
- The ₹20 cashback inside a "You paid ₹200 … cashback ₹20" message is not recorded separately. The ₹200 spend is now correct.
- Rows imported by v1.0.0 have hashes that no longer match their SMS, so deleting one of them is not remembered across a rescan.
- Bank alerts sent from ordinary phone numbers are still skipped (personal SMS privacy).
- A balance alert that also mentions the last transaction ("Balance Rs 5,000. Last debit Rs 500") is now imported. If the bank also sent the real debit alert more than ten minutes apart, both are counted. The duplicate sweep in Settings can merge them.
