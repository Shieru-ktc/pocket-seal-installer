package com.github.shieru_lab

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.delay

class MainCommand : SuspendingCliktCommand() {
    val count by option(help = "Number of greetings").int().default(5)

    override suspend fun run() {
        repeat(count) {
            println("#$it Hello Kotlin!")
            delay(1000)
        }
    }
}

suspend fun main(args: Array<String>) = MainCommand().main(args)
