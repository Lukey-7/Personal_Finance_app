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
import androidx.glance.appwidget.background
import androidx.glance.layout.Box
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
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as UnitColorProvider
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
        // The app's own tokens, with a night variant: Buro blue for actions, never the old green.
        val ink = ColorProvider(day = Color(0xFF101112), night = Color(0xFFF2F2F3))
        val soft = ColorProvider(day = Color(0xFF6A6F77), night = Color(0xFFA8ABB0))
        val accent = ColorProvider(day = Color(0xFF0000FF), night = Color(0xFF9DA8FF))
        Column(
            GlanceModifier.fillMaxSize().background(day = Color(0xFFFFFFFF), night = Color(0xFF121314)).cornerRadius(22.dp).padding(16.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Spent this month", style = TextStyle(color = soft, fontSize = 12.sp))
            Text(t.spent, style = TextStyle(color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
            t.budgetLine?.let { Text(it, style = TextStyle(color = soft, fontSize = 12.sp)) }
            t.nextBill?.let { Text("Next: $it", style = TextStyle(color = soft, fontSize = 12.sp)) }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Action(context, "+ Expense", cash = false, accent)
                Spacer(GlanceModifier.width(8.dp))
                Action(context, "+ Cash", cash = true, accent)
            }
        }
    }

    /** A pill-shaped action at least 48dp tall, so it is easy to hit on a home screen. */
    @Composable
    private fun Action(context: Context, label: String, cash: Boolean, accent: UnitColorProvider) {
        Box(
            GlanceModifier.height(48.dp).background(day = Color(0xFFF0EFFB), night = Color(0xFF1B1F3B)).cornerRadius(24.dp)
                .padding(horizontal = 16.dp)
                .clickable(actionStartActivity(QuickAddActivity.intent(context, cash = cash))),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)) }
    }

    companion object {
        /** Redraw every placed widget; cheap when none is placed. */
        suspend fun refresh(context: Context) { runCatching { FinTrackWidget().updateAll(context) } }
    }
}

class FinTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinTrackWidget()
}
