package com.pft.financetracker

import com.pft.financetracker.data.readAtMost
import com.pft.financetracker.domain.ai.HttpJson
import com.pft.financetracker.domain.ai.ProviderConfig
import com.pft.financetracker.domain.ai.ProviderKind
import com.pft.financetracker.domain.parser.AccountExtractor
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.StringReader

/** The v1.5.3 guards: each one refuses hostile input without changing what real input does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SecurityHardeningTest {

    @Test fun aRegionThatIsNotAnAwsRegionCantChangeTheHost() {
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com", ProviderConfig(ProviderKind.BEDROCK, region = "evil.com/x?").resolvedBaseUrl)
        assertEquals("https://bedrock-runtime.eu-central-2.amazonaws.com", ProviderConfig(ProviderKind.BEDROCK, region = " EU-Central-2 ").resolvedBaseUrl)
        assertEquals("https://bedrock-runtime.us-gov-west-1.amazonaws.com", ProviderConfig(ProviderKind.BEDROCK, region = "us-gov-west-1").resolvedBaseUrl)
    }

    @Test fun aHugeReplyIsRefusedNotLoaded() {
        assertEquals("ok", HttpJson.readBounded(StringReader("ok"), max = 10))
        val thrown = runCatching { HttpJson.readBounded(StringReader("x".repeat(11)), max = 10) }.exceptionOrNull()
        assertTrue(thrown is java.io.IOException)
    }

    @Test fun aProviderErrorIsCutShort() {
        val long = "e".repeat(5_000)
        val msg = HttpJson.errorMessage(HttpJson.Response(500, """{"error":{"message":"$long"}}"""))
        assertEquals(300, msg.length)
        assertTrue(HttpJson.failure(RuntimeException(long), null).message.length <= 300)
    }

    @Test fun aFileIsReadOnlyUpToItsLimit() {
        assertArrayEquals(ByteArray(10) { 1 }, ByteArrayInputStream(ByteArray(10) { 1 }).readAtMost(10))
        assertNull(ByteArrayInputStream(ByteArray(11)).readAtMost(10))
    }

    @Test fun aMessageFarLongerThanABankAlertIsSkipped() {
        val body = "Rs 500 debited from a/c XX1234 to SWIGGY. " + "x".repeat(SmsParser.MAX_BODY)
        val r = SmsParser().parse(SmsMessage("VM-HDFCBK-S", body, 0L))
        assertEquals(SmsParser.TOO_LONG, (r as ParseResult.Ignored).reason)
    }

    @Test fun aLongRunOfXsDoesNotStallTheAccountPattern() {
        assertEquals("1234", AccountExtractor.extract("Rs 500 debited from a/c XX1234 on 12-Oct"))
        assertEquals("5678", AccountExtractor.extract("Card ending 5678 used"))
        val started = System.nanoTime()
        AccountExtractor.extract("ac" + "x".repeat(20_000))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000)
    }
}
