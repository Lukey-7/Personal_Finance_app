package com.pft.financetracker

import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.fail
import org.junit.Test

class SmsCorpusTest {
    private val parser = SmsParser()
    private val now = System.currentTimeMillis()

    private fun check(c: CorpusCase): String? {
        val r = parser.parse(SmsMessage(c.sender, c.body, now))
        return when (val e = c.expect) {
            Expect.Ignored -> if (r is ParseResult.Ignored) null else "expected Ignored, got $r"
            Expect.Review -> if (r is ParseResult.NeedsReview) null else "expected Review, got $r"
            is Expect.Saved -> {
                val t = (r as? ParseResult.Success)?.transaction ?: return "expected Saved, got $r"
                val cat = Categorizer.categorize(t.merchant, t.type, t.bankName)
                val flow = FlowClassifier.classify(t.type, c.body, t.merchant, cat)
                listOfNotNull(
                    if (e.type != null && t.type != e.type) "type ${t.type} != ${e.type}" else null,
                    if (t.amountPaise != e.paise) "paise ${t.amountPaise} != ${e.paise}" else null,
                    if (e.flow != null && flow != e.flow) "flow $flow != ${e.flow}" else null,
                    if (e.merchantContains != null && !t.merchant.lowercase().contains(e.merchantContains)) "merchant '${t.merchant}' lacks '${e.merchantContains}'" else null,
                    if (e.ref != null && t.refNumber != e.ref) "ref ${t.refNumber} != ${e.ref}" else null,
                ).joinToString("; ").ifEmpty { null }
            }
        }
    }

    @Test
    fun everyCorpusMessageParsesAsExpected() {
        val failures = SmsCorpus.cases.mapNotNull { c -> check(c)?.let { "${c.name}: $it" } }
        if (failures.isNotEmpty()) fail("${failures.size} of ${SmsCorpus.cases.size} corpus messages wrong:\n" + failures.joinToString("\n"))
    }
}
