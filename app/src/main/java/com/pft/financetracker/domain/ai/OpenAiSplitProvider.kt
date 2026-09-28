package com.pft.financetracker.domain.ai

import com.pft.financetracker.domain.split.SplitAiProvider
import com.pft.financetracker.domain.split.SplitAiRequest

/**
 * Split judging with OpenAI, using the user's own key. Sends only the anonymised [SplitAiRequest] (amounts,
 * relative days, categories, "Person A" labels) and returns the model's JSON. Every answer is verified locally
 * before it can change anything (see SplitVerifier), so a wrong or strange answer is harmless.
 */
class OpenAiSplitProvider(private val apiKey: () -> String?, private val client: OpenAiClient = OpenAiClient()) : SplitAiProvider {
    override suspend fun judge(request: SplitAiRequest): String? {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: return null
        return when (val r = client.chatJson(key, SplitAiRequest.SYSTEM_PROMPT, request.json)) {
            is OpenAiClient.Result.Ok -> r.text
            is OpenAiClient.Result.Error -> null
        }
    }
}
