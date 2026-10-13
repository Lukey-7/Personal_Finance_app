# v1.5.1 Plan: Fixes from the v1.5.0 health check

**Status:** built and released as v1.5.1 on 13 Oct 2026, with section G (any AI provider, by key). 780 unit tests green.
**Source:** the health check on 12 Oct 2026. 762 unit tests green, release APK audit clean, the v1.5.0 build walked
through on the emulator with no crashes, and a code review of `v1.4.0..v1.5.0`. This plan fixes what that check found.
**Executor:** `superpowers:executing-plans`, inline in one session. Each fix starts with a failing test.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done and verified

---

## 1. Invariants

1. **No database change.** Room stays at version 7, no migration.
2. **No new dependency or permission.** The only new network endpoints are the AI providers in section G, which the
   owner asked for on 13 Oct 2026. Each is contacted only after the owner picks it and saves a key, and only when an AI
   feature runs. The telemetry baselines must not grow.
3. Money stays integer paise, formatted only through `Rupees.format` / `money()` / `approxMoney`.
4. Every write a person can trigger is all-or-nothing (the `Ledger` promise), and a failure shows a message instead of
   crashing.
5. Nothing touches the owner's real data or the shared `.debug` install. Emulator tests use `-PappIdSuffix=.v151`.

## 2. Decisions (my defaults, to confirm or change)

| # | Question | Default |
|---|---|---|
| D1 | Where to build | Branch `v1.5.1-fixes` from `main` (`d04d4ac`) in a worktree at `../PF-v151`, with the git-ignored local files copied in |
| D2 | Version | `1.5.1`, `appVersionCode = 11` |
| D3 | Merging when a twin has changed since the scan | Skip that pair and say so ("1 pair changed since the scan, scan again"). No guessing |
| D4 | Ask tile wording | "Answered by ChatGPT" when Ask uses ChatGPT, "Answered on this phone" when it is switched off in Settings › AI |
| D5 | The two "maybe" findings (split freeze, quick add) | Fix them in this release: both are small and both can lose or block a payment |
| D6 | Providers offered (G) | OpenAI, Anthropic (Claude), Google Gemini, AWS Bedrock, Groq, OpenRouter, Mistral, DeepSeek, a model on this phone or Wi-Fi (Ollama, LM Studio, llama.cpp), and "Other (OpenAI-compatible)" with any https URL. Llama, Qwen, Gemma, gpt-oss and other open models run through Groq, OpenRouter, Bedrock or a local server |
| D7 | One provider or several at once | **One active provider** for every AI feature (summary, splits, Ask). Each provider keeps its own saved key, so switching back needs no retyping. Per-task routing waits until there is a reason for it |
| D8 | AWS sign-in | A Bedrock API key (bearer token) and a region. IAM access keys (SigV4 signing) are left for later |
| D9 | Plain http | Only to `localhost` / `127.0.0.1` (a model running in Termux on the phone). Every other address must be https, so no cleartext leaves the phone |
| D10 | Built-in key | Unchanged: the `-personal` build still ships the owner's OpenAI key, and OpenAI stays the default provider |
| D11 | New libraries | None. Every adapter uses `HttpsURLConnection` + `org.json`, like today's client |

## 3. Fixes

### A. Merging duplicates never deletes the only copy (high)

`TransactionRepository.mergeDuplicates` (Repositories.kt:98) writes back the two rows captured when the scan ran, then
deletes the "drop" row. If the "keep" row was deleted or edited after the scan, the payment is lost or the edit is
overwritten. `SamePayment.combine` also drops a category the user corrected on the dropped row.

- [x] Test: keep row deleted after the scan → merge leaves the drop row in place.
- [x] Test: keep row edited after the scan (note, category, refund pairing) → the edit survives the merge.
- [x] Test: drop row has `userEdited = true` with a corrected category, keep row does not → the merged row keeps the
      user's category.
- [x] Fix: inside the `Ledger.mergeTwins` transaction, re-read both rows by id. Skip the pair when either is gone or the
      two no longer count as the same payment (`SamePayment`). Combine the fresh rows, not the snapshot.
- [x] Fix: `SamePayment.combine` prefers the user-edited side's category and note.
- [x] `mergeTwins` returns how many pairs merged and how many were skipped. The Duplicates screen shows "Merged 3.
      1 pair changed since the scan. Scan again to check it."

### B. A statement import is all-or-nothing and never crashes (high)

`StatementImporter.commit` (StatementImporter.kt:61) does its inserts one by one outside a transaction, and
`AppViewModel` (line 633) has no error handling, so a failure part way leaves a half import and crashes the app.

- [x] Test (fake DAO that throws on the Nth insert): after the failure there is no batch row, no imported payments and
      no matches.
