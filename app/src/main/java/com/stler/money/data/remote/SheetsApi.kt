package com.stler.money.data.remote

import com.stler.money.data.remote.dto.BatchUpdateValuesBody
import com.stler.money.data.remote.dto.BatchValuesResponse
import com.stler.money.data.remote.dto.ValuesBody
import com.stler.money.data.remote.dto.ValuesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SheetsApi {

    /**
     * Fetch multiple ranges in a single request.
     * Used by the pull phase: ranges = ["transactions", "accounts", "categories"].
     */
    @GET("spreadsheets/{spreadsheetId}/values:batchGet")
    suspend fun batchGet(
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("ranges") ranges: List<String>,
        @Query("valueRenderOption") renderOption: String = "UNFORMATTED_VALUE",
    ): BatchValuesResponse

    /**
     * Append a new row at the end of the sheet (INSERT).
     * insertDataOption=INSERT_ROWS ensures existing data is not overwritten.
     */
    @POST("spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun append(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") inputOption: String = "RAW",
        @Query("insertDataOption") insertOption: String = "INSERT_ROWS",
        @Body body: ValuesBody,
    ): ValuesResponse

    /**
     * Update one or more specific ranges (UPDATE).
     * Used after row-number lookup to write a full entity row.
     */
    @POST("spreadsheets/{spreadsheetId}/values:batchUpdate")
    suspend fun batchUpdate(
        @Path("spreadsheetId") spreadsheetId: String,
        @Body body: BatchUpdateValuesBody,
    ): ValuesResponse

    /**
     * Clear a range (soft-delete: empties the row, mapper skips rows with no id).
     */
    @POST("spreadsheets/{spreadsheetId}/values/{range}:clear")
    suspend fun clear(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
    ): ValuesResponse

    /** Single-range read — used for the settings!A1 JSON blob (base currency, exchange rates). */
    @GET("spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueRenderOption") renderOption: String = "UNFORMATTED_VALUE",
    ): ValuesResponse

    /** Single-range write (PUT) — used for the settings!A1 JSON blob. */
    @retrofit2.http.PUT("spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") inputOption: String = "RAW",
        @Body body: ValuesBody,
    ): ValuesResponse
}
