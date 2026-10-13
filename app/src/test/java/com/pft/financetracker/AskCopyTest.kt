package com.pft.financetracker

import com.pft.financetracker.ui.model.askSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Ask tile never says "on this phone" while questions go to a provider. */
class AskCopyTest {
    @Test fun saysWhoAnswersWhenAProviderIsOn() {
        assertEquals("Answered by ChatGPT", askSource(online = true, assistantName = "ChatGPT"))
        assertEquals("Answered by Claude", askSource(online = true, assistantName = "Claude"))
    }

    @Test fun saysThisPhoneOnlyWhenNothingIsSent() {
        assertEquals("Answered on this phone", askSource(online = false, assistantName = "ChatGPT"))
    }
}
