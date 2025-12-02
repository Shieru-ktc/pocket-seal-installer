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
