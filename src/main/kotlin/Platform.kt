package com.github.shieru_lab

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

const val PYTHON_DOWNLOAD_URL = "https://www.python.org/ftp/python/3.13.9/python-3.13.9-embed-amd64.zip"

object HttpClientFactory {
    val client = HttpClient(CIO)
}

sealed class Platform {
    abstract fun greet()
    abstract suspend fun createPythonEnv()
    abstract override fun toString(): String

    class Windows() : Platform() {
        override fun greet() {
            println("Windows")
        }

        override suspend fun createPythonEnv() {
            val response = HttpClientFactory.client.get(PYTHON_DOWNLOAD_URL)
            val channel: ByteReadChannel = response.body()
            val file = File("./python.zip")
            channel.copyTo(
                file.outputStream(),
            )
            ZipInputStream(file.inputStream()).use { inputStream ->
                val baseDir = Paths.get("./python/").toAbsolutePath().normalize()

                var entry = inputStream.nextEntry
                while (entry != null) {
                    val destination = baseDir.resolve(entry.name).normalize()

                    if (!destination.startsWith(baseDir)) {
                        throw SecurityException("Invalid file path: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        Files.createDirectories(destination)
                    } else {
                        if (destination.parent != null) {
                            Files.createDirectories(destination.parent)
                        }
                        Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                    entry = inputStream.nextEntry
                }
            }
        }

        override fun toString() = "Windows"
    }

    class Linux() : Platform() {
        override fun greet() {
            println("Hello from Linux!")
        }

        override suspend fun createPythonEnv() {
            ProcessBuilder("python", "-m", "venv", "python")
                .inheritIO()
                .start()
                .waitFor()
            ProcessBuilder("./python/bin/pip", "install", "uv")
                .inheritIO()
                .start()
                .waitFor()
        }

        override fun toString() = "Linux"

    }
}