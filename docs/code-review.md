# Code review — Stler Money Android

**Date:** 2026-09-11  
**Scope:** full repository (`com.stler.money`), read-only review  
**Baseline:** `docs/tech-spec.md` v0.1, `README.md`, application code under `app/`  
**Verdict:** solid v1 architecture (MVVM + Room-first writes + WorkManager sync). The domain and UI are coherent and clearly ported from Money PWA / Tasks Android. The highest risks are **data integrity around sync and spreadsheet switching**, **missing automated tests**, and a few **spec/implementation gaps** that will surprise users.

This is a review, not a changelog. Findings are ordered by impact.

---

## 1. What the app is

Personal finance tracker. Google Sheets (`db_money`) is the remote source of truth; Room is the offline cache and sync queue. Kotlin, Jetpack Compose, Hilt, WorkManager, Credential Manager. Min SDK 26, target 36, `applicationId` `com.stler.money`.

Package layout matches the spec: `ui/` → ViewModels → repositories → Room / Sheets. Mutations write Room first, enqueue `sync_queue`, UI observes `Flow`s. That design is the right one for this product.

---

## 2. Strengths

- **Clear layering.** Repositories own write + enqueue; ViewModels stay thin; `BaseViewModel.safeLaunch` gives a consistent user-visible error path.
- **Sheets mapping is careful.** `SheetsMapper` handles Sheets serial dates/times (`dateStr` / `timeStr`) and boolean defaults (`is_expense` default true). That is real production knowledge, not a naive `toString()`.
- **Sync push has the Money-specific dedup** (`SyncWorker.push`, spec §16.2): INSERT/UPDATE collapse per entity; DELETE keeps a separate key so it is not swallowed.
- **Pending-safety on pull.** IDs still in the queue are not overwritten; `deleteNotIn(remote + pending)` preserves local unsent work.
- **Category join table** is maintained next to the comma-separated `categoryIds` column — correct split for Sheets round-trip vs Room queries.
- **Category reorder** only upserts rows whose `sortOrder` actually changed (avoids a 28-row sync storm).
- **Auth + backup.** `drive.file` only; `auth_prefs` excluded from Auto Backup / device transfer.
- **UI polish already paid for.** Filter sheet vs top-bar highlight, tap-to-filter navigation (`popUpTo` inclusive so SavedStateHandle is not stale), FAB color vs `primaryContainer`, exchange-rate refresh on `MainViewModel` init (documented as a real on-device bug fix).
- **CI** builds a signed APK on `v*` tags with keystore from secrets.

---

## 3. Findings

Severity: **P0** ship-blocker / data loss · **P1** likely user-visible corruption or crash · **P2** correctness / maintainability · **P3** polish / spec drift.

### P0 — Spreadsheet switch can push the old queue onto the new file

`SettingsViewModel.switchSpreadsheet()`:

1. Writes the new spreadsheet id.
2. Clears Room transactions / accounts / categories.
3. Triggers sync.

It does **not** clear `sync_queue`.

`signOut()` *does* call `syncQueueDao.deleteAll()`. Switch does not.

If the user had pending INSERT/UPDATE/DELETE for spreadsheet A, then picked spreadsheet B, `SyncWorker.push()` will apply those payloads to B. Pull then mixes two datasets. This is silent corruption of the user’s Google Sheet.

**Fix:** delete the queue (and ideally wait until the previous worker is not running) *before* changing `spreadsheetId`. Treat switch like a logout of the data plane.

---

### P0 — Transaction write and balance side-effect are not one atomic unit

`TransactionRepositoryImpl.createTransaction` / `updateTransaction` / `deleteTransaction`:

1. Room transaction: upsert/delete the row + category refs.
2. Enqueue the transaction op.
3. `applyDelta` → `AccountRepository.adjustBalance` (separate Room writes + extra queue rows).

A crash, process death, or exception between (1) and (3) leaves **denormalised `Account.balance` wrong** with no automatic repair (spec explicitly forbids recomputing balance from transactions). Update path is worse: `reverseDelta(old)` then `applyDelta(new)` as two independent adjustments — a failure in the middle double-applies one side.

