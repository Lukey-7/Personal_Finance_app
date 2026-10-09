package com.pft.financetracker.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.pft.financetracker.MainActivity
import com.pft.financetracker.appContainer
import com.pft.financetracker.data.bills.toDomain
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.widget.WidgetSnapshot
import com.pft.financetracker.domain.widget.WidgetText
import java.time.LocalDate

/**
 * Home-screen widget: spend so far this month, budget left and the next bill, plus one-tap buttons to note a purchase.
 * Two sizes: small shows only the month's figure; medium adds the budget and bill lines and the Add / Cash actions.
 * Amounts are hidden unless the person turns that off in Settings, because a home screen is seen by anyone nearby.
 */
class FinTrackWidget : GlanceAppWidget() {
    /** Two layouts: the figure alone when small, the whole card with its two actions from four cells wide. */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val text = runCatching { load(context) }.getOrElse { WidgetText("₹••••", null, null) }
        provideContent { Content(context, text) }
    }

    private suspend fun load(context: Context): WidgetText {
        val c = context.appContainer
        val txns = c.db.transactionDao().getAll().map { it.toDomain() }
        val marks = c.db.billDao().allMarks().groupBy({ it.billId }, { LocalDate.ofEpochDay(it.dueDay) })
        val bills = c.bills.statesOf(c.db.billDao().getAll().map { it.toDomain() }, txns, marks, LocalDate.now())
        return WidgetSnapshot.build(txns, c.budgets.getAll(), bills, c.settings.widgetHideAmounts.value,
            com.pft.financetracker.domain.books.CountingRules(cashIsSpend = c.settings.countCashAsSpend.value))
    }

    @Composable
    private fun Content(context: Context, t: WidgetText) {
        // In Responsive mode LocalSize is the size bucket Glance is drawing for, not the exact widget size.
        val size = LocalSize.current
        val medium = size.width >= MEDIUM.width && size.height >= MEDIUM.height
        Box(
            GlanceModifier.fillMaxSize().background(day = Surface, night = SurfaceNight).cornerRadius(24.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            if (medium) Medium(context, t) else Small(t)
        }
    }

    /** Two cells: the label and the figure, nothing else. */
    @Composable
    private fun Small(t: WidgetText) {
        Column(
            GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Spent this month", style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(t.spent, style = TextStyle(color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    /** Four cells: the figure, budget left and the next bill on the left; Add and Cash stacked on the right. */
    @Composable
    private fun Medium(context: Context, t: WidgetText) {
        Row(
            GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text("Spent this month", style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(t.spent, style = TextStyle(color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                t.budgetLine?.let {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(it, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 2)
                }
                t.nextBill?.let { Text("Next: $it", style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1) }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column {
                Action(context, "+ Add", "Add a payment", cash = false)
                Spacer(GlanceModifier.height(8.dp))
                Action(context, "+ Cash", "Add cash you spent", cash = true)
            }
        }
    }

    /** A pill-shaped action 48dp tall, so it is easy to hit on a home screen. Opens the quick-add sheet. */
    @Composable
    private fun Action(context: Context, label: String, spoken: String, cash: Boolean) {
        Box(
            GlanceModifier.width(96.dp).height(48.dp).background(day = AccentSoft, night = AccentSoftNight).cornerRadius(24.dp)
                .clickable(actionStartActivity(QuickAddActivity.intent(context, cash = cash)))
                .semantics { contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) { Text(label, style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp), maxLines = 1) }
    }

    companion object {
        /** Two cells (2x1 or 2x2): "Spent this month" and the figure. */
        val SMALL = DpSize(110.dp, 48.dp)

        /** Four cells wide and tall enough for two stacked 48dp actions: the full card. */
        val MEDIUM = DpSize(250.dp, 128.dp)

        /** Redraw every placed widget; cheap when none is placed. */
        suspend fun refresh(context: Context) { runCatching { FinTrackWidget().updateAll(context) } }
    }
}

// The app's "Quiet ledger" tokens, light and night. res/values(-night)/widget_colors.xml holds the same values for the
// picker preview.
private val Surface = Color(0xFFFFFFFF)
private val SurfaceNight = Color(0xFF151618)
private val AccentSoft = Color(0xFFEBEEFC)
private val AccentSoftNight = Color(0xFF1E2340)
private val ink = ColorProvider(day = Color(0xFF111214), night = Color(0xFFF2F2F3))
private val muted = ColorProvider(day = Color(0xFF62666E), night = Color(0xFFA3A7AE))
private val accent = ColorProvider(day = Color(0xFF1F3BD6), night = Color(0xFF9DA8FF))

class FinTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinTrackWidget()
}
