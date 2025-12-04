package com.github.shieru_lab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LogEvent

@Serializable
@SerialName("download")
data class DownloadProgress(
    val filename: String,
    val downloaded: Long,
    val total: Long
) : LogEvent

@Serializable
@SerialName("error")
data class ErrorEvent(val message: String) : LogEvent