- [x] Fix: add `Ledger.importStatement(preview)`, which runs `statements.commit` inside `changed { }`, the same way
      `undoImport` already does.
- [x] Fix: the view model catches the failure and sets `StatementUiState.Error("Couldn't save this statement. Nothing
      was added.")`, so the screen leaves "Reading".
- [ ] ~~Test: view model state goes `Reading → Error`…~~ Deviation: not written. `AppViewModel` is an
      `AndroidViewModel` over the whole app container and has no test harness; the all-or-nothing rule is tested in
      `LedgerTest.aStatementThatFailsPartWayLeavesNothingBehind`, and the catch is three lines.

### C. The Ask tile says where answers come from (medium, privacy copy)

The Tools tile says "Answered on this phone" (ToolsScreen.kt:114), but Ask sends a summary of payments, payees
included, to ChatGPT by default (`askUseOpenAi` defaults to true). The Ask screen itself already says this.

- [x] Fix: the tile reads `askUseOpenAi` and a saved key. It says "Answered by ChatGPT" when both are on, otherwise
      "Answered on this phone".
- [x] Fix: the stale header comment in `OpenAiClient.kt:16-19` ("only aggregates, no merchant names") says what
      `chat()` really sends.
- [x] Test: the tile subtitle logic (pure function in `ui/model`) for the on / off / no-key cases.

### D. SBI UPI alert from a sender without a prefix (check first)

On the emulator, "Dear UPI user A/C X1234 debited by 250.0 on date 09Oct26 trf to SWIGGY …" from sender `SBIUPI` sits
in review as "No amount found". The SMS test corpus covers this exact text, but only from `VM-SBIUPI`.

