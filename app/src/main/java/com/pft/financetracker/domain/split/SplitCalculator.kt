package com.pft.financetracker.domain.split

/**
 * Pure integer maths for splitting a bill. All amounts are paise so parts always add up exactly.
 * Rounding remainders (e.g. ₹100 / 3) go to the payer, so nobody pays a paisa more than their share
 * except the person who already has the money in hand.
 */
object SplitCalculator {

    fun equal(totalPaise: Long, people: Int, payerIndex: Int): SplitResult {
        require(people > 0)
        val base = totalPaise / people
        val remainder = totalPaise - base * people
        val shares = (0 until people).map { i -> PersonShare(i, base + if (i == payerIndex) remainder else 0L) }
        return SplitResult(totalPaise, shares)
    }

    /** [shares] like [2, 1, 1] -> first person pays half. */
    fun byShares(totalPaise: Long, shares: List<Int>, payerIndex: Int): SplitResult {
        require(shares.isNotEmpty() && shares.all { it >= 0 } && shares.sum() > 0)
        return proportional(totalPaise, shares.map { it.toLong() }, payerIndex)
    }

    /** Custom amounts must add up to the total; the caller shows the difference until they do. */
    fun custom(totalPaise: Long, amounts: List<Long>): SplitResult {
        require(amounts.sumOf { it } == totalPaise) { "custom amounts must sum to total" }
        return SplitResult(totalPaise, amounts.mapIndexed { i, a -> PersonShare(i, a) })
    }

    fun customDifference(totalPaise: Long, amounts: List<Long>): Long = totalPaise - amounts.sumOf { it }

    /**
     * Per-item split. Each item's cost is divided equally among the people assigned to it (unassigned
     * items are shared by everyone). Tax, service, tip and discount are spread in proportion to each
     * person's item subtotal, so someone who ordered 40% of the food pays 40% of the tax.
     */
    fun byItem(items: List<BillItem>, extras: BillExtras, people: Int, payerIndex: Int): SplitResult {
        require(people > 0)
        // Divide each item exactly, keeping the leftover paise aside rather than handing them to the
        // payer item by item - doing that per item is what turned a clean 990/3 into 330.04 / 329.98 /
        // 329.98. The pooled remainder is settled once at the end.
        val itemSubtotals = LongArray(people)
        var pooledRemainder = 0L
        for (item in items) {
            val cost = item.pricePaise * item.quantity
            val who = item.assignedTo.filter { it in 0 until people }.ifEmpty { (0 until people).toList() }
            val each = Math.floorDiv(cost, who.size.toLong())
            who.forEach { p -> itemSubtotals[p] += each }
            pooledRemainder += cost - each * who.size
        }
        val itemsTotal = itemSubtotals.sum()
        val total = itemsTotal + pooledRemainder + extras.netPaise
        if (itemsTotal == 0L) return equal(total, people, payerIndex)

        // Extras (tax, service, tip, less discount) ride along in proportion to what each person ate,
        // and the pooled remainder rides with them, so the parts still add up to the exact total.
        val extrasSplit = proportional(extras.netPaise + pooledRemainder, itemSubtotals.toList(), payerIndex)
        val shares = (0 until people).map { i -> PersonShare(i, itemSubtotals[i] + extrasSplit.shares[i].amountPaise) }
        return SplitResult(total, shares)
    }

    /** Divide [totalPaise] in proportion to [weights]; remainder to payer. Works for negative totals too. */
    fun proportional(totalPaise: Long, weights: List<Long>, payerIndex: Int): SplitResult {
        val sum = weights.sumOf { it }
        if (sum == 0L) return equal(totalPaise, weights.size, payerIndex)
        val raw = weights.map { w -> Math.floorDiv(totalPaise * w, sum) }
        val remainder = totalPaise - raw.sumOf { it }
        val payer = payerIndex.coerceIn(0, weights.lastIndex)
        return SplitResult(totalPaise, raw.mapIndexed { i, a -> PersonShare(i, a + if (i == payer) remainder else 0L) })
    }

    /** Net balances across open splits from "my" point of view: positive = owed to me. */
    fun balances(splits: List<Split>): List<Balance> {
        val net = linkedMapOf<String, Long>()
        for (s in splits) {
            val me = s.myIndex
            if (me < 0) continue
            for (share in s.shares) {
                if (share.personIndex == s.payerIndex) continue
                val remaining = share.remainingPaise
                if (remaining <= 0) continue
                val name = s.people[share.personIndex].name
                if (s.payerIndex == me) {
                    // they owe me
                    net[name] = (net[name] ?: 0L) + remaining
                } else if (share.personIndex == me) {
                    val payer = s.people[s.payerIndex].name
                    net[payer] = (net[payer] ?: 0L) - remaining
                }
            }
        }
        return net.filter { it.value != 0L }.map { Balance(it.key, it.value) }.sortedByDescending { it.netPaise }
    }
}
