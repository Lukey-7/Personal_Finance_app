package com.pft.financetracker.domain.split

/**
 * The per-person parts of a new split's form, kept in step with the people list. Index 0 is me and can't be removed.
 * [itemAssignments] holds, for each item on the bill, the indices of the people tagged on it.
 */
data class PeopleDraft(
    val people: List<String>,
    val shareWeights: List<String>,
    val customAmounts: List<String>,
    val itemAssignments: List<Set<Int>>,
    val payer: Int,
) {
    /**
     * Remove person [index]: their shares weight and typed amount go with them, everyone after them keeps theirs, item
     * tags move down to follow the people they belong to, and when the removed person had paid, I did.
     */
    fun remove(index: Int): PeopleDraft {
        if (index <= 0 || index >= people.size) return this
        return PeopleDraft(
            people = people.withoutIndex(index),
            shareWeights = shareWeights.withoutIndex(index),
            customAmounts = customAmounts.withoutIndex(index),
            itemAssignments = itemAssignments.map { tags -> tags.filter { it != index }.map { if (it > index) it - 1 else it }.toSet() },
            payer = when {
                payer == index -> 0
                payer > index -> payer - 1
                else -> payer
            },
        )
    }

    private fun <T> List<T>.withoutIndex(i: Int): List<T> = filterIndexed { j, _ -> j != i }
}
