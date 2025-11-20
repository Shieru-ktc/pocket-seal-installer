package com.github.shieru_lab

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.zip.ZipInputStream

object HttpClientFactory {
    val client = HttpClient(CIO)
}

const val UV_DOWNLOAD_BASE = "https://github.com/astral-sh/uv/releases"

val systemArch = when (System.getProperty("os.arch").lowercase(Locale.getDefault())) {
    "x86_64", "amd64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> throw UnsupportedOperationException("Unsupported Architecture: ${0}")
}

sealed class Platform {
    abstract fun greet()
    abstract override fun toString(): String
    abstract fun uvDownloadUrl(arch: String?, version: String?): String
    abstract fun prepareUv(inputStream: BufferedInputStream)

    suspend fun downloadUv(arch: String? = null, version: String? = null) {
        val response = HttpClientFactory.client.get(uvDownloadUrl(arch, version))
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException(response.bodyAsText())
        }
        prepareUv(response.bodyAsChannel().toInputStream().buffered())
    }

    class Windows() : Platform() {
        override fun greet() {
            println("Windows")
        }

        override fun uvDownloadUrl(arch: String?, version: String?): String {
            return "${UV_DOWNLOAD_BASE}/${version ?: "latest"}/download/uv-${arch ?: systemArch}-pc-windows-msvc.zip"
        }

        override fun prepareUv(inputStream: BufferedInputStream) {
            ZipInputStream(inputStream).use { stream ->
                val baseDir = Paths.get("./python/").toAbsolutePath().normalize()

                var entry = stream.nextEntry
                while (entry != null) {
                    val destination = baseDir.resolve(entry.name).fileName.normalize()
                    if (!entry.isDirectory) {
                        Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                    entry = stream.nextEntry
                }
            }

        }


        override fun toString() = "Windows"
    }

    class Linux() : Platform() {
        override fun greet() {
            println("Hello from Linux!")
        }

        override fun uvDownloadUrl(arch: String?, version: String?): String {
            return "${UV_DOWNLOAD_BASE}/${version ?: "latest"}/download/uv-${arch ?: systemArch}-unknown-linux-gnu.tar.gz"
        }

        override fun prepareUv(inputStream: BufferedInputStream) {
            val gzipIn = GzipCompressorInputStream(inputStream)
            TarArchiveInputStream(gzipIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val filePath = Paths.get(entry.name).fileName.normalize()
                    if (!entry.isDirectory) {
                        Files.copy(tarIn, filePath, StandardCopyOption.REPLACE_EXISTING)

                        if (filePath.toString().endsWith("uv")) {
                            filePath.toFile().setExecutable(true)
                        }
                    }
                    entry = tarIn.nextEntry
                }
            }
        }

        override fun toString() = "Linux"

    }
}