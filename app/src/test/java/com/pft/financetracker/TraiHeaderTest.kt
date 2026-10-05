package com.pft.financetracker

import com.pft.financetracker.domain.parser.BankExtractor
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SenderId
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TRAI (2025) adds -S / -T / -P / -G to sender IDs; some phones show them with, some without, the operator prefix. */
class TraiHeaderTest {
    private val body = "Rs.450.00 debited from a/c **1234 on 03-10-26 to VPA swiggy@ybl (UPI Ref No 426212345678)."

    @Test fun headerCoreIgnoresOperatorPrefixAndTraiSuffix() {
        assertEquals("HDFCBK", SenderId.core("VM-HDFCBK-S"))
        assertEquals("HDFCBK", SenderId.core("HDFCBK-S"))
        assertEquals("HDFCBK", SenderId.core("VM-HDFCBK"))
        assertEquals("HDFCBK", SenderId.core("HDFCBK"))
        assertEquals("ICICIT", SenderId.core("AX-ICICIT-T"))
        assertEquals("SBIUPI", SenderId.core("JK-SBIUPI-G"))
    }

    @Test fun suffixIsRead() {
        assertEquals('S', SenderId.suffix("VM-HDFCBK-S"))
        assertEquals('P', SenderId.suffix("HDFCBK-P"))
        assertEquals(null, SenderId.suffix("VM-HDFCBK"))
    }

    @Test fun bankIsFoundWithoutTheOperatorPrefix() {
        assertEquals("HDFC Bank", BankExtractor.extract("HDFCBK-S", "Rs 100 debited"))
        assertEquals("Kotak", BankExtractor.extract("KOTAKB-T", "Rs 100 debited"))
    }

    @Test fun anUnknownSenderIsNamedByItsCoreNotItsSuffix() {
        assertEquals("ZETABK", BankExtractor.extract("ZETABK-S", "Rs 100 debited from your account"))
    }

    @Test fun serviceAndTransactionalSendersAreParsed() {
        for (s in listOf("VM-HDFCBK-S", "HDFCBK-T", "VM-HDFCBK")) {
            assertTrue(s, SmsParser().parse(SmsMessage(s, body, 1_760_000_000_000L)) is ParseResult.Success)
        }
    }

    @Test fun promotionalSendersAreIgnored() {
        val r = SmsParser().parse(SmsMessage("VM-HDFCBK-P", body, 1_760_000_000_000L))
        assertEquals("promotional_sender", (r as ParseResult.Ignored).reason)
    }

    @Test fun everyMajorBankIsNamedWithAnySuffix() {
        val headers = mapOf("HDFCBK" to "HDFC Bank", "ICICIT" to "ICICI Bank", "SBIINB" to "SBI", "AXISBK" to "Axis Bank", "KOTAKB" to "Kotak",
            "PNBSMS" to "PNB", "BOBTXN" to "Bank of Baroda", "CANBNK" to "Canara Bank", "UNIONB" to "Union Bank", "IDFCFB" to "IDFC First",
            "YESBNK" to "Yes Bank", "INDUSB" to "IndusInd")
        for ((core, bank) in headers) for (h in listOf("VM-$core-S", "$core-T", "AD-$core", "JK-$core-G")) {
            assertEquals(h, bank, BankExtractor.extract(h, "Rs 100 debited from your account"))
        }
    }
}
