package com.github.shieru_lab

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.io.path.exists

object HttpClientFactory {
    val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
        }
    }
}

const val UV_DOWNLOAD_BASE = "https://github.com/astral-sh/uv/releases"
const val PROJECT_REPOSITORY_URL = "https://github.com/Shieru-ktc/NER-can-use-NPU-Test"

val systemArch = when (System.getProperty("os.arch").lowercase(Locale.getDefault())) {
    "x86_64", "amd64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> throw UnsupportedOperationException("Unsupported Architecture: ${0}")
}

sealed class Platform(val logger: Logger) {
    abstract fun greet()
    abstract override fun toString(): String
    abstract fun uvDownloadUrl(arch: String?, version: String?): String
    protected abstract fun prepareUv(inputStream: BufferedInputStream)

    suspend fun downloadUv(arch: String? = null, version: String? = null) {
        withContext(Dispatchers.IO) {
            val response = HttpClientFactory.client.get(uvDownloadUrl(arch, version)) {
                onDownload { bytesSentTotal, contentLength ->
                    val progress = if (contentLength != null && contentLength > 0) {
                        bytesSentTotal.toDouble() / contentLength.toDouble()
                    } else {
                        null
                    }
                    logger.log(
                        TaskLog(
                            task = TaskName.DOWNLOAD_UV,
                            status = LogStatus.ONGOING,
                            progress = progress?.toFloat()
                        )
                    )
                }
            }
            if (response.status != HttpStatusCode.OK) {
                throw RuntimeException(response.bodyAsText())
            }
            logger.log(
                TaskLog(
                    task = TaskName.DOWNLOAD_UV,
                    status = LogStatus.COMPLETE
                )
            )
            prepareUv(response.bodyAsChannel().toInputStream().buffered())
        }
    }

    fun createUvProcess(vararg args: String, block: ProcessBuilder.() -> ProcessBuilder): ProcessBuilder {
        val executable = when (this) {
            is Windows -> "uv.exe"
            is Linux -> "uv"
        }
        val builder = ProcessBuilder(listOf(executable, *args))
        builder.block()
        return builder
    }

