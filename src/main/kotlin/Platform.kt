package com.github.shieru_lab

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.io.path.exists

object HttpClientFactory {
    val client = HttpClient(CIO)
}

const val UV_DOWNLOAD_BASE = "https://github.com/astral-sh/uv/releases"
const val PROJECT_REPOSITORY_URL = "https://github.com/Shieru-ktc/NER-can-use-NPU-Test"

val systemArch = when (System.getProperty("os.arch").lowercase(Locale.getDefault())) {
    "x86_64", "amd64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> throw UnsupportedOperationException("Unsupported Architecture: ${0}")
}

sealed class Platform {
    abstract fun greet()
    abstract override fun toString(): String
    abstract fun uvDownloadUrl(arch: String?, version: String?): String
    protected abstract fun prepareUv(inputStream: BufferedInputStream)
    protected abstract suspend fun prepareProject()

    suspend fun downloadUv(arch: String? = null, version: String? = null) {
        val response = HttpClientFactory.client.get(uvDownloadUrl(arch, version))
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException(response.bodyAsText())
        }
        prepareUv(response.bodyAsChannel().toInputStream().buffered())
    }

    suspend fun downloadModels() {

    }

    suspend fun cloneProject(repoUrl: String = PROJECT_REPOSITORY_URL, branch: String = "main") {
        val cloneUrl = "$repoUrl/archive/refs/heads/$branch.zip"
        val response = HttpClientFactory.client.get(cloneUrl)
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException(response.bodyAsText())
        }
        val inputStream = response.bodyAsChannel().toInputStream().buffered()
        val targetDir = Paths.get("./").toAbsolutePath().normalize()
        ZipInputStream(inputStream).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                // 1階層無視して展開する
                val parts = entry.name.split("/", "\\").drop(1).joinToString("/")
                val filePath = targetDir.resolve(parts).normalize()

                if (!filePath.startsWith(targetDir)) {
                    throw SecurityException("Invalid Path")
                }
                if (!filePath.parent.exists()) {
                    Files.createDirectory(filePath.parent)
                }
                if (!entry.isDirectory) {
                    Files.copy(stream, filePath, StandardCopyOption.REPLACE_EXISTING)
                }
                entry = stream.nextEntry
            }
        }
        prepareProject()
    }

    suspend fun downloadModels(modelInfos: List<ModelDownloadInfo>) {
        for (modelInfo in modelInfos) {
            val saveDir = Paths.get(modelInfo.saveDir).toAbsolutePath().normalize()
            if (!saveDir.exists()) {
                Files.createDirectories(saveDir)
            }
            for (url in modelInfo.urls) {
                val response = HttpClientFactory.client.get(url)
                if (response.status != HttpStatusCode.OK) {
                    throw RuntimeException("Failed to download model from $url: ${response.bodyAsText()}")
                }
                val fileName = url.substringAfterLast("/")
                val filePath = saveDir.resolve(fileName).normalize()
                response.bodyAsChannel().toInputStream().use { input ->
                    Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    class Windows() : Platform() {
        override fun greet() {
            println("Windows")
        }

        override fun uvDownloadUrl(arch: String?, version: String?): String {
            return "${UV_DOWNLOAD_BASE}/${version ?: "latest"}/download/uv-${arch ?: systemArch}-pc-windows-msvc.zip"
        }

        override fun prepareUv(inputStream: BufferedInputStream) {
            val targetDir = Paths.get("./").toAbsolutePath().normalize()
            ZipInputStream(inputStream).use { stream ->
                var entry = stream.nextEntry
                while (entry != null) {

                    // 論理的に安全なので、パストラバーサルに関する警告を抑制
                    @Suppress("JvmTaintAnalysis")
                    val filePath = targetDir.resolve(Paths.get(entry.name).fileName).normalize()

                    if (!filePath.startsWith(targetDir)) {
                        throw SecurityException("Invalid Path")
                    }
                    if (!entry.isDirectory) {
                        Files.copy(stream, filePath, StandardCopyOption.REPLACE_EXISTING)
                    }
                    entry = stream.nextEntry
                }
            }
        }

        override suspend fun prepareProject() {
            val builder = ProcessBuilder("uv.exe", "sync").apply {
                redirectOutput(ProcessBuilder.Redirect.INHERIT)
                redirectError(ProcessBuilder.Redirect.INHERIT)
            }.start()
            val exitCode = builder.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("uv sync failed with exit code $exitCode")
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
            val targetDir = Paths.get(".").toAbsolutePath().normalize()
            TarArchiveInputStream(gzipIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val filePath = targetDir.resolve(Paths.get(entry.name).fileName).normalize()

                    if (!filePath.startsWith(targetDir)) {
                        throw SecurityException("Invalid Path")
                    }

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

        override suspend fun prepareProject() {
            val builder = ProcessBuilder("uv", "sync").apply {
                redirectOutput(ProcessBuilder.Redirect.INHERIT)
                redirectError(ProcessBuilder.Redirect.INHERIT)
            }.start()
            val exitCode = builder.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("uv sync failed with exit code $exitCode")
            }
        }

        override fun toString() = "Linux"

    }
}