package com.pft.financetracker

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.FinSheetControl
import com.pft.financetracker.ui.components.rememberFinSheet
import com.pft.financetracker.ui.components.PrimaryButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A sheet closed from one of its own buttons slides away first and only then tells the caller, so its window is never
 * pulled out while it still holds focus (the "no focused window" freeze). Letting go of the keyboard is checked on the
 * emulator: Robolectric does not move focus inside a sheet's own window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FinSheetTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var sheet: FinSheetControl
    private var open by mutableStateOf(true)
    private var calls = 0
    private val visibleWhenCalled = mutableListOf<Boolean>()

    private fun showSheet() {
        rule.setContent {
            sheet = rememberFinSheet()
            if (open) FinSheet({ open = false }, title = "Restore a backup", sheet = sheet) {
                TextField("", {}, label = { Text("Passphrase") }, modifier = Modifier.testTag("field"))
                PrimaryButton("Restore", {
                    sheet.close {
                        calls++
                        visibleWhenCalled += sheet.state.isVisible
                        open = false
                    }
                })
            }
        }
        rule.waitForIdle()
    }

    /** Lets the slide-away run to the end on the test clock. */
    private fun settle() {
        rule.mainClock.autoAdvance = true
        repeat(3) { rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle() }
    }

    @Test fun closeRunsTheCallerOnlyOnceTheSheetHasSlidAway() {
        showSheet()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Restore").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeByFrame()
        assertTrue("the button reached close", sheet.isClosing)
        assertEquals("nothing happens while the sheet is still on screen", 0, calls)
        settle()
        assertEquals(1, calls)
        assertEquals(listOf(false), visibleWhenCalled)
        assertFalse(open)
        rule.onNodeWithText("Restore a backup").assertDoesNotExist()
    }

    @Test fun aSecondTapWhileClosingDoesNothing() {
        showSheet()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Restore").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Restore").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals(1, calls)
    }

    @Test fun aSheetDroppedAtOnceStillLeavesCleanly() {
        showSheet()
        rule.onNodeWithTag("field").performClick()
        open = false
        rule.waitForIdle()
        rule.onNodeWithText("Restore a backup").assertDoesNotExist()
    }
}
