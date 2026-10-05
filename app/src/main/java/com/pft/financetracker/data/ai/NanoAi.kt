package com.pft.financetracker.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.pft.financetracker.domain.ask.NanoPrompt
import kotlinx.coroutines.flow.takeWhile

/**
 * Gemini Nano through Android's AICore, on phones that have it (Pixel 9 and later, Galaxy S24 and later, a few
 * others). The model and the question stay on the phone. AICore downloads the model itself the first time, and
 * Google's ML Kit keeps its own usage statistics (see README).
 */
class NanoAi {
    enum class Status { UNSUPPORTED, DOWNLOADABLE, DOWNLOADING, READY }

    private val model: GenerativeModel? by lazy { runCatching { Generation.getClient() }.getOrNull() }

    suspend fun status(): Status = when (runCatching { model?.checkStatus() }.getOrNull()) {
        FeatureStatus.AVAILABLE -> Status.READY
        FeatureStatus.DOWNLOADABLE -> Status.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> Status.DOWNLOADING
        else -> Status.UNSUPPORTED
    }

    /** Asks AICore to fetch the model; returns true once it is ready. */
    suspend fun download(onProgress: (Long) -> Unit = {}): Boolean {
        val m = model ?: return false
        var ok = false
        runCatching {
            m.download().takeWhile { s ->
                when (s) {
                    is DownloadStatus.DownloadProgress -> { onProgress(s.totalBytesDownloaded); true }
                    is DownloadStatus.DownloadCompleted -> { ok = true; false }
                    is DownloadStatus.DownloadFailed -> false
                    else -> true
                }
            }.collect { }
        }
        return ok && status() == Status.READY
    }

    /** The model's short answer from [facts], or null when it is not available or says nothing usable. */
    suspend fun answer(question: String, facts: String): String? {
        if (status() != Status.READY) return null
        val m = model ?: return null
        return runCatching { NanoPrompt.clean(m.generateContent(NanoPrompt.prompt(question, facts)).candidates.firstOrNull()?.text) }.getOrNull()
    }
}
