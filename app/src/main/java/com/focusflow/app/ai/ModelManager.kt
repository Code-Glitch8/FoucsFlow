package com.focusflow.app.ai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.focusflow.app.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ModelSpec(
    val id: String,
    val name: String,
    val fileName: String,
    val url: String,
    val approxBytes: Long,
    val description: String,
    /** Qwen3 models "think" aloud by default; this switches that off for fast, clean JSON. */
    val noThink: Boolean = false,
)

object ModelCatalog {
    val all = listOf(
        ModelSpec(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B (small, fast)",
            fileName = "Qwen3-0.6B.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            approxBytes = 600L * 1024 * 1024,
            description = "About 0.6 GB. Works on most phones. Good enough for turning a study plan into tasks.",
            noThink = true,
        ),
        ModelSpec(
            id = "gemma4-e2b",
            name = "Gemma 4 E2B (better quality)",
            fileName = "gemma-4-E2B-it.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            approxBytes = 2_600L * 1024 * 1024,
            description = "About 2.6 GB (Apache-2.0). Better wording and summaries. Needs a phone with about 6 GB of RAM or more.",
        ),
    )

    fun forFile(fileName: String): ModelSpec? = all.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
}

data class InstalledModel(val fileName: String, val sizeBytes: Long)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val modelId: String, val bytes: Long, val total: Long) : DownloadState
    data class Failed(val modelId: String, val message: String) : DownloadState
}

sealed interface ImportResult {
    data class Imported(val fileName: String) : ImportResult
    data class Rejected(val reason: String) : ImportResult
}

/**
 * Owns the model files. Models are never bundled in the APK and are only downloaded when the
 * user presses Download (or imports a file they fetched themselves). The only network use in
 * the whole app is that explicit download.
 */
class ModelManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
) {
    private val _installed = MutableStateFlow<List<InstalledModel>>(emptyList())
    val installed: StateFlow<List<InstalledModel>> = _installed

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    private var downloadJob: Job? = null

    init { refresh() }

    private fun dir(): File = File(context.filesDir, "models").also { it.mkdirs() }

    fun refresh() {
        _installed.value = dir().listFiles { f -> f.isFile && f.name.endsWith(".litertlm") }
            ?.map { InstalledModel(it.name, it.length()) }
            ?.sortedBy { it.fileName }
            ?: emptyList()
    }

    fun fileFor(fileName: String): File = File(dir(), fileName)

    /** The model to use: the user's choice if it still exists, else the first installed one. */
    suspend fun activeModelFile(): File? {
        val chosen = settings.aiModelFile.first()
        val file = chosen?.let { fileFor(it) }?.takeIf { it.isFile }
        return file ?: _installed.value.firstOrNull()?.let { fileFor(it.fileName) }
    }

    fun totalRamBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info.totalMem
    }

    fun freeBytes(): Long = StatFs(dir().path).availableBytes

    /**
     * Loading a model needs roughly twice its file size in RAM. Refusing up front avoids the
     * system killing the app, which cannot be caught. Returns null if fine, else a reason.
     */
    fun memoryProblem(file: File): String? {
        val ram = totalRamBytes()
        if (ram <= 0) return null
        return if (file.length() * 2 > ram) {
            "This phone has about ${ram / (1024 * 1024 * 1024)} GB of RAM, which is not enough to run " +
                "${file.name} (${file.length() / (1024 * 1024)} MB) safely. Try the smaller model."
        } else null
    }

    suspend fun select(fileName: String) = settings.setAiModelFile(fileName)

    suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { fileFor(fileName).delete(); File(dir(), "$fileName.part").delete() }
        if (settings.aiModelFile.first() == fileName) settings.setAiModelFile(null)
        refresh()
    }

    fun startDownload(spec: ModelSpec) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _download.value = DownloadState.Downloading(spec.id, 0, spec.approxBytes)
            try {
                withContext(Dispatchers.IO) { download(spec) }
                _download.value = DownloadState.Idle
                refresh()
                if (settings.aiModelFile.first() == null) settings.setAiModelFile(spec.fileName)
            } catch (e: CancellationException) {
                _download.value = DownloadState.Idle // the .part file stays so the download can resume
                throw e
            } catch (e: DownloadException) {
                _download.value = DownloadState.Failed(spec.id, e.message ?: "Download failed")
            } catch (e: Exception) {
                _download.value = DownloadState.Failed(spec.id, "Download failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }
    fun dismissFailure() { if (_download.value is DownloadState.Failed) _download.value = DownloadState.Idle }

    private class DownloadException(message: String) : Exception(message)

    private suspend fun download(spec: ModelSpec) {
        val part = File(dir(), spec.fileName + ".part")
        val dest = File(dir(), spec.fileName)
        var existing = if (part.exists()) part.length() else 0L

        val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FocusFlow-ModelDownload")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            when {
                code == 401 || code == 403 -> throw DownloadException(
                    "This model needs you to accept its license on Hugging Face first. Download the file in your " +
                        "browser, then use \"Import model file\".",
                )
                code == 404 -> throw DownloadException("The model file was not found at its source. Download it manually and use \"Import model file\".")
                code == 416 -> { part.delete(); throw DownloadException("The partial download was invalid and has been cleared. Try again.") }
                code !in 200..299 -> throw DownloadException("The server answered with error $code.")
            }
            val resumed = code == 206
            if (!resumed) { existing = 0; part.delete() }
            val remaining = conn.contentLengthLong
            val total = if (remaining > 0) existing + remaining else spec.approxBytes

            val needed = (total - existing) + 200L * 1024 * 1024
            if (freeBytes() < needed) {
                throw DownloadException("Not enough free storage: about ${needed / (1024 * 1024)} MB is needed.")
            }

            var downloaded = existing
            var lastReport = 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 300) {
                            lastReport = now
                            _download.value = DownloadState.Downloading(spec.id, downloaded, total)
                            if (!scope.isActive) throw CancellationException()
                        }
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
            if (remaining > 0 && part.length() != total) {
                throw DownloadException("The download ended early. Press Download again to resume.")
            }
            if (part.length() < 10L * 1024 * 1024) {
                part.delete()
                throw DownloadException("The downloaded file is too small to be a model.")
            }
            dest.delete()
            if (!part.renameTo(dest)) throw DownloadException("Could not save the model file.")
        } finally {
            conn.disconnect()
        }
    }

    /** Copies a .litertlm file the user chose (e.g. downloaded in a browser) into app storage. */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        var name = "imported-model.litertlm"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        val safeName = File(name).name
        if (!safeName.endsWith(".litertlm")) {
            return@withContext ImportResult.Rejected("That file is not a .litertlm model. (Older .task files are not supported.)")
        }
        if (size > 0 && freeBytes() < size + 100L * 1024 * 1024) {
            return@withContext ImportResult.Rejected("Not enough free storage for this file.")
        }
        val part = File(dir(), "$safeName.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                part.outputStream().use { input.copyTo(it) }
            } ?: return@withContext ImportResult.Rejected("Could not open that file.")
            if (part.length() < 10L * 1024 * 1024) {
                part.delete()
                return@withContext ImportResult.Rejected("That file is too small to be a model.")
            }
            val dest = File(dir(), safeName)
            dest.delete()
            part.renameTo(dest)
        } catch (e: Exception) {
            part.delete()
            return@withContext ImportResult.Rejected("Import failed: ${e.message}")
        }
        refresh()
        if (settings.aiModelFile.first() == null) settings.setAiModelFile(safeName)
        ImportResult.Imported(safeName)
    }
}
