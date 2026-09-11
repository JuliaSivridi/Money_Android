# Stler Tasks Android — Technical Specification

**Version:** 3.0 (September 2026)  
**Repository:** github.com/JuliaSivridi/Tasks_Android  
**Stack:** Kotlin · Jetpack Compose · Room · Hilt · WorkManager · Glance · Google Sheets API v4 · Google Calendar API v3  
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

---

## Table of Contents

1. [Overview](#1-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture](#3-architecture)
4. [Package / Folder Structure](#4-package--folder-structure)
5. [Data Model](#5-data-model)
   - [5.1 Task](#51-task)
   - [5.2 Folder](#52-folder)
   - [5.3 Label](#53-label)
   - [5.4 CalendarEvent](#54-calendarevent)
   - [5.5 CalendarItem](#55-calendaritem)
   - [5.6 SyncQueue Entry](#56-syncqueue-entry)
   - [5.7 Room Database (version 8)](#57-room-database-version-8)
   - [5.8 Google Sheets Schema](#58-google-sheets-schema)
6. [Authentication & First-Launch Setup](#6-authentication--first-launch-setup)
   - [6.1 OAuth Scopes](#61-oauth-scopes)
   - [6.2 Sign-In Flow](#62-sign-in-flow)
   - [6.3 First-Launch Spreadsheet Creation](#63-first-launch-spreadsheet-creation)
   - [6.4 Onboarding Seed Data](#64-onboarding-seed-data)
   - [6.5 Token Refresh](#65-token-refresh)
   - [6.6 Sign-Out](#66-sign-out)
7. [Synchronization](#7-synchronization)
8. [UI Screens](#8-ui-screens)
   - [AuthScreen](#authscreen)
   - [8.1 Upcoming](#81-upcoming)
   - [8.2 All Tasks](#82-all-tasks)
   - [8.3 Completed](#83-completed)
   - [8.4 Folder](#84-folder)
   - [8.5 FilterBar](#85-filterbar-shared-component)
   - [8.6 Calendar](#86-calendar)  <!-- was removed: Label (8.5), Priority (8.6) -->
   - [8.7 CalendarEventItem](#87-calendareventitem)
   - [8.8 MainScreen](#88-mainscreen--global-fab-and-navigation)
   - [8.9 Settings Screen](#89-settings-screen)
   - [8.10 Sidebar](#810-sidebar-sidebarmenu)
   - [HelpScreen](#helpscreen)
   - [FeedbackScreen](#feedbackscreen)
9. [TaskItem Component](#9-taskitem-component)
10. [TaskFormSheet](#10-taskformsheet)
11. [Widgets](#11-widgets)
12. [Theme & Colors](#12-theme--colors)
13. [Navigation](#13-navigation)
14. [Loading & Empty States](#14-loading--empty-states)
15. [CI/CD & Build](#15-cicd--build)
16. [First-Time Setup (New Developer)](#16-first-time-setup-new-developer)
17. [Key Algorithms](#17-key-algorithms)

---

## 1. Overview

Stler Tasks is a personal task manager for Android. It is a native rewrite of a PWA with the same Google Sheets backend, so both apps share one `db_tasks` spreadsheet per user. There is no dedicated backend server — all persistent task data lives in Google Sheets, accessed via the Sheets API v4. Room serves as a local cache that makes the app fully functional offline.

In addition to tasks, the app integrates with **Google Calendar API v3**: events from selected calendars are fetched, cached in Room, and displayed alongside tasks in the Upcoming screen, Calendar screen, and all four widgets.

**Key design goals:**
- Google Sheets as the single source of truth for tasks — no proprietary cloud service
- Full offline support via Room + sync queue
- Reactive UI — all screens observe Room Flows; data updates propagate automatically
- Clean MVVM + Repository architecture with Hilt DI throughout
- Glance widgets that react to Room changes without explicit refresh calls
- Google Calendar events surfaced inline with tasks, unified by date

**Locale override:** `MainActivity.attachBaseContext()` forces `en-GB` locale for the entire activity. This locks UI strings to English and sets Monday as the first day of week (used by the Material3 DatePicker via `Locale.getDefault()`).

---

## 2. Tech Stack

| Layer | Library | Version | Notes |
|---|---|---|---|
| Language | Kotlin | — | JVM toolchain 11 |
| UI | Jetpack Compose + Material 3 | BOM (platform) | Compose-only, no XML layouts |
| DI | Hilt | — | KSP processor; `@HiltAndroidApp`, `@HiltViewModel`, `@HiltWorker` |
| Local DB | Room | — | version 8; explicit migrations 4→5→6→7→8 |
| Networking | Retrofit + OkHttp | — | Bearer token interceptor + 401 Authenticator |
| Serialization | Gson | — | Retrofit converter + SyncQueue payload serialization |
| Background | WorkManager (Hilt) | — | Periodic sync (30 min) + one-off manual sync |
| Widgets | Glance (`glance-appwidget`, `glance-material3`) | — | 4 widget types |
| Auth | CredentialManager + Identity API | — | Google Sign-In + scope authorization |
| Preferences | DataStore (`datastore-preferences`) | — | Token, user info, spreadsheet ID, calendar IDs |
| Image loading | Coil (`coil-compose`) | — | User avatar in TopAppBar |
| Drag & drop | `reorderable` (sh.calvin) | — | FolderScreen drag-to-reorder |
| Coroutines | `kotlinx.coroutines-android` + `-play-services` | — | |
| Navigation | Navigation Compose | — | Single NavHost inside MainScreen |
| Lifecycle | Lifecycle ViewModel / Runtime | — | `WhileSubscribed(5000)` sharing strategy |

**Build config:** `applicationId = "com.stler.tasks"`, `versionCode = 31`, `versionName = "3.0"`, `minSdk = 26`, `targetSdk = 36`. KSP with `room.schemaLocation = "$projectDir/schemas"`. Signing via environment variables `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (only wired if `KEYSTORE_PATH` is non-blank, so debug builds are unaffected).

---

## 3. Architecture

**Pattern:** MVVM + Repository

```
┌──────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  Composables ← ViewModel (StateFlow) ← Repository           │
└──────────────────────────────────────────────────────────────┘
         │                       │
         │ suspend fun            │ Flow<List<T>>
         ▼                       ▼
┌─────────────────────┐  ┌──────────────────────────────────┐
│  TaskRepositoryImpl │  │  CalendarRepositoryImpl          │
│  (write path)       │  │  (Calendar API + Room cache)     │
└──────────┬──────────┘  └──────────────────────────────────┘
           │
    ┌──────┴──────────────────────────────┐
    │               Room DAOs              │
    │  TaskDao / FolderDao / LabelDao /    │
    │  SyncQueueDao / CalendarEventDao     │
    └──────┬──────────────────────────────┘
           │                          ▲
    SyncQueue                         │
           │                     SyncWorker (WorkManager)
           │                          │
           ▼                          │
    Google Sheets API v4         Google Calendar API v3
    (SheetsApi via Retrofit)     (CalendarApi via Retrofit)
```

### Write path (task mutation)

1. ViewModel calls `repository.createTask(task)` / `updateTask` / `deleteTask` / etc.
2. Repository writes to Room via DAO (`taskDao.upsert(entity)`).
3. Repository enqueues a `SyncQueueEntity` (INSERT / UPDATE / DELETE) via `syncQueueDao.enqueue()`.
4. Repository calls `widgetRefresher.refreshAll()` (debounced 400 ms fire-and-forget).
5. Room Flow emits → all observing ViewModels recompose → UI updates immediately (offline-first).
6. On next sync trigger, `SyncWorker.push()` drains the queue to Sheets API.

### Read path

1. Screen collects `StateFlow` from ViewModel (backed by `stateIn(WhileSubscribed(5000))`).
2. ViewModel observes Room DAO Flow (e.g. `taskDao.observeAllPending()`).
3. `SyncWorker.pull()` calls `taskRepository.fetchAllAndSave()` → `sheetsApi.batchGet()` → `db.withTransaction { taskDao.upsertAll(...) }`.
4. Room emits new data → ViewModels recompose → UI updates.

### Error handling

- All repository operations are wrapped in `runCatching` or expose `Result<T>` to callers.
- ViewModels extend `BaseViewModel` which exposes `uiError: SharedFlow<String>` via `safeLaunch { }`.
- `SyncWorker` returns `Result.retry()` on exception for up to 4 attempts (5th returns `Result.failure()`).
- `SyncQueueDao.deleteExhausted(maxRetries = 5)` removes items that have failed 5+ times.
- On 401 from OkHttp, the `Authenticator` calls `tokenProvider.refreshToken()` once; returns null if refresh fails.

---

## 4. Package / Folder Structure

```
com.stler.tasks/
├── auth/                    GoogleAuthRepository, AuthPreferences (DataStore), AuthData, FeatureFlags
├── data/
│   ├── local/
│   │   ├── dao/             TaskDao, FolderDao, LabelDao, SyncQueueDao, CalendarEventDao
│   │   ├── entity/          Room entities + toDomain() / toEntity() mappers
│   │   └── TaskDatabase.kt  RoomDatabase v8, migration objects
│   ├── remote/
│   │   ├── SheetsApi.kt     Retrofit interface (batchGet, append, batchUpdate, clear)
│   │   ├── SheetsMapper.kt  Row ↔ Entity conversions; date serial handling
│   │   ├── CalendarApi.kt   Retrofit interface (Calendar API v3)
│   │   ├── CalendarMapper.kt RFC3339 parsing, DTO → entity
│   │   ├── TokenProvider.kt Interface implemented by GoogleAuthRepository
│   │   └── dto/             Gson DTOs for Sheets, Calendar, Drive responses
│   └── repository/
│       ├── TaskRepository.kt          Interface
│       ├── TaskRepositoryImpl.kt      Mutations + pull
│       ├── CalendarRepository.kt      Interface
│       └── CalendarRepositoryImpl.kt  CRUD + fetch/cache
├── di/
│   ├── DatabaseModule.kt    Room, all DAOs
│   └── NetworkModule.kt     OkHttpClient, Retrofit, SheetsApi, Gson
├── domain/model/            Task, Folder, Label, CalendarEvent, CalendarItem, ListItem, SyncState
├── sync/
│   ├── SyncState.kt         Sealed class: Idle / Syncing / Pending(count)
│   ├── SyncManager.kt       WorkManager scheduling + syncState Flow
│   └── SyncWorker.kt        Push + pull + calendar sync
├── ui/
│   ├── auth/                AuthScreen, AuthViewModel, AuthUiState
│   ├── main/                MainScreen, MainViewModel, TasksTopAppBar, SidebarMenu, SidebarPreferences
│   │                        MenuScreen, FoldersListScreen, CalendarsListScreen, AboutScreen
│   ├── alltasks/            ActiveTasksScreen (toggle wrapper), AllTasksScreen + AllTasksViewModel, FilterBar
│   ├── upcoming/            UpcomingScreen + UpcomingViewModel
│   ├── completed/           CompletedScreen + CompletedViewModel
│   ├── folder/              FolderScreen (drag-reorder) + FolderViewModel
│   ├── calendar/            CalendarScreen + CalendarViewModel + CalendarEventItem
│   ├── task/                TaskFormSheet, TaskFormViewModel, TaskItem, TaskColors, pickers
│   ├── settings/            SettingsScreen + SettingsViewModel (nav mode, feature flags, folder/label/calendar mgmt)
│   ├── help/                HelpScreen (static content)
│   ├── feedback/            FeedbackScreen + FeedbackViewModel
│   ├── navigation/          Screen.kt (route constants: UPCOMING, ALL_TASKS, FOLDERS_LIST, CALENDARS_LIST, MENU_SCREEN, FOLDER, CALENDAR)
│   ├── theme/               Color.kt, Theme.kt, Type.kt
│   └── util/                EmptyState, ShimmerTaskList, ErrorSnackbarEffect, LocalSnackbarHostState
├── widget/
│   ├── UpcomingWidget.kt    GlanceAppWidget — 7-day timeline
│   ├── FolderWidget.kt      GlanceAppWidget — single folder, hierarchical
│   ├── TaskListWidget.kt    GlanceAppWidget — flat list with filters
│   ├── CalendarWidget.kt    GlanceAppWidget — calendar events
│   ├── WidgetRefresher.kt   Debounced updateAll() coordinator
│   ├── WidgetPrefs.kt       SharedPreferences per widget instance
│   ├── WidgetColors.kt      ColorProvider constants for Glance
│   └── WidgetTaskRow.kt     Shared row composable for task rows in widgets
├── AppContainer.kt          Manual DI container for non-Hilt singletons (retained)
├── MainActivity.kt          Locale override, auth gate, deep link handler
└── TasksApplication.kt      Application; initializes SyncManager + WidgetRefresher
```

---

## 5. Data Model

### 5.1 Task

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Primary key, e.g. `tsk_a1b2c3d4` |
| parentId | String | `""` | Parent task ID; `""` = root task |
| folderId | String | `"fld-inbox"` | Owning folder ID |
| title | String | — | Required task description |
| status | TaskStatus | `PENDING` | `PENDING` or `COMPLETED` |
| priority | Priority | `NORMAL` | `URGENT`, `IMPORTANT`, or `NORMAL` |
| deadlineDate | String | `""` | ISO date `"YYYY-MM-DD"` or `""` |
| deadlineTime | String | `""` | `"HH:MM"` or `""` |
| isRecurring | Boolean | `false` | Whether the task recurs |
| recurType | RecurType | `NONE` | `NONE`, `DAYS`, `WEEKS`, `MONTHS`, `YEARS` |
| recurValue | Int | `1` | Recurrence interval |
| labels | List\<String\> | `[]` | List of label IDs (domain); comma-separated string in entity/Sheets |
| sortOrder | Int | `0` | Position within sibling list (multiples of 10 after reorder) |
| createdAt | String | `""` | ISO 8601 instant (`Instant.now().toString()`) |
| updatedAt | String | `""` | ISO 8601 instant |
| completedAt | String | `""` | ISO 8601 instant or `""` |
| isExpanded | Boolean | `false` | Whether subtree is expanded in FolderScreen |

**Invariants:**
- `isRoot = parentId.isEmpty()` (computed property on domain model)
- Completing a recurring task advances `deadlineDate` by `recurValue × recurType` instead of marking it `COMPLETED`
- Completing a non-recurring task recursively completes all non-deleted descendants
- Deleting a task sets `status = "deleted"` and recursively sets descendants to `"deleted"` (soft delete)
- Restoring a task sets `status = "pending"` and recursively restores all `completed` descendants; `deleted` descendants are left unchanged
- `deleted` tasks are filtered from all DAO queries (queries check `status = 'pending'` or `status = 'completed'`)

### 5.2 Folder

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Primary key, e.g. `fld-inbox`, `fld_a1b2c3d4` |
| name | String | — | Display name |
| color | String | — | Hex color string, e.g. `"#f97316"` |
| sortOrder | Int | `0` | Display order in sidebar |

**Invariants:**
- `isInbox = id == "fld-inbox"` — the Inbox folder always appears first in `FolderDao.observeAll()` (SQL `CASE id WHEN 'fld-inbox' THEN 0 ELSE 1 END`)
- Deleting a folder moves all non-deleted tasks to `fld-inbox` in a single batch

### 5.3 Label

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Primary key, e.g. `lbl_a1b2c3d4` |
| name | String | — | Display name |
| color | String | — | Hex color string |
| sortOrder | Int | `0` | Column D in labels sheet |

### 5.4 CalendarEvent

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Google Calendar event ID |
| calendarId | String | — | Google Calendar ID (e.g. `"primary"` or email) |
| calendarName | String | — | Human-readable calendar name |
| calendarColor | String | — | Hex color from Calendar API |
| title | String | — | Event summary; `"(No title)"` if blank |
| startDate | String | — | `"YYYY-MM-DD"` |
| startTime | String | — | `"HH:MM"` or `""` for all-day |
| endDate | String | — | `"YYYY-MM-DD"` |
| endTime | String | — | `"HH:MM"` or `""` for all-day |
| isAllDay | Boolean | — | True when `start.dateTime` is null in API response |
| recurringEventId | String | `""` | Base event ID of recurring series; `""` if one-off |
| isEditable | Boolean | `true` | False for shared read-only calendars (access role not `owner`/`writer`) |

**Computed:** `isRecurring = recurringEventId.isNotBlank()`

### 5.5 CalendarItem

| Field | Type | Description |
|---|---|---|
| id | String | Google Calendar ID |
| summary | String | Display name |
| color | String | Hex color string |
| isSelected | Boolean | Whether the user has enabled this calendar |
| accessRole | String | `"owner"`, `"writer"`, `"reader"`, `"freeBusyReader"` |

Used in Settings to display the calendar picker list and to determine `isEditable` on events (`owner` or `writer` → editable).

### 5.6 SyncQueue Entry

| Field | Type | Default | Description |
|---|---|---|---|
| id | Long | auto-generate | Primary key |
| entityType | String | — | `"task"`, `"folder"`, `"label"`, or `"settings"` |
| operation | String | — | `"INSERT"`, `"UPDATE"`, or `"DELETE"` |
| entityId | String | — | ID of the affected entity |
| payloadJson | String | — | Gson-serialized entity; empty for DELETE |
| retryCount | Int | `0` | Incremented on failure; items with `retryCount >= 5` are deleted |

### 5.7 Room Database (version 8)

**Database name:** `tasks.db`  
**Room version:** 8

#### Table: `tasks`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| parentId | TEXT NOT NULL | Default `''`; indexed |
| folderId | TEXT NOT NULL | Default `'fld-inbox'`; indexed |
| title | TEXT NOT NULL | |
| status | TEXT NOT NULL | `'pending'` / `'completed'`; indexed |
| priority | TEXT NOT NULL | `'urgent'` / `'important'` / `'normal'` |
| deadlineDate | TEXT NOT NULL | `'YYYY-MM-DD'` or `''`; indexed |
| deadlineTime | TEXT NOT NULL | `'HH:MM'` or `''` |
| isRecurring | INTEGER NOT NULL | 0/1 |
| recurType | TEXT NOT NULL | `'days'`/`'weeks'`/`'months'`/`'years'`/`''` |
| recurValue | INTEGER NOT NULL | Default 1 |
| labels | TEXT NOT NULL | Comma-separated label IDs or `''` |
| sortOrder | INTEGER NOT NULL | |
| createdAt | TEXT NOT NULL | ISO instant |
| updatedAt | TEXT NOT NULL | ISO instant |
| completedAt | TEXT NOT NULL | ISO instant or `''` |
| isExpanded | INTEGER NOT NULL | 0/1 |

**Indices:** `parentId`, `folderId`, `status`, `deadlineDate`

#### Table: `folders`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| name | TEXT NOT NULL | |
| color | TEXT NOT NULL | hex |
| sortOrder | INTEGER NOT NULL | Default 0 |

#### Table: `labels`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| name | TEXT NOT NULL | |
| color | TEXT NOT NULL | hex |
| sortOrder | INTEGER NOT NULL | Default 0 |

#### Table: `sync_queue`

| Column | SQLite Type | Notes |
|---|---|---|
| id | INTEGER PRIMARY KEY AUTOINCREMENT | |
| entityType | TEXT NOT NULL | |
| operation | TEXT NOT NULL | |
| entityId | TEXT NOT NULL | |
| payloadJson | TEXT NOT NULL | |
| retryCount | INTEGER NOT NULL | Default 0 |

#### Table: `calendar_events`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| calendarId | TEXT NOT NULL | indexed |
| calendarName | TEXT NOT NULL | |
| calendarColor | TEXT NOT NULL | |
| title | TEXT NOT NULL | |
| startDate | TEXT NOT NULL | |
| startTime | TEXT NOT NULL | |
| endDate | TEXT NOT NULL | |
| endTime | TEXT NOT NULL | |
| isAllDay | INTEGER NOT NULL | |
| recurringEventId | TEXT NOT NULL | Default `''`; indexed |
| isEditable | INTEGER NOT NULL | Default 1 |

**Indices:** `calendarId`, `recurringEventId`

#### Migration History

| From→To | Change |
|---|---|
| 4→5 | Added `calendar_events` table |
| 5→6 | `ALTER TABLE calendar_events ADD COLUMN recurringEventId TEXT NOT NULL DEFAULT ''` |
| 6→7 | Added indices on `calendarId` and `recurringEventId` |
| 7→8 | `ALTER TABLE calendar_events ADD COLUMN isEditable INTEGER NOT NULL DEFAULT 1` |

`fallbackToDestructiveMigration(dropAllTables = true)` is set as a safety net for unexpected version gaps.

### 5.8 Google Sheets Schema

All reads use `valueRenderOption = UNFORMATTED_VALUE`. All writes use `valueInputOption = RAW`. Rows with a blank `id` column (col A) are skipped by `SheetsMapper.rowToTask()` / `rowToFolder()` / `rowToLabel()`.

#### Sheet: `tasks` (columns A–Q)

| Col | Header | Format / Values |
|---|---|---|
| A | id | String, e.g. `tsk_a1b2c3d4` |
| B | parent_id | String or `""` |
| C | folder_id | String, e.g. `fld-inbox` |
| D | title | String |
| E | status | `"pending"` or `"completed"` |
| F | priority | `"urgent"`, `"important"`, or `"normal"` |
| G | deadline_date | `"YYYY-MM-DD"` string (RAW write); may be read back as Sheets serial number (double) when written via USER_ENTERED elsewhere — `SheetsMapper.dateStr()` converts serial to ISO format |
| H | deadline_time | `"HH:MM"` or `""` |
| I | is_recurring | `"TRUE"` or `"FALSE"` |
| J | recur_type | `"days"`, `"weeks"`, `"months"`, `"years"`, or `""` |
| K | recur_value | Integer string, e.g. `"1"` |
| L | labels | Comma-separated label IDs or `""` |
| M | sort_order | Integer string |
| N | created_at | ISO 8601 instant string |
| O | updated_at | ISO 8601 instant string |
| P | completed_at | ISO 8601 instant string or `""` |
| Q | is_expanded | `"TRUE"` or `"FALSE"` |

#### Sheet: `folders` (columns A–D)

| Col | Header | Format |
|---|---|---|
| A | id | String |
| B | name | String |
| C | color | Hex string e.g. `"#f97316"` |
| D | sort_order | Integer string |

#### Sheet: `labels` (columns A–D)

| Col | Header | Format |
|---|---|---|
| A | id | String |
| B | name | String |
| C | color | Hex string |
| D | sort_order | Integer string |

#### Sheet: `settings` (index 3)

Present in spreadsheet structure (created at onboarding); not currently read/written by the Android app.

#### Sheet: `meta` (index 4)

Present in spreadsheet structure (created at onboarding); not currently read/written by the Android app.

**Row-number lookup for UPDATE/DELETE:** `SheetsMapper.findRowNumber(rows, id)` iterates rows, skips the header (index 0), and returns the 1-based row number when `row[0] == id`. Returns null if not found (operation is skipped).

---

## 6. Authentication & First-Launch Setup

### 6.1 OAuth Scopes

- `https://www.googleapis.com/auth/drive.file` — read/write files this app created or that the user picked via the Picker in the web client; covers Sheets API access for `db_tasks`
- `https://www.googleapis.com/auth/calendar.readonly` — list calendars and read calendar metadata (no access to calendar settings or sharing)
- `https://www.googleapis.com/auth/calendar.events` — create, update, and delete calendar events

### 6.2 Sign-In Flow

1. **`AuthViewModel` init** — reads `GoogleAuthRepository.isSignedIn` (one-shot `first()`). If already signed in, emits `AuthUiState.SignedIn`; otherwise emits `AuthUiState.SignedOut`.
2. **User taps "Sign in with Google"** → `AuthViewModel.startSignIn(context)` → `GoogleAuthRepository.signIn(context)`.
3. **Step 1 — Google ID Token:** `CredentialManager.getCredential()` with `GetGoogleIdOption` (all accounts, not just pre-authorized). Returns `GoogleIdTokenCredential` containing `id` (email), `displayName`, `profilePictureUri`.
4. **Step 2 — Scope authorization:** `Identity.getAuthorizationClient(context).authorize(...)` requesting scopes: `drive.file`, `calendar.readonly`, `calendar.events` (see §6.1).
5. **If `hasResolution()` is true** — user has not yet approved the scopes. User info is saved to DataStore (without token/spreadsheetId). `AuthUiState.NeedsAuthorization(pendingIntent)` is emitted. `AuthScreen` launches the intent via `ActivityResultContracts.StartIntentSenderForResult`. On `RESULT_OK`, `AuthViewModel.finalizeAuth(intent)` is called.
6. **If no resolution needed** (scopes already approved) — `accessToken` is extracted directly. Proceeds to step 7.
7. **`completeSignIn(token, credential)` / `finalizeAuth(intent)`** — calls `findOrCreateSpreadsheetWithName(token)` → Drive API search. If `db_tasks` found, returns its ID. If not found, calls `createSpreadsheet(token)`.
8. **`authPreferences.saveAll()`** — persists `accessToken`, `tokenExpiry` (now + 1 hour), `spreadsheetId`, `spreadsheetName`, `userEmail`, `userName`, `userAvatarUrl`.
9. `AuthUiState.SignedIn` is emitted → `MainActivity` shows `MainScreen`.

### 6.3 First-Launch Spreadsheet Creation

`createSpreadsheet(token)` — via raw OkHttp (avoids circular Hilt dependency):

1. POST to `https://sheets.googleapis.com/v4/spreadsheets` — creates spreadsheet with **5 named sheets**: `tasks` (index 0), `folders` (index 1), `labels` (index 2), `settings` (index 3), `meta` (index 4).
2. POST to `.../values:batchUpdate` — writes header rows for tasks, folders, labels **plus full onboarding seed data** (see §6.4).
3. Upserts all seed folders, labels, and tasks to Room so the app is immediately usable without waiting for the first sync.

### 6.4 Onboarding Seed Data

Created in both Sheets and Room the first time `db_tasks` is created. Matches the PWA seed exactly.

**Folders:**

| ID | Name | Color | Order |
|---|---|---|---|
| `fld-inbox` | Inbox | `#6b7280` | 0 |
| `fld-work` | Work | `#4A90D9` | 1 |
| `fld-personal` | Personal | `#7ED321` | 2 |

**Labels:**

| ID | Name | Color | Order |
|---|---|---|---|
| `lbl-review` | Review | `#F5A623` | 1 |
| `lbl-idea` | Idea | `#9B59B6` | 2 |

**Tasks (5):**

| ID | Folder | Title | Priority | Labels | Deadline |
|---|---|---|---|---|---|
| `tsk-seed-1` | Work | Make important call | important | — | — |
| `tsk-seed-2` | Work | Review project draft | normal | `lbl-review` | — |
| `tsk-seed-3` | Personal | Buy groceries | urgent | — | — |
| `tsk-seed-4` | Personal | Schedule dentist appointment | normal | — | now + 5 days |
| `tsk-seed-5` | Inbox | Plan the week | normal | `lbl-idea` | — |

All seed tasks: `status=pending`, `isExpanded=true`, `isRecurring=false`, `createdAt=updatedAt=Instant.now()`. Deadline format: `YYYY-MM-DD` via `LocalDate.now().plusDays(5)`.

### 6.5 Token Refresh

`isExpiredSoon(expiry)` returns true if `Instant.now()` is within 300 seconds of the stored expiry. Refresh is done via `Identity.getAuthorizationClient(context).authorize(buildAuthRequest())` in `refreshToken()`. If `hasResolution()` is true (interactive re-auth needed), returns null. On success, saves new token with new 1-hour expiry. The OkHttp `Authenticator` calls `refreshToken()` once on 401.

### 6.6 Backup Exclusion

The `auth_prefs` DataStore file (holds the Google access token) is excluded from Android Auto Backup and device transfers:
- `backup_rules.xml` (API < 31): `<exclude domain="file" path="datastore/auth_prefs.preferences_pb"/>`
- `data_extraction_rules.xml` (API ≥ 31): same exclusion in both `<cloud-backup>` and `<device-transfer>` sections

All other app data (Room DB, widget config) is backed up normally.

### 6.7 Sign-Out

`GoogleAuthRepository.signOut()`: clears all DataStore prefs, then deletes all rows from `tasks`, `folders`, `labels`, and `sync_queue` tables. Calendar events are NOT explicitly cleared (they are pure cache and contain no user-generated data).

---

## 7. Synchronization

### SyncState

```kotlin
sealed class SyncState {
    data object Idle : SyncState()         // cloud-check icon in TopAppBar
    data object Syncing : SyncState()      // spinning icon
    data class Pending(val count: Int) : SyncState()  // cloud-upload + badge
}
```

`SyncManager.syncState` combines:
- `WorkManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME)` — running state
- `WorkManager.getWorkInfosForUniqueWorkFlow(MANUAL_WORK_NAME)` — running state
- `syncQueueDao.observePendingCount()` — pending item count

Displayed in `TasksTopAppBar` as a sync icon button: `Idle` → `CloudDone` icon; `Pending(count)` → `CloudUpload` with muted counter badge (transparent container, no default red); `Syncing` → `Sync` icon with infinite rotation animation (1 s, LinearEasing).

### Periodic Sync

`SyncManager.initialize()` (called from `TasksApplication.onCreate()`) enqueues a `PeriodicWorkRequest` with 30-minute interval, `ExistingPeriodicWorkPolicy.KEEP`, and `NetworkType.CONNECTED` constraint. Name: `"StlerTasksPeriodicSync"`.

### Manual Sync

`SyncManager.triggerSync()` enqueues a `OneTimeWorkRequest` with `ExistingWorkPolicy.REPLACE` and `NetworkType.CONNECTED`. Name: `"StlerTasksManualSync"`. Also triggered automatically from `MainViewModel.init { syncManager.triggerSync() }`.

### SyncWorker Execution Order

1. Read `spreadsheetId` from DataStore. If blank, call `authRepository.findAndSaveSpreadsheetId()` (Drive search). If the Drive search returns empty (possible stale/revoked token returning 401), the method silently re-authorizes once via `refreshToken()` and retries the search before returning.
2. If still blank, return `Result.success()` (abort silently).
3. **Push phase** — drain `SyncQueue`:
   - Load all queue items in order.
   - Per item: `executeOperation(item, spreadsheetId, rowsCache)`.
   - On success: `syncQueueDao.deleteById(item.id)`.
   - On failure: `syncQueueDao.incrementRetry(item.id)`.
   - After all items: `syncQueueDao.deleteExhausted(maxRetries = 5)`.
   - Row cache: sheet rows are fetched once per sheet name per push run (lazy, keyed by sheet name).
4. If push had pending changes, `delay(1_000L)` to avoid reading stale cached Sheets data.
5. **Pull phase** — `taskRepository.fetchAllAndSave(spreadsheetId)`:
   - `sheetsApi.batchGet(spreadsheetId, listOf("tasks", "folders", "labels"))`.
   - Collect IDs of items still in the sync queue (pending unsent changes → these rows are skipped to prevent local edits being overwritten).
   - Upsert tasks from remote (skipping pending-queue IDs).
   - Folders and labels: upsert remote rows, then **prune** any local entity whose ID is absent from the remote sheet and has no queued changes — this propagates deletions made on another device. (`folderDao.deleteNotIn(remoteIds + pendingIds)` / `labelDao.deleteNotIn(...)`)
6. **Calendar sync** — if selected calendar IDs are non-empty:
   - `calendarRepository.fetchEventsAndSave(selectedIds, from = now - 1 day, to = now + 366 days)`.
   - Internally: `calendarApi.listCalendars()` (populates metadata cache), then per-calendar `listEvents(timeMin, timeMax)`.
   - Per calendar: `calendarEventDao.deleteAndReplace(calendarId, entities)` (atomic @Transaction).
7. Return `Result.success()`.

On exception: `Result.retry()` for `runAttemptCount < 4`, else `Result.failure()`.

### Calendar Event Write Path

All methods live in `CalendarRepositoryImpl`. Mutations bypass the SyncQueue entirely and go directly to the Calendar API.

**`createEvent(calendarId, request)`**
1. POST to Calendar API → receives dto.
2. Fetch `calendarMeta` (5-min in-memory cache; refreshed from `listCalendars()` if stale) to resolve calendar name + color.
3. Map dto → entity via `CalendarMapper.dtoToEntity`.
4. Upsert entity to Room immediately (instant UI feedback).
5. If `request.recurrence` is non-empty: re-fetch full calendar (today..today+366) to pick up all recurring instances.
6. Return the mapped domain event.

**`updateEvent(calendarId, eventId, request)`**: PUT to Calendar API → re-fetch `calendarMeta` → upsert updated entity to Room.

**`deleteEvent(calendarId, eventId)`**: DELETE to Calendar API (treats 410 Gone as success) → delete entity from Room.

**`deleteSeries(calendarId, recurringEventId)`**: calls `deleteEvent(calendarId, recurringEventId)` on the base event → deletes all instances with matching `recurringEventId` from Room.

**`calendarMeta` cache**: 5-minute TTL in-memory cache mapping `calendarId → (calendarName, calendarColor)`. Used after create/update/move to label Room entities correctly without extra API calls on every write.

**EventDateTime serialization:** Custom Gson `TypeAdapter` (`EventDateTimeAdapter`) registered in `CalendarModule`:
- All-day: `EventDateTime(date = "YYYY-MM-DD")` — `dateTime` absent from JSON
- Timed: `EventDateTime(dateTime = "YYYY-MM-DDTHH:MM:SS+HH:MM", timeZone = "IANA/Zone")` — `date` absent from JSON
- Null fields silently omitted (`serializeNulls=false`) — sending `"date": null` causes HTTP 400 for recurring timed events
- `timeZone` is REQUIRED by Google Calendar API v3 for recurring timed events

### SheetsApi Endpoints

| Operation | HTTP | Path | Notes |
|---|---|---|---|
| Pull (read all) | GET | `.../values:batchGet?ranges=tasks,folders,labels` | `valueRenderOption=UNFORMATTED_VALUE` |
| INSERT | POST | `.../values/{sheet}!A:{lastCol}:append` | `valueInputOption=RAW`, `insertDataOption=INSERT_ROWS` |
| UPDATE | POST | `.../values:batchUpdate` | Updates specific row range e.g. `tasks!A5:Q5` |
| DELETE (soft) | POST | `.../values/{range}:clear` | Empties the row; mapper skips rows with blank `id` |

### CalendarApi Endpoints

| Operation | HTTP | Path |
|---|---|---|
| List calendars | GET | `calendar/v3/users/me/calendarList` |
| List events | GET | `calendar/v3/calendars/{calendarId}/events?timeMin=...&timeMax=...&singleEvents=true&orderBy=startTime` |
| Create event | POST | `calendar/v3/calendars/{calendarId}/events` |
| Update event | PUT | `calendar/v3/calendars/{calendarId}/events/{eventId}` |
| Get event | GET | `calendar/v3/calendars/{calendarId}/events/{eventId}` |
| Move event | POST | `calendar/v3/calendars/{calendarId}/events/{eventId}/move?destination={targetCalendarId}` |
| Delete event | DELETE | `calendar/v3/calendars/{calendarId}/events/{eventId}` (204 or 410 = success) |

---

## 8. UI Screens

### AuthScreen

**ViewModel:** `AuthViewModel`  
**State:** `uiState: StateFlow<AuthUiState>` — sealed: `Loading`, `SignedOut`, `NeedsAuthorization(pendingIntent)`, `SignedIn(userName, userAvatarUrl, spreadsheetId)`, `Error(message)`  
**Layout:** Centered `Column` — app logo + title row, subtitle text, conditional content per state: `CircularProgressIndicator` (Loading/NeedsAuthorization), "Sign in with Google" button (SignedOut), error text + "Try again" button (Error).  
**Actions:** `startSignIn(context)`, `finalizeAuth(intent)`, `clearError()`

---

### 8.1 Upcoming

**ViewModel:** `UpcomingViewModel`  
**Data source:** `repository.observeAllPendingTasksWithDeadline()` + `calendarRepository.getEventsForCalendars(selectedIds, now, now+366)`  
**StateFlows:** `allGroupedTasks: Map<LocalDate, List<ListItem>>`, `isLoading`, `weekDays`, `weekOffset`, `labels`, `folders`, `priorityFilter`, `labelFilter`, `folderFilter`, `calendarFilter`, `calendarsInEvents`  
**Sort order within a day:** timed items before all-day, then by time string.  
**Overdue group:** Items with `deadlineDate < today` are grouped under key `LocalDate.MIN` (displayed as "Overdue" header in red).  
**Week strip:** Mon–Sun strip driven by `_weekOffset`. Scroll syncs to strip via `snapshotFlow` + debounce 120ms. Day pills show a dot (orange = today, primary = has tasks). Navigation via chevron buttons or direct tap.  
**Filter matrix:** calendar-only filter → tasks hidden; task-only filter → events hidden; both active → both filtered; none → all shown.  
**Empty state:** "No upcoming tasks" centered text.  
**Actions:** complete task, delete task, delete event, delete event series, update deadline/priority/labels inline.

---

### 8.2 All Tasks

**ViewModel:** `AllTasksViewModel`  
**Data source:** `repository.observeAllPendingTasks()` + `calendarRepository.getEventsForCalendars(selectedIds, now, now+366)`  
**StateFlows:** `filteredItems: List<ListItem>`, `isLoading`, `labels`, `folders`, `priorityFilter`, `labelFilter`, `folderFilter`, `calendarFilter`, `calendarsInEvents`  
**Sort order:** priority (URGENT→IMPORTANT→NORMAL, events count as NORMAL) → deadline date → timed-before-allday → time. Undated tasks appended after all dated items, sorted by priority only.  
**Scroll-to-today:** On first load, scrolls to the first item whose date is today or future (debounced 200ms).  
**Filter bar:** `FilterBar` composable — see §8.11.  
**Empty state:** `EmptyState` with `FormatListBulleted` icon.

---

### 8.3 Completed

**ViewModel:** `CompletedViewModel`  
**Data source:** `repository.observeCompletedTasks()` — ordered by `completedAt DESC` (falls back to `updatedAt`).  
**StateFlows:** `filteredTasks`, `isLoading`, `labels`, `folders`, `priorityFilter`, `labelFilter`, `folderFilter`  
**Filter bar:** Priority, label, folder chips (no calendar chip).  
**Actions:** restore task (unchecking checkbox), delete task.  
**Empty state:** `EmptyState` with `CheckCircle` icon.

---

### 8.4 Folder

**ViewModel:** `FolderViewModel` (gets `folderId` from `SavedStateHandle`)  
**Data source:** `repository.observePendingInFolder(folderId)` + `repository.observeCompletedChildCountsInFolder(folderId)`  
**StateFlows:** `displayList: List<TaskNode>`, `labels`  
**Layout:** `ReorderableLazyListState` (sh.calvin reorderable library). Drag handle on the left of each row.  
**Tree building:** `buildDisplayList()` — recursive depth-first traversal, roots sorted by `sortOrder`, children appended only if `task.isExpanded`. Each `TaskNode` carries `task`, `depth`, `childCount`, `completedChildCount`.  
**Drag-to-reorder:** `onMove` updates local optimistic list and stores pending `fromIdx`/`toIdx`. On `isDragging → false`, calls `reorderSiblings(parentId, fromIdx, toIdx)` (one batch DB write).  
**Indent/outdent:** Shown in task "…" menu — `reparentTask(taskId, newParentId)`.  
**`toggleExpanded(task)`:** writes to Room without touching `updatedAt`.  
**Empty state:** "No tasks in this folder" centered text.

---

### 8.5 Calendar

**ViewModel:** `CalendarViewModel` (gets `calendarId` from `SavedStateHandle`)  
**Data source:** `calendarRepository.getEventsForCalendars(setOf(calendarId), now, now+366)`  
**StateFlows:** `groupedEvents: Map<LocalDate, List<CalendarEvent>>`, `isLoading`  
**Layout:** `LazyColumn` with date headers. Overdue items grouped under `LocalDate.MIN`.  
**Actions:** delete event, delete event series (via `CalendarEventItem`).  
**FAB behavior:** pressing the global FAB on this screen checks `CalendarItem.accessRole` for the current calendar. If `"owner"` or `"writer"` → `openCreateEvent(currentCalendarId)` — form opens in EVENT mode with this calendar pre-selected (`initialCalendarId`). If read-only (`"reader"` / `"freeBusyReader"`) or calendar not found → falls back to `openCreate()` (TASK mode, Inbox).  
**Empty state:** `EmptyState` with `CalendarMonth` icon.

---

### 8.6 CalendarEventItem

Shared composable for displaying a single calendar event — used in `CalendarScreen`, `AllTasksScreen`, and `UpcomingScreen`. Layout mirrors `TaskItem`.

```kotlin
fun CalendarEventItem(
    event          : CalendarEvent,
    showDate       : Boolean = true,
    onEdit         : (() -> Unit)? = null,      // full edit form
    onEditSchedule : (() -> Unit)? = null,      // schedule-only form
    onDelete       : (() -> Unit)? = null,
    onDeleteSeries : (() -> Unit)? = null,
    modifier       : Modifier = Modifier,
)
```

**Internal state:** `showMenu: Boolean`, `showDeleteConfirm: Boolean`  
**Layout:** `Row` — calendar icon (40dp slot matching TaskItem checkbox), `Column { title; FlowRow{ recurring icon, time label, calendar name icon+text } }`, optional action icons (Schedule + MoreHoriz).  
**Menu:** `ModalBottomSheet` with Edit + Delete entries. Delete → `AlertDialog`. Recurring event delete → 3-option dialog (this event / all in series / cancel).  
**Time label:** `formatEventTime()` — all-day events show date when `showDate=true`; timed events show `"HH:MM — HH:MM"` plus optional date prefix.  
**Deadline color:** uses `deadlineColor(deadlineStatus(startDate, startTime))` from `TaskColors.kt`.  
**Read-only calendars:** when `event.isEditable == false`, the Edit and Schedule buttons are hidden; only Delete remains.

---

### 8.7 MainScreen — Global FAB and Navigation

**ViewModel:** `MainViewModel`, `TaskFormViewModel`  
**StateFlows:** `folders`, `labels`, `syncState`, `authData`, `sidebarState`, `selectedCalendars`, `featureFlags`, `navMode`

#### Navigation modes

`navMode` is persisted in DataStore (`SidebarPreferences`, key `"nav_mode"`). Default: `"bottom"`. Toggled in Settings → Navigation section.

**Bottom bar mode (`navMode == "bottom"`):**
- `NavigationBar` (icon-only, no labels) with items: Upcoming · All Tasks · Folders (if enabled) · Calendars (if enabled + non-empty) · Menu (avatar or AccountCircle icon)
- No `ModalNavigationDrawer` — main content renders in a plain `Box`
- TopAppBar: no hamburger, no avatar dropdown. Back arrow shown when current route is `FOLDER` or `CALENDAR` (returns to `FOLDERS_LIST` / `CALENDARS_LIST` respectively)
- FAB hidden on `FOLDERS_LIST`, `CALENDARS_LIST`, `MENU_SCREEN` routes

**Sidebar mode (`navMode == "sidebar"`):**
- `ModalNavigationDrawer` with `SidebarMenu` — navigation-only; folder CRUD in Settings. Folders/Calendars sections gated by `featureFlags`
- TopAppBar: hamburger + avatar dropdown (Settings · Help · Feedback · About · Sign out)
- FAB always visible on content screens

**Overlay screens** (rendered in `Box` on top of the NavHost — NavHost stays in composition so its back stack is preserved): Settings · Help · Feedback · About — pushed/popped via `overlayStack: List<String>`. `BackHandler` inside each overlay screen pops the stack; physical back and in-screen back button both call `popOverlay()`.

**Routes:** `UPCOMING`, `ALL_TASKS`, `FOLDERS_LIST`, `CALENDARS_LIST`, `MENU_SCREEN`, `FOLDER/{folderId}`, `CALENDAR/{calendarId}`  
**Start destination:** `Screen.UPCOMING`  
**Back stack:** `navigateTo()` uses `saveState = true` / `restoreState = true` — per-tab state is preserved when switching bottom nav tabs.

**FAB:** On `CALENDAR` screen → checks `accessRole`: `"owner"`/`"writer"` → EVENT mode; read-only → TASK mode (Inbox). Otherwise → TASK mode with current folder context.  
**Task form:** `TaskFormSheet` shown outside the `Box` overlay (always on top) when `showForm = true`.  
**Deep links:** Handled in `LaunchedEffect(initialDeepLinkUri)`.

---

### 8.7a Active Tasks Screen (`ALL_TASKS` route)

Wraps `AllTasksScreen` (Active) and `CompletedScreen` (Done) with a `SingleChoiceSegmentedButtonRow` toggle at the top. Both `AllTasksViewModel` and `CompletedViewModel` are instantiated at `ActiveTasksScreen` level so state is preserved while the ALL_TASKS back-stack entry is alive. Tab selection uses `rememberSaveable`.

---

### 8.7b Folders List Screen (`FOLDERS_LIST` route, bottom nav only)

`LazyColumn` of all folders. Rows: `heightIn(min=56dp)`, `bodyLarge` text, 22dp icon, `ChevronRight`. Tapping a row navigates to `FOLDER/{folderId}` (inner-tab navigation, back arrow in TopAppBar returns here). Empty state when Folders feature is disabled or no folders exist.

---

### 8.7c Calendars List Screen (`CALENDARS_LIST` route, bottom nav only)

`LazyColumn` of selected calendars. Each row shows `CalendarMonth` icon tinted by calendar colour. Tapping navigates to `CALENDAR/{calendarId}`. Empty state when no calendars are selected.

---

### 8.7d Menu Screen (`MENU_SCREEN` route, bottom nav only)

Shows: user avatar (72dp, `CircleShape`, Coil `AsyncImage`) or `AccountCircle` fallback, user name (`titleMedium`), email (`bodyMedium`). Menu rows (`heightIn(min=56dp)`, `bodyLarge`, 24dp icons): Settings · Help · Feedback · About · Sign out (error colour). Each row pushes the appropriate overlay (`pushOverlay("settings")` etc.).

---

### 8.7e About Screen (overlay)

Static screen: version string (`BuildConfig.VERSION_NAME`) + "Check for updates" `OutlinedButton` (opens GitHub releases page). No ViewModel. `BackHandler` pops overlay.

---

### 8.8 Settings Screen

**ViewModel:** `SettingsViewModel`  
**StateFlows:** `featureFlags`, `folders`, `labels`, `spreadsheetId`, `spreadsheetName`, `files`, `loading`, `switching`, `calendars`, `calendarsLoading`  
**Physical back button:** `BackHandler` intercepts system back → calls `onNavigateBack` (returns to main screen, does not exit app).

**Section order (top → bottom, no headers):**

1. **Spreadsheet** — current file name + "Change" button → expandable Drive file picker; `switchSpreadsheet()` clears all Room data and triggers sync.
2. **Navigation** — `SingleChoiceSegmentedButtonRow`: "Bottom bar" (index 0, default) · "Side menu" (index 1). Persisted via `SidebarPreferences.setNavMode()` in DataStore. Switching takes effect immediately without restart.
3. **Priorities** — `Switch` toggle (`featureFlags.prioritiesEnabled`); hides priority UI everywhere when off.
4. **Labels** — `Switch` toggle + animated label list (color dot + name + Edit/Delete) + Add button in header; label CRUD inline.
5. **Folders** — `Switch` toggle + animated folder list (icon tinted with folder color + name + Edit/Delete, Inbox excluded) + Add button in header; folder CRUD inline.
6. **Calendars** — `Switch` toggle + Refresh button + checkbox list of Google Calendars; clearing events on disable; `PackageManager.setComponentEnabledSetting` hides `CalendarWidgetReceiver` from widget picker.

**Feature flags storage:** `AuthPreferences` DataStore keys `folders_enabled`, `labels_enabled`, `priorities_enabled`, `calendars_enabled` (all default `true`). Combined into `featureFlags: Flow<FeatureFlags>` via `combine`.

**`FeatureFlags` data class** (`auth/FeatureFlags.kt`): `foldersEnabled`, `labelsEnabled`, `prioritiesEnabled`, `calendarsEnabled`. All ViewModels that gate UI subscribe to `authPreferences.featureFlags` once (single subscription pattern). Auto-clear stale filters on feature disable via `init` coroutines.

**Widget picker hiding:** `PackageManager.setComponentEnabledSetting(FolderWidgetReceiver, DISABLED)` removes Folder widget from system picker when `foldersEnabled = false`; same for `CalendarWidgetReceiver`.

---

### 8.9 FilterBar

Shared composable defined in `AllTasksScreen.kt`, reused by `AllTasksScreen` and `CompletedScreen`.

```kotlin
fun FilterBar(
    labels          : List<Label>,
    folders         : List<Folder> = emptyList(),
    priorityFilter  : Set<Priority>,
    labelFilter     : Set<String>,
    folderFilter    : Set<String> = emptySet(),
    calendars       : List<CalendarItem> = emptyList(),
    calendarFilter  : Set<String> = emptySet(),
    featureFlags    : FeatureFlags = FeatureFlags(),
    onTogglePriority: (Priority) -> Unit,
    onToggleLabel   : (String) -> Unit,
    onToggleFolder  : (String) -> Unit = {},
    onToggleCalendar: (String) -> Unit = {},
    onClearAll      : () -> Unit = {},
    showLabelFilter : Boolean = true,
    showFolderFilter: Boolean = true,
)
```

Priority/label/folder chips are hidden when the corresponding `featureFlags.*Enabled` is `false`.

**Layout:** `Row` — optional ✕ clear-all button, then icon-only `FilterChip` buttons (priority 🚩, labels 🏷, folders 📁, calendars 📅). Each chip shows a count badge when active. Each chip opens a `DropdownMenu` with multi-select items (checkmark on active).  
**Neutral chip colors:** overrides Material You warm tint — selected container = `#d8d8d8` (light) / `primaryContainer` (dark).

---

### HelpScreen

No ViewModel. Static scrollable content in cards: Getting started, Tasks, Views, Calendar & events, Sync & offline, Widgets.  
**Physical back button:** `BackHandler(onBack = onNavigateBack)` — returns to main screen, does not exit app.

---

### FeedbackScreen

**ViewModel:** `FeedbackViewModel`  
**Physical back button:** `BackHandler(onBack = onNavigateBack)` — returns to main screen, does not exit app.  
**State:** `sendResult: StateFlow<SendResult?>` — `null`, `Sending`, `Success`, `Error`  
**Transport:** HTTP POST to Google Apps Script URL with form-encoded body (`app=Tasks`, `email=...`, `message=...`). `instanceFollowRedirects = false` (Apps Script returns 302 on success; following it converts POST to GET and the script never executes).  
**Success:** clears message field, shows snackbar.

---

## 9. TaskItem Component

**Location:** `ui/task/TaskItem.kt`

A large composable used in all task list screens and widgets. Key parameters:

| Parameter | Description |
|---|---|
| `task` | The `Task` domain model |
| `labels` | List of all `Label`s for chip rendering |
| `depth` | Indentation depth (0 = root) |
| `hasChildren` | Shows expand/collapse toggle |
| `completedChildCount` / `totalChildCount` | Progress indicator in subtask badge |
| `featureFlags` | `FeatureFlags` — gates checkbox color, label chips, priority chip, context menu items |
| `showFolder` | Whether to show folder name/color in row 2 |
| `showLabels` | Whether to show label chips in row 2 |
| `showExpandSlot` | Reserve space for expand icon even if no children |
| `showDateInDeadline` | Show date or relative label (e.g. "Tomorrow") |
| `enableSwipe` | Swipe-to-complete and swipe-to-delete gestures |
| Callbacks | `onCheckedChange`, `onToggleExpand`, `onDeadlineChange`, `onPriorityChange`, `onLabelChange`, `onAddSubtask`, `onEdit`, `onDelete`, `onIndent`, `onOutdent` |

**Layout:** Two-row card — Row 1: checkbox + title + optional expand button; Row 2: deadline chip + priority chip + folder chip + label chips + more button.

**Swipe gestures:** Left swipe → complete (green checkmark reveal); right swipe → delete (red trash reveal). Completion of a recurring task advances `deadlineDate` instead of changing status.

---

## 10. TaskFormSheet

**ViewModel:** `TaskFormViewModel`  
**Mode:** Determined by what is passed — `task != null` = edit task; `calendarEvent != null` = edit/create event; `initialCalendarId != null` = create event with calendar pre-selected (EVENT tab opens, calendar pre-filled); neither = create task. `scheduleOnly = true` restricts the event form to date/time/repeat only.  
**Fields (task mode):** title (BasicTextField), priority (SegmentedButton), deadline (DatePicker + TimePicker + recurrence), folder (ExposedDropdownMenu), labels (LabelPickerSheet), parent (implicit via `initialParentId`).  
**Fields (event mode):** title, calendar (ExposedDropdownMenu), start date + time, end time, repeat (recurrence picker with RRULE builder).  
**Label sentinel:** New labels are encoded as `"__new__:colorHex:name"` — `TaskFormViewModel.resolveLabelSentinels()` creates the label in Room/Sheets and returns the real ID.  
**Output:** `TaskFormResult(title, folderId, parentId, priority, labelIds, deadlineDate, deadlineTime, isRecurring, recurType, recurValue)` — passed back via `onConfirm` lambda to `MainScreen.handleFormResult()`.

### Deadline label row (task edit mode)

The "Deadline" heading row doubles as an action row when a deadline is set. Layout: "Deadline" label left-aligned → `Spacer(weight(1f))` → **Clear** TextButton → 8 dp gap → **Postpone** TextButton (visible only when `isRecurring = true`).

- **Clear** — resets `deadlineDate`, `deadlineTime`, `isRecurring` to blank/false and clears recurrence fields immediately (no dialog).
- **Postpone** — advances `deadlineDate` by `recurValue × recurType` (same logic as recurring completion) and saves.
- Both buttons use `ButtonDefaults.textButtonColors(contentColor = primary)` with a 14 dp icon and the button text beside it (`Icon + Spacer(4 dp) + Text`).

### Bottom action row (task edit mode)

When editing an existing task (`isEditing = true`), the bottom row uses `Arrangement.SpaceBetween`:
- **Left:** Delete `TextButton` with `contentColor = MaterialTheme.colorScheme.error`, 16 dp `Icons.Outlined.Delete` icon + "Delete" text. Tapping shows a confirmation `AlertDialog` ("Delete task and all subtasks?") before calling `viewModel.deleteTask(task.id)` and closing the sheet.
- **Right:** Cancel + Save buttons (unchanged from create mode).

When creating a new task, the bottom row is `Arrangement.End` (Cancel + Save only).

### DeadlinePickerDialog

A separate bottom sheet (`ui/task/DeadlinePickerDialog.kt`) used for inline deadline editing triggered by swipe-left or tapping the deadline chip on a task row (not part of `TaskFormSheet`). It has its own local state and the same deadline label row pattern:

- Title "Deadline" left-aligned → Spacer → **Clear** (calls `onConfirm("", "", false, DAYS, 1)` immediately) → 8 dp gap → **Postpone** (calls `onConfirm(advancedDate, ...)` immediately, saving without a separate Save tap).
- Bottom action row: `Arrangement.End` with **Cancel** + **Save** only.
- `onConfirm` callback signature: `(date, time, isRecurring, recurType, recurValue) → Unit`.

**Smart title parsing (TASK mode only):**

| Token | Match rule | Behavior |
|---|---|---|
| `@FolderName` | Case-insensitive folder name | Sets `folderId`; stripped from title |
| `#LabelName` | Case-insensitive label name | Adds to `labelIds`; stripped from title |
| `!1`, `!2`, `!3` | Literal strings | Sets priority: `!1` → Urgent, `!2` → Important, `!3` → Normal |
| `today`, `tomorrow`, `mon`…`sun` | Locale-insensitive day names | Sets `deadlineDate`; stripped from title |

**`buildEventDateTime` (in `TaskFormViewModel`):**
```kotlin
if (time.isBlank())
    EventDateTime(date = date)
else {
    val zone = ZoneId.systemDefault()
    val zdt  = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)
    EventDateTime(dateTime = zdt.format(ISO_OFFSET_DATE_TIME), timeZone = zone.id)
}
```

**`buildEndDateTime`:** All-day → end = next calendar day. Timed → end time defaults to start + 1 h if blank or ≤ start.

---

## 11. Widgets

All widgets extend `GlanceAppWidget`, use `PreferencesGlanceStateDefinition`, and obtain dependencies via `EntryPointAccessors` from `WidgetEntryPoint` (a Hilt entry point interface). `WidgetRefresher.refreshAll()` (debounced 400ms) calls `updateAll()` on all four widget classes after every task mutation. `refreshOnStartup()` (no debounce) is called from `Application.onCreate()` to re-register Glance sessions before WorkManager replays stale `SessionWorker` jobs.

### UpcomingWidget

**Data source:** `repo.observeAllPendingTasks()` + `calendarRepo.getEventsForCalendars(selectedIds, today, today+7)`  
**Configuration:** None (no config activity). Shows today + next 6 days (7-day window). Events cut off beyond `today+6`.  
**Update trigger:** `WidgetRefresher.refreshAll()` from repository; `refreshOnStartup()` on app start.  
**Layout:** `WidgetHeader("Upcoming", screenUri="stlertasks://upcoming")` + `LazyColumn` with `DateHeader` per date + `WidgetTaskRow` per task + `WidgetEventRow` per event. Overdue tasks grouped under "Overdue" header (red).  
**Pending-complete:** tap on checkbox sets `pendingCompleteKey` in Glance Preferences state (transient visual checkmark for up to 4 seconds while Room commits).

### FolderWidget

**Data source:** `repo.observePendingInFolder(folderId)` + `repo.observeCompletedChildCountsInFolder(folderId)` + `repo.observeLabels()` + `repo.observeFolders()`  
**Configuration:** `WidgetConfigActivity` — user selects folder. Stored in `WidgetPrefs.getFolderId(context, appWidgetId)` (SharedPreferences file `widget_config`). `isFolderWidgetConfigured` also returns true for widgets with a legacy `folder_N` pref.  
**Layout:** `WidgetHeader(folderName, screenUri="stlertasks://folder/{folderId}")` + `LazyColumn` (capped at 20 items). Recursive tree: `addRecursive()` builds `FolderRow` list respecting `isExpanded`.  
**Deep-link on tap:** task row → `stlertasks://task/{taskId}`.

### TaskListWidget

**Data source:** `repo.observeAllPendingTasks()` + `calendarRepo.getEventsForCalendars(selectedIds, now-1, now+7)` (events suppressed when any task filter is active).  
**Configuration:** `WidgetConfigActivity` — multi-select filters: folders (`filterFolders_N`), labels (`filterLabels_N`), priorities (`filterPriorities_N`). Stored in `WidgetPrefs` (comma-separated sets). Legacy single-value prefs (`filterFolder_N`, etc.) are migrated on first read.  
**Row cap:** 50 when filters active; 20 otherwise (guards Binder 1 MB RemoteViews limit).  
**Header title:** built from active filters — `@FolderName` / `#LabelName` / `!1 !2` priority codes, joined by space. Falls back to `"Tasks"`.  
**Layout:** `WidgetHeader(title, screenUri="stlertasks://all_tasks")` + `LazyColumn`.

### CalendarWidget

**Data source:** `calendarRepo.getEventsForCalendars(widgetCalendarIds or selectedIds, now-1, now+30)`  
**Configuration:** `WidgetConfigActivity` — selects one or more calendars. Stored as `calendarIds_N` (comma-separated) and `calendarName_N` in `WidgetPrefs`. Falls back to globally selected calendars if empty.  
**Header deep-link:** single calendar → `stlertasks://calendar/{calendarId}`; multiple → `stlertasks://upcoming`.  
**Row cap:** 40.  
**Layout:** `WidgetHeader(calendarName)` + `LazyColumn` with date headers + `WidgetEventRow` rows. 30-day window.

---

## 12. Theme & Colors

Theme is `TasksTheme` wrapping `MaterialTheme`. No dynamic color (Material You). Light and dark color schemes are fully custom.

### Primary and Accent Colors

| Constant | Hex (light) | Usage |
|---|---|---|
| `Primary` | `#e07e38` | Primary buttons, FAB, links, cover accent line |
| `PrimaryDark` | `#D98D52` | Primary in dark mode |
| `OnPrimary` | `#ffffff` | Text on primary buttons |

### Background / Surface

| Constant | Light | Dark |
|---|---|---|
| `Background` | `#ffffff` | `#1c1c1c` |
| `Surface` | `#ffffff` | `#363636` |
| `Popover` | `#ffffff` | `#242424` |

### Text

| Constant | Light | Dark |
|---|---|---|
| `Foreground` | `#18181f` | `#f2f2f2` |
| `MutedForeground` | `#6b6b6b` | `#949494` |

### Selection Highlights

| Constant | Hex | Usage |
|---|---|---|
| `SelectedHighlightLight` | `#d8d8d8` | Week-strip pill, filter chips, FAB, sidebar items (light) |
| `SelectedHighlightDark` | `#515151` | Same in dark mode |
| `OnChipSelected` | `#424242` | Text/icons on selected chips (light mode) |

### Priority Colors

| Constant | Hex | Usage |
|---|---|---|
| `PriorityUrgent` | `#f87171` | Red-400 |
| `PriorityImportant` | `#fb923c` | Orange-400 |
| `PriorityNormal` | `#9ca3af` | Gray-400 |

### Deadline Status Colors

| Constant | Hex | Usage |
|---|---|---|
| `DeadlineOverdue` | `#f87171` | Past due dates |
| `DeadlineToday` | `#16a34a` | Due today (green-600) |
| `DeadlineTomorrow` | `#fb923c` | Due tomorrow |
| `DeadlineThisWeek` | `#a78bfa` | Due this week (violet-400) |
| `Destructive` | `#e96060` | Error states, delete actions |

### Borders and Inputs

| Constant | Light | Dark |
|---|---|---|
| `Border` / `Input` | `#e0e0e0` | `#4a4a4a` / `#383838` |

### Widget Colors (XML resources)

Widget colors reference XML color resources in `res/values/colors.xml` and `res/values-night/colors.xml` (day/night variants). Glance `ColorProvider(R.color.widget_xxx)` is used rather than `ColorProvider(Color)` to get proper day/night behavior.

---

## 13. Navigation

### Route Constants (`Screen.kt`)

| Constant | Route String | Args |
|---|---|---|
| `Screen.UPCOMING` | `"upcoming"` | — |
| `Screen.ALL_TASKS` | `"all_tasks"` | — |
| `Screen.COMPLETED` | `"completed"` | — |
| `Screen.FOLDER` | `"folder/{folderId}"` | `folderId: String` |
| `Screen.LABEL` | `"label/{labelId}"` | `labelId: String` |
| `Screen.PRIORITY` | `"priority/{priority}"` | `priority: String` |
| `Screen.CALENDAR` | `"calendar/{calendarId}"` | `calendarId: String` (URL-encoded) |

Helper functions: `folderRoute(id)`, `labelRoute(id)`, `priorityRoute(priority)`, `calendarRoute(id)`.

### Navigation Behavior

- All navigation uses `popUpTo(graph.startDestinationId) + launchSingleTop = true` to prevent stack buildup when switching sections from the sidebar.
- The `NavHost` lives inside `MainScreen`. Navigation between screens happens by calling `navController.navigate(route)`.
- **Overlay screens** (Settings, Help, Feedback) are shown by toggling `showSettings/showHelp/showFeedback` local state in `MainScreen`. They replace the entire content via `return` and do not create NavBackStack entries.
- The `TaskFormSheet` (ModalBottomSheet) is shown as a local overlay without navigation.
- Deep links are handled in `MainActivity.onNewIntent()` and passed as `initialDeepLinkUri` to `MainScreen`.

### Deeplinks (`stlertasks://`)

| URI | Action |
|---|---|
| `stlertasks://task/{taskId}` | Open edit form for the task |
| `stlertasks://create` | Open create form (Inbox, default priority) |
| `stlertasks://event/{calendarId}/{eventId}` | Open event edit form |
| `stlertasks://upcoming` | Navigate to Upcoming screen |

---

## 14. Loading & Empty States

### ShimmerTaskList

`isLoading: StateFlow<Boolean>` in every ViewModel: `filteredTasks.map { false }.stateIn(..., initialValue = true)`. While `isLoading = true`, pulsing skeleton rows are displayed (alpha 0.25↔0.6, 900 ms, LinearEasing, Reverse). 6 rows per screen.

### EmptyState

Centered `Column`: 64dp icon (40% opacity) + message (`titleMedium`) + optional subtitle (`bodyMedium`, muted). Used in AllTasks, Completed, Priority, Label, Calendar screens.

---

## 15. CI/CD & Build

### GitHub Actions Workflow (`release.yml`)

**Trigger:** push of any tag matching `v*`  
**Runner:** `ubuntu-latest`  
**Steps:**
1. `actions/checkout@v4.2.2`
2. `actions/setup-java@v4.7.0` — JDK 17 (Temurin distribution)
3. `chmod +x ./gradlew`
4. Decode keystore: `echo "$KEYSTORE_BASE64" | base64 --decode > keystore.jks`
5. `./gradlew assembleRelease` with env vars `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m"` (D8 dex merge requires ≥ 4 GB; 2 GB causes OOM on release builds)
6. Rename APK: `app-release.apk` → `stler-tasks.apk`
7. `softprops/action-gh-release@v2` — creates GitHub Release and attaches `stler-tasks.apk`

**Secrets required:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`  
**Permissions:** `contents: write` (to create the GitHub Release)

### Local / Debug Build

No signing config is created when `KEYSTORE_PATH` is blank — debug builds use Android's default debug keystore. `isMinifyEnabled = false` for release (no ProGuard obfuscation).

---

## 16. First-Time Setup (New Developer)

1. Clone the repository.
2. In Google Cloud Console:
   - Enable **Google Sheets API**, **Google Drive API**, **Google Calendar API**.
   - Create **OAuth 2.0 Web Client ID** → copy to `res/values/strings.xml` as `google_web_client_id`.
   - Create **OAuth 2.0 Android Client ID** (package `com.stler.tasks`, SHA-1 of debug keystore).
   - Set OAuth consent screen to **Production** and add all three scopes.
3. Run on device/emulator from Android Studio.
4. Sign in — the app will find or create the `db_tasks` spreadsheet automatically.

**For release builds:** Create a keystore, base64-encode it, add GitHub secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Push a `v*` tag to trigger the build.

---

## 17. Key Algorithms

### Recursive Tree Build (FolderViewModel + FolderWidget)

```pseudocode
function buildDisplayList(tasks, completedCounts):
    byParent = group tasks by parentId
    result = []

    function addTask(task, depth):
        children = byParent[task.id] ?? []
        completedCount = completedCounts[task.id] ?? 0
        result.append(TaskNode(task, depth, children.size, completedCount))
        if task.isExpanded:
            for child in sort(children, by sortOrder):
                addTask(child, depth + 1)

    for root in sort(byParent[""], by sortOrder):
        addTask(root, 0)

    return result
```

### Restore Task + Descendants (TaskRepositoryImpl.restoreTask)

```pseudocode
function restoreTask(id):
    entity = taskDao.getById(id) ?? return
    now = nowIso()
    allTasks = taskDao.getAll()   // load once — no N+1
    updated = entity.copy(status = "pending", completedAt = "", updatedAt = now)
    taskDao.upsert(updated)
    enqueue("task", "UPDATE", id, updated)
    restoreDescendants(id, now, allTasks)
    widgetRefresher.refreshAll()

function restoreDescendants(parentId, now, allTasks):
    // Only restore "completed" children — "deleted" children stay deleted
    children = allTasks.filter(parentId == parentId AND status == "completed")
    for child in children:
        restored = child.copy(status = "pending", completedAt = "", updatedAt = now)
        taskDao.upsert(restored)
        enqueue("task", "UPDATE", child.id, restored)
        restoreDescendants(child.id, now, allTasks)  // recurse
```

### Task Reorder (FolderViewModel.reorderSiblings)

```pseudocode
function reorderSiblings(parentId, fromIndex, toIndex):
    siblings = allTasks
        .filter(parentId == parentId)
        .sortedBy(sortOrder)
    if fromIndex or toIndex out of range: return
    moved = siblings.removeAt(fromIndex)
    siblings.insertAt(toIndex, moved)
    now = Instant.now().toString()
    updated = siblings.mapIndexed { i, task ->
        task.copy(sortOrder = i * 10, updatedAt = now)
    }
    repository.updateTasks(updated)   // single batch write
```

### Sync Push Loop (SyncWorker.push)

```pseudocode
function push(spreadsheetId):
    queue = syncQueueDao.getAll()
    if queue.isEmpty: return

    rowsCache = {}

    for item in queue:
        try:
            executeOperation(item, spreadsheetId, rowsCache)
            syncQueueDao.deleteById(item.id)
        catch:
            syncQueueDao.incrementRetry(item.id)

    syncQueueDao.deleteExhausted(maxRetries = 5)

function executeOperation(item, spreadsheetId, rowsCache):
    sheetName = sheetOf(item.entityType)   // "tasks", "folders", "labels"
    if item.operation == "INSERT":
        range = sheetName + "!A:" + lastColOf(item.entityType)
        sheetsApi.append(spreadsheetId, range, body = entityRow(item))
    else:
        rows = rowsCache.getOrFetch(sheetName)
        rowNum = findRowNumber(rows, item.entityId)
        if rowNum == null: return   // entity not found in sheet (skip)
        range = sheetName + "!A" + rowNum + ":" + lastColOf(...) + rowNum
        if item.operation == "UPDATE":
            sheetsApi.batchUpdate(spreadsheetId, range, body = entityRow(item))
        if item.operation == "DELETE":
            sheetsApi.clear(spreadsheetId, range)
```

### Onboarding Seed Detection

```pseudocode
// At sign-in, after Drive search for "db_tasks":
function findOrCreateSpreadsheetWithName(accessToken):
    found = findAndSaveSpreadsheetId(accessToken)   // Drive API search with token retry
    if found.isNotBlank():
        return (found, "db_tasks")
    // No spreadsheet found → first-time user
    created = createSpreadsheet(accessToken)
    return (created, "db_tasks")

function findAndSaveSpreadsheetId(accessToken):
    id = findSpreadsheetId(accessToken)
    if id.isBlank():
        // Empty may mean Drive returned 401 for a stale/revoked token —
        // refresh once and retry before giving up.
        fresh = refreshToken()
        if fresh != null and fresh != accessToken:
            id = findSpreadsheetId(fresh)
    if id.isNotBlank():
        authPreferences.setSpreadsheet(id, "db_tasks")
    return id

function findSpreadsheetId(accessToken):
    query = "name='db_tasks' AND mimeType='application/vnd.google-apps.spreadsheet' AND trashed=false"
    response = Drive.files.list(q=query, fields="files(id,name)")
    return response.files.firstOrNull()?.id ?? ""
```

### Recurring Task Completion

```kotlin
if (task.isRecurring) {
    val newDate = when (task.recurType) {
        DAYS   -> LocalDate.parse(deadlineDate).plusDays(recurValue.toLong())
        WEEKS  -> LocalDate.parse(deadlineDate).plusWeeks(recurValue.toLong())
        MONTHS -> LocalDate.parse(deadlineDate).plusMonths(recurValue.toLong())
        YEARS  -> LocalDate.parse(deadlineDate).plusYears(recurValue.toLong())
        else   -> return  // NONE — treat as non-recurring
    }
    // update deadlineDate only; status stays PENDING; completedAt = ""
} else {
    // set status = COMPLETED, completedAt = now
    // recursively complete all descendants
}
```

### RRULE Builder (CustomRecurrenceSheet)

```
RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;COUNT=10
RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=2TU        // 2nd Tuesday
RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1SA       // last Saturday
RRULE:FREQ=DAILY;INTERVAL=1;UNTIL=20261231T235959Z
```

Ordinal prefix: `ceil(dayOfMonth / 7.0).toInt()` for 1st–4th; `-1` when the day can fall in the last position (`dayOfMonth > 21`).
