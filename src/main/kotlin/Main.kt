package com.github.shieru_lab

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import java.nio.file.Paths
import kotlin.io.path.exists

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
        // if current directory has "uv" or "uv.exe", skip download
        val uvExists = when (platform) {
            is Platform.Windows -> System.getProperty("user.dir")
                .let { dir -> Paths.get(dir, "uv.exe") }.exists()

            is Platform.Linux -> System.getProperty("user.dir")
                .let { dir -> Paths.get(dir, "uv") }.exists()
        }
        when {
            uvExists -> echo("UV already exists, skipping download.")
            else -> platform.downloadUv()
        }

        val projectExists = System.getProperty("user.dir")
            .let { dir -> Paths.get(dir, "pyproject.toml") }.exists()
        when {
            projectExists -> echo("Project already exists, skipping clone.")
            else -> platform.cloneProject()
        }

        platform.modelList().forEach {
            println("Downloading model: ${it.type}... ")
            it.urls.forEach { model ->
                println("  $model")
            }
        }
    }
}

suspend fun main(args: Array<String>) = MainCommand().main(args)
