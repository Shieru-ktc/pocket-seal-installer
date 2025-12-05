package com.github.shieru_lab

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import java.nio.file.Paths
import kotlin.io.path.exists

enum class OsType {
    Windows, Linux
}

class MainCommand() : SuspendingCliktCommand() {
    val jsonMode by option(help = "Print log in json lines style").flag()

    val osType by option("--platform", help = "Override platform (windows/linux)")
        .choice("windows" to OsType.Windows, "linux" to OsType.Linux)
        .default(
            System.getProperty("os.name").lowercase().let { os ->
                when {
                    os.contains("windows") -> OsType.Windows
                    os.contains("linux") -> OsType.Linux
                    else -> throw IllegalArgumentException("Unsupported OS: $os")
                }
            }
        )

    override suspend fun run() {
        val logger = if (jsonMode) JsonLogger() else PlainLogger()
        val platform = when (osType) {
            OsType.Windows -> Platform.Windows(logger)
            OsType.Linux -> Platform.Linux(logger)
        }

        logger.log(
           TaskLog(
                task = TaskName.DOWNLOAD_UV,
                status = LogStatus.SCHEDULED
           )
        )
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

        val models = platform.modelList()
        models.forEach {
            println("Downloading model: ${it.type}... ")
            it.urls.forEach { model ->
                println("  $model")
            }
        }
        platform.downloadModels(models)
        platform.preprocessModels()
    }
}

suspend fun main(args: Array<String>) = MainCommand().main(args)
