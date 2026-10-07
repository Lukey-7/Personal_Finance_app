package com.pft.financetracker.ui.model

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType

/** The quick-add number pad's rules. [press] returns null for a key that cannot extend the amount, so the pad can buzz. */
object AmountKeys {
    private const val MAX_RUPEE_DIGITS = 8

    fun press(text: String, key: Char): String? {
        val dot = text.indexOf('.')
        return when {
            key == '.' -> when {
                dot >= 0 -> null
                text.isEmpty() -> "0."
                else -> "$text."
            }
            !key.isDigit() -> null
            dot >= 0 -> if (text.length - dot - 1 >= 2) null else text + key
            text.isEmpty() && key == '0' -> null
            text == "0" -> key.toString()
            text.length >= MAX_RUPEE_DIGITS -> null
            else -> text + key
        }
    }

    fun backspace(text: String): String = text.dropLast(1).let { if (it.endsWith('.')) it.dropLast(1) else it }
}

/** What the quick-add sheet holds until Save. */
data class QuickAddDraft(
    val amount: String = "",
    val category: Category? = null,
    val note: String = "",
    val cash: Boolean = false,
) {
    val paise: Long? get() = Money.parsePaise(amount)?.takeIf { it > 0 }
    val canSave: Boolean get() = paise != null && category != null

    /**
     * The row Save writes: a manual expense named by the note, or by the category when there is none. A cash purchase
     * already paid for by an ATM withdrawal that counts as spend is a move within your own money, so it is not counted
     * twice.
     */
    fun toTransaction(now: Long, cashCounted: Boolean): Transaction? {
        val p = paise ?: return null
        val c = category ?: return null
        return Transaction(
            amountPaise = p, type = TransactionType.DEBIT, merchant = note.trim().ifBlank { c.label }, category = c,
            timestamp = now, bankName = if (cash) "Cash" else null, accountRef = null, source = Transaction.Source.MANUAL,
            flow = if (cash && cashCounted) Flow.TRANSFER else Flow.EXPENSE, note = if (cash) "Paid in cash" else null,
        )
    }
}

/** The quick-add category grid: the ones you added by hand most recently first, then the rest in the usual order. */
object RecentCategories {
    val choices: List<Category> = Category.entries.filter { it != Category.INCOME && it != Category.TRANSFER && it != Category.INVESTMENT }

    fun order(txns: List<Transaction>): List<Category> {
        val recent = txns.asSequence().filter { it.source == Transaction.Source.MANUAL && it.category in choices }
            .sortedByDescending { it.timestamp }.map { it.category }.distinct().toList()
        return recent + (choices - recent.toSet())
    }
}