    fun modelList(): List<ModelDownloadInfo> {
        logger.log(
            TaskLog(
                task = TaskName.GETTING_MODEL_URLS,
                status = LogStatus.ONGOING
            )
        )
        val process = createUvProcess("run", "src/ner_openvino/setup.py") {
            redirectError(ProcessBuilder.Redirect.DISCARD)
            redirectOutput(ProcessBuilder.Redirect.PIPE)
        }.start()
        process.waitFor()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val jsonLines = mutableListOf<String>()

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isNotBlank()) {
                jsonLines.add(line)
            }
        }
        val models = jsonLines.map {
            Json.decodeFromString<ModelDownloadInfo>(it)
        }
        logger.log(
            ModelInfoLog(
                urls = models.flatMap { it.urls },
                save_to = models.joinToString(", ") { it.saveDir }
            )
        )
        logger.log(
            TaskLog(
                task = TaskName.GETTING_MODEL_URLS,
                status = LogStatus.COMPLETE
            )
        )
        return models
    }

    suspend fun cloneProject(repoUrl: String = PROJECT_REPOSITORY_URL, branch: String = "main") {
        val cloneUrl = "$repoUrl/archive/refs/heads/$branch.zip"
        val response = HttpClientFactory.client.get(cloneUrl) {
            onDownload {
                bytesSentTotal, contentLength ->
                val progress = if (contentLength != null && contentLength > 0) {
                    bytesSentTotal.toDouble() / contentLength.toDouble()
                } else {
                    null
                }
                logger.log(
                    TaskLog(
                        task = TaskName.DOWNLOAD_PROJECT,
                        status = LogStatus.ONGOING,
                        progress = progress?.toFloat()
                    )
                )
            }
        }
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
        logger.log(
            TaskLog(
                task = TaskName.DOWNLOAD_PROJECT,
                status = LogStatus.COMPLETE
            )
        )
        logger.log(
            TaskLog(
                task = TaskName.UV_SYNC,
                status = LogStatus.ONGOING
            )
        )
        withContext(Dispatchers.IO) {
            createUvProcess("sync") {
                redirectOutput(ProcessBuilder.Redirect.DISCARD)
                redirectError(ProcessBuilder.Redirect.DISCARD)
            }.start().waitFor()
        }
        logger.log(
            TaskLog(
                task = TaskName.UV_SYNC,
                status = LogStatus.COMPLETE
            )
        )
    }

    suspend fun downloadModels(modelInfos: List<ModelDownloadInfo>) = coroutineScope {
        val semaphore = Semaphore(5)
        val bufferSize = 8192L

        for (modelInfo in modelInfos) {
            val saveDir = Paths.get(modelInfo.saveDir).toAbsolutePath().normalize()
            if (!saveDir.exists()) {
                Files.createDirectories(saveDir)
            }

            for (url in modelInfo.urls) {
                val fileName = url.substringAfterLast("/")
                val filePath = saveDir.resolve(fileName).normalize()
                logger.log(
                    TaskLog(
                        task = TaskName.DOWNLOAD_MODEL_FILE,
                        status = LogStatus.SCHEDULED,
                        file = filePath.toString()
                    )
                )
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        HttpClientFactory.client.prepareGet(url).execute { httpResponse ->
                            if (httpResponse.status != HttpStatusCode.OK) {
                                throw RuntimeException("Failed: $url")
                            }

                            val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                            val contentLength = httpResponse.contentLength()
                            var totalBytesRead = 0L
                            var lastLoggedPercent = -1

                            Files.newOutputStream(filePath).use { fileOutput ->
                                while (!channel.isClosedForRead) {
                                    val packet = channel.readRemaining(bufferSize)

                                    if (packet.exhausted()) break

                                    val bytes = packet.readByteArray()
                                    fileOutput.write(bytes)

                                    totalBytesRead += bytes.size

                                    if (contentLength != null && contentLength > 0) {
                                        val percent = (totalBytesRead * 100 / contentLength).toInt()
                                        if (percent > lastLoggedPercent) {
                                            logger.log(
                                                TaskLog(
                                                    task = TaskName.DOWNLOAD_MODEL_FILE,
                                                    status = LogStatus.ONGOING,
                                                    progress = (totalBytesRead.toDouble() / contentLength.toDouble()).toFloat(),
                                                    file = filePath.toString()
                                                )
                                            )
                                            lastLoggedPercent = percent
                                        }
                                    } else {
                                        logger.log(
                                            TaskLog(
                                                task = TaskName.DOWNLOAD_MODEL_FILE,
                                                status = LogStatus.ONGOING,
                                                file = filePath.toString(),
                                                progress = null
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        logger.log(
                            TaskLog(
                                task = TaskName.DOWNLOAD_MODEL_FILE,
                                status = LogStatus.COMPLETE,
                                file = filePath.toString()
                            )
                        )
                    }
                }
            }
        }
        logger.log(
            TaskLog(
                task = TaskName.DOWNLOAD_MODEL_FILE,
                status = LogStatus.COMPLETE,
            )
        )
    }

    suspend fun preprocessModels() {
        withContext(Dispatchers.IO) {
            logger.log(
                TaskLog(
                    task = TaskName.PREPARE_MODELS,
                    status = LogStatus.ONGOING
                )
            )
            createUvProcess("run", "src/ner_openvino/preprocess_model.py") {
                redirectOutput(ProcessBuilder.Redirect.DISCARD)
                redirectError(ProcessBuilder.Redirect.DISCARD)
            }.start().waitFor()
        }
    }

    class Windows(logger: Logger) : Platform(logger) {
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

        override fun toString() = "Windows"
    }

    class Linux(logger: Logger) : Platform(logger) {
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

        override fun toString() = "Linux"

    }
}