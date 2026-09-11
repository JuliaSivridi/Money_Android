package com.stler.money.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DriveFilesResponse(
    @SerializedName("files") val files: List<DriveFile> = emptyList(),
)

data class DriveFile(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
)