`adjustBalance` no-ops if the account row is missing (`?: return`), so a transfer to a deleted/unknown `toAccountId` silently drops the destination delta.

**Fix:** one `db.withTransaction { … }` covering transaction row, category refs, both account balance writes, and all `sync_queue` inserts. Fail the whole mutation if either account is missing.

---

### P1 — No automated tests for money-critical logic

`app/build.gradle.kts` declares JUnit / Espresso / Compose test deps. There is **no** `src/test` or `src/androidTest` source. CI (`release.yml`) only runs `assembleRelease`.

The logic that most needs tests is pure and cheap:

- `computeBalanceDelta` (expense / income / transfer / debt ± repayment)
- queue dedup keys (INSERT+UPDATE vs DELETE)
- `convertToBase`
- `Transaction.matchesFilter`
- `SheetsMapper` date/time serial numbers
- category delete-with-transfer + `distinct()`

Without these, the P0 bugs above are unguarded, and the next Sheets-format edge case will ship on a `v*` tag.

---

### P1 — `convertToBase` fails open as 1:1

```kotlin
val rate = rates[currency] ?: return amount
```

Unknown currency or empty `rates` (failed jsDelivr fetch, cold start before refresh) stores **wrong `amountBase`**. Analytics (`MonthlyView`, `YearlyChartView`) sums `amountBase` forever. The team already hit this on device (RUB shown as EUR). Startup refresh in `MainViewModel` reduced the window; it did not close it:

- `refresh()` returns silently on network failure (`?: return`).
- Existing rows are **never recomputed** when the user changes base currency in Settings (`setBaseCurrency` only updates the settings blob + rate cache).
- Historical `amountBase` stays in the old base; charts lie after a EUR → USD switch.

**Fix:** do not save `amountBase` when the rate is missing (block save / show error). On base-currency change, recompute `amountBase` for all local transactions (or compute analytics from `amount` + live rates, and treat `amountBase` as a cache).

---

### P1 — Pull can apply an empty remote snapshot

`fetchAllAndSave` maps `valueRanges.firstOrNull()?.values?.drop(1)`. If `values` is present but empty (or header-only) and the queue is empty, `deleteNotIn(empty-or-header-ids)` deletes local rows. That is correct when the sheet is truly empty. It is catastrophic if `batchGet` returns a 200 with an empty `values` for a transient/partial response or a newly created spreadsheet whose seed `batchUpdate` failed.

`createSpreadsheet()`:

- Does not check the seed `batchUpdate` HTTP status.
- OkHttp `Response` objects are not closed with `.use {}`.
- Room is seeded even if the Sheets write failed.

Then the first successful pull of an empty sheet wipes the local seed.

**Fix:** abort pull if the transactions sheet has no header row; treat seed write failure as sign-in failure; close responses; do not upsert Room until Sheets seed succeeds.

---

### P1 — Crash: default account when every account is archived

```kotlin
accountId = accounts.first { !it.archived }.id
```

in `TransactionFormSheet` `LaunchedEffect(accounts)`. `first { }` throws `NoSuchElementException` if the list is non-empty but all archived. `safeLaunch` does not wrap composable effects.

**Fix:** `firstOrNull { !it.archived } ?: accounts.first()`.

---

### P1 — OkHttp authenticator can retry 401 forever

`NetworkModule` authenticator always calls `refreshToken()` and rebuilds the request. There is no `responseCount` / `priorResponse` guard. A revoked grant that still “refreshes” to a token Google rejects will loop until the call times out.

`runBlocking` on every request (including `getAccessToken` → possible Identity call) is acceptable on OkHttp’s thread pool, but it couples network latency to token refresh on the same client. Fine for v1; not fine if Identity ever reused this `OkHttpClient`.

HttpLoggingInterceptor `Level.BASIC` is on in **release** as well. URLs include spreadsheet ids.

**Fix:** refuse authenticate after one prior 401; `Level.NONE` (or `BASIC` only on debug).

