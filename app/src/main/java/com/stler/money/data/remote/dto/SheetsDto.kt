package com.stler.money.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response from GET spreadsheets/{id}/values/{range} */
data class ValuesResponse(
    @SerializedName("range") val range: String = "",
    @SerializedName("values") val values: List<List<Any?>> = emptyList(),
)

/** Response from GET spreadsheets/{id}/values:batchGet */
data class BatchValuesResponse(
    @SerializedName("valueRanges") val valueRanges: List<ValueRange> = emptyList(),
)

data class ValueRange(
    @SerializedName("range") val range: String = "",
    @SerializedName("values") val values: List<List<Any?>> = emptyList(),
)

/** Body for PUT/POST values write operations */
data class ValuesBody(
    @SerializedName("range") val range: String,
    @SerializedName("majorDimension") val majorDimension: String = "ROWS",
    @SerializedName("values") val values: List<List<Any?>>,
)

/** Body for POST spreadsheets/{id}/values:batchUpdate */
data class BatchUpdateValuesBody(
    @SerializedName("valueInputOption") val valueInputOption: String = "RAW",
    @SerializedName("data") val data: List<ValuesBody>,
)
