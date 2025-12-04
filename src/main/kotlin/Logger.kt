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

class PlainLogger: Logger {
    override fun log(event: LogEvent) {
        when (event) {
            is DownloadProgress -> {
                val percent = if (event.total > 0) {
                    event.downloaded * 100 / event.total
                } else {
                    0
                }
                println("Downloading ${event.filename}: $percent% (${event.downloaded}/${event.total} bytes)")
            }
            is ErrorEvent -> {
                System.err.println("Error: ${event.message}")
            }
        }
        System.out.flush()
    }
}
