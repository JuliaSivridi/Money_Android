package com.stler.money.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.gson.Gson
import com.stler.money.R
import com.stler.money.data.local.dao.AccountDao
import com.stler.money.data.local.dao.CategoryDao
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.dao.TransactionDao
import com.stler.money.data.local.entity.AccountEntity
import com.stler.money.data.local.entity.CategoryEntity
import com.stler.money.data.local.entity.TransactionCategoryCrossRef
import com.stler.money.data.local.entity.TransactionEntity
import com.stler.money.data.remote.TokenProvider
import com.stler.money.data.remote.dto.DriveFilesResponse
import com.stler.money.util.generateId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sign-in / spreadsheet lifecycle for `db_money` — see tech spec §6.
 * Mirrors the Tasks Android sibling's GoogleAuthRepository, narrowed to a
 * single `drive.file` scope (no Calendar) and Money's 4-sheet spreadsheet.
 */
@Singleton
class GoogleAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val syncQueueDao: SyncQueueDao,
) : TokenProvider {

    private val httpClient = OkHttpClient()

    // ── Public state ──────────────────────────────────────────────────────

    val isSignedIn: Flow<Boolean> = authPreferences.accessToken.map { it.isNotBlank() }

    val authData: Flow<AuthData> = authPreferences.authData

    // ── TokenProvider ─────────────────────────────────────────────────────

    override suspend fun getAccessToken(): String {
        val token = authPreferences.accessToken.first()
        val expiry = authPreferences.tokenExpiry.first()
        if (token.isBlank()) return ""
        return if (isExpiredSoon(expiry)) refreshToken() ?: "" else token
    }

    /**
     * Refreshes the token silently using the Authorization client.
     * Works in the background (no Activity needed) once scopes are approved.
     * Returns null if the user must re-authenticate interactively.
     */
    override suspend fun refreshToken(): String? = runCatching {
        val result = Identity.getAuthorizationClient(context)
            .authorize(buildAuthRequest())
            .await()
        if (result.hasResolution()) return@runCatching null
        val newToken = result.accessToken ?: return@runCatching null
        authPreferences.saveToken(newToken, expiryInOneHour())
        newToken
    }.getOrNull()

    // ── Sign-in flow ──────────────────────────────────────────────────────

    sealed class SignInStep {
        data object Success : SignInStep()
        /** User must approve scopes via this intent before sign-in completes. */
        class NeedsAuthorization(val pendingIntent: android.app.PendingIntent) : SignInStep()
    }

    /**
     * Full sign-in flow.
     * 1. CredentialManager -> Google account picker -> ID token (user info)
     * 2. Identity.authorize -> drive.file scope token
     * 3. Drive API -> find db_money spreadsheet ID (create if missing)
     * 4. Save everything to DataStore
     *
     * [context] must be an Activity context (for CredentialManager UI).
     */
    suspend fun signIn(context: Context): Result<SignInStep> = runCatching {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.google_web_client_id))
            .build()

        val credentialResponse = credentialManager.getCredential(
            context = context,
            request = GetCredentialRequest(listOf(googleIdOption)),
        )
        val googleCredential = GoogleIdTokenCredential
            .createFrom(credentialResponse.credential.data)

        val authResult = Identity.getAuthorizationClient(context)
            .authorize(buildAuthRequest())
            .await()

        if (authResult.hasResolution()) {
            // Save user info now so finalizeAuth() can read it later
            authPreferences.saveAll(
                accessToken   = "",
                tokenExpiry   = "",
                spreadsheetId = "",
                userEmail     = googleCredential.id,
                userName      = googleCredential.displayName ?: "",
                userAvatarUrl = googleCredential.profilePictureUri?.toString() ?: "",
            )
            val intent = authResult.pendingIntent
                ?: throw IllegalStateException("hasResolution() is true but pendingIntent is null")
            return@runCatching SignInStep.NeedsAuthorization(intent)
        }

        val token = authResult.accessToken
            ?: throw IllegalStateException("No access token in authorization result")
        completeSignIn(token, googleCredential)
        SignInStep.Success
    }

    /** Called after the user approves scopes via the authorization intent. */
    suspend fun finalizeAuth(intent: Intent): Result<Unit> = runCatching {
        val authResult = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(intent)

        val token = authResult.accessToken
            ?: throw IllegalStateException("No access token in authorization result")

        if (authPreferences.spreadsheetId.first().isBlank()) {
            val (spreadsheetId, spreadsheetName) = findOrCreateSpreadsheetWithName(token)
            authPreferences.saveAll(
                accessToken     = token,
                tokenExpiry     = expiryInOneHour(),
                spreadsheetId   = spreadsheetId,
                spreadsheetName = spreadsheetName,
                userEmail       = authPreferences.userEmail.first(),
                userName        = authPreferences.userName.first(),
                userAvatarUrl   = authPreferences.userAvatarUrl.first(),
            )
        } else {
            authPreferences.saveToken(token, expiryInOneHour())
        }
    }

    suspend fun getAuthData(): AuthData = authPreferences.getAuthData()

    /**
     * Called from SyncWorker when spreadsheetId is blank but a token exists.
     * Runs the Drive search again and persists the result.
     */
    suspend fun findAndSaveSpreadsheetId(): String {
        val token = authPreferences.accessToken.first()
        if (token.isBlank()) {
            Log.w(TAG, "findAndSaveSpreadsheetId: no access token")
            return ""
        }
        var id = findSpreadsheetId(token)

        // Empty result can mean a stale/revoked token (Drive answers 401) —
        // silently re-authorize once and retry before giving up.
        if (id.isBlank()) {
            val fresh = refreshToken()
            if (fresh != null && fresh != token) {
                Log.i(TAG, "findAndSaveSpreadsheetId: retrying with refreshed token")
                id = findSpreadsheetId(fresh)
            }
        }

        if (id.isNotBlank()) {
            authPreferences.setSpreadsheet(id, "db_money")
            Log.i(TAG, "findAndSaveSpreadsheetId: saved spreadsheetId=$id")
        } else {
            Log.w(TAG, "findAndSaveSpreadsheetId: Drive search returned empty result")
        }
        return id
    }

    // ── Drive helpers ─────────────────────────────────────────────────────

    /** Lists all Google Spreadsheets in the user's Drive — used by SettingsScreen's "Change" picker. */
    suspend fun listUserSheets(): List<com.stler.money.data.remote.dto.DriveFile> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getAccessToken()
            if (token.isBlank()) return@runCatching emptyList()
            val query = Uri.encode("mimeType='application/vnd.google-apps.spreadsheet' and trashed=false")
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)&orderBy=modifiedTime+desc"
            val response = httpClient.newCall(
                Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            ).execute()
            if (!response.isSuccessful) return@runCatching emptyList()
            val body = response.body?.string() ?: return@runCatching emptyList()
            Gson().fromJson(body, DriveFilesResponse::class.java).files
        }.onFailure { e ->
            Log.e(TAG, "listUserSheets exception: ${e.message}", e)
        }.getOrDefault(emptyList())
    }

    // ── Sign-out ──────────────────────────────────────────────────────────

    suspend fun signOut() {
        authPreferences.clearAll()
        transactionDao.deleteAll()
        transactionDao.deleteAllCategoryRefs()
        accountDao.deleteAll()
        categoryDao.deleteAll()
        syncQueueDao.deleteAll()
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun completeSignIn(
        accessToken: String,
        credential: GoogleIdTokenCredential,
    ) {
        val (spreadsheetId, spreadsheetName) = findOrCreateSpreadsheetWithName(accessToken)
        authPreferences.saveAll(
            accessToken     = accessToken,
            tokenExpiry     = expiryInOneHour(),
            spreadsheetId   = spreadsheetId,
            spreadsheetName = spreadsheetName,
            userEmail       = credential.id,
            userName        = credential.displayName ?: "",
            userAvatarUrl   = credential.profilePictureUri?.toString() ?: "",
        )
    }

    private fun buildAuthRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    // drive.file: access only to files this app (project) created or the
                    // user picked via the Google Picker in the web client — tech spec §6.1.
                    Scope("https://www.googleapis.com/auth/drive.file"),
                )
            )
            .build()

    /**
     * Returns (spreadsheetId, spreadsheetName). If no `db_money` exists yet,
     * creates one with the correct sheet structure and seeds onboarding data.
     */
    private suspend fun findOrCreateSpreadsheetWithName(accessToken: String): Pair<String, String> {
        val found = findSpreadsheetId(accessToken)
        if (found.isNotBlank()) return found to "db_money"
        Log.i(TAG, "No db_money found — creating new spreadsheet")
        val created = createSpreadsheet(accessToken)
        return created to "db_money"
    }

    /**
     * Creates a new Google Spreadsheet named `db_money` with 4 sheets
     * (transactions, accounts, categories, settings), writes header rows,
     * and seeds onboarding data — byte-matched to Money PWA's
     * src/api/seedOnboarding.ts (tech spec §6.4).
     *
     * Uses OkHttp directly (not Retrofit) to avoid circular dependency with
     * the Hilt-managed OkHttpClient that requires a valid TokenProvider.
     */
    private suspend fun createSpreadsheet(accessToken: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val client = httpClient
            val jsonType = "application/json".toMediaType()

            // ── Step 1: create the spreadsheet with 4 named sheets ─────────
            val createBody = """
                {
                  "properties": { "title": "db_money" },
                  "sheets": [
                    { "properties": { "title": "transactions", "index": 0 } },
                    { "properties": { "title": "accounts",     "index": 1 } },
                    { "properties": { "title": "categories",   "index": 2 } },
                    { "properties": { "title": "settings",     "index": 3 } }
                  ]
                }
            """.trimIndent()

            val createResp = client.newCall(
                Request.Builder()
                    .url("https://sheets.googleapis.com/v4/spreadsheets")
                    .header("Authorization", "Bearer $accessToken")
                    .post(createBody.toRequestBody(jsonType))
                    .build()
            ).execute()

            if (!createResp.isSuccessful) {
                Log.e(TAG, "createSpreadsheet HTTP ${createResp.code}")
                return@runCatching ""
            }

            val spreadsheetId = JSONObject(createResp.body?.string() ?: "")
                .getString("spreadsheetId")
            Log.i(TAG, "Created spreadsheet: $spreadsheetId")

            // ── Step 2: write headers + seed onboarding data ────────────────
            val now = Instant.now().toString()
            val today = LocalDate.now().toString()
            val acc1Id = generateId("acc")
            val acc2Id = generateId("acc")
            val cat1Id = generateId("cat")
            val cat2Id = generateId("cat")
            val cat3Id = generateId("cat")
            val txn1Id = generateId("txn")

            // NOTE: accounts header/rows intentionally stop at column I (no color) —
            // matches seedOnboarding.ts exactly; color defaults client-side to #6b7280
            // until the user sets one. accounts!A:J stays the addressable range for
            // regular reads/writes (column J exists, just unpopulated for seed rows).
            val batchBody = """
                {
                  "valueInputOption": "RAW",
                  "data": [
                    {
                      "range": "transactions!A1:P1",
                      "values": [["id","date","time","type","amount","currency","amount_base",
                                  "account_id","category_ids","to_account_id","to_amount","to_currency",
                                  "debt_ref_id","comment","created_at","updated_at"]]
                    },
                    {
                      "range": "transactions!A2:P2",
                      "values": [
                        ["$txn1Id","$today","00:00","expense","10","EUR","10","$acc1Id","$cat1Id","","0","","","Groceries","$now","$now"]
                      ]
                    },
                    {
                      "range": "accounts!A1:I1",
                      "values": [["id","name","currency","type","balance","archived","sort_order","created_at","updated_at"]]
                    },
                    {
                      "range": "accounts!A2:I3",
                      "values": [
                        ["$acc1Id","Cash (€)","EUR","cash","-10","FALSE","1","$now","$now"],
                        ["$acc2Id","Cash (₽)","RUB","cash","0","FALSE","2","$now","$now"]
                      ]
                    },
                    {
                      "range": "categories!A1:K1",
                      "values": [["id","name","icon","color","is_expense","expense_limit",
                                  "is_income","income_limit","sort_order","created_at","updated_at"]]
                    },
                    {
                      "range": "categories!A2:K4",
                      "values": [
                        ["$cat1Id","Groceries","ShoppingCart","#22c55e","TRUE","0","FALSE","0","1","$now","$now"],
                        ["$cat2Id","Transport","Bus","#3b82f6","TRUE","0","FALSE","0","2","$now","$now"],
                        ["$cat3Id","Health","Heart","#f87171","TRUE","0","FALSE","0","3","$now","$now"]
                      ]
                    }
                  ]
                }
            """.trimIndent()

            client.newCall(
                Request.Builder()
                    .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate")
                    .header("Authorization", "Bearer $accessToken")
                    .post(batchBody.toRequestBody(jsonType))
                    .build()
            ).execute()

            // ── Step 3: seed all onboarding data to Room so the app is usable immediately ───
            accountDao.upsertAll(
                listOf(
                    AccountEntity(id = acc1Id, name = "Cash (€)", currency = "EUR", type = "cash", balance = -10.0, sortOrder = 1, createdAt = now, updatedAt = now),
                    AccountEntity(id = acc2Id, name = "Cash (₽)", currency = "RUB", type = "cash", balance = 0.0, sortOrder = 2, createdAt = now, updatedAt = now),
                )
            )
            categoryDao.upsertAll(
                listOf(
                    CategoryEntity(id = cat1Id, name = "Groceries", icon = "ShoppingCart", color = "#22c55e", isExpense = true, sortOrder = 1, createdAt = now, updatedAt = now),
                    CategoryEntity(id = cat2Id, name = "Transport", icon = "Bus", color = "#3b82f6", isExpense = true, sortOrder = 2, createdAt = now, updatedAt = now),
                    CategoryEntity(id = cat3Id, name = "Health", icon = "Heart", color = "#f87171", isExpense = true, sortOrder = 3, createdAt = now, updatedAt = now),
                )
            )
            transactionDao.upsert(
                TransactionEntity(
                    id = txn1Id, date = today, time = "00:00", type = "expense",
                    amount = 10.0, currency = "EUR", amountBase = 10.0,
                    accountId = acc1Id, categoryIds = cat1Id, comment = "Groceries",
                    createdAt = now, updatedAt = now,
                )
            )
            transactionDao.insertCategoryRefs(listOf(TransactionCategoryCrossRef(txn1Id, cat1Id, 0)))
            Log.i(TAG, "Seeded onboarding data to Sheets and Room")

            spreadsheetId
        }.onFailure { e ->
            Log.e(TAG, "createSpreadsheet exception: ${e.message}", e)
        }.getOrDefault("")
    }

    /**
     * Finds the spreadsheetId of `db_money` via Drive API v3.
     * Uses a plain OkHttpClient with the token passed directly — avoids circular
     * dependency with the Hilt-managed OkHttpClient that uses TokenProvider.
     */
    private suspend fun findSpreadsheetId(accessToken: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val query = Uri.encode(
                "name='db_money' and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false"
            )
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@runCatching ""
            val result = Gson().fromJson(body, DriveFilesResponse::class.java)
            val id = result.files.firstOrNull()?.id ?: ""
            Log.i(TAG, "findSpreadsheetId result: '${result.files.firstOrNull()?.name}' id='$id' (${result.files.size} files found)")
            id
        }.onFailure { e ->
            Log.e(TAG, "findSpreadsheetId exception: ${e.message}", e)
        }.getOrDefault("")
    }

    private fun isExpiredSoon(expiry: String): Boolean {
        if (expiry.isBlank()) return true
        return runCatching {
            Instant.now().isAfter(Instant.parse(expiry).minusSeconds(300))
        }.getOrDefault(true)
    }

    private fun expiryInOneHour(): String =
        Instant.now().plusSeconds(3600).toString()

    companion object {
        private const val TAG = "GoogleAuthRepository"
    }
}
