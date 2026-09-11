# Stler Money Android — Development Plan

Phased build order derived from
[`Money-Android-tech-spec.md`](./Money-Android-tech-spec.md). Section
references (`§x`) point back into that spec — read the referenced section
before starting the task, don't implement from the checklist title alone.

**Suggested sequencing:** Phases 0–3 (scaffolding → data layer → auth → sync)
have no UI and are fully testable with unit/integration tests before a single
screen exists — do them first and in order, they're a dependency chain. Once
Phase 4 (app shell) lands, Phases 5–9 (the five bottom-nav destinations) are
largely independent of each other and can be parallelized across devs.
**Charting (Phase 8) carries the most technical risk** (§17.1 — no library
does exactly what the donut needs) — worth a throwaway spike early, in
parallel with Phase 0–3, so the decision is proven before Phase 8 is on the
critical path.

> **Status (2026-09-10):** Phases 0–9 are implemented and building cleanly
> (`./gradlew :app:assembleDebug` succeeds, Hilt DI graph validated), and the
> user is actively testing on a physical device as work lands — this has
> caught several real bugs no amount of reading source could have (a Vico
> crash at 2Y/3Y, a chart-overflow layout bug, and a missing startup
> exchange-rate refresh that silently broke two things in Analytics at
> once), all fixed. Phase 10 (cross-cutting polish) is now mostly done —
> snackbar error wiring, shimmer loading, icon/color parity, and dark mode
> are all in and confirmed; only the accessibility pass is still open (the
> user wants to do it after filters, not before). The full multi-select
> `FilterMenu` (Phase 5/12) is now implemented too — see below. Phase 11
> (CI/CD & release) is the only major piece left unstarted; the user has
> explicitly deferred it.

---

## Phase 0 — Project Scaffolding

- [x] Gradle project: `applicationId = "com.stler.money"`, `minSdk = 26`,
      `targetSdk = 36`, Kotlin JVM toolchain 11 (§2)
- [x] Wire Hilt + KSP, Compose BOM, Navigation Compose, DataStore, Coil,
      `reorderable`, Vico (§2)
- [x] `MoneyTheme` skeleton — filled in fully already, see Phase 4 (§11)
- [x] `MainActivity` + `NavHost` stub — grew into the real auth gate directly
      (see Phase 2/4, done ahead of schedule)
- [x] Google Cloud Console: Sheets API + Drive API + OAuth client IDs (§15)
      — `res/values/oauth.xml`'s `google_web_client_id` is a real client ID,
      not the placeholder this item originally described; sign-in has worked
      throughout on-device testing all session
- [x] `release.yml` — done, see Phase 11

---

## Phase 1 — Domain Model & Room

- [x] `domain/model`: `Transaction`, `Account`, `Category`, `DebtSummary`
      data classes (§5) — plus `computeBalanceDelta()` (§16.1) alongside
      `Transaction.kt`; `SyncState` lives in `sync/` per the actual Tasks
      Android layout, not `domain/model` (the spec's package tree listed it
      in both places — this follows the real, working precedent)
- [x] Room entities + DAOs: `TransactionDao`, `AccountDao`, `CategoryDao`,
      `SyncQueueDao` (§5.6)
- [x] `transaction_categories` join table + repository-level dual-write in
      the same `@Transaction` as `categoryIds` changes — **additive, not a
      redesign** of the `transactions` table (§5.6, §17.3)
- [x] `MoneyDatabase` + `DatabaseModule` (Hilt)
- [ ] Unit tests: DAO CRUD, join-table stays consistent with `categoryIds`
      across create/update/delete — not written yet

> **Spec gap fixed during implementation:** §5.6's Room `transactions` table
> was missing a `time` column, even though §5.7's Sheets schema has one
> (column C, `HH:MM`, default `"00:00"`) and the PWA's `Transaction` type has
> `time` too. Added `time: String` to the domain model, entity, and mapper so
> the round-trip is lossless — update the tech spec's §5.6 table to match.

---

## Phase 2 — Authentication

- [x] `AuthPreferences` DataStore; backup/device-transfer exclusion for the
      token file (§6, §6.6)
- [x] `GoogleAuthRepository`: `CredentialManager` ID token +
      `Identity.getAuthorizationClient` scope authorization, **`drive.file`
      only** — no Calendar scopes (§6.1–6.2)
- [x] `AuthViewModel` + `AuthUiState` + `AuthScreen` (§8 AuthScreen)
- [x] Find-or-create `db_money` spreadsheet, 4 named sheets (§6.3)
- [x] Port onboarding seed data **byte-for-byte** from PWA's
      `seedOnboarding.ts` (§6.4) — read the actual PWA source file; it seeds
      2 accounts, 3 categories, 1 transaction (not invented — see
      `GoogleAuthRepository.createSpreadsheet()`)
- [x] Token refresh (`isExpiredSoon`, 300s window) + OkHttp `Authenticator`
      on 401 (§6.5)
