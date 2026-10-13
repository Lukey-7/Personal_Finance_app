package com.pft.financetracker.domain.ai

import com.pft.financetracker.domain.split.SplitAiProvider
import com.pft.financetracker.domain.split.SplitAiRequest

/**
 * Split judging with whichever AI provider is set up. Sends only the anonymised [SplitAiRequest] (amounts, relative
 * days, categories, "Person A" labels) and returns the model's JSON. Every answer is verified locally before it can
 * change anything (see SplitVerifier), so a wrong or strange answer is harmless.
 */
class LlmSplitProvider(private val provider: () -> LlmProvider?) : SplitAiProvider {
    override val cacheScope: String get() = provider()?.cacheScope.orEmpty()

    override suspend fun judge(request: SplitAiRequest): String? {
        val llm = provider() ?: return null
        val asked = LlmRequest.ask(SplitAiRequest.SYSTEM_PROMPT, request.json, wantJson = true, maxTokens = 1_500, temperature = 0.0)
        return (llm.complete(asked) as? LlmResult.Ok)?.text
    }
}
