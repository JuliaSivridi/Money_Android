# Stler Money Android — Technical Specification

**Version:** 0.1 (draft) · **Date:** 2026-09-10
**Repository:** D:\Projects\Money-Android
**Stack:** Kotlin · Jetpack Compose · Room · Hilt · WorkManager · Google Sheets API v4
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

> This spec is derived from two sources: the domain model and sync design of
> **Money PWA** (the web app this rewrites, [github.com/JuliaSivridi/Money_PWA](https://github.com/JuliaSivridi/Money_PWA)),
> and the native architecture patterns of the sibling app **Tasks Android**
> (`com.stler.tasks`, [github.com/JuliaSivridi/Tasks_Android](https://github.com/JuliaSivridi/Tasks_Android),
> same author, same Google Sheets-as-backend approach). Where the two disagree,
> this document follows Tasks Android's proven native patterns, and Money PWA's
> data/sync semantics.
>
> **Deliberate deviation from Tasks Android:** this app ships **bottom navigation
> only**. Tasks Android supports a user-togglable sidebar/bottom-nav mode; Money
> Android does not implement the sidebar mode or the Settings toggle that selects
> it — there is exactly one navigation shape.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture](#3-architecture)
4. [Package / Folder Structure](#4-package--folder-structure)
5. [Data Model](#5-data-model)
6. [Authentication & First-Launch Setup](#6-authentication--first-launch-setup)
7. [Synchronization](#7-synchronization)
8. [UI Screens](#8-ui-screens)
9. [TransactionItem Component](#9-transactionitem-component)
10. [TransactionFormSheet](#10-transactionformsheet)
11. [Theme & Colors](#11-theme--colors)
12. [Navigation](#12-navigation)
13. [Loading & Empty States](#13-loading--empty-states)
14. [CI/CD & Build](#14-cicd--build)
15. [First-Time Setup (New Developer)](#15-first-time-setup-new-developer)
16. [Key Algorithms](#16-key-algorithms)
17. [Design Decisions & Remaining Open Items](#17-design-decisions--remaining-open-items)

---

## 1. Overview

Stler Money is a personal finance tracker for Android — a native rewrite of Money
PWA sharing the same per-user `db_money` Google Sheets spreadsheet as its backend.
There is no dedicated backend server; the Sheets API v4 is the remote database.
Room serves as the local cache/offline queue, exactly as Tasks Android uses it
for `db_tasks`.

**Key design goals (inherited from both siblings):**
- Google Sheets as the single source of truth — no proprietary cloud service.
- Full offline support via Room + a sync queue (create/update/delete land in
  Room synchronously; a background worker drains them to Sheets).
- Reactive UI — screens observe Room `Flow`s; writes update local state
  immediately, network sync is fire-and-forget.
- MVVM + Repository architecture with Hilt DI throughout, matching Tasks Android.
- **Bottom navigation only.** No sidebar, no drawer, no navigation-mode setting.
  This removes an entire settings section and a `navMode` DataStore key that
  Tasks Android has to maintain.

**Differences from the Money PWA domain that matter for the native build:**
- Multi-category transactions (`category_ids`, up to 2, first = primary) carry
  over unchanged — same Sheets column, same analytics rule (index 0 only).
- The denormalised `Account.balance` field and `adjustBalance()` side-effect
  pattern carry over unchanged: balances are never recomputed by summing
  transactions on the fly.
- Analytics charts (month bar, yearly composed chart, category donut) have no
  direct Compose equivalent to Recharts; charting approach is decided in
  [§17.1](#17-design-decisions--remaining-open-items) (Vico + a hand-rolled
  `Canvas` donut).

---

## 2. Tech Stack

Matches Tasks Android's stack, minus Calendar API, Glance widgets, and the
`reorderable` library is kept (categories need drag-to-reorder, same as PWA's
`@dnd-kit`). Exchange-rate fetching is new (PWA has it; Tasks does not).

| Layer | Library | Notes |
|---|---|---|
| Language | Kotlin | JVM toolchain 11 |
| UI | Jetpack Compose + Material 3 | Compose-only, no XML layouts |
| DI | Hilt | KSP processor |
| Local DB | Room | Explicit migrations from v1 |
| Networking | Retrofit + OkHttp | Bearer token interceptor + 401 Authenticator |
| Serialization | Gson | Retrofit converter + SyncQueue payload serialization |
| Background | WorkManager (Hilt) | Periodic sync (30 min) + one-off manual sync |
| Auth | CredentialManager + Identity API | Google Sign-In + scope authorization |
| Preferences | DataStore (`datastore-preferences`) | Token, user info, spreadsheet ID, last-used account, base currency |
| Image loading | Coil (`coil-compose`) | User avatar in Menu screen |
| Drag & drop | `reorderable` (sh.calvin) | CategoriesScreen drag-to-reorder |
| Charts | Vico (bar/composed) + hand-rolled `Canvas` (donut) — see [§17.1](#17-design-decisions--remaining-open-items) | Month bar / yearly / category donut |
| Coroutines | `kotlinx.coroutines-android` + `-play-services` | |
| Navigation | Navigation Compose | Single NavHost inside MainScreen |
| Lifecycle | Lifecycle ViewModel / Runtime | `WhileSubscribed(5000)` sharing strategy |

**Build config:** `applicationId = "com.stler.money"`, `minSdk = 26`,
`targetSdk = 36`. KSP with `room.schemaLocation = "$projectDir/schemas"`.
Signing via environment variables `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD` (mirrors Tasks Android exactly — only wired when
`KEYSTORE_PATH` is non-blank).

---

## 3. Architecture

**Pattern:** MVVM + Repository (identical shape to Tasks Android).

```
┌──────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  Composables ← ViewModel (StateFlow) ← Repository            │
└──────────────────────────────────────────────────────────────┘
         │                       │
         │ suspend fun            │ Flow<List<T>>
         ▼                       ▼
┌──────────────────────┐  ┌──────────────────────────────────┐
│ TransactionRepository │  │  ExchangeRateRepository           │
│ AccountRepository     │  │  (jsDelivr currency-api + Room    │
│ CategoryRepository    │  │   cache; no SyncQueue involved)   │
└──────────┬────────────┘  └──────────────────────────────────┘
           │
    ┌──────┴──────────────────────────────┐
    │               Room DAOs              │
    │  TransactionDao / AccountDao /       │
    │  CategoryDao / SyncQueueDao          │
    └──────┬──────────────────────────────┘
           │                          ▲
    SyncQueue                         │
           │                     SyncWorker (WorkManager)
           │                          │
           ▼                          │
    Google Sheets API v4
    (SheetsApi via Retrofit)
```

### Write path (e.g. creating a transaction)

1. ViewModel calls `transactionRepository.createTransaction(input)`.
2. ViewModel (`TransactionFormViewModel`) computes `amount_base` via
   `convertToBase()` *before* calling the repository — needs the current
   exchange-rate map, read from `ExchangeRateRepository`'s cached Room/
   DataStore snapshot, never a blocking network call — see
   [§16.5](#165-converttobaseamount-currency-basecurrency-rates-samecurrencyhistory)
   for the fallback chain when there's no live rate.
3. Steps 3–5 all run inside **one `db.withTransaction { }`** (code-review
   fix — these used to be three separate, un-atomic writes; a crash between
   them left the denormalised `Account.balance` silently wrong forever, with
   no automatic repair, since recomputing it from transaction history is
   explicitly forbidden — see below):
   - Repository writes the transaction to Room (`transactionDao.upsert(entity)`).
   - Repository enqueues a `SyncQueueEntity` (INSERT) for the transaction via
     `syncQueueDao.enqueue()`.
   - Repository calls `accountRepository.adjustBalance(accountId, delta)` —
     same `computeBalanceDelta` table as the PWA (see
     [§16.1](#161-transaction-balance-delta)); this itself does a Room write
     (account balance) plus its own SyncQueue entry, both enlisted in the
     same outer transaction. `adjustBalance` **throws** if the account row
     is missing (rather than silently no-op'ing), rolling back the entire
     mutation — a transfer to a deleted/unknown account fails loudly instead
     of dropping the destination side's delta.
4. Room `Flow` emits → all observing ViewModels recompose → UI updates
   immediately (offline-first, same guarantee as Tasks Android).
5. On next sync trigger, `SyncWorker.push()` drains the queue to Sheets API.

**Balance side-effects must always go through `AccountRepository.adjustBalance()`.**
Never recompute `balance` by summing transactions inline — this mirrors the PWA's
explicit warning in its Account data model.

### Read path

1. Screen collects `StateFlow` from ViewModel (`stateIn(WhileSubscribed(5000))`).
2. ViewModel observes Room DAO `Flow` (e.g. `transactionDao.observeAllByDate()`).
3. `SyncWorker.pull()` calls `sheetsApi.batchGet(spreadsheetId, listOf("transactions", "accounts", "categories"))`.
4. Pending-safety: entity IDs with unsent local changes (from `SyncQueueDao`) are
   excluded from being overwritten by the pull — same invariant as both siblings.
5. Room emits new data → ViewModels recompose → UI updates.

### Error handling

- All repository operations wrapped in `runCatching` / expose `Result<T>`.
- ViewModels extend `BaseViewModel` exposing `uiError: SharedFlow<String>` via
  `safeLaunch { }` (same helper as Tasks Android).
- `SyncWorker` returns `Result.retry()` on exception for up to 4 attempts (5th
  returns `Result.failure()`).
- `SyncQueueDao.deleteExhausted(maxRetries = 5)` removes items that failed 5+
  times — matches both siblings' retry ceiling.
- On 401 from OkHttp, the `Authenticator` calls `tokenProvider.refreshToken()`
  once; returns null if refresh fails.

---

## 4. Package / Folder Structure

```
com.stler.money/
├── auth/                     GoogleAuthRepository, AuthPreferences (DataStore), AuthData
├── data/
│   ├── local/
│   │   ├── dao/              TransactionDao, AccountDao, CategoryDao, SyncQueueDao
│   │   ├── entity/            Room entities + toDomain() / toEntity() mappers
│   │   └── MoneyDatabase.kt   RoomDatabase, migration objects
│   ├── remote/
│   │   ├── SheetsApi.kt       Retrofit interface (batchGet, append, batchUpdate, clear)
│   │   ├── SheetsMapper.kt    Row ↔ Entity conversions
│   │   ├── ExchangeRateApi.kt Retrofit interface (jsDelivr currency-api)
│   │   ├── TokenProvider.kt   Interface implemented by GoogleAuthRepository
│   │   └── dto/                Gson DTOs for Sheets, Drive responses
│   └── repository/
│       ├── TransactionRepository.kt / Impl.kt   Mutations + balance side-effects + pull
│       ├── AccountRepository.kt / Impl.kt       CRUD + adjustBalance
│       ├── CategoryRepository.kt / Impl.kt      CRUD + reorder + deleteWithTransfer
│       └── ExchangeRateRepository.kt / Impl.kt  fetch + cache rates
├── di/
│   ├── DatabaseModule.kt      Room, all DAOs
│   └── NetworkModule.kt       OkHttpClient, Retrofit, SheetsApi, Gson
├── domain/model/               Transaction, Account, Category, DebtSummary, SyncState
├── sync/
│   ├── SyncState.kt            Sealed class: Idle / Syncing / Pending(count)
│   ├── SyncManager.kt          WorkManager scheduling + syncState Flow
│   └── SyncWorker.kt           Push + pull
├── ui/
│   ├── auth/                   AuthScreen, AuthViewModel, AuthUiState
│   ├── main/                   MainScreen, MainViewModel, MoneyTopAppBar
│   ├── transactions/            TransactionsScreen + ViewModel, FilterMenu
│   ├── accounts/                AccountsScreen + ViewModel, AccountFormSheet
│   ├── categories/              CategoriesScreen (drag-reorder) + ViewModel, CategoryFormSheet
│   ├── analytics/                AnalyticsScreen + ViewModel, MonthBarChart, YearlyChart,
│   │                              MonthlyView, CategoryDonut
│   ├── transaction/              TransactionFormSheet, TransactionFormViewModel,
│   │                              TransactionItem, NumericKeyboard, pickers
│   ├── menu/                     MenuScreen (avatar, Settings/Help/Feedback/About/Sign out)
│   ├── settings/                  SettingsScreen + SettingsViewModel
│   ├── help/                      HelpScreen (static content)
│   ├── feedback/                  FeedbackScreen + FeedbackViewModel
│   ├── navigation/                Screen.kt (route constants)
│   ├── theme/                     Color.kt, Theme.kt, Type.kt
│   └── util/                       EmptyState, ShimmerTransactionList, ErrorSnackbarEffect
├── AppContainer.kt             Manual DI container for non-Hilt singletons (if needed)
├── MainActivity.kt             Auth gate — no deep link handler (decided against, §12)
└── MoneyApplication.kt         Application; initializes SyncManager
```

**Notable absence vs. Tasks Android:** no `widget/` package, no `CalendarApi` /
`CalendarMapper` / `CalendarRepository`, no `SidebarMenu` / `SidebarPreferences`
/ `navMode` anywhere in `ui/main` or `auth/FeatureFlags.kt`-equivalent. There is
no folder/label feature-flag system either — Money PWA has no concept of
disabling Accounts/Categories/Analytics, so no `FeatureFlags` data class exists.

---

## 5. Data Model

Ported directly from Money PWA's own data model, with Kotlin types and Room
column types substituted for TS/Dexie ones.

### 5.1 Transaction

| Field | Type | Description |
|---|---|---|
| id | String (PK) | `txn_` + 8 hex chars |
| date | String | ISO date `yyyy-MM-dd` |
| type | String | `expense` \| `income` \| `transfer` \| `debt_lent` \| `debt_borrowed` |
| amount | Double | Amount in the account's currency |
| currency | String | ISO currency code |
| amountBase | Double | Amount converted to base currency (computed on create/update) |
| accountId | String | Source account ID |
| categoryIds | String | Comma-separated, up to 2 IDs; index 0 = primary (analytics), index 1 = tag |
| toAccountId | String | Destination account (transfers only; `""` otherwise) |
| toAmount | Double | Amount received in destination account |
| toCurrency | String | Destination currency (transfers only) |
| debtRefId | String | For debt repayments: ID of the originating debt transaction |
| comment | String | Free text comment / payee |
| createdAt | String | ISO 8601 instant |
| updatedAt | String | ISO 8601 instant |

**Debt invariant:** identical to PWA — a debt transaction with `debtRefId = ""`
is *open*. "Mark as repaid" creates a new transaction with the same `type` and
`debtRefId` = the original's `id`; the balance effect is reversed.

**Balance delta table** — see [§16.1](#161-transaction-balance-delta).

### 5.2 Account

| Field | Type | Description |
|---|---|---|
| id | String (PK) | `acc_` + 8 hex chars |
| name | String | Display name |
| currency | String | ISO currency code |
| type | String | `card` \| `cash` \| `savings` \| `investment` |
| color | String | Hex color; fallback `#6b7280` |
| balance | Double | Denormalised, maintained only by `adjustBalance()` |
| archived | Boolean | Hidden from active lists when true |
| sortOrder | Int | Display order within type section |
| createdAt / updatedAt | String | ISO 8601 |

### 5.3 Category

| Field | Type | Description |
|---|---|---|
| id | String (PK) | `cat_` + 8 hex chars |
| name | String | Display name |
| icon | String | Lucide icon name, ported to Compose (see [§17.2](#17-design-decisions--remaining-open-items) re: icon set) |
| color | String | Hex color; fallback `#6b7280` |
| isExpense | Boolean | Usable for expense transactions; **Sheets default TRUE** |
| expenseLimit | Double | Monthly spending limit (0 = none) |
| isIncome | Boolean | Usable for income transactions; default FALSE |
| incomeLimit | Double | Monthly income target (0 = none) |
| sortOrder | Int | User-draggable position |
| createdAt / updatedAt | String | ISO 8601 |

**Delete invariant:** identical to PWA — deleting a category requires a target
category; referencing transactions get the ID replaced (preserving array
position) and deduplicated. See [§16.7](#167-category-delete-with-transfer).

### 5.4 SyncQueue Entry

| Field | Type | Description |
|---|---|---|
| id | Long (PK, autoincrement) | |
| entityType | String | `"transaction"`, `"account"`, or `"category"` |
| operation | String | `"INSERT"`, `"UPDATE"`, or `"DELETE"` |
| entityId | String | ID of the affected entity |
| payloadJson | String | Gson-serialized entity snapshot; empty for DELETE |
| createdAt | String | ISO 8601 — needed for the dedup-by-latest rule, see [§16.2](#162-queue-deduplication-flush) |
| retryCount | Int | Incremented on failure; deleted at ≥ 5 |

### 5.5 DebtSummary (computed, not persisted)

| Field | Type | Description |
|---|---|---|
| counterpart | String | From transaction `comment` |
| type | String | `lent` \| `borrowed` |
| totalAmount | Double | |
| currency | String | |
| transactionIds | List\<String\> | |

### 5.6 Room Database

**Database name:** `money.db` · **Room version:** 1 (greenfield — no legacy
Dexie migration baggage to replicate; Money PWA's `MoneyDB` → `MoneyDB2`
migration history does not need porting).

#### Table: `transactions`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| date | TEXT NOT NULL | indexed |
| type | TEXT NOT NULL | indexed |
| amount | REAL NOT NULL | |
| currency | TEXT NOT NULL | |
| amountBase | REAL NOT NULL | |
| accountId | TEXT NOT NULL | indexed |
| categoryIds | TEXT NOT NULL | comma-separated |
| toAccountId | TEXT NOT NULL | Default `''` |
| toAmount | REAL NOT NULL | Default 0 |
| toCurrency | TEXT NOT NULL | Default `''` |
| debtRefId | TEXT NOT NULL | Default `''`; indexed |
| comment | TEXT NOT NULL | |
| createdAt | TEXT NOT NULL | |
| updatedAt | TEXT NOT NULL | indexed |

**Indices:** `date`, `type`, `accountId`, `debtRefId`, `updatedAt`.
Unlike Dexie's `*category_ids` multi-entry index, Room has no native
multi-value index. **Decided:** `categoryIds` (comma-separated) stays on the
`transactions` row as-is — it's the field that round-trips to Sheets column I
verbatim, so it has to exist in that shape regardless. Alongside it, Room
maintains a small **derived** join table:

```
transaction_categories(transactionId TEXT, categoryId TEXT, position INTEGER)
  PRIMARY KEY (transactionId, categoryId)
  indexed on categoryId
```

This is *not* a redesign of the main table — nothing about `transactions`
changes shape, and no migration of existing data is implied beyond "also
populate this new table." `TransactionRepository` writes both in the same
Room `@Transaction` whenever `categoryIds` changes (create/update/delete), so
they never drift. All category-filtered queries (the Category section in
`FilterMenu`, `CategoryDonut`'s breakdown, the donut-tap-to-filter nav arg)
join through this table instead of `LIKE`-scanning the comma string; the
comma string itself is only read/written at the Sheets-mapping boundary.

#### Table: `accounts`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| name | TEXT NOT NULL | |
| currency | TEXT NOT NULL | Default `'EUR'` |
| type | TEXT NOT NULL | indexed |
| color | TEXT NOT NULL | Default `'#6b7280'` |
| balance | REAL NOT NULL | |
| archived | INTEGER NOT NULL | 0/1; indexed |
| sortOrder | INTEGER NOT NULL | |
| createdAt | TEXT NOT NULL | |
| updatedAt | TEXT NOT NULL | indexed |

#### Table: `categories`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| name | TEXT NOT NULL | |
| icon | TEXT NOT NULL | Default `'Tag'` |
| color | TEXT NOT NULL | Default `'#6b7280'` |
| isExpense | INTEGER NOT NULL | Default 1 |
| expenseLimit | REAL NOT NULL | Default 0 |
| isIncome | INTEGER NOT NULL | Default 0 |
| incomeLimit | REAL NOT NULL | Default 0 |
| sortOrder | INTEGER NOT NULL | indexed |
| createdAt | TEXT NOT NULL | |
| updatedAt | TEXT NOT NULL | |

#### Table: `sync_queue`

| Column | SQLite Type | Notes |
|---|---|---|
| id | INTEGER PRIMARY KEY AUTOINCREMENT | |
| entityType | TEXT NOT NULL | |
| operation | TEXT NOT NULL | |
| entityId | TEXT NOT NULL | |
| payloadJson | TEXT NOT NULL | |
| createdAt | TEXT NOT NULL | |
| retryCount | INTEGER NOT NULL | Default 0 |

### 5.7 Google Sheets Schema

Identical column layout to Money PWA — **do not change these ranges**, since
the spreadsheet is shared byte-for-byte with the web app (same `db_money`
file, same account). All reads use `valueRenderOption = UNFORMATTED_VALUE`;
all writes use `valueInputOption = RAW`, matching Tasks Android's convention
(not PWA's, which uses the same RAW option, so no conflict).

#### Sheet: `transactions` (`A:P`)

| Col | Field | Format |
|---|---|---|
| A | id | `txn_` prefixed |
| B | date | `yyyy-MM-dd` |
| C | time | `HH:MM`, default `00:00` |
| D | type | `expense` \| `income` \| `transfer` \| `debt_lent` \| `debt_borrowed` |
| E | amount | decimal string |
| F | currency | ISO code |
| G | amount_base | decimal string |
| H | account_id | `acc_` prefixed |
| I | category_ids | comma-separated |
| J | to_account_id | `acc_` prefixed or empty |
| K | to_amount | decimal string or `0` |
| L | to_currency | ISO code or empty |
| M | debt_ref_id | transaction ID or empty |
| N | comment | string |
| O | created_at | ISO 8601 |
| P | updated_at | ISO 8601 |

#### Sheet: `accounts` (`A:J`)

Same 10 columns as PWA: `id, name, currency, type, balance, archived,
sort_order, created_at, updated_at, color`.

#### Sheet: `categories` (`A:K`)

Same 11 columns as PWA: `id, name, icon, color, is_expense, expense_limit,
is_income, income_limit, sort_order, created_at, updated_at`.

**Icon column caveat:** PWA stores `lucide-react` icon names (e.g.
`ShoppingCart`). The Android app maps these to the Lucide-for-Compose icon
set chosen in [§17.2](#17-design-decisions--remaining-open-items) so existing
spreadsheets stay readable by both apps. Do not invent a new icon-naming
scheme; translate at the mapper layer (`SheetsMapper` ↔ a `MoneyIcons` lookup
table), keeping the Sheets value as the lucide name always.

#### Sheet: `settings`

Single cell `A1`, JSON blob — same shape as PWA:
```json
{ "base_currency": "EUR", "collapsed_account_groups": ["savings"], "exchange_rates": { "USD": 1.08, "RUB": 95.4 } }
```
`collapsed_account_groups` and `exchange_rates` are read/written exactly as
the PWA does, so collapse-state and cached rates stay in sync across devices.

**Row-number lookup for UPDATE/DELETE:** same `findRowNumber()` pattern as
Tasks Android's `SheetsMapper` — iterate rows, skip header, return 1-based row
number on `row[0] == id`; null if not found (operation skipped, not retried
forever).

---

## 6. Authentication & First-Launch Setup

Follows Tasks Android's `CredentialManager` + `Identity` flow exactly, with a
narrower scope set (no Calendar).

### 6.1 OAuth Scopes

```
https://www.googleapis.com/auth/drive.file
```

Single scope — `drive.file` covers Sheets API access to `db_money` (files this
app created or the user picked). No `calendar.readonly` / `calendar.events`
(Money has no calendar integration).

### 6.2 Sign-In Flow

1. `AuthViewModel` init — reads `GoogleAuthRepository.isSignedIn` (`first()`).
   Emits `SignedIn` or `SignedOut`.
2. User taps "Sign in with Google" → `startSignIn(context)` →
   `GoogleAuthRepository.signIn(context)`.
3. **Step 1 — ID Token:** `CredentialManager.getCredential()` with
   `GetGoogleIdOption`.
4. **Step 2 — Scope authorization:** `Identity.getAuthorizationClient(context).authorize(...)`
   requesting `drive.file` only.
5. If `hasResolution()` is true → user info saved to DataStore (no
   token/spreadsheetId yet) → `AuthUiState.NeedsAuthorization(pendingIntent)` →
   `AuthScreen` launches via `ActivityResultContracts.StartIntentSenderForResult`
   → `finalizeAuth(intent)` on `RESULT_OK`.
6. If no resolution needed, `accessToken` is extracted directly.
7. `completeSignIn(token, credential)` / `finalizeAuth(intent)` → Drive search
   for `db_money`. Found → use its ID. Not found → `createSpreadsheet(token)`.
   `completeSignIn` requires the resulting `spreadsheetId` to be non-blank
   before continuing (code-review fix) — a blank id means `createSpreadsheet`
   failed (bad HTTP status, or the seed write in §6.3 step 2 failed), and
   used to still get saved as a "signed in" state with nothing to sync
   against: `SyncWorker` no-ops on a blank id, landing the user on a
   silently empty `MainScreen` instead of a sign-in error. Now throws,
   which `signIn()`'s `runCatching` turns into a real `AuthUiState.Error`.
8. `authPreferences.saveAll()` persists `accessToken`, `tokenExpiry` (now + 1h),
   `spreadsheetId`, `spreadsheetName`, `userEmail`, `userName`, `userAvatarUrl`.
9. `AuthUiState.SignedIn` → `MainActivity` shows `MainScreen`.

### 6.3 First-Launch Spreadsheet Creation

`createSpreadsheet(token)` via raw OkHttp:

1. POST `https://sheets.googleapis.com/v4/spreadsheets` — creates spreadsheet
   with **4 named sheets**: `transactions` (0), `accounts` (1), `categories`
   (2), `settings` (3). (Money PWA has no `meta` sheet — Tasks does; do not add
   one here.) Checks the response status and closes it (`.use {}`) —
   code-review fix, this response's body/connection previously leaked and
   its status went unchecked on the *second* call below (this one already
   checked status, just didn't close the response).
2. POST `.../values:batchUpdate` — writes header rows plus full onboarding
   seed data (§6.4). **Status now checked** (code-review fix): this call's
   HTTP status went unchecked entirely before, so a failed seed write (403,
   500, whatever) still fell through to step 3 as if it had succeeded —
   Room ended up "seeded" with data Sheets never actually received. A
   failed status here aborts and returns `""`, which step 7 of §6.2 now
   treats as a sign-in failure rather than silently proceeding.
3. Upserts all seed accounts/categories/transactions to Room so the app is
   immediately usable without waiting for first sync — only reached if
   step 2 above actually succeeded.

### 6.4 Onboarding Seed Data

**Must byte-match** `seedOnboarding.ts` from Money PWA — do not invent new seed
values. At implementation time, port the exact category/account list (colors,
icons, starter transactions if any) from `Money-PWA/src/api/seedOnboarding.ts`
so a freshly created `db_money` looks identical regardless of which client
created it first.

### 6.5 Token Refresh

Identical mechanics to Tasks Android: `isExpiredSoon(expiry)` (within 300s),
refresh via `Identity.getAuthorizationClient(context).authorize(buildAuthRequest())`,
OkHttp `Authenticator` calls it once on 401 — "once" wasn't actually
enforced (code-review fix, no `responseCount`/`priorResponse` guard): a
revoked grant whose refresh call still "succeeds" but returns a token
Google rejects again retried forever instead of surfacing an error. Bails
after one retry now, the standard OkHttp recipe. Request logging
(`HttpLoggingInterceptor`) is debug-only now too — it was `Level.BASIC` in
release builds as well, which logs full request URLs, spreadsheet ids
included.

### 6.6 Backup Exclusion

Same as Tasks Android — `auth_prefs` DataStore file excluded from Auto Backup
and device transfer (`backup_rules.xml` / `data_extraction_rules.xml`). Room
DB is backed up normally.

### 6.7 Sign-Out

`GoogleAuthRepository.signOut()` clears all DataStore prefs, then deletes all
rows from `transactions`, `accounts`, `categories`, `sync_queue`.

---

## 7. Synchronization

### SyncState

```kotlin
sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Pending(val count: Int) : SyncState()
}
```

Same `SyncManager.syncState` combine pattern as Tasks Android (WorkManager
work-info flows + `syncQueueDao.observePendingCount()`), same TopAppBar icon
semantics (`CloudDone` / `CloudUpload` with badge / spinning `Sync`).

### Periodic & Manual Sync

Identical to Tasks Android: `PeriodicWorkRequest` every 30 minutes
(`ExistingPeriodicWorkPolicy.KEEP`, `NetworkType.CONNECTED`), name
`"StlerMoneyPeriodicSync"`; manual `OneTimeWorkRequest`
(`ExistingWorkPolicy.REPLACE`), name `"StlerMoneyManualSync"`, triggered from
`MainViewModel.init`.

### SyncWorker Execution Order

1. Read `spreadsheetId` from DataStore; find-or-create if blank (same
   401-retry-once guard as Tasks Android's `findAndSaveSpreadsheetId`).
2. **Push phase** — drain `SyncQueue`, but with the **dedup-by-latest** step
   Money PWA uses and Tasks Android does not (Tasks pushes every queued item
   individually). See [§16.2](#162-queue-deduplication-flush): collapse
   multiple queued mutations for the same entity down to one, superseded
   entries are deleted from the queue without an API call.
3. `delay(1_000L)` after a non-empty push, to avoid reading stale cached
   Sheets data (same guard as Tasks Android).
4. **Pull phase** — `batchGet(spreadsheetId, listOf("transactions", "accounts", "categories"))`.
   Collect IDs still in the sync queue → skip those rows on upsert (pending
   local edits win). Prune local rows absent from remote **and** not pending
   (propagates deletions) — same `deleteNotIn(remoteIds + pendingIds)` pattern
   as Tasks Android's folder/label pruning, applied here to all three tables.
   **Guard (code-review fix):** if the response has no header row at all
   (`values` null or empty — a transient/partial response, or a
   newly-created spreadsheet whose seed write failed), skip straight to
   step 5 without touching Room. A genuinely empty sheet still has its
   header row (`values.size == 1`) and prunes down to empty correctly; only
   the fully-empty-array case is refused, since `deleteNotIn` would
   otherwise wipe every local row not in the sync queue.
5. Return `Result.success()`.

On exception: `Result.retry()` for `runAttemptCount < 4`, else `Result.failure()`.

### Exchange Rate Sync

Not part of `SyncQueue` — mirrors PWA's `exchangeRateService`:
- `ExchangeRateRepository.refresh(baseCurrency)` fetches from
  `https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/{base}.json`.
- On success, caches to DataStore (`rates`, `baseCurrency`) so rates are
  available offline immediately on next launch, and writes the same blob into
  the `settings!A1` JSON (`exchange_rates` key) as a cross-device fallback,
  exactly like the PWA.
- Triggered: on base-currency change in Settings, and once at `initialLoad()`
  after auth — **not** on the 30-minute periodic worker (rates don't need that
  freshness, and PWA doesn't refresh periodically either).

### SheetsApi Endpoints

| Operation | HTTP | Path | Notes |
|---|---|---|---|
| Pull (read all) | GET | `.../values:batchGet?ranges=transactions,accounts,categories` | `valueRenderOption=UNFORMATTED_VALUE` |
| INSERT | POST | `.../values/{sheet}!A:{lastCol}:append` | `valueInputOption=RAW`, `insertDataOption=INSERT_ROWS` |
| UPDATE | POST | `.../values:batchUpdate` | Updates specific row range, e.g. `transactions!A5:P5` |
| DELETE | POST | `.../values/{range}:clear` | Empties the row; mapper skips blank-`id` rows |
| Settings read/write | GET / PUT | `.../values/settings!A1` | JSON blob in single cell |

---

## 8. UI Screens

### Navigation shape (bottom nav only — no sidebar mode exists)

`NavigationBar` (icon-only, 5 items) is the **only** navigation surface in the
app. Tab order is deliberately **not** the same as the start destination —
Accounts sits to the left of Transactions, but the app still opens on
Transactions:

| Order | Item | Icon | Destination |
|---|---|---|---|
| 1 | Accounts | `CreditCard` | `AccountsScreen` |
| 2 | Transactions | `Wallet` | `TransactionsScreen` — **start destination**, despite not being tab 1 |
| 3 | Categories | `Tag` | `CategoriesScreen` |
| 4 | Analytics | `BarChart` | `AnalyticsScreen` |
| 5 | Menu | avatar (Coil) or `AccountCircle` | `MenuScreen` |

`NavHost(startDestination = Screen.TRANSACTIONS)` while the `NavigationBar`
items list is ordered independently — Navigation Compose has no constraint
tying tab order to start destination, so this is just two separate lists that
happen to disagree on index 0.

There is no `navMode` DataStore key, no Settings "Navigation" section, no
`ModalNavigationDrawer` anywhere in the codebase — unlike Tasks Android, this
is not a mode toggle, it is the only mode. FAB is hidden on `MenuScreen` only.

**Cross-screen filtering:** two interactions elsewhere in the app hand
Transactions a pre-applied filter and switch to that tab — tapping an account
name in `AccountsScreen` (§8.2) and tapping a category slice in the Analytics
donut (§8.4). Both are implemented as optional nav arguments on the
Transactions route rather than a shared mutable store — see [§12](#12-navigation).

---

### AuthScreen

Same shape as Tasks Android's `AuthScreen`: centered `Column`, logo + title,
subtitle, state-driven content (`Loading` / `SignedOut` button / `Error` +
retry). Ported almost verbatim; only copy text changes ("Stler Money").

---

### 8.1 TransactionsScreen

**ViewModel:** `TransactionsViewModel`
**Data source:** `transactionRepository.observeAll()` combined with
`TransactionFilterState`/search (live, in-memory filtering via
`Transaction.matchesFilter` — no separate `observeFiltered` query).
**Balance bar:** none — confirmed absent from the live PWA's
`TransactionList.tsx`; this doc previously described one, stale.
**Filters:** a single filter-icon (`Tune`) button lives in `MoneyTopAppBar`,
next to the sync icon — filled/primary when any filter is set, muted outline
otherwise (matches the real PWA's `Header.tsx` `SlidersHorizontal` button
exactly, including the placement). Tapping it opens `FilterMenu`, a
`ModalBottomSheet` — see [FilterMenu](#84a-filtermenu-shared-component)
below. There is **no separate active-filter pill row** on this screen: the
user explicitly rejected that (a multi-pill row "will fall apart" at small
widths); the sheet itself is the only place filter state is shown, via each
section's highlighted chips.

**Arriving pre-filtered:** when the screen is entered via the `accountId` /
`categoryId` / `dateFrom` / `dateTo` nav args (§12 — Accounts/Categories row
tap, Analytics donut drill-down), `TransactionsViewModel` seeds the initial
`TransactionFilterState` from them directly. There's no longer a distinct
"arrived pre-filtered" mode to reconcile with manual filtering — opening the
sheet afterward shows exactly those chips already selected, and editing them
behaves identically to filtering by hand.
**List:** `LazyColumn` grouped by date header (`14 Jun · Saturday` /
`14 Jun 2023 · Wednesday` for past years — see [§16.3](#163-date-group-labeling)).
Paging via `LazyColumn` + `Paging3` or simple `visibleCount` state with
`derivedStateOf` on scroll position (PWA uses `IntersectionObserver` +
`PAGE_SIZE = 20`; native equivalent: load-more when the last visible item
index is within 5 of the list end).
**Empty state:** `Wallet` icon (40% opacity), "No transactions yet" + "＋ Add
transaction" text button.
**FAB:** opens `TransactionFormSheet` in create mode.

---

### 8.2 AccountsScreen

**ViewModel:** `AccountsViewModel`
**Section order (fixed):** `cash, card, savings, investment` — same as PWA.
Each section collapsible; collapse state persisted cross-device via
`settings!A1.collapsed_account_groups` (same mechanism the PWA uses, not a
local-only preference).
**Row:** icon container `40dp` circle, background = `color + "33"` (20%
opacity), icon tinted `color`; type icon (`Wallet` / `CreditCard` /
`PiggyBank` / `TrendingUp`); balance in `error` color when negative.

**Row interactions (decided — no global "edit mode" toggle):**
- **Tapping the account name/row body** navigates to the Transactions tab
  pre-filtered to that account (`Screen.TRANSACTIONS` with `accountId` nav
  arg — see §12). This is the primary interaction: tap to drill into its
  transactions, not to open an edit form.
- **Editing/archiving/deleting** is reached through a trailing `⋮`
  (`MoreVert`) icon on each row, opening a small menu (`DropdownMenu` or a
  compact `ModalBottomSheet` — pick whichever reads better once both are
  prototyped) with Edit / Archive-Unarchive / Delete. This mirrors the
  "trailing kebab menu" pattern already used elsewhere in the sibling app
  (`CalendarEventItem`'s menu, `TaskItem`'s more button) rather than
  introducing a new interaction shape. A top-bar "Edit mode" toggle was
  considered and rejected: it would be the only mode-toggle affordance in an
  app that otherwise has none, and it conflicts with tap-to-filter — the row
  tap target would need to mean two different things depending on mode.
- Delete → `ConfirmDialog`, same reversal-warning pattern as
  `TransactionItem`'s delete.

**Archived section:** hidden by default, "Show/Hide archived (N)" toggle.
**Open debts section:** below account sections — debt transactions where
`debtRefId` is empty and whose `id` is not referenced as anyone's `debtRefId`.
Lent → `error` color; borrowed → green.
**Edit mode balance field:** label changes from "Opening balance" (create) to
"Current balance" (edit) — saving writes `balance` directly, same drift-repair
affordance as PWA.
**FAB:** opens `AccountFormSheet` in create mode.
**Empty state:** `CreditCard` icon, "No accounts yet".

---

### 8.3 CategoriesScreen

**ViewModel:** `CategoriesViewModel`
**Drag-and-drop:** `ReorderableLazyListState` (sh.calvin `reorderable`),
mirrors Tasks Android's `FolderScreen` pattern rather than PWA's `@dnd-kit`
(no web equivalent needed — same end behavior: `reorderCategories(newOrder)`
writes `sortOrder = index` for the moved range, single batch write).
**Row:** drag handle (`GripVertical`, `cursor-grab` equivalent = long-press
drag), `CategoryIcon` + name + type badges (expense: rose, income: emerald;
limit chip shown if > 0).
**FAB:** opens `CategoryFormSheet` in create mode.
**Empty state:** `Tag` icon, "No categories yet".

---

### 8.4 AnalyticsScreen

**ViewModel:** `AnalyticsViewModel`

**Layout — two independent tabs (`AnalyticsPage.tsx`'s real, live structure;
`MonthBarChart.tsx`/`IncomeExpenseChart.tsx`/`BalanceChart.tsx` turned out to
be dead PWA files, never imported from `AnalyticsPage.tsx` — checked the
actual source before building this, not assumed from the file listing):**
1. `YearlyChart` — income/expense/balance composed chart, year nav, date
   fields, period chips (`This year` / `1Y` / `2Y` / `3Y`), series toggles.
2. `MonthlyView` — month nav + date fields + period chips (`Month` / `3M` /
   `6M` / `Year`) + Expenses/Income toggle + `CategoryDonut`.

**State:** the two tabs are fully independent, each owning its own month/date
state locally — no shared `analyticsMonth`. See "Drill-down" below for why:
the PWA's `uiStore.analyticsMonth` existed specifically to wire the
bar-tap → Monthly-tab jump, and that interaction was deliberately dropped for
Android, so nothing needs a value shared across the two tabs.

**Charting implementation (decided):**
- `MonthBarChart` and `YearlyChart` (bar + line composed) → **Vico**
  (`patrykandpatrick/vico`), a Compose-native charting library that covers
  bar/line/combined charts directly — the closest thing to a Recharts
  equivalent on Android.
- `CategoryDonut` → **hand-rolled `Canvas`**, not Vico (Vico has no pie/donut
  chart type). This is also the only chart that needs pixel-level control for
  the icon-in-slice layout, so a custom composable is the right tool
  regardless. It must keep the same *composition* as PWA's donut, not the
  same look:
  - a ring of colored arcs sized by each category's share of the period's
    spend (an actual donut — `innerRadius`/`outerRadius` — not a full pie),
  - a small circular icon badge (category color fill + the category's icon,
    white) positioned next to/on its arc, for segments above a minimum share
    threshold (PWA hides icon labels under 3%; keep a similar cutoff so tiny
    slices don't collide),
  - center text showing the period total (or average, for multi-month
    periods) in base currency.
  The exact visual treatment (arc thickness, icon placement radius, colors)
  does not need to match PWA pixel-for-pixel — only those three elements
  (colored sector + adjacent category icon + center total) are required.

**Bar tap → Monthly tab: deliberately dropped, not ported.** The real
`YearlyChart.tsx` lets tapping a bar jump to `MonthlyView` at that month.
Vico's Cartesian API (unlike Recharts) doesn't expose a simple per-datapoint
tap callback, and after the first implementation attempt the user confirmed
she had never actually used this affordance in the PWA either — so rather
than build a workaround for an interaction nobody uses, the decision was to
drop it from the Android version entirely. `YearlyChart` shows a plain
(non-interactive) row of month labels below the chart instead of an
`HorizontalAxis`, purely for readability — no tap, no hint text, no shared
`analyticsMonth` state with `MonthlyView`.

**Donut → Transactions filter:** tapping a category slice (or its list row
below the donut) navigates to the Transactions tab pre-filtered to that
category **and** the Monthly tab's current period — `categoryId` + `dateFrom`
+ `dateTo` nav args (§12), not just a single month, since the period can be a
multi-month range (3M/6M/Year chips). Same "pop + fresh push" navigation
mechanism as the account/category-row tap-to-filter in §8.2/§8.3. This is a
new interaction not present in the PWA (which has no cross-view navigation at
all — everything lives in one `selectedView`).

**CategoryDonut data rule:** only the **primary** category (`categoryIds[0]`)
counts per transaction — same anti-double-counting rule as PWA. List below
the donut: icon, name, amount, limit status, thin progress bar.

**Empty state:** `BarChart` icon, "No data for this period".

---

### 8.4a FilterMenu (shared component)

**Location:** `ui/transactions/FilterMenu.kt`

Replaces the "row of per-field chips, each opening its own dropdown" pattern
Tasks Android still uses natively (its `FilterBar`, §8.9 of the Tasks spec).
Both Tasks PWA and Money PWA have since replaced that with a single popup
menu in the web UI; Money Android follows the web apps' newer pattern rather
than the older native one, since the goal is one obvious entry point instead
of four.

```kotlin
fun FilterMenu(
    filterState       : TransactionFilterState,
    search            : String,
    accounts          : List<Account>,
    categories        : List<Category>,
    onToggleType      : (TransactionType) -> Unit,
    onToggleDebt      : () -> Unit,
    onToggleAccount   : (String) -> Unit,
    onToggleCategory  : (String) -> Unit,
    onDateFromChange  : (String?) -> Unit,
    onDateToChange    : (String?) -> Unit,
    onAmountMinChange : (String) -> Unit,
    onAmountMaxChange : (String) -> Unit,
    onSearchChange    : (String) -> Unit,
    onClearAll        : () -> Unit,
    onDismiss         : () -> Unit,
)
```

No `onApply` — matches the real `FilterPanel.tsx` exactly: every chip tap or
field edit calls its setter immediately and the list re-filters live while
the sheet is still open, there's no separate Apply step.

**Trigger:** a single `Tune` filter icon button in `MoneyTopAppBar`, next to
the sync icon (real PWA's `Header.tsx` places its equivalent button the same
way). Filled/primary when `filterState.isActive`, muted outline otherwise —
no count badge (the real web button doesn't have one either, just the two
color states).

**Presentation:** resolved as a `ModalBottomSheet` — §17.9's floating-popup
alternative turned out not to be a real contender once the actual
`FilterPanel.tsx` content was accounted for (five sections, one of which
needs its own internal scroll region).

**Sections inside the sheet, top to bottom:** a pinned "Clear all filters"
row (only rendered when something is actually set), a **search field**
(Android-only placement — the real PWA puts search in the header bar, not
the panel; the user asked for it here instead, right after "Clear all"),
then: Type (`FilterChip` row: Expense / Income / Transfer / Debt — "Debt" is
a virtual chip toggling `DEBT_LENT`+`DEBT_BORROWED` together, matching
`FilterPanel.tsx`'s `toggleDebt`), Accounts (colored-dot `FilterChip`s,
active-only, sorted by `sortOrder`), Categories (`CategoryIconView`
`FilterChip`s), Date range (two date chips + "This month"/"Last month"
presets), Amount range (min/max numeric fields). **Accounts and Categories
each wrap in a `FlowRow` inside a `heightIn(max = 130.dp)` scrollable
`Column`** — without the cap a long list pushes every section below off
screen; matches the real panel's `max-h-36 overflow-y-auto` (~3 rows).

**Applied-filter display:** nowhere outside the sheet. The user explicitly
rejected a removable-pill row (the natural read of `FilterBar.tsx`) for
Android — "все поедет" at the widths this app runs at. The filter button's
filled/outline state is the only always-visible indicator; the sheet itself,
reopened, shows the specifics via each section's highlighted chips.

---

### 8.5 MenuScreen

Mirrors Tasks Android's `MenuScreen` exactly (bottom-nav-mode "Menu" tab):
user avatar (72dp circle, Coil `AsyncImage`, `AccountCircle` fallback), name
(`titleMedium`), email (`bodyMedium`). Rows (`heightIn(min=56dp)`,
`bodyLarge`, 24dp icons): **Settings · Help · Feedback · Sign out** (error
color). Each row pushes the corresponding overlay.

**About row — decided: yes, include it**, mirroring Tasks Android's `About`
overlay (`BuildConfig.VERSION_NAME` + "Check for updates" → GitHub releases
page). Money PWA has no such screen simply because it never needed one yet;
this is a new native app that will accumulate versions from day one, so the
row — and the `About` overlay it opens — is in scope for v1, not deferred.

---

### 8.6 SettingsScreen

**ViewModel:** `SettingsViewModel`
**Physical back button:** `BackHandler` → `onNavigateBack` (returns to main
screen, does not exit app) — same convention as Tasks Android.

**Section order (top → bottom, no headers), narrower than Tasks Android — no
Navigation section, no feature-flag switches (Money has nothing to disable):**

1. **Spreadsheet** — current file name (`db_money` fallback) + "Change"
   button → expandable Drive file picker; `switchSpreadsheet()` clears
   `sync_queue` *first* (code-review fix — a pending op for the old
   spreadsheet used to survive the switch and get pushed against the new
   one, silently corrupting it), then clears all Room entity data and
   triggers sync — same order `GoogleAuthRepository.signOut()` already used.
2. **Base currency** — segmented/select control: EUR / USD / RUB. Changing it
   calls `setBaseCurrency()` → persists to `settings!A1` → triggers
   `ExchangeRateRepository.refresh(currency)`.

---

### 8.7 HelpScreen

No ViewModel. Static scrollable content in cards, content scope matches PWA's
`HelpPage.tsx`: Basics, Data & sync, Usage tips. `BackHandler` returns to main
screen.

---

### 8.8 FeedbackScreen

**ViewModel:** `FeedbackViewModel`
**Transport:** HTTP POST to the same Google Apps Script endpoint pattern as
Tasks Android's `FeedbackScreen`, with `app=Money` instead of `app=Tasks`
(matches PWA's `VITE_FEEDBACK_URL` posting with `app=Money`).
`instanceFollowRedirects = false` (Apps Script 302-on-success gotcha — see
Tasks Android spec §8.8 for why).
**State:** `sendResult: StateFlow<SendResult?>` — `null` / `Sending` /
`Success` / `Error`. Success clears the field, shows a snackbar.
**Physical back button:** `BackHandler` → `onNavigateBack`.

---

## 9. TransactionItem Component

**Location:** `ui/transaction/TransactionItem.kt`

| Parameter | Description |
|---|---|
| `transaction` | The `Transaction` domain model |
| `account` | Resolved `Account` (for colored name + currency) |
| `categories` | Resolved `Category` list for icon/color/name |
| `onEdit` | Opens `TransactionFormSheet` in edit mode |
| `onDelete` | Shows `ConfirmDialog` ("This will reverse the balance change.") |

**Layout:** single row — stacked category icon(s) on the left (primary
category's `CategoryIcon`, small secondary badge if a tag category is set),
middle column with account-colored account name + comment, right-aligned
amount (red for expense/negative, green for income, neutral for transfer).

---

## 10. TransactionFormSheet

**ViewModel:** `TransactionFormViewModel`
**Presentation:** `ModalBottomSheet`, mirrors Tasks Android's `TaskFormSheet`
container pattern rather than PWA's `Dialog` + Radix `Tabs` (no material
difference — same 4-tab structure).

**Tabs:** Expense · Income · Transfer · Debt (`SingleChoiceSegmentedButtonRow`
or `TabRow`).

**Form layout (each tab), ported from PWA §9.6:**
1. Date field (Material3 `DatePicker` in a dialog, not an overlaid native
   input — no web-input hack needed on Android).
2. Category grid (expense/income tabs only; 4 columns, sorted by usage
   frequency desc; max 2 selections — tapping a 3rd replaces index 1; badge
   shows position number, 1 = primary, 2 = secondary). See
   [§16.6](#166-multi-category-toggle-category-grid).
3. Account select (colored dot + name) + amount field, one row.
4. Comment field.

**Transfer tab additionally:** From account + amount, then To account (side-
by-side with a `to_amount` field when currencies differ).

**Debt tab additionally:** "I lent" / "I borrowed" toggle, comment relabeled
"Person's name", "Mark as repaid" button (edit mode only, when `debtRefId` is
empty).

**NumericKeyboard:** custom 4×3 Compose keypad (`1 2 3 / 4 5 6 / 7 8 9 / . 0 ⌫`),
`activeField` toggles between `amount` and `toAmount`, max 2 decimals — ported
1:1 from PWA's `NumericKeyboard.tsx` rules. Uses touch targets ≥ 48dp
(Material guideline) rather than the web's `touchAction: manipulation` hack.

**Footer:** edit mode → Delete (`error` color, left) + Cancel/Save (right);
create mode → Cancel/Save only (`Arrangement.End`). Delete → `AlertDialog`
"This will reverse the balance change."

**Last-account persistence:** selected `accountId` written to DataStore
(`money-last-account-id`) on Save — pre-fills the account field next time the
sheet opens, matching PWA's `localStorage['money-lastAccountId']`.

---

## 11. Theme & Colors

Direct hex conversion of the PWA's HSL tokens (`src/index.css`), since Compose
`Color` wants ARGB, not CSS custom properties. `TasksTheme`'s structure
(`MoneyTheme` here) is reused — light/dark schemes fully custom, **no dynamic
color (Material You)**, matching both siblings' design-consistency goal.

| Token | Light | Dark | Usage |
|---|---|---|---|
| `Background` | `#ffffff` | `#1c1c1c` | Page background |
| `Foreground` | `#18181f` | `#f2f2f2` | Body text |
| `Surface` (card) | `#ffffff` | `#363636` | Card surfaces |
| `Popover` | `#ffffff` | `#242424` | Dropdowns |
| `Primary` | `#e0813d`¹ | `#d9995e`¹ | Buttons, FAB, focus rings — orange, shared with Tasks' `#e07e38` family |
| `OnPrimary` | `#ffffff` | `#ffffff` | Text on primary |
| `MutedForeground` | `#6b6b6b` | `#949494` | Placeholder/secondary text |
| `Accent` | warm cream | `#2e2e2e` | Hover/selected states |
| `Destructive` | `#e96060` | `#d9736c` | Delete actions |
| `Border` / `Input` | `#e0e0e0` | `#4a4a4a` | |

¹ PWA's `--primary` is HSL `25 75% 55%` (light) / `25 65% 63%` (dark) — convert
at implementation time with a colorimetric tool rather than eyeballing; values
above are close approximations, not final.

**Named account/category colors** (onboarding seed, imported CSV): same 12-hex
palette as PWA §11 — port verbatim so seeded data renders identically on both
apps.

---

## 12. Navigation

### Route Constants (`Screen.kt`)

| Constant | Route String |
|---|---|
| `Screen.TRANSACTIONS` | `"transactions?accountId={accountId}&categoryId={categoryId}&month={month}"` — all three args optional/nullable, default `null` |
| `Screen.ACCOUNTS` | `"accounts"` |
| `Screen.CATEGORIES` | `"categories"` |
| `Screen.ANALYTICS` | `"analytics"` |
| `Screen.MENU` | `"menu"` |

Mostly no parameterized routes — unlike Tasks Android (`FOLDER/{folderId}`,
`CALENDAR/{calendarId}`), Money has no nested list→detail navigation: Accounts
and Categories are flat lists edited via bottom sheets in place, not pushed
screens. The one exception is Transactions, which accepts the optional args
above so the two tap-to-filter interactions (§8.2 account row, §8.4 donut
slice) can hand it a starting filter without a shared mutable store.
`TransactionsViewModel` reads them once from `SavedStateHandle` on first
collection and clears the consumed value, so a later, un-parameterized return
to the tab (via the bottom nav item itself) doesn't reapply a stale filter.

### Navigation Behavior

- Bottom nav tab switches use `popUpTo(graph.startDestinationId) { saveState = true }`
  + `launchSingleTop = true` + `restoreState = true` — per-tab scroll/filter
  state survives switching tabs, same as Tasks Android.
- **Overlay screens** (Settings, Help, Feedback) are shown by toggling local
  `showSettings/showHelp/showFeedback` state in `MainScreen`, replacing content
  via early return — no NavBackStack entries, identical to Tasks Android.
- `TransactionFormSheet` / `AccountFormSheet` / `CategoryFormSheet`
  (`ModalBottomSheet`) are local overlays, not navigation destinations.

### Deeplinks — decided against

Was "optional for v1" here; resolved. The `stlermoney://` `intent-filter`
was registered in the manifest with no handler ever written — any such URI
just opened the app to the auth/main gate, doing nothing else. The user
confirmed this app doesn't need deep links at all, so the dead manifest
entry was removed rather than left as inert, exported config (code-review
finding). Not revisiting unless a real use case shows up.

---

## 13. Loading & Empty States

### Loading

| Situation | Indicator |
|---|---|
| Initial app startup (token check) | Full-screen loading indicator |
| Sync in progress | `Sync` icon, `animate-spin` equivalent (infinite rotation, 1s LinearEasing) in TopAppBar |
| Pending writes | `CloudUpload` icon + muted count badge |
| Form sheet saving | Save button shows a small `CircularProgressIndicator`, disabled |

### Shimmer / Empty states

Same `ShimmerTransactionList` pattern as Tasks Android's `ShimmerTaskList`:
`isLoading: StateFlow<Boolean>` derived as `list.map { false }.stateIn(..., initialValue = true)`,
pulsing skeleton rows (alpha 0.25↔0.6, 900ms) while true.

| Screen | Icon | Message |
|---|---|---|
| TransactionsScreen | `Wallet` (40% opacity) | "No transactions yet" + "＋ Add transaction" |
| AccountsScreen | `CreditCard` (40% opacity) | "No accounts yet" |
| CategoriesScreen | `Tag` (40% opacity) | "No categories yet" |
| CategoryDonut | `BarChart` (40% opacity) | "No data for this period" |

---

## 14. CI/CD & Build

Mirrors Tasks Android's `release.yml` exactly, with app-specific naming.

**Trigger:** push of any tag matching `v*` · **Runner:** `ubuntu-latest`

1. `actions/checkout@v4`
2. `actions/setup-java@v4` — JDK 17 (Temurin)
3. `chmod +x ./gradlew`
4. Decode keystore: `echo "$KEYSTORE_BASE64" | base64 --decode > keystore.jks`
5. `./gradlew assembleRelease` with `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`, `GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m"`
6. Rename APK: `app-release.apk` → `stler-money.apk`
7. `softprops/action-gh-release@v2` — creates the GitHub Release, attaches the APK

**Secrets required:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` · **Permissions:** `contents: write`

**Local/debug build:** no signing config when `KEYSTORE_PATH` is blank;
`isMinifyEnabled = false` for release (no ProGuard obfuscation), same as
Tasks Android.

---

## 15. First-Time Setup (New Developer)

1. Clone the repository.
2. In Google Cloud Console:
   - Enable **Google Sheets API** and **Google Drive API** (no Calendar API
     needed for this app).
   - Create **OAuth 2.0 Web Client ID** → `res/values/strings.xml` as
     `google_web_client_id`.
   - Create **OAuth 2.0 Android Client ID** (package `com.stler.money`, SHA-1
     of debug keystore).
   - Set OAuth consent screen to Production, add the `drive.file` scope.
3. Run on device/emulator from Android Studio.
4. Sign in — the app finds or creates the `db_money` spreadsheet automatically.

**For release builds:** create a keystore, base64-encode it, add GitHub
secrets as listed in [§14](#14-cicd--build). Push a `v*` tag to trigger the
build.

---

## 16. Key Algorithms

Ported directly from Money PWA §16, unchanged in logic — reference
implementations there are pseudocode-equivalent to Kotlin.

### 16.1 Transaction balance delta

```
computeBalanceDelta(t: Transaction):
  expense       → { accountId: t.accountId,  delta: -t.amount }
  income        → { accountId: t.accountId,  delta: +t.amount }
  transfer      → { accountId: t.accountId,  delta: -t.amount,
                    toAccountId: t.toAccountId, toDelta: +t.toAmount }
  debt_lent     → if t.debtRefId != "":  { delta: +t.amount }   // repayment received
                  else:                   { delta: -t.amount }   // lent out
  debt_borrowed → if t.debtRefId != "":  { delta: -t.amount }   // repayment made
                  else:                   { delta: +t.amount }   // received
```

On **update**: reverse old delta, then apply new delta — both inside the
same `db.withTransaction` as the row write (code-review fix; these used to
be two independent steps, so a failure between them could double-apply one
side instead of rolling back cleanly).
On **delete**: reverse old delta only, same transaction as the row deletion.

### 16.2 Queue deduplication (push)

```
Input: items[] sorted by createdAt ASC

latestMap = Map<key, item>
  key for non-deletes = "${entityType}:${entityId}"
  key for deletes     = "${entityType}:${entityId}:delete"

for item in items:
  existing = latestMap[key]
  if existing == null OR item.createdAt > existing.createdAt:
    latestMap[key] = item

// A later create + update for the same entity collapse to one push.

latestIds = latestMap.values().map { it.id }.toSet()
for item not in latestIds: deleteFromQueue(item.id)   // superseded, no API call

for item in latestMap.values():
  executeOperation(item)   // then delete on success, incrementRetry on failure
```

This is the one place Money Android's `SyncWorker.push()` diverges from Tasks
Android's (which pushes every queued item individually, since Tasks entities
are rarely edited multiple times before a sync). Money transactions are
edited more often before the next sync tick (numeric keypad amount edits,
category toggles), so the PWA's dedup step is load-bearing here — port it.

### 16.3 Date-group labeling

```
formatGroupLabel(date: LocalDate):
  datePart = if (date.year == LocalDate.now().year) "dd MMM" else "dd MMM yyyy"
  return "$datePart · EEEE"   // e.g. "14 Jun · Saturday"
```

### 16.4 `generateId(prefix)`

```kotlin
fun generateId(prefix: String) = "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
```

### 16.5 `convertToBase(amount, currency, baseCurrency, rates, sameCurrencyHistory)`

Three-step fallback chain (code-review finding: a blind 1:1 fallback with no
live rate silently corrupted `amount_base` for the rest of that transaction's
life — analytics sums `amount_base` forever, so a wrong value never
self-corrects). `sameCurrencyHistory` defaults to empty and is only passed
by `TransactionFormViewModel` (the one call site where getting this right
matters) — the Analytics live-balance call sites don't bother, a stale 1:1
in a transient display value is a lesser concern than in a permanently
stored one.

```
if currency == baseCurrency: return amount
rates[currency]?.let { return amount / it }               // 1. live rate

impliedRates = sameCurrencyHistory                          // 2. average of the user's own
  .filter { it.currency == currency && it.amountBase != 0 } //    recent real transactions in
  .map { it.amount / it.amountBase }                         //    this currency (last 8, see
if impliedRates.isNotEmpty():                                //    TransactionFormViewModel)
  avgRate = impliedRates.average()
  if avgRate is finite and nonzero: return amount / avgRate

return amount   // 3. final fallback: 1:1 — no live rate AND no history yet
                //    (e.g. the very first transaction ever in a new currency)
```

Rates map is `{ USD: 1.08, RUB: 95.4, ... }` where each value is
`1 baseCurrency = N foreignCurrency`; division converts foreign → base. Same
direction for the history-implied rate: a past transaction's own
`amount / amount_base` is exactly what `rates[currency]` would have been at
save time, by construction.

Deliberately NOT revisited on a later base-currency change: existing
`amount_base` values are left as-is when the user changes base currency in
Settings — chosen once, by design (user's call — she's the only user today).

### 16.6 Multi-category toggle (category grid)

```
toggle(id):
  cur = current categoryIds list
  idx = cur.indexOf(id)
  if idx == -1:
    if cur.size < 2: cur + id            // append
    else:            listOf(cur[0], id)  // replace second
  else:
    cur - id                              // deselect
```

Index 0 = primary (analytics, donut chart). Index 1 = secondary tag only.

### 16.7 Category delete with transfer

```
deleteCategory(id, transferToId):
  txns = transactionDao.getByCategoryId(id)
  db.withTransaction {
    for txn in txns:
      newIds = txn.categoryIds
        .map { if (it == id) transferToId else it }
        .distinct()   // dedup — prevents double-listing if transferToId was already present
      update(txn.copy(categoryIds = newIds)); enqueue(UPDATE, txn.id)
    categoryDao.delete(id); enqueue(DELETE, id)   // DELETE enqueue inside the same
  }                                                 // transaction (code-review fix) — it
                                                     // used to happen after commit, so a
                                                     // process death in between left the
                                                     // category deleted locally with no
                                                     // DELETE queued; the next pull then
                                                     // restored it from Sheets.
```

---

## 17. Design Decisions & Remaining Open Items

Decisions made after the first draft of this spec, plus what's still
genuinely open.

### Resolved

1. **Charting library — Vico for bar/composed charts, hand-rolled `Canvas`
   for the donut.** See [§8.4](#84-analyticsscreen) for the required
   composition of the donut (colored arc + adjacent category icon + center
   total). The PWA's bar→Monthly-tab drill-down did **not** survive the
   rewrite — see §8.4's "deliberately dropped" note and resolved item 8 below.
2. **Category icon set — Lucide, ported to Compose.** Visual parity with the
   PWA's `lucide-react` icons matters (same spreadsheet, same 37 names in
   Sheets column C, users see both apps side by side). Primary approach: pull
   the Lucide set as Compose `ImageVector`s from an existing port (e.g.
   `DevSrSouza/compose-icons`'s Lucide module via JitPack) so the icons are
   pixel-faithful to the web app with no manual drawing. **Fallback**, only if
   that library is missing icons or unmaintained by the time this is built:
   hand-trace the needed Lucide SVGs (MIT-licensed, source available) into
   local vector drawables bundled as app resources — a one-time asset task,
   not something generated at runtime. Either way, the name→icon lookup table
   is frozen once built: adding new names later is fine, renaming existing
   ones breaks category icons for spreadsheets shared with the PWA.
3. **`category_ids` query strategy — join table, additive.** Resolved in
   [§5.6](#56-room-database) — `transaction_categories` is a derived index
   Room maintains alongside the existing comma-separated column, not a
   replacement for it and not a redesign of the `transactions` table.
4. **About row in MenuScreen — yes, in scope for v1.** See
   [§8.5](#85-menuscreen): the app will accumulate versions from the start,
   so version display + update check is included from the first release
   rather than bolted on later.
5. **Bottom nav tab order vs. start destination — Accounts left of
   Transactions, Transactions still opens first.** See
   [Navigation shape](#navigation-shape-bottom-nav-only--no-sidebar-mode-exists)
   at the top of §8 — two independent lists (tab order, start destination)
   that happen to disagree on index 0, no framework fight involved.
6. **Filter UI — one popup menu, not a chip row.** `FilterMenu` ([§8.4a](#84a-filtermenu-shared-component))
   replaces the per-field-chip-with-its-own-dropdown pattern Tasks Android
   still uses natively; Money Android follows the newer pattern both web apps
   (Tasks PWA and Money PWA) have already moved to.
7. **Account row interactions — tap-to-filter, not an edit-mode toggle.**
   See [§8.2](#82-accountsscreen): tapping an account opens Transactions
   filtered to it; editing/archiving/deleting lives behind a trailing `⋮`
   menu per row.
8. **Analytics → Transactions drill-down catalog — donut only, decided after
   real use rather than more guessing.** A category slice/row in
   `CategoryDonut` filters Transactions by category + the Monthly tab's
   current period (§8.4). Explicitly **not** doing a `YearlyChart` bar tap
   (superseded by resolved item 9) or an Income/Expense-toggle drill-down —
   the user never asked for either once she'd actually used the screen; no
   open catalog to keep chasing.
9. **`YearlyChart` bar tap → Monthly tab: decided to drop this PWA
   functionality for the Android version.** Vico's Cartesian API doesn't
   expose a simple per-datapoint tap, and the user confirmed she'd never used
   the equivalent interaction in the PWA either — not worth building a
   workaround for. `YearlyChart` shows a plain, non-interactive month-label
   row instead of a tappable `HorizontalAxis`; see §8.4.
10. **`FilterMenu` presentation — `ModalBottomSheet`, no floating-popup
    prototype needed in the end.** Once the real `FilterPanel.tsx` content
    was actually read (five sections, including a capped-scroll account/
    category list, not the four originally assumed), a bottom sheet was the
    only option that could fit it — no meaningful decision to prototype.
    Also resolved: **no removable-pill state row** on `TransactionsScreen`
    (the user rejected that read of `FilterBar.tsx` for Android — "все
    поедет" at phone widths) and **no per-field chip row** either (ruling
    out the Tasks Android sibling's own pattern, which the user doesn't
    consider a good precedent there in the first place). The filter button's
    filled/outline state plus the sheet's own highlighted chips are the only
    UI for filter state; see [§8.4a](#84a-filtermenu-shared-component).
11. **Shape convention — rounded rectangles everywhere, never fully-rounded
    pills, a deliberate Android-only deviation from the PWA.** The web app
    keeps `rounded-full` chips (filter chips only — everything else there,
    inputs and buttons, is already a rounded rectangle); the user chose one
    consistent rounded-rectangle shape for every chip/button/tab on Android
    instead of mixing the two. `ui/theme/Shape.kt`'s `ControlShape`
    (`RoundedCornerShape(10.dp)`) is the single shared constant; Material3's
    `Button`/`SegmentedButton`/`FilterChip` don't read their corner radius
    from `MaterialTheme.shapes` (they're hardcoded to a "corner full" token
    internally), so it's applied explicitly at each call site rather than
    through one theme-level override. Chips specifically go through the
    shared `ui/common/PillChip.kt` component (`PillChip`/`DateFieldChip`)
    rather than Material3's own `FilterChip` at all — see
    [§8.4a](#84a-filtermenu-shared-component) for why (a fixed ~48dp
    touch-target regardless of visual chip size was the real cause of a
    capped-height chip-wrap layout only fitting 2 rows instead of 3).

### Still open

_None remaining from this list — the accessibility pass (dev plan Phase 10)
is scheduled after `FilterMenu`, not a design decision._
