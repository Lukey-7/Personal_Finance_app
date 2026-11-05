package com.pft.financetracker

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CategoryChoice
import com.pft.financetracker.domain.model.CustomCategory
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.components.LocalCustomCategories
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.screens.dashboard.Tiles
import com.pft.financetracker.ui.screens.transactions.AlsoMove
import com.pft.financetracker.ui.screens.transactions.AlsoMoveDialog
import com.pft.financetracker.ui.screens.transactions.CategoryPickerSheet
import com.pft.financetracker.ui.theme.FinTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.6 screen pieces, drawn for real: the Savings tile, rows and pickers with the person's own categories, the "move earlier ones" prompt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ScreenPiecesTest {
    @get:Rule val rule = createComposeRule()

    private val pets = mapOf(3L to CustomCategory(3, "Pets", Category.SHOPPING))
    private val month = Periods.month()

    private fun pay(id: Long, merchant: String, paise: Long, type: TransactionType = TransactionType.DEBIT, custom: Long? = null) = Transaction(
        id = id, amountPaise = paise, type = type, merchant = merchant, category = if (type == TransactionType.DEBIT) Category.SHOPPING else Category.INCOME,
        timestamp = month.start + id * 60_000L, bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS,
        flow = if (type == TransactionType.DEBIT) Flow.EXPENSE else Flow.INCOME, customCategoryId = custom,
    )

    @Test fun savingsWithNoIncomeSaysSoInsteadOfARedMinus() {
        val summary = Books.of(listOf(pay(1, "Amazon", 80_000))).summary(month)
        rule.setContent { FinTrackTheme(darkTheme = false) { Tiles(summary) { _, _ -> } } }
        rule.onNodeWithText("No income yet").assertExists()
        rule.onNodeWithText("-₹800", substring = true).assertDoesNotExist()
    }

    @Test fun savingsWithIncomeShowsTheFigure() {
        val summary = Books.of(listOf(pay(1, "Amazon", 80_000), pay(2, "Acme salary", 1_00_000, TransactionType.CREDIT))).summary(month)
        rule.setContent { FinTrackTheme(darkTheme = false) { Tiles(summary) { _, _ -> } } }
        rule.onNodeWithText("No income yet").assertDoesNotExist()
    }

    @Test fun aRowShowsTheirOwnCategoryName() {
        rule.setContent {
            FinTrackTheme(darkTheme = false) {
                CompositionLocalProvider(LocalCustomCategories provides pets) { TransactionRow(pay(1, "Supertails", 50_000, custom = 3)) {} }
            }
        }
        rule.onNodeWithText("Pets", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun thePickerOffersTheirOwnCategoriesAndReturnsTheChoice() {
        var picked: CategoryChoice? = null
        rule.setContent {
            FinTrackTheme(darkTheme = false) {
                CompositionLocalProvider(LocalCustomCategories provides pets) {
                    CategoryPickerSheet("Move to a category", CategoryChoice(Category.SHOPPING), onPick = { picked = it }, onDismiss = {})
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Pets").performSemanticsAction(SemanticsActions.OnClick)
        repeat(3) { rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle() }
        assertEquals(CategoryChoice(Category.SHOPPING, 3), picked)
    }

    @Test fun theMovePromptNamesTheCountAndMovesOnlyWhenAsked() {
        var moved = 0
        var closed = 0
        rule.setContent {
            FinTrackTheme(darkTheme = false) {
                CompositionLocalProvider(LocalCustomCategories provides pets) {
                    AlsoMoveDialog(AlsoMove("Supertails", CategoryChoice(Category.SHOPPING, 3), setOf(4L, 5L)), onMove = { moved++ }, onDismiss = { closed++ })
                }
            }
        }
        rule.onNodeWithText("Move 2 earlier payments too?").assertExists()
        rule.onNodeWithText("Pets", substring = true).assertExists()
        rule.onNodeWithText("Just this one").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(0, moved); assertEquals(1, closed)
        rule.onNodeWithText("Move them").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, moved)
    }
}