- [x] Sign-out: clear DataStore + wipe `transactions`/`accounts`/
      `categories`/`sync_queue` (§6.7)

**Blocked on an external step:** sign-in will not work until a real
`google_web_client_id` replaces the placeholder in
`app/src/main/res/values/oauth.xml` — see Phase 0's Google Cloud Console item.

---

## Phase 3 — Remote Layer & Sync Engine

- [x] `SheetsApi` Retrofit interface + `SheetsMapper` (row ↔ entity for all
      three sheets) (§5.7, §7)
- [x] `NetworkModule` (OkHttp + bearer interceptor, Retrofit, Gson)
- [x] Repository write paths: create/update/delete → Room write → enqueue
      `SyncQueueEntity` → `adjustBalance()` side-effect for transactions (§3)
      — three repositories (`TransactionRepositoryImpl`,
      `AccountRepositoryImpl`, `CategoryRepositoryImpl`), matching the
      spec's separate-repository architecture (§3 diagram)
- [x] `SyncWorker.push()` **with the latest-per-entity dedup step** (§16.2 —
      the one place Money's queue behavior diverges from Tasks Android's
      push-everything-individually approach)
- [x] `SyncWorker.pull()`: pending-safety (skip queued IDs) + prune
      (`deleteNotIn(remoteIds + pendingIds)`) for all three tables (§7)
- [x] `SyncManager`: periodic 30-min `PeriodicWorkRequest` + manual
      `OneTimeWorkRequest`, `SyncState` flow (Idle/Syncing/Pending) (§7)
- [x] `ExchangeRateRepository`: fetch from jsDelivr currency-api, cache to
      DataStore, fallback-write to `settings!A1.exchange_rates` (§7 Exchange
      Rate Sync) — read-modify-write against the shared settings blob so it
      never clobbers keys other screens own. **Two real, compounding bugs
      found and fixed**, both invisible everywhere else in the app (every
      other screen formats amounts in their own account's currency, never
      converting) but breaking Analytics in two ways at once — the Yearly
      balance line reading offset from its true (partly negative) shape, and
      the balance-accounts picker showing raw RUB amounts labeled with the
      EUR symbol:
      1. §7 always said "refreshed on base-currency change and once at
         startup", but the "once at startup" half was never actually wired —
         `refresh()` was only ever called from `setBaseCurrency()`
         (Settings). Fixed by calling
         `exchangeRateRepository.refresh(baseCurrency)` once from
         `MainViewModel.init`, alongside the existing startup sync trigger.
      2. That fix alone didn't work either, on-device — because
         `fetchRatesFromApi()`'s OkHttp call is a raw blocking `.execute()`,
         not a suspend Retrofit call, and nothing dispatched it off
         `viewModelScope`'s default `Dispatchers.Main`. On a real device that
         throws `NetworkOnMainThreadException` — silently swallowed by
         `fetchRatesFromApi`'s own `runCatching`, so `rates` just stayed
         permanently empty with no visible error at all. Every other
         `OkHttpClient.execute()` call in the app (`GoogleAuthRepository`)
         already correctly wraps in `withContext(Dispatchers.IO)`; this was
         the one call site that didn't. Fixed the same way.
- [ ] Integration test: create transaction offline → lands in queue → push
      succeeds → row appears in a test spreadsheet; verify dedup collapses a
      rapid create+update+update into one push — not written yet; also
      **not yet verified against a real spreadsheet** (needs Phase 0's OAuth
      client ID first)

---

## Phase 4 — App Shell & Navigation

- [x] `MoneyTheme` full light/dark token set (§11 — HSL→hex conversion is a
      reasonable approximation; flagged in `Color.kt` as not colorimetrically
      exact, worth a proper pass later)
- [x] `Screen.kt` route constants, including Transactions' optional
      `accountId`/`categoryId`/`month` args (§12)
- [x] `MainScreen`: `NavHost` + `NavigationBar`, **tab order Accounts →
      Transactions → Categories → Analytics → Menu, start destination =
      Transactions** (two independent lists, don't couple them) (§8 nav
      shape)
- [x] `MoneyTopAppBar`: title per destination, sync-status icon
      (`CloudDone`/`CloudUpload`+badge/spinning `Sync`) (§7). Had drifted to
      `Filled` icons and Material3's default loud red/error `Badge` —
      inconsistent with the rest of the app's icon-set decision (§8.4/§17.2)
      and noticeably rougher than the Tasks Android sibling's own sync icon,
      which the user spotted. Fixed to match `TasksTopAppBar` exactly:
      `Outlined` icons, one consistent `onSurfaceVariant` tint across all
      three states, and a muted transparent-background badge instead of the
      error-colored default.
- [x] Overlay-stack mechanism for Settings/Help/Feedback/About — local state
      in `MainScreen`, no back-stack entries (§12)
