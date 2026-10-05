package com.pft.financetracker.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pft.financetracker.MainActivity
import com.pft.financetracker.appContainer
import com.pft.financetracker.data.bills.toDomain
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.widget.WidgetSnapshot
import com.pft.financetracker.domain.widget.WidgetText
import java.time.LocalDate

/**
 * Home-screen widget: spend so far this month, budget left and the next bill, plus one-tap buttons to note a purchase.
 * Amounts are hidden unless the person turns that off in Settings, because a home screen is seen by anyone nearby.
 */
class FinTrackWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val text = runCatching { load(context) }.getOrElse { WidgetText("₹••••", null, null) }
        provideContent { Content(context, text) }
    }

    private suspend fun load(context: Context): WidgetText {
        val c = context.appContainer
        val txns = c.db.transactionDao().getAll().map { it.toDomain() }
        val marks = c.db.billDao().allMarks().groupBy({ it.billId }, { LocalDate.ofEpochDay(it.dueDay) })
        val bills = c.bills.statesOf(c.db.billDao().getAll().map { it.toDomain() }, txns, marks, LocalDate.now())
        return WidgetSnapshot.build(txns, c.budgets.getAll(), bills, c.settings.widgetHideAmounts.value, c.settings.countCashAsSpend.value)
    }

    @Composable
    private fun Content(context: Context, t: WidgetText) {
        val ink = ColorProvider(Color(0xFF14171A))
        val soft = ColorProvider(Color(0xFF5B6168))
        val accent = ColorProvider(Color(0xFF0E4F3E))
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFFFFFFFF)).cornerRadius(20.dp).padding(16.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            Text("Spent this month", style = TextStyle(color = soft, fontSize = 12.sp))
            Text(t.spent, style = TextStyle(color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
            t.budgetLine?.let { Text(it, style = TextStyle(color = soft, fontSize = 12.sp)) }
            t.nextBill?.let { Text("Next: $it", style = TextStyle(color = soft, fontSize = 12.sp)) }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("+ Expense", style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity(QuickAddActivity.intent(context, cash = false))).padding(4.dp))
                Spacer(GlanceModifier.width(16.dp))
                Text("+ Cash", style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity(QuickAddActivity.intent(context, cash = true))).padding(4.dp))
            }
        }
    }

    companion object {
        /** Redraw every placed widget; cheap when none is placed. */
        suspend fun refresh(context: Context) { runCatching { FinTrackWidget().updateAll(context) } }
    }
}

class FinTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinTrackWidget()
}
