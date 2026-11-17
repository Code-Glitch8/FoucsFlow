package com.focusflow.app.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Thin wrapper over the LiteRT-LM engine. Everything runs on the phone's CPU; nothing is sent
 * anywhere. The engine is loaded on first use and released after a few idle minutes to give the
 * RAM back. Any failure is returned as a Result so the caller can fall back instead of crashing.
 */
class LocalLlm(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null
    private var idleJob: Job? = null

    suspend fun generate(
        modelPath: String,
        system: String,
        user: String,
        timeoutMs: Long = 150_000,
    ): Result<String> = mutex.withLock {
        idleJob?.cancel()
        try {
            withContext(Dispatchers.Default) {
                withTimeout(timeoutMs) {
                    val e = ensureEngine(modelPath)
                    val cfg = ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                    )
                    val sb = StringBuilder()
                    e.createConversation(cfg).use { conversation ->
                        conversation.sendMessageAsync(user).collect { sb.append(it.toString()) }
                    }
                    Result.success(sb.toString())
                }
            }
        } catch (e: TimeoutCancellationException) {
            closeEngine()
            Result.failure(IllegalStateException("The model took too long to answer."))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Includes OutOfMemoryError and native loading problems.
            closeEngine()
            Result.failure(t)
        } finally {
            scheduleIdleRelease()
        }
    }

    private fun ensureEngine(path: String): Engine {
        engine?.let { if (loadedPath == path) return it }
        closeEngine()
        val config = EngineConfig(
            modelPath = path,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path,
        )
        return Engine(config).also { it.initialize(); engine = it; loadedPath = path }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }

    private fun scheduleIdleRelease() {
        idleJob = scope.launch {
            delay(3 * 60_000L)
            mutex.withLock { closeEngine() }
        }
    }

    /** Frees the model's memory right away, e.g. when it is deleted. */
    suspend fun release() = mutex.withLock { closeEngine() }
}