- [x] `MainActivity`: real auth gate (`AuthScreen` vs `MainScreen`)
- [ ] Deep link handler — `stlermoney://` scheme registered in the manifest,
      but `MainActivity`/`MainScreen` don't parse `onNewIntent` yet (§12
      Deeplinks marked optional for v1 in the spec)

---

## Phase 5 — Transactions

> **Create/edit and the full multi-select `FilterMenu` both work end-to-end.**
> The "arrive pre-filtered" nav args (Accounts/Categories row tap, Analytics
> donut drill-down) now just seed the same `TransactionFilterState` the
> filter sheet edits — no separate single-entity concept anymore.

- [ ] `TransactionItem` composable (§9) — a minimal inline row exists in
      `TransactionsScreen.kt` with the real stacked-icon/transfer/debt logic
      ported from `TransactionItem.tsx`; not yet split into its own file
- [x] `TransactionsScreen`: date-grouped list (§8.1) — no balance bar
      (confirmed absent from the live PWA, tech spec's §9.5 mention was
      stale); load-more/paging not implemented (small dataset assumption)
- [x] `FilterMenu` (Type/Account/Category/Date range/Amount range/Search) —
      resolved as a `ModalBottomSheet`, following the real PWA's
      `FilterPanel.tsx` rather than the Tasks Android sibling's per-field
      chip-dropdown row (the user explicitly ruled that pattern out — see
      §17.9). Ported field-for-field from the real source, not guessed:
      `FilterPanel.tsx` (sections, live filtering — every chip tap calls
      `setFilter` directly, no separate Apply button), `FilterBar.tsx` (the
      removed-pill-row idea — the user rejected this for Android, "все
      поедет"; replaced with a single filter button that lights up, matching
      `Header.tsx`'s `SlidersHorizontal`/`filterActive` styling instead),
      and `useTransactions.ts#useFilteredTransactions` (the exact predicate,
      now `TransactionFilterState.matchesFilter`). One deliberate Android-only
      deviation: search lives inside the sheet (right after the pinned
      "Clear all" row) instead of the web header, per the user's request.
      Accounts/Categories sections cap their chip wrap at ~130dp with
      internal scroll (~3 rows), matching the web panel's
      `max-h-36 overflow-y-auto` — without it a long list pushed every
      section below off screen. The filter button sits in `MoneyTopAppBar`
      next to the sync icon; since the button and sheet live in `MainScreen`
      (a sibling of the `NavHost`) but must share the *same*
      `TransactionsViewModel` instance `TransactionsScreen` uses internally,
      `MainScreen` obtains it via `hiltViewModel(backStackEntry)` on the
      current Transactions `NavBackStackEntry` — Navigation Compose caches
      ViewModels per-entry, so this resolves to the identical instance, not
      a second one.
- [x] Selected-first ordering in the Accounts/Categories chip sections —
      those wrap at ~3 rows with internal scroll (above), so a chip
      pre-selected by arriving from Accounts/Categories/Analytics could sit
      below the fold, invisible without scrolling. Both sections now sort
      selected chips first (`FilterMenu.kt`'s `initialSelectedAccounts`/
      `initialSelectedCategories`), but that ordering is captured once via a
      plain `remember { filterState.accountIds }` when the sheet composes —
      i.e. on every fresh *open* (`MainScreen` only composes `FilterMenu`
      while `showFilterMenu` is true, so a `remember` with no keys resets
      each time) — not recomputed live on every toggle. The user was explicit
      about this: reordering while the sheet is already open, mid-tap, would
      read as the list rearranging itself under the user's thumb; the point
      is only to make an already-selected chip visible without scrolling the
      *next* time the sheet opens.
- [x] `TransactionFormSheet`: 4 tabs (Expense/Income/Transfer/Debt), category
      grid (max 2, primary+tag), native Android keyboard for amount entry,
      Material3 date picker, delete with confirm dialog, Copy (§10). Account
      pickers are `ModalBottomSheet`s (`AccountPickerSheet`, popup-panel idiom
      matching Tasks Android's `LabelPickerSheet`), sorted by `sortOrder`, not
      an `ExposedDropdownMenuBox`/`DropdownMenu` — a later UI pass moved every
      picker in the app to this pattern. "Mark as repaid" auto-creates the
      repayment on the same account/amount as the original rather than
      opening its own account/amount picker.
- [x] Live filtering — the nav-arg seeding (`accountId`/`categoryId`/
      `dateFrom`/`dateTo` via `SavedStateHandle`) that now feeds directly into
      `TransactionFilterState` (see the `FilterMenu` bullet above). Getting
      the *navigation* side of this right took two real bugs found by the
      user on-device, independent of the filter-state rework that came later:
      (1) `MainScreen`'s tab-switch nav pattern
      (`popUpTo(startDestinationId){saveState=true}+restoreState=true`)
      *restores* the previously *saved* Transactions entry instead of
      applying new query args to the "same" destination — fixed with a
      dedicated `navigateToTransactionsFiltered()` that pops the existing
      entry (discarding its saved state) before pushing a fresh one; (2) that
      fix alone still left a stale filter behind when the user tapped the
      *bottom-nav Transactions icon* after a filtered drill-down and then
      navigated around — because that icon still called the old
      `navigateTo("transactions")`, which hits the exact same restoreState
      bug. Fixed with `navigateToTransactionsHome()`, the bottom-nav icon's
      dedicated always-unfiltered equivalent of `navigateToTransactionsFiltered()`.
- [x] Empty state (§13) — shimmer loading via the per-screen `isLoading` flag
      (see Phase 10)

---

## Phase 6 — Accounts

> **Done.** `AccountsScreen` + `AccountsViewModel` render live accounts
> grouped into the correct fixed section order, plus an open-Debts section
> and a working archived-accounts show/hide. Row tap filters Transactions;
> the trailing `⋮` menu opens `AccountFormSheet` in edit mode.

- [x] `AccountsScreen`: fixed section order (cash/card/savings/investment)
      (§8.2). Archived accounts were counted in a header but never actually
      rendered or reachable at all — fixed with a local (unpersisted,
      matching the real PWA's own `showArchived`) show/hide toggle. Per-type
      section collapse (chevron + type icon + title + count, real
      `AccountsPage.tsx`'s `Section`) is now also implemented — persisted via
      a small dedicated `AccountsPreferences` DataStore (`collapsedGroups`),
      the same local-only-not-synced-to-`settings!A1` scope decision as
      `AnalyticsPreferences`'s balance-accounts selection.
- [x] Row tap → navigate to Transactions filtered by `accountId`, a real live
      filter end-to-end (Android-only deviation from the PWA, where the row
      tap opens edit instead — kept deliberately, and confirmed by the user
      as the actual reason this whole rewrite exists; see the matching
      deviation on Categories below). `TransactionsScreen` shows a
      dismissible `FilterChip` with the account name while a filter is
      active.
- [x] Trailing `⋮` menu (`ModalBottomSheet` + `ListItem`, matching Tasks
      Android's `TaskMobileMenu`/`LabelPickerSheet` idiom, not a
      `DropdownMenu`): **Edit** only — real `AccountModal.tsx` has no
      Delete, just an Archive checkbox inside the edit form, so that's what
      got built (no balance-reversal `ConfirmDialog`, since there's nothing
      to confirm)
- [x] `AccountFormSheet`: create mode ("Opening balance") vs. edit mode
      ("Current balance", writes `balance` directly), color picker, currency
      + type dropdowns, Archive checkbox
- [x] Open debts section (unresolved `debt_lent`/`debt_borrowed`), real
      `AccountsPage.tsx` layout — comment (or "Unknown") + "You lent"/"You
      borrowed", amount colored via the same Expense/Income convention
      `TransactionsScreen` uses for debt rows
- [x] Empty state

---

## Phase 7 — Categories

> **Done.** `CategoriesScreen` + `CategoriesViewModel` render a live, sorted
> category list with real icon+color (`CategoryIconView`). Row tap filters
> Transactions; the trailing `⋮` menu offers Edit (→ `CategoryFormSheet`) and
> Delete (→ the delete-with-transfer dialog); drag-to-reorder is wired via
> `sh.calvin.reorderable`.

- [x] `CategoriesScreen`: drag-to-reorder via `sh.calvin.reorderable`
      (`ReorderableItem` + `draggableHandle()`, `rememberReorderableLazyListState`)
      — same library and "mutate an optimistic local copy per drag frame,
      persist once on drop" performance idiom as the Tasks Android sibling's
      `FolderScreen`. A drop within one tab is translated into a single move
      within the *full* category list (both tabs share one `sortOrder` space,
      matching real `CategoriesPage.tsx`'s `handleDragEnd`, which reorders
      the full `categories` array even though only one tab is rendered) before
      calling `CategoryRepository.reorder()`. **Real bug found and fixed**:
      `reorder()` unconditionally rewrote and re-enqueued *every* category in
      the list on every drag, not just the ones that actually moved — a
      two-item swap in a 28-category list enqueued all 28 for sync (visible
      as a "28" badge on the sync icon) and needlessly re-serialized every
      other field too (numeric formatting drift like `expense_limit` "24"
      silently becoming "24.0" purely from an unnecessary `Double` round
      trip). Checked whether Tasks Android's equivalent (`FolderViewModel
      .reorderSiblings`) already solved this to port the fix — it has the
      identical blanket-rewrite behavior, so this needed its own fix: skip
      any category whose `sortOrder` already equals its new index before
      writing/enqueueing.
- [x] Row tap → navigate to Transactions filtered by `categoryId`, real live
      filter (same Android-only deviation as Accounts — real
      `CategoriesPage.tsx` opens edit on row tap; here that moved to the
      `⋮` menu, also now a `ModalBottomSheet`, not a `DropdownMenu`).
- [x] `CategoryFormSheet`: icon picker, color picker, expense/income toggles
      + limits
- [x] Delete-with-transfer flow, dedup on merge (§16.7) — implemented in
      `CategoryRepositoryImpl.deleteCategoryWithTransfer()`, now exposed both
      inside `CategoryFormSheet` and via the row's `⋮ → Delete` menu item
- [x] Empty state

---

## Phase 8 — Analytics

> **Done**, to first-pass fidelity, closing out the plan's highest-risk
> remaining phase. Before writing any UI, checked which of the real PWA's
> `analytics/*.tsx` files are actually reachable from `AnalyticsPage.tsx` —
> `MonthBarChart.tsx`, `IncomeExpenseChart.tsx`, and `BalanceChart.tsx` are
> dead code (not imported anywhere), so the real screen is just two tabs:
> `YearlyChart` (composed bar+line) and `MonthlyView` (date range +
> `CategoryDonut`) — the plan's separate "`MonthBarChart`" checklist item
> below never corresponded to a real live component. Vico's actual 2.1.3
> Compose API (`CartesianChartHost`, `rememberCartesianChart`,
> `ColumnCartesianLayer`/`LineCartesianLayer`) was read from the library's
> sources jar before writing any chart code, rather than guessed from
> memory — worth doing again if Vico is ever upgraded.

- [x] `AnalyticsScreen`: Yearly/Monthly tab bar — real `AnalyticsPage.tsx`.
      The two tabs are fully independent (no shared `analyticsMonth`) — see
      the dropped bar-tap item below for why.
- [x] `YearlyChartView` (Vico `ColumnCartesianLayer` + `LineCartesianLayer`
      combo, income up / expense down / balance line): year nav, editable
      date range (`DatePickerDialog`, same pattern as `TransactionFormSheet`),
      period chips (This year/1Y/2Y/3Y), Income/Expenses/Balance series
      toggles, "balance accounts" picker (`ModalBottomSheet`, real
      `AnalyticsAccountPicker.tsx`) with the same unconvertible-currency guard
      as the real component, **persisted across restarts** via
      `AnalyticsPreferences` (a small dedicated DataStore) per the user's
      request — the real PWA persists the equivalent `analyticsAccountIds` in
      its `usePrefsStore` too. Accounts in that picker are sorted by
      `sortOrder`, not the DAO's default `type, sortOrder` (which only makes
      sense for the type-grouped AccountsScreen, not a flat picker) — same
      fix applied to the account picker in `TransactionFormSheet`.
      On-device testing surfaced two real bugs in the first pass, both fixed:
      a crash at 2Y/3Y (`CartesianValueFormatter.format` returned an empty
      string when Vico's own `HorizontalAxis` probed past the label array's
      bounds) and bars overflowing the screen width instead of shrinking to
      fit, so only some months were visible. Fixed by dropping Vico's
      `HorizontalAxis` entirely (its labels duplicated the month strip below
      the chart anyway — this was the actual crash's root cause, not a
      separate bug) and setting `scrollState = rememberVicoScrollState(scrollEnabled = false)`,
      which puts Vico in its "fit everything, no scroll" mode (`Zoom.Content`)
      instead of overflowing — the real fix, not just thinner bars. The
      Y-axis still uses Vico's auto-range across both layers rather than the
      real component's manually computed combined domain — the one remaining
      scope-down, flagged in the file's doc comment. Briefly added, then
      reverted, a `HorizontalLine` decoration at y=0 to make the missing
      X-axis's implicit zero marker explicit — the user pointed out this
      duplicated a reference that already exists: the grouped columns
      themselves (income rising from 0, expense dropping from 0) already
      mark zero at the gap where they meet, so a second explicit line was
      redundant clutter, not a fix. What actually looked like a chart offset
      was the exchange-rate startup bug below, not a missing zero marker.
- [x] `MonthlyView`: month nav, same editable date range + period chips
      (Month/3M/6M/Year), Expenses/Income toggle with totals, hosts
      `CategoryDonut`. Date chips now show the year (`d MMM yy`, matching
      `YearlyChartView` — an on-device-testing catch, they were year-less at
      first).
- [x] `CategoryDonut` (hand-rolled `Canvas`, tech spec §17.1's call — no
      library does a ring-with-icon-badges donut): a `Stroke`-style arc ring
      (drawn as an arc stroke, not a filled path) + icon badges positioned by
      trig for slices >3% share + center total/period label + below it the
      category list with limit progress bars and a "today" tick marker. Ring
      starts at 12 o'clock (Android `drawArc` convention) rather than the
      PWA's 3 o'clock — cosmetic only. Category order confirmed correct
      on-device (sorted by `sortOrder`, same as `CategoriesScreen`).
- [x] Drill-down wiring: donut slice/row tap → Transactions filtered by
      `categoryId` **and the Monthly tab's current date range**
      (`dateFrom`/`dateTo` nav args, not just a single `month` — the period
      can span multiple months via the 3M/6M/Year chips, so a single-month
      filter wasn't enough; `TransactionsViewModel` now filters by date range
      too). **`YearlyChart` bar tap → Monthly tab: dropped, not built** — the
      user confirmed she'd never used the equivalent PWA interaction, and
      Vico's Cartesian API doesn't expose a simple per-datapoint tap the way
      Recharts does, so this isn't a gap to come back to; tech spec §17
      updated to record it as a resolved decision.
- [x] Empty state (both tabs, and the donut)

**Verified on-device**, iterating through several real bugs the user found
by actually using the screen (crash at 2Y/3Y, chart overflow, missing year in
date chips, unpersisted account filter, unsorted account pickers) — all
fixed above.

- [x] Resolve icon set (§17.2) — **decided and done**, hybrid rather than
      either of the two original options: `CategoryIcons` (`ui/icons/`) maps
      each of the 36 Lucide names from the PWA's real `IconPicker.tsx` (the
      tech spec's count of 37 was stale) to a Material Icons Extended
      equivalent — family-consistent with Tasks Android, which uses stock
      Material icons throughout its real UI (a few duplicated as raw
      drawables, but only for Glance-widget technical reasons, not a
      deliberate Lucide-style choice — checked against its actual source).
      All app chrome (bottom nav, empty-state icons, account-type icons,
      Menu rows) switched from Filled to Outlined to match Tasks' icon
      weight. One name (`HandCoins`) has no decent Material match — hand-
      drawn as `ic_lucide_hand_coins.xml` in genuine Lucide style (24x24,
      stroke-only, strokeWidth 2, round caps/joins), the fallback path from
      the original decision, now used for real. `CategoryIconView` (mirrors
      PWA's `CategoryIcon.tsx`: colored circle, white icon) is wired into
      CategoriesScreen, TransactionsScreen's row, and the category grid in
      TransactionFormSheet.

---

## Phase 9 — Menu, Settings, Help, Feedback, About

> **Done**, to first-pass fidelity — this phase is fully implemented,
> including two things marked "follow-up" in the spec's own Settings section
> (spreadsheet switching, base-currency selection) actually being wired to
> real repositories rather than left as stubs.

- [x] `MenuScreen`: avatar, name/email, rows for Settings/Help/Feedback/
      About/Sign out (§8.5)
- [x] `SettingsScreen`: Spreadsheet switch card (dropdown of Drive files,
      not yet the richer "expandable picker" the spec describes, but
      functionally complete — selecting one clears local data and re-syncs),
      base-currency selector (EUR/USD/RUB) → triggers
      `ExchangeRateRepository.setBaseCurrency()` (§8.6)
- [x] `HelpScreen`: static content (Basics, Data & sync, Usage tips) (§8.7)
- [x] `FeedbackScreen`: POST to Apps Script endpoint, `app=Money`,
      `instanceFollowRedirects = false` (§8.8) — **reuses the Tasks Android
      sibling's Apps Script URL**, since that endpoint is evidently shared
      across the author's apps and differentiated by the `app` field; confirm
      it actually accepts `app=Money` server-side, or swap in a dedicated URL
- [x] `AboutScreen`: `BuildConfig.VERSION_NAME` + "Check for updates" link to
      `github.com/JuliaSivridi/Money_Android/releases` (§17 resolved item 4)
      — repo URL guessed from the Tasks Android sibling's naming convention;
      fix if the actual repo lives somewhere else

---

## Phase 10 — Polish & Cross-Cutting

- [x] Shimmer loading rows (§13) — `ShimmerListPlaceholder` (pulse-alpha
      idiom, same as the Tasks Android sibling's `ShimmerTaskList`), shown on
      Transactions/Accounts/Categories whenever the list is empty and still
      loading. First attempt gated this on `SyncManager.syncState ==
      Syncing`, confirmed wrong by the user on-device: with a warm install
      Room already has cached data, so nothing is ever "empty and syncing"
      even though the actual flash she kept seeing is real — just a
      different, narrower gap (every cold start, between the ViewModel
      existing and its `StateFlow` moving past its own default initial
      value), unrelated to whether a sync happens to be running. Replaced
      with an `isLoading` flag on each screen's own state, `true` only until
      that screen's first real Room emission and `false` forever after —
      the three ViewModels no longer need `SyncManager` for this at all.
- [x] `ErrorSnackbarEffect` wired from `BaseViewModel.uiError` — same
      `LocalSnackbarHostState` CompositionLocal + effect pattern as the Tasks
      Android sibling. `MainScreen` provides the shared host (and its own
      `MainViewModel`'s errors) and every screen/sheet with a `BaseViewModel`
      hangs off it: Transactions, Accounts, Categories, Analytics, and the
      three form sheets (`TransactionFormSheet`, `AccountFormSheet`,
      `CategoryFormSheet`) — CompositionLocals propagate correctly through
      `ModalBottomSheet`'s dialog window. `SettingsScreen` is the one
      exception: it renders as a full-screen overlay on top of MainScreen's
      own `Scaffold`, which would hide that Scaffold's snackbar host behind
      it, so it gets its own local `SnackbarHostState` instead (same pattern
      `FeedbackScreen` already used for its own send-result messages).
- [x] Icon/color parity spot-check against the PWA — **user confirmed
      on-device**, side by side, no issues found
- [x] Dark-mode pass — **user confirmed on-device**, looks correct; no
      per-screen issues found (the HSL→hex approximation flagged in Phase 4
      as "not colorimetrically exact" evidently reads fine in practice)
- [x] Shape consistency — the user asked for rounded rectangles everywhere,
      never fully-rounded/oval pills (a deliberate Android-only deviation
      from the PWA, which keeps `rounded-full` for filter chips specifically
      — see tech spec §17 resolved item 11). `ui/theme/Shape.kt`'s
      `ControlShape` (`RoundedCornerShape(10.dp)`) is applied to every
      Button/SegmentedButton/chip call site app-wide (Feedback's Send,
      Sign-in, "Check for updates", Settings' spreadsheet-switch button, all
      four SegmentedButtonRow groups, all chips). More importantly, chips
      themselves were consolidated into one shared component,
      `ui/common/PillChip.kt` (`PillChip` + `DateFieldChip`) — previously
      `FilterMenu`, `TransactionFormSheet`'s date field, and
      `YearlyChartView`'s date-range/period chips each used Material3's own
      `FilterChip` independently, which (a) reserves a fixed ~48dp
      touch-target regardless of visual size — the actual reason the
      Accounts/Categories capped-height sections in `FilterMenu` only fit 2
      rows instead of 3, and "Debt" wrapped to its own line — and (b) meant
      three near-identical but not-quite-identical implementations of "a
      chip with a calendar icon that opens a `DatePickerDialog`." Now there's
      one.
- [x] Field-height consistency (partial) — `AccountFieldTrigger`
      (`PickerFieldTrigger`'s external-caption-above-a-box anatomy) and
      `AmountField` (a stock `OutlinedTextField` with its own internal
      floating label) render at different heights, which showed up as a
      visibly misaligned Account/Amount row in `TransactionFormSheet`. Fixed
      the immediate symptom by bottom-aligning that row (and
      `CategoryFormSheet`'s Expense/Income limit rows, plus giving those
      limit fields a proper `label` instead of an ad-hoc caption `Text`
      before them — the one `OutlinedTextField` in the app missing a label).
      **Not fixed**: the deeper inconsistency of having two different field
      anatomies at all. `PickerFieldTrigger` exists specifically because a
      real `OutlinedTextField(readOnly = true)` swallows the outer
      `.clickable` tap for its own cursor/focus handling (a bug hit earlier
      on the old account dropdown) — making it visually match
      `OutlinedTextField`'s internal-floating-label style would need a
      verified-on-device workaround (e.g. an invisible click-intercepting
      overlay on top of a real disabled `OutlinedTextField`) that wasn't
      attempted here since it can't be validated without a device in hand;
      worth doing as part of the accessibility/UI audit below rather than
      guessing blind.
- [x] Accessibility pass — done via a general-purpose agent audit (read-only,
      checked against real Material3 component source + WCAG contrast
      thresholds, not general Material Design knowledge) plus a follow-up fix
      round. Confirmed clean: content descriptions (every icon-only
      affordance app-wide), shape consistency (zero stray `FilterChip(`
      usage, `ControlShape` applied everywhere it should be). Fixed:
      - **Color contrast** — `ExpenseColor`/`IncomeColor` (Tailwind
        red-400/green-500) used as literal `Text` color failed WCAG AA badly
        in light mode (~2.8:1/2.3:1 on white, need 4.5:1) — exactly the color
        of every expense/income amount in the app. Added
        `expenseTextColor()`/`incomeTextColor()` (`ui/theme/Theme.kt`):
        darker Tailwind red-600/green-700 for light mode, the original
        bright values for dark mode (already fine there — a darker shade
        would fail worse against a dark background, so this needed two
        variants, not one). Wired into `TransactionsScreen.kt`'s
        `RowAmount`, `AccountsScreen.kt`'s `DebtRow`, `CategoryDonut.kt`'s
        limit-status text — NOT into the progress-bar fill or icon tints,
        which only need WCAG's 3:1 graphic threshold and read fine as-is.
        Same root cause hit the selected `PillChip` label (`primary` text on
        a `primary/15%` tint ≈2.9:1) — fixed with `selectedChipTextColor()`,
        which turned out to just be the real PWA's own (previously
        unwired) `--accent-foreground` value (`AccentForeground` for light,
        `ForegroundDark` for dark — the PWA's dark `--accent-foreground` is
        literally `0 0% 95%`, identical to its `--foreground`). Also
        lightened `MutedForegroundDark` (#949494→#A3A3A3, same neutral gray,
        no hue shift) — the dark-mode `onSurfaceVariant` caption color was
        at ~3.98:1 against `SurfaceDark`/`SurfaceContainerDark`, under 4.5:1.
      - **Touch targets** — `ColorPickerGrid`/`IconPickerGrid` cells raised
        to a real 48dp tap area (was ~30×28dp / 40dp); `AccountFormSheet`/
        `CategoryFormSheet`'s icon-and-color-swatch pickers wrapped in 48dp
        invisible tap boxes around the same visual size; `YearlyChartView`'s
        Income/Expenses/Balance `SeriesToggle` legend had *zero* padding
        (the smallest tap target in the app) — added `padding(vertical =
        14.dp)`. Left alone, deliberately: `DateFieldChip`'s clear icon (user
        likes the current look), Settings' 32dp "Change" button, the
        `CategoryCell` grid item names at 11sp (user is wary of wrap at a
        larger size, "к этому можно привыкнуть").
      - **Brand color drift** — `Primary`/`PrimaryDark` (`#E0813D`/`#D9995E`)
        turned out to be a slightly-off hand conversion of the PWA's
        `hsl(25 75% 55%)`/`hsl(25 65% 63%)` tokens, NOT byte-identical to the
        PWA's actual icon/theme-color (`#E07E38`) or to Tasks Android's own
        (already-correct) `Primary`/`PrimaryDark`. Fixed to `#E07E38`/
        `#D98D52`, adopted verbatim from Tasks Android rather than
        re-deriving from HSL by hand again.
      - **App icon** — the vector port from the previous round used the same
        scale ratio as the source SVG (~75% of canvas), which turned out to
        undersell how much some real launchers (MIUI, confirmed via user
        screenshot) zoom an adaptive icon's foreground beyond the web
        maskable-icon convention — the wallet nearly touched the rounded-
        square edge on-device. Scaled down further (~46% of canvas) for real
        margin under that extra zoom.
      Not touched (background/surface grays are off-limits per explicit user
      instruction — "тёмно-серые без оттенков фоны... неприкасаемые"): only
      foreground/text colors were adjusted, no `Background*`/`Surface*`/
      `SurfaceContainer*` token changed.
- [ ] `NavigationBarItem`'s selected-item indicator is a fully-rounded pill
      (`CircleShape`) with **no way to change it** — verified in Material3's
      own source (`NavigationBar.kt`): the indicator shape is hardcoded to
      `ShapeKeyTokens.CornerFull` unconditionally, not read from
      `MaterialTheme.shapes` or exposed as a component parameter at all.
      Fixing it means reimplementing `NavigationBarItem` from scratch — the
      user decided this isn't worth the risk for a small, transient element;
      explicitly not doing this.
- [x] FAB color — fixed, explicit `containerColor = colorScheme.primary` /
      `contentColor = colorScheme.onPrimary` on all 3 `FloatingActionButton`
      calls in `MainScreen.kt`. Tasks Android had the identical bug and fixed
      it the same way independently, same session.
- [x] App launcher icon — replaced with a real vector port of Money PWA's
      own icon (see the accessibility-pass entry above for the scale
      correction that followed).

---

## Phase 11 — CI/CD & Release

- [x] `release.yml`: tag-triggered (`v*`), keystore decode, `assembleRelease`
      with the 4 GB heap `GRADLE_OPTS` (§14) — ported verbatim from Tasks
      Android's own working workflow (`chmod +x ./gradlew` before the build
      step is why — GitHub Actions' checkout doesn't reliably preserve the
      executable bit, the actual "GitHub ругается на права" the user
      remembered from before). Reuses her one shared keystore across all her
      apps (same signing key → same SHA-1, only the Android OAuth client
      registration is per-package) — `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/
      `KEY_ALIAS`/`KEY_PASSWORD` are repo Secrets, decoded to a file at
      `${{ github.workspace }}/keystore.jks` and read by `app/build.gradle.kts`'s
      existing (already-present, unmodified) env-var-driven signing config —
      same `KEYSTORE_PATH`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`
      mechanism locally and in CI, no code difference between the two.
      Not yet dry-run (needs the release OAuth client's SHA-1 registration —
      done, propagating — and the Secrets, which the user was setting up as
      of this note).
- [ ] Dry-run the First-Time Setup doc (§15) on a clean machine/second dev —
      confirm no missing steps
- [ ] Signed release build smoke test: fresh install, sign in, create
      `db_money`, create+sync a transaction, kill/reopen app

---

## Phase 12 — Resolve Remaining Open Items

Not blocking earlier phases individually, but should close out before/around
whichever phase touches them:

- [x] `FilterMenu` presentation resolved as a `ModalBottomSheet` (§17.9) —
      see Phase 5. No prototyping of the floating-popup alternative was
      needed in the end: once the real `FilterPanel.tsx` source was read
      (five sections including a capped-scroll account/category list), a
      bottom sheet was the only option that could fit the content at all.
- [x] Analytics → Transactions drill-down catalog — resolved as donut-only
      (§17 resolved item 8, stale duplicate checkbox — this and that item
      described the same open question)
