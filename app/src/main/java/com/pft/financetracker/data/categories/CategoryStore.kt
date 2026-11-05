package com.pft.financetracker.data.categories

import com.pft.financetracker.data.local.BudgetDao
import com.pft.financetracker.data.local.CustomCategoryDao
import com.pft.financetracker.data.local.CustomCategoryEntity
import com.pft.financetracker.data.local.PayeeRuleDao
import com.pft.financetracker.data.local.PayeeRuleEntity
import com.pft.financetracker.domain.categorize.PayeeRules
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CategoryChoice
import com.pft.financetracker.domain.model.CustomCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The person's own categories and the category they gave each payee, kept in the encrypted database and mirrored in
 * memory ([rules]) so the SMS importer can read them without touching the disk for every message. [load] at app start;
 * every change here keeps the mirror current.
 */
class CategoryStore(
    private val customDao: CustomCategoryDao,
    private val ruleDao: PayeeRuleDao,
    private val budgetDao: BudgetDao,
) {
    @Volatile var rules: PayeeRules = PayeeRules.NONE
        private set

    val customs: Flow<Map<Long, CustomCategory>> = customDao.observeAll().map { list -> list.toDomain() }

    /** Every payee rule as (payee key, the choice it files to), for Settings › Categories. */
    val payeeRules: Flow<List<Pair<String, CategoryChoice>>> = combine(ruleDao.observeAll(), customs) { rules, customs ->
        rules.map { it.payeeKey to CategoryChoice.fromKey(it.category, customs) }
    }

    suspend fun load() {
        val customs = customDao.getAll().toDomain()
        rules = PayeeRules(ruleDao.getAll().associate { it.payeeKey to it.category }, customs)
    }

    suspend fun customsNow(): Map<Long, CustomCategory> = customDao.getAll().toDomain()

    /** Remembers [choice] for [merchant]'s next payments. A placeholder name ("Payment (HDFC Bank)") teaches nothing. */
    suspend fun learn(merchant: String, choice: CategoryChoice) {
        val key = PayeeRules.keyOf(merchant) ?: return
        ruleDao.upsert(PayeeRuleEntity(payeeKey = key, category = choice.key))
        load()
    }

    suspend fun forget(payeeKey: String) {
        ruleDao.delete(payeeKey)
        load()
    }

    /** Adds a category of the person's own; null when [name] is blank or already taken (ignoring case). */
    suspend fun add(name: String, parent: Category): Long? {
        val clean = name.trim().take(NAME_MAX)
        if (!nameFree(clean, null)) return null
        return customDao.insert(CustomCategoryEntity(name = clean, parent = parent.name)).also { load() }
    }

    /** Renames [id] or changes what it counts as; false when the new name is blank or taken. */
    suspend fun update(id: Long, name: String, parent: Category): Boolean {
        val clean = name.trim().take(NAME_MAX)
        val existing = customDao.getAll().firstOrNull { it.id == id } ?: return false
        if (!nameFree(clean, id)) return false
        customDao.update(existing.copy(name = clean, parent = parent.name))
        load()
        return true
    }

    /**
     * Deletes [id]: its payments go back to the built-in it counted as, and its budget and payee rules go with it. Run
     * inside one database transaction by the caller, so a failure leaves the category whole.
     */
    suspend fun delete(id: Long) {
        val key = CategoryChoice(Category.OTHER, id).key
        customDao.releasePayments(id)
        budgetDao.delete(key)
        ruleDao.deleteForCategory(key)
        customDao.delete(id)
        load()
    }

    private suspend fun nameFree(name: String, exceptId: Long?): Boolean {
        if (name.isEmpty()) return false
        if (Category.entries.any { it.label.equals(name, ignoreCase = true) }) return false
        return customDao.getAll().none { it.id != exceptId && it.name.equals(name, ignoreCase = true) }
    }

    companion object {
        const val NAME_MAX = 30

        fun List<CustomCategoryEntity>.toDomain(): Map<Long, CustomCategory> =
            associate { it.id to CustomCategory(it.id, it.name, Category.fromName(it.parent)) }
    }
}