---

### P2 — `fallbackToDestructiveMigration(dropAllTables = true)`

`DatabaseModule` will wipe `money.db` on any unmigrated version bump, including pending `sync_queue`. Spec says “explicit migrations from v1”. Today version is 1, so this is a footgun for the *next* schema change, not a current production bug.

`exportSchema = true` but **no `app/schemas/` in the repo** — CI cannot fail on accidental schema drift.

---

### P2 — Category delete vs queue ordering

`deleteCategoryWithTransfer` updates transactions and deletes the category inside `withTransaction`, but `enqueue("category", "DELETE", …)` is **after** the transaction. Transaction UPDATEs are enqueued *inside*. If the process dies after commit, local category is gone, DELETE was never queued, next pull **restores** the deleted category from Sheets.

Also: no foreign key from `transaction_categories` to `transactions`. `deleteNotIn` on transactions can leave orphan join rows (queries still join, so mostly harmless, but the table can grow junk).

---

### P2 — Money as `Double`

Amounts, limits, and balances are `Double`. Binary floating point will drift (`24` → `24.0` was already noticed on category reorder). For a personal tracker this is usually invisible; for round-trip equality with the PWA it can cause noisy UPDATE diffs. `BigDecimal` or integer minor units would be the durable model; out of scope for a hotfix, but do not add more `Double` arithmetic in analytics without rounding for display (some paths already `roundToLong`).

---

### P2 — Sync worker policy vs spec

Spec §7: periodic work `ExistingPeriodicWorkPolicy.KEEP`. Code: `UPDATE`. Every process start reschedules the 30-minute cadence (can delay or bunch sync). Not corruption, but not what the spec promised.

Soft-delete on Sheets (`values:clear`) leaves blank rows. `findRowNumber` skips blank ids, so INSERTs append after holes. The sheet grows empty rows over time. Same as PWA/Tasks; worth a periodic compact later.

`MAX_RETRIES = 5` plus `if (retryCount >= MAX_RETRIES) continue` then `deleteExhausted`: exhausted items vanish with no user signal. A failed INSERT is a silent data loss from the user’s point of view (local row still exists until a pull that… pending id protects it until the queue row is deleted). After `deleteExhausted`, the next pull **overwrites or deletes** the local entity. That is how both sibling apps work; it should be visible in UI (“N changes dropped”).

---

### P2 — Auth / token storage

Access token lives in plaintext DataStore (`auth_prefs`). Excluded from backup, which is the important control. Encrypted DataStore would be nicer, not required for `drive.file` personal data.

`AuthViewModel` treats signed-in as **non-blank access token** (`isSignedIn`). `AuthData.isSignedIn` is **non-blank spreadsheet id**. After `NeedsAuthorization`, token is empty (correct). After a successful token but failed spreadsheet create (`createSpreadsheet` → `""`), the user can enter `MainScreen` with an empty sheet id; `SyncWorker` then no-ops with `Result.success()`. Empty home, no error.

`MainActivity` comment: deep links not handled. Manifest still registers `stlermoney://`. Harmless, but any such URI just opens the auth/main gate.

---

### P2 — ExchangeRateRepository `init { runBlocking { … } }`

Blocks the thread that first injects the singleton (often main, during `MainViewModel` creation). DataStore reads are usually fast; under contention this is ANR material. Prefer `viewModelScope` / application scope collection.

`JSONObject(fetched as Map<*, *>)` is an unchecked cast; it works for `Map<String, Double>` today.

---

### P2 — UI / product gaps vs spec and README

| Spec / README | Code |
|---|---|
| Last-used account in DataStore (`money-last-account-id`) | Not implemented |
| Category grid sorted by usage frequency | Insertion / `sortOrder` only |
| Transaction list paging (`PAGE_SIZE` / load-more) | Comment in `TransactionsScreen`: “Infinite scroll is still follow-up”; full list in memory + in-memory filter |
| `collapsed_account_groups` in `settings!A1` | Local DataStore only (`AccountsPreferences` documents the deviation) |
| Account delete + confirm (§8.2) | Archive checkbox only (matches live PWA; spec is stale) |
| Time on transactions | Stored; form always `00:00` on create, no time picker |
| `AppContainer.kt` in spec tree | Does not exist (Hilt only — fine) |
| Strings / i18n | Almost all UI copy hardcoded in composables; `strings.xml` is only `app_name`. `oauth.xml` holds the web client id |

