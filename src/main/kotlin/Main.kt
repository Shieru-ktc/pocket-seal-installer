package com.github.shieru_lab

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import java.lang.System

class MainCommand() : SuspendingCliktCommand() {
    val platform by option("--platform", help = "Override platform (windows/linux)").convert {
        when (it.lowercase()) {
            "windows" -> Platform.Windows()
            "linux" -> Platform.Linux()
            else -> fail("Unknown platform: $it")
        }
    }.default(System.getProperty("os.name").lowercase().let { os ->
        when {
            os.contains("windows") -> Platform.Windows()
            os.contains("linux") -> Platform.Linux()
            else -> throw IllegalArgumentException("Unsupported OS: $os")
        }
    })

    override suspend fun run() {
        platform.downloadUv()
    }
}

suspend fun main(args: Array<String>) = MainCommand().main(args)
