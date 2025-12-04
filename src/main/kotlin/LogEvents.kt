package com.github.shieru_lab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LogStatus {
    SCHEDULED, ONGOING, COMPLETE, FAILED
}

@Serializable
enum class TaskName {
    DOWNLOAD_UV,
    DOWNLOAD_PROJECT,
    UV_SYNC,
    GETTING_MODEL_URLS,
    DOWNLOAD_MODEL_FILE,
    PREPARE_MODELS,
}

@Serializable
sealed interface LogEvent

@Serializable
@SerialName("TaskLog")
data class TaskLog(
    val task: TaskName,
    val status: LogStatus,
    val progress: Float? = null,
    val file: String? = null
) : LogEvent

@Serializable
@SerialName("ModelInfoLog")
data class ModelInfoLog(
    val urls: List<String>,
    val save_to: String
) : LogEvent {
}