`deleteAccount()` exists on the repository and is unused by UI (dead API).

`TransactionFormSheet` embeds `LazyVerticalGrid` inside a vertically scrolling `Column` — nested scroll, height capped at 200.dp. Works, but fling/a11y is worse than a single `LazyColumn` with a grid item.

Analytics yearly/monthly aggregations run in composition (`for` loops over all transactions). Fine for thousands of rows; will jank at tens of thousands. Move to `ViewModel` + `combine`.

---

### P3 — Misc

- `generateId`: 8 hex chars from UUID (~32 bits). Collision risk is theoretical for a personal ledger; still possible if two clients seed at once. Acceptable.
- Seed `sortOrder` starts at 1; drag-reorder writes 0-based indexes. Harmless mixed orders until the user reorders once.
- Feedback URL is a hard-coded Apps Script deployment. Same pattern as Tasks; rotating the script requires an app release.
- `isMinifyEnabled = false` for release — matches spec / Tasks; larger APK, easier reverse engineering of client ids (already in resources).
- `FeedbackViewModel` does not extend `BaseViewModel` (inconsistent error plumbing).
- Overlay stack in `MainScreen` is a `List<String>` of magic names (`"settings"`, …), not a type.
- Compose: `hiltViewModel(backStackEntry)` in `MainScreen` to share `TransactionsViewModel` with the filter sheet is clever and correct; it is also easy to break on a navigation refactor — a short KDoc on `Screen.kt` already exists, keep it.

---

## 4. Security (brief)

In scope for a personal Sheets app: token at rest, backup exclusion, OAuth scope, logging.

- Scope `drive.file` is the right least-privilege choice. README correctly warns that a PWA-created `db_money` may be invisible to this OAuth client.
- Token not in Auto Backup: good.
- Do not log tokens (BASIC is OK-ish; do not ever switch to BODY in release).
- Web client id in `oauth.xml` is expected for Credential Manager; it is not a secret, but it identifies the Cloud project.
- No local network cleartext traffic; HTTPS only.

Not reviewed: Google Cloud OAuth consent screen configuration, Play App Signing SHA-1 vs `release.yml` keystore (README already flags `DEVELOPER_ERROR` on mismatched SHA-1).

---

## 5. Suggested fix order

1. Clear `sync_queue` (and cancel/replace WorkManager work) on spreadsheet switch.  
2. Wrap transaction + balance + queue writes in one Room transaction; fail if accounts are missing.  
3. Guard pull against empty/header-less sheets; fail sign-in if seed `batchUpdate` fails.  
4. Unit tests for delta, dedup, mapper, `convertToBase`, category transfer.  
5. Stop 1:1 fallback; recompute `amountBase` on base-currency change.  
6. `firstOrNull` for default account; authenticator retry cap; debug-only HTTP logs.  
7. Last-account DataStore + usage-sorted category grid if you still want spec parity.  
8. Drop `fallbackToDestructiveMigration` before Room version 2; commit schemas.

---

## 6. Summary table

| Area | Status |
|---|---|
| Architecture / DI | Good |
| UI / navigation | Good, a few nested-scroll and in-memory-list limits |
| Sync protocol | Right design; spreadsheet switch and empty-pull are the holes |
| Balance invariant | Implemented, not atomic |
| Auth | Works; empty-spreadsheet SignedIn edge case |
| Tests / CI quality gate | Missing |
| Spec fidelity | High; listed gaps are mostly documented in code comments as deliberate or follow-up |

The codebase reads as a careful port, not a prototype. The P0 items should be fixed before relying on Settings → Change spreadsheet or on long offline sessions with process death during a save.
