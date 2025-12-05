package com.github.shieru_lab

import kotlinx.serialization.json.Json

fun interface Logger {
    fun log(event: LogEvent)
}

class JsonLogger : Logger {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override fun log(event: LogEvent) {
        val jsonString = json.encodeToString(event)
        println(jsonString)
        System.out.flush()
    }
}

class PlainLogger : Logger {
    override fun log(event: LogEvent) {
        when (event) {
            is ModelInfoLog -> {
                println("Model URLs to download:")
                println("Save to: ${event.save_to}")
                println("URLs:")
                event.urls.forEach { url ->
                    println(" - $url")
                }
            }
            is TaskLog -> {
                val statusString = when (event.status) {
                    LogStatus.SCHEDULED -> "Scheduled"
                    LogStatus.ONGOING -> "Ongoing"
                    LogStatus.COMPLETE -> "Complete"
                    LogStatus.FAILED -> "Failed"
                }
                val progressString = event.progress?.let { " - Progress: ${"%.2f".format(it * 100)}%" } ?: ""
                val fileString = event.file?.let { " - File: $it" } ?: ""
                println("Task: ${event.task} - Status: $statusString$progressString$fileString")
            }
        }
        System.out.flush()
    }
}
