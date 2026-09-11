# Stler Money — Android

[![Live PWA](https://img.shields.io/badge/Money_PWA-Live_PWA-E07E38?style=for-the-badge)](https://juliasivridi.github.io/Money_PWA/)
[![Releases](https://img.shields.io/github/v/release/JuliaSivridi/Money_Android?style=for-the-badge&logo=github&logoColor=white&color=181717)](https://github.com/JuliaSivridi/Money_Android/releases)

![Kotlin](https://img.shields.io/badge/Kotlin_2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android_8.0+-34A853?style=for-the-badge&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room_SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![Google Sheets](https://img.shields.io/badge/Google_Sheets_API-34A853?style=for-the-badge&logo=googlesheets&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

A native Android personal finance tracker — the companion app to [Stler Money PWA](https://juliasivridi.github.io/Money_PWA/). Both apps share the same `db_money` Google Spreadsheet, so accounts, categories, and transactions stay in sync between web and phone automatically.

---

## Features

**💸 Transactions**
- Four types: Expense, Income, Transfer (between accounts), Debt (lent / borrowed, with a one-tap "Mark as repaid")
- Up to 2 categories per transaction (primary + tag), date + time, comment
- Native Android keyboard for amount entry
- Date-grouped list ("8 Sep · Tuesday" — omits the year for the current year, matching the PWA)

**🔍 Filters**
- One filter sheet: Type, Account, Category, Date range, Amount range, and search by comment
- Every toggle applies live — no separate "Apply" step
- The filter button in the top bar lights up (primary color) whenever a filter is active
- Arriving from a tap on an Account, Category, or an Analytics donut slice seeds the same filter sheet, pre-selected

**🏦 Accounts**
- Card, cash, savings, investment — grouped into collapsible sections (state persisted)
- Live balances, updated immediately on every transaction
- Archive support; archived accounts stay reachable for historical transfers
- Open debts section (unresolved lent / borrowed)

**🏷️ Categories**
- Custom icon (Lucide set) and color; drag-to-reorder
- Monthly budget limits with progress bars and ✓ / ✗ status
- Delete-with-transfer: reassign a deleted category's transactions to another one instead of losing history

**📊 Analytics**
- **Yearly** — income/expense bar chart with a balance line, editable date range + period chips (This year / 1Y / 2Y / 3Y), account picker for the balance calculation
- **Monthly** — month navigation, period modes (Month / 3M / 6M / Year, with an averaged total across multi-month spans), category donut with icon badges, per-category progress bars against the monthly limit
- Tap a category slice to jump to Transactions, pre-filtered by category and period

**💱 Multi-currency**
- Each account has its own currency; exchange rates fetched automatically (no API key) and cached locally
- Every transaction stores both its original amount and the base-currency equivalent

**🔄 Sync & offline**
- Full read/write offline via Room (SQLite); a sync queue flushes when back online
- Background sync every 30 minutes via WorkManager
- Sync status in the top bar: cloud (synced) · cloud-upload with a pending count · spinning arrows (syncing)
- First sign-in automatically creates the `db_money` spreadsheet — no manual setup

**⚙️ Settings**
- Switch between any spreadsheet in your Google Drive
- Change base currency (EUR / USD / RUB)

**🎨 Design**
- Material 3, light/dark theme following the OS, matching Money PWA's orange-and-gray palette exactly (not Material's own baseline colors)
- One shared rounded-rectangle control shape app-wide (buttons, chips, tabs) — a deliberate Android-only departure from the PWA's oval filter chips

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 |
| Architecture | MVVM + Repository | — |
| DI | Hilt | 2.59.2 |
| Local DB | Room | 2.7.1 |
| Async | Coroutines + Flow | 1.10.2 |
| Background sync | WorkManager | 2.10.1 |
| Auth | Credential Manager + Google Identity API | 21.3.0 / 1.1.1 |
| Network | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| Remote storage | Google Sheets API v4 | — |
| Charts | Vico (Cartesian) | 2.1.3 |
| Drag & drop | sh.calvin.reorderable | 2.4.3 |
| Min SDK | 26 (Android 8.0) | — |

---

## Architecture

```
ui/
  main/          — MainScreen (NavHost, bottom nav), MoneyTopAppBar, AboutScreen
  transactions/  — TransactionsScreen, FilterMenu, TransactionFilterState
  transaction/   — TransactionFormSheet, TransactionFormViewModel
  accounts/      — AccountsScreen, AccountFormSheet, AccountsPreferences
  categories/    — CategoriesScreen (drag-reorder), CategoryFormSheet
  analytics/     — AnalyticsScreen (Yearly/Monthly tabs), YearlyChartView, MonthlyView, CategoryDonut
  auth/          — AuthScreen
  settings/      — SettingsScreen
  feedback/      — FeedbackScreen
  help/          — HelpScreen
  menu/          — MenuScreen
  common/        — PillChip (shared chip component replacing FilterChip everywhere), PickerFieldTrigger, ColorPickerGrid, IconPickerGrid
  theme/         — Color, Theme, Type, Shape (ControlShape = RoundedCornerShape(10.dp))
  navigation/    — Screen (route constants)
data/
  local/         — Room DB, DAOs, entities
  remote/        — Retrofit SheetsApi + SheetsMapper (row ↔ entity)
  repository/    — *RepositoryImpl (Room-first writes, enqueue-then-sync)
sync/            — SyncWorker (WorkManager), SyncManager, SyncState
auth/            — GoogleAuthRepository, AuthPreferences (DataStore)
di/              — Hilt modules
```

**Data flow:** every mutation writes to Room immediately (triggers UI recomposition) and enqueues a sync-queue entry. `SyncWorker` drains the queue on the next sync cycle, then pulls fresh data from Sheets into Room.

---

## Data Model

All data lives in a Google Spreadsheet named `db_money` — one per Google account, shared with the PWA.

| Sheet | Columns (A → last) |
|---|---|
| `transactions` | id · date · time · type · amount · currency · amount_base · account_id · category_ids · to_account_id · to_amount · to_currency · debt_ref_id · comment · created_at · updated_at |
| `accounts` | id · name · currency · type · balance · archived · sort_order · created_at · updated_at · color |
| `categories` | id · name · icon · color · is_expense · expense_limit · is_income · income_limit · sort_order · created_at · updated_at |

Row 1 of every sheet is a header row. `type` is one of `expense` / `income` / `transfer` / `debt_lent` / `debt_borrowed`; `category_ids` holds up to 2 comma-separated ids (primary + tag).

---

## Setup

### Prerequisites

- Android Studio (latest stable)
- Google account
- Google Cloud project with **Google Sheets API** and **Google Drive API** enabled

### Google Cloud Console

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and enable **Google Sheets API** and **Google Drive API**
2. Create an OAuth 2.0 Client ID → type **Android**
   - Package name: `com.stler.money`
   - SHA-1: run `./gradlew signingReport`
3. Create an OAuth 2.0 Client ID → type **Web application** (needed for Credential Manager's token exchange — this is the one the app code actually references)
4. Set the OAuth consent screen to **Production** so any Google account can sign in

> **Release builds need their own SHA-1 registered too** — a release keystore signs with a different certificate than the debug keystore Android Studio generates automatically, so it needs an additional Android-type OAuth client (same package name, the release SHA-1) or Google Sign-In will fail with a `DEVELOPER_ERROR` on release-signed builds specifically.

### Local development

```bash
git clone https://github.com/JuliaSivridi/Money_Android.git
cd Money_Android
```

The Web Client ID is stored in `app/src/main/res/values/oauth.xml`. If you're using your own Google Cloud project, replace the value there with your own Web application Client ID.

Open in Android Studio and run on a device or emulator (API 26+).

On first sign-in the app automatically creates the `db_money` spreadsheet — no manual spreadsheet setup required.

> **PWA + Android on the same account:** the app uses the `drive.file` scope, which gives access only to files created by this OAuth client. If `db_money` was created by the PWA (a different OAuth client), the Android app won't be able to find it and will create a new one. To share one spreadsheet, either start from the Android app and open it in the PWA, or use the spreadsheet picker in Settings to point both apps at the same file.

### Release builds

The GitHub Actions workflow (`.github/workflows/release.yml`) builds a signed APK on every `v*` tag push and attaches it to a GitHub Release.

Required secrets: `KEYSTORE_BASE64` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`

---

## Related

- **PWA version:** [github.com/JuliaSivridi/Money_PWA](https://github.com/JuliaSivridi/Money_PWA) — React + TypeScript, same Google Sheets backend
- **Live PWA:** [juliasivridi.github.io/Money_PWA](https://juliasivridi.github.io/Money_PWA/)
- **Sibling native app:** [github.com/JuliaSivridi/Tasks_Android](https://github.com/JuliaSivridi/Tasks_Android) — same architecture, a task manager instead of a finance tracker
- **Technical specification:** [`docs/tech-spec.md`](docs/tech-spec.md)
