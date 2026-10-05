package com.pft.financetracker.data.networth

import com.pft.financetracker.data.bills.BillService
import com.pft.financetracker.data.local.AccountBalanceEntity
import com.pft.financetracker.data.local.AssetEntity
import com.pft.financetracker.data.local.HoldingEntity
import com.pft.financetracker.data.local.NetWorthDao
import com.pft.financetracker.data.local.NetWorthSnapshotEntity
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.domain.networth.AccountBalance
import com.pft.financetracker.domain.networth.Asset
import com.pft.financetracker.domain.networth.AssetKind
import com.pft.financetracker.domain.networth.Holding
import com.pft.financetracker.domain.networth.NetWorth
import com.pft.financetracker.domain.networth.NetWorthSummary
import java.time.LocalDate
import java.time.YearMonth

/** Net worth from hand-typed assets and debts, bank balances in SMS, CAS holdings and loans set up as bills. */
class NetWorthService(private val dao: NetWorthDao, private val bills: BillService) {

    suspend fun saveAsset(a: Asset): Long = dao.upsertAsset(a.toEntity())
    suspend fun deleteAsset(id: Long) = dao.deleteAsset(id)

    /** Keeps the balance from the newest message, whatever order messages are read in. */
    suspend fun recordBalance(accountRef: String, bankName: String?, balancePaise: Long, at: Long) {
        val old = dao.getBalance(accountRef)
        if (old == null || old.at <= at) dao.upsertBalance(AccountBalanceEntity(accountRef, bankName, balancePaise, at))
    }

    suspend fun replaceHoldings(h: List<Holding>) {
        dao.clearHoldings()
        dao.insertHoldings(h.map { HoldingEntity(folio = it.folio, scheme = it.scheme, valuePaise = it.valuePaise, asOfDay = it.asOf.toEpochDay()) })
    }

    suspend fun summary(today: LocalDate = LocalDate.now()): NetWorthSummary =
        summaryOf(dao.getAssets(), dao.getBalances(), dao.getHoldings(), bills.all(), today)

    fun summaryOf(assets: List<AssetEntity>, balances: List<AccountBalanceEntity>, holdings: List<HoldingEntity>, loans: List<Bill>, today: LocalDate): NetWorthSummary =
        NetWorth.compute(
            assets.map { it.toDomain() },
            balances.map { AccountBalance(it.accountRef, it.bankName, it.balancePaise, it.at) },
            holdings.map { Holding(it.folio, it.scheme, it.valuePaise, LocalDate.ofEpochDay(it.asOfDay)) },
            loans.sumOf { BillTracker.loanProgress(it, today)?.outstandingPaise ?: 0L },
        )

    /** Records this month's figure (overwriting earlier ones from the same month), for the history line. */
    suspend fun snapshot(today: LocalDate = LocalDate.now()) {
        val s = summary(today)
        dao.upsertSnapshot(NetWorthSnapshotEntity(YearMonth.from(today).toString(), s.totalPaise, s.ownPaise, s.owePaise))
    }

    suspend fun history(): List<NetWorthSnapshotEntity> = dao.getSnapshots()
}

fun AssetEntity.toDomain() = Asset(id, name, AssetKind.fromName(kind), valuePaise, liability, accountRef)
fun Asset.toEntity() = AssetEntity(id = id, name = name, kind = kind.name, valuePaise = valuePaise, liability = liability, accountRef = accountRef)
