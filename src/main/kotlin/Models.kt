package com.github.shieru_lab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelDownloadInfo(
    val type: String,

    @SerialName("savedir")
    val saveDir: String,

    val urls: List<String>
)