- [x] Test: add corpus cases for the same body from `SBIUPI`, `VM-SBIUPI-S` and `AD-SBIUPI-T`.
- [x] Result: the three new cases pass, so the parser already reads a bare `SBIUPI`. The review item on the
      emulator was left over from an older version. No parser change. (Original step: if the test fails: fix the sender handling in the parser so the bare code is read the same way. If it passes,
      the review item is left over from an older version: record that here and change nothing.
- [x] Either way: re-check the item (fresh `.v151` install: the review inbox has 1 item, not 2) with the review inbox's "Check again" on the emulator (on the `.v151` install).

### E. Saving a manual split cannot freeze behind the AI check (medium, unconfirmed)

`saveSplit` runs inside `Ledger.together` (a database transaction) and calls `SplitEngine.releaseForManual`, which waits
for the engine's lock. `SplitEngine.run` holds that lock through a whole run, AI calls included (up to 90 s), and writes
to the database while holding it. A save during a run can stall, or deadlock, on SQLCipher's single write connection.

- [x] Deviation: the test (`SplitEngineLockTest`) checks the shape that used to hang, lock → transaction →
      engine action, finishing within 5 s, and that an action waits for the holder and then runs. A real engine run
      with a slow fake AI needs a full split fixture; not written. (Original: that writes after a delay, then save a manual split. Both finish within a time
      limit.
- [x] Fix: take the engine lock first, then open the transaction (`splitEngine.holding { ledger.together { … } }`, only when the split takes over a payment), so
      the lock is never waited on while the database is held. Apply the same order to `accept`, `reject`,
      `linkSettlement` and `deleteManual`.
- [x] Fix: those four engine actions each run their writes in one transaction (today a crash between writes can leave
      a link without its share update).

### F. Smaller fixes

- [x] Quick add (`QuickAddActivity.kt:54-68`): set `done = true` only after the write succeeds, show "Couldn't save"
      on failure, and run the write in a scope that outlives the sheet so closing it mid-save never drops the payment.
- [x] `FinanceApp.afterChange` (`FinanceApp.kt:75-77`): rethrow `CancellationException` instead of swallowing it in
      `runCatching`, so a cancelled AI run stops instead of carrying on to the widget refresh.

### G. Any AI provider, by key (new)

Today every AI feature calls `OpenAiClient` directly (summary in `AppViewModel`, splits through `OpenAiSplitProvider`,
Ask in `AppViewModel.ask`). Following the provider-abstraction approach the owner shared: the features depend on one
contract that says what the app needs, each vendor is an adapter behind it, and the app picks the adapter from
Settings. Kotlin interfaces exist at runtime, so the NestJS token/Symbol step is not needed: the app's own container
(`FinanceApp`) hands the features a `() -> LlmProvider?`.

```
Summary · Splits · Ask
        ↓
   LlmProvider            complete(LlmRequest): LlmResult   (system, turns, wantJson, maxTokens, temperature)
        ↑ implements
OpenAiCompatible   Anthropic   Bedrock          ← adapters translate the request for one vendor's API
  (OpenAI, Gemini, Groq, OpenRouter, Mistral, DeepSeek, local, other)
        ↑ picked by
   AiProviders.current(settings)                ← the strategy: active provider + model + its key
```

- [x] `domain/ai/Llm.kt`: `LlmProvider`, `LlmRequest`, `LlmResult` (`Ok(text)` / `Error(message)`), and
      `ProviderKind` presets (id, display name, default base URL, default model, key hint, whether a key is needed).
- [x] `domain/ai/HttpJson.kt`: one POST helper shared by all adapters (timeouts, error body → message, key-shaped
      strings scrubbed from every error, http refused unless the host is localhost).
- [x] `OpenAiCompatibleProvider(baseUrl, model, key)`: `/chat/completions`. JSON mode via `response_format`; if a
      server rejects that field, retry once without it. Covers OpenAI, Gemini (its OpenAI-compatible endpoint), Groq,
      OpenRouter, Mistral, DeepSeek, Ollama, LM Studio, llama.cpp and any other compatible server.
- [x] `AnthropicProvider(model, key)`: Messages API (`x-api-key`, `anthropic-version`, `system` field, turns). JSON
      mode by instruction plus taking the first `{…}` block from the reply.
- [x] `BedrockProvider(region, model, apiKey)`: Converse API with `Authorization: Bearer`. Same JSON handling.
- [x] Tests for each adapter against a fake HTTP server (request shape, headers, JSON mode, error message, no key in
      errors), and for the localhost-only http rule.
- [x] Settings: active provider, model and base URL/region in plain prefs; one key per provider in the encrypted prefs
      (`api_key_<id>`). Today's OpenAI key stays where it is, so nothing is lost on upgrade. `clearAll` clears them all.
- [x] Callers switch to `LlmProvider`: monthly summary, `SplitAiProvider` (renamed from `OpenAiSplitProvider`), Ask.
      `OpenAiClient` keeps only `buildPayload` (the "exact payload" sheet) and moves next to the summary.
- [x] The AI answer cache key includes provider and model, so switching never replays another model's answer.
- [x] Settings › AI: "Provider" picker, model field (default filled in), base URL for local/other, region for Bedrock,
      the key field labelled for that provider, and "Test connection" (one tiny request, shows the reply or the error).
- [x] Copy: every "OpenAI" / "ChatGPT" in the UI names the active provider ("Claude answers from a summary of your
      payments…"). The privacy text says the data goes to the provider you picked. Fix C uses the same name.
- [x] `network_security_config.xml`: cleartext allowed for `localhost` and `127.0.0.1` only.
- [x] `scripts/audit_apk.py`: the allowed endpoints become the preset list; anything else is printed as a plain string for a person to judge, as before (the script only ever failed on
      telemetry growth). HOW-IT-WORKS has no AI section, so the provider list went into README only. README and
      `docs/HOW-IT-WORKS.md` list each provider and what each AI feature sends.

## 4. Verify and release

- [x] `bash build.sh testDebugUnitTest`: all green (762 + the new ones). Stop Gradle afterwards.
- [x] `bash build.sh assembleDebug -PappIdSuffix=.v151`, install beside the others on `fintest`. Deviation: on it, the provider picker, Bedrock form, Ask tile copy and
      Test connection (OpenAI replied) were checked; the merge, import and split cases are covered by unit tests only.
      A focus-loss ANR seen twice on the slow debug build led to closing the picker with its hide animation; six
      switches afterwards, no ANR. Original: merge a
      duplicate after deleting the keep row (payment stays), import a statement, check the Ask tile with ChatGPT on and
      off, save a manual split during "Check again now". No crashes in `logcat -b crash`.
- [x] Bump to 1.5.1 / code 11, add the dated `CHANGELOG.md` section.
- [x] `assembleRelease` with `FINTRACK_EMBED_KEY=1`, run `py -3 scripts/audit_apk.py` on the arm64 APK: baselines
      unchanged.
- [x] Update the "State" line in `CLAUDE.md` (v1.5.1, DB still 7, the new test count) and point the emulator notes at
      `py -3` (plain `python` isn't on the PATH on this PC).
- [x] Merge `--no-ff` into `main` ("Merge v1.5.1: …"), tag `v1.5.1`, push as Lukey-7 and create the GitHub release with
      the arm64 `-personal` APK and its SHA-256, as in `CLAUDE.md`.

## 5. Not in this release

- No new features beyond G, no copy changes beyond C and G.
- Several providers at once (per-task routing, fallback to a second provider) and AWS IAM access keys (D7, D8).
- The "431% more than 1–12 Sep" line on Home is correct for the emulator data (₹10,620 against ₹2,000). Large
  percentages on thin history read oddly, but changing that is a design choice, not a fix.
