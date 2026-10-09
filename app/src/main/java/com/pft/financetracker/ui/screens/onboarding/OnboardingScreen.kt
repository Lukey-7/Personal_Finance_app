package com.pft.financetracker.ui.screens.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.ImportUiState
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CardShape
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.scanResultLine
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.motion
import com.pft.financetracker.ui.theme.reducedMotion
import com.pft.financetracker.ui.theme.surfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val StepCount = 3

/**
 * First run: three short steps (what FinTrack does, why it reads SMS, the privacy promise), then the SMS permission.
 * Allowed: the first scan runs right here with live progress, and Home opens once it has finished. Declined or skipped:
 * straight to Home. Onboarding is marked done only as Home opens, because flipping it earlier swaps the app's start
 * screen and would cut the first scan off.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            vm.setOnboarded(true)
            onDone()
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.READ_SMS] == true
        if (granted) {
            vm.scanInbox(full = true)
            scanning = true
        } else finish()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Crossfade(targetState = scanning, animationSpec = motion(Motion.effects<Float>()), label = "onboarding") { isScanning ->
            if (isScanning) FirstScan(vm, onFinish = finish)
            else Steps(
                onAllow = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) },
                onSkip = finish,
            )
        }
    }
}

/* ---------------------------------------------------------------- The three steps ---- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Steps(onAllow: () -> Unit, onSkip: () -> Unit) {
    val pager = rememberPagerState(pageCount = { StepCount })
    val scope = rememberCoroutineScope()
    val reduced = reducedMotion
    val goTo: (Int) -> Unit = { page ->
        scope.launch { if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page, animationSpec = Motion.effects()) }
    }
    BackHandler(enabled = pager.currentPage > 0) { goTo(pager.currentPage - 1) }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            StepPage(page, active = pager.currentPage == page)
        }
        PageDots(StepCount, pager.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(vertical = Space.md))
        Column(
            Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (pager.currentPage < StepCount - 1) {
                PrimaryButton("Next", { goTo(pager.currentPage + 1) })
                TextAction("Skip the intro", { goTo(StepCount - 1) }, Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Allow SMS and import", onAllow)
                TextAction("Skip – I'll add them myself", onSkip, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StepPage(page: Int, active: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, top = Space.xl, bottom = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Illustration(page, active)
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            CapsLabel("Step ${page + 1} of $StepCount")
            Text(
                when (page) {
                    0 -> "Your money, read from your bank SMS"
                    1 -> "Why FinTrack asks for SMS"
                    else -> "Private by design"
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (page) {
            0 -> Lead(
                "FinTrack turns the bank, UPI and card alerts you already get into a tidy ledger: what you spent, where it " +
                    "went and how it compares. No typing, no account.",
            )
            1 -> {
                Lead("To log payments for you, FinTrack reads your messages. Here is exactly what that means.")
                CapsLabel("Read")
                Point(Icons.Outlined.CheckCircle, "Bank, UPI and card alerts from sender IDs like VM-HDFCBK")
                Point(Icons.Outlined.CheckCircle, "Only the amount, merchant, date and last 4 account digits are kept. The message text isn't, unless you choose to review it.")
                CapsLabel("Skipped")
                Point(Icons.Outlined.Block, "One-time codes (OTPs) and offers", muted = true)
                Point(Icons.Outlined.Block, "Personal messages from phone numbers", muted = true)
                Text(
                    "You can turn SMS access off any time; the app keeps working with manual entry.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Lead("Your money is nobody else's business, so FinTrack is built to keep it that way.")
                Point(Icons.Outlined.Lock, "Encrypted, in a database on this phone")
                Point(Icons.Outlined.PersonOff, "No account to create")
                Point(Icons.Outlined.CloudOff, "No cloud sync, no analytics, no tracking")
                Point(Icons.Outlined.AutoAwesome, "Ask answers with ChatGPT, using the built-in key or your own; turn it off in Settings")
            }
        }
    }
}

@Composable
private fun Lead(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One promise or fact: an outline glyph and a sentence. The words carry the meaning; the glyph only echoes it. */
@Composable
private fun Point(icon: ImageVector, text: String, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Where you are in the steps: the current dot stretches into a pill. Read as "Step 2 of 3". */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val s = surfaces
    Row(
        modifier.heightIn(min = 24.dp).semantics(mergeDescendants = true) { contentDescription = "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            val w by animateDpAsState(if (on) 24.dp else 8.dp, motion(Motion.spatialFast()), label = "dot")
            Box(
                Modifier.width(w).height(8.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else s.outline)
            )
        }
    }
}

/* ---------------------------------------------------------------- The first scan ---- */

/**
 * The first scan, live: progress while the bank messages are read, then the result line and a short pause before Home.
 * If the scan fails, it says so and waits for "Continue".
 */
@Composable
private fun FirstScan(vm: AppViewModel, onFinish: () -> Unit) {
    val state by vm.importState.collectAsState()
    val reduced = reducedMotion
    // After the app was closed mid-scan nothing is running any more: start it again (saved payments are skipped).
    LaunchedEffect(Unit) { if (vm.importState.value is ImportUiState.Idle) vm.scanInbox(full = true) }
    LaunchedEffect(state) {
        if (state is ImportUiState.Done) {
            delay(2200)
            onFinish()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, top = Space.xxl, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Illustration(page = 0, active = true, loop = state is ImportUiState.Running)
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            CapsLabel("First scan")
            when (val s = state) {
                is ImportUiState.Done -> {
                    Text("You're all set", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TintedSquare(Icons.Outlined.CheckCircle)
                        Spacer(Modifier.width(Space.md))
                        Text(scanResultLine(s.stats, false), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    Text("Opening Home…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ImportUiState.Failed -> {
                    Text("The first scan didn't finish", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TintedSquare(Icons.Outlined.ErrorOutline, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(Space.md))
                        Text(scanResultLine(null, true), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    Lead("You can scan again from Home, or add payments yourself.")
                }
                else -> {
                    Text("Reading your bank messages…", style = MaterialTheme.typography.headlineMedium)
                    Lead("The first time can take a minute. Only bank, UPI and card alerts are read.")
                    if (reduced) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.HourglassEmpty, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Space.sm))
                            Text("Working on it", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (state is ImportUiState.Failed) PrimaryButton("Continue", onFinish)
    }
}

/* ---------------------------------------------------------------- Illustrations ---- */

/** The theme colours an illustration is drawn with, read once outside the Canvas. */
private class Ink(
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val card: Color,
    val hairline: Color,
    val outline: Color,
    val muted: Color,
    val text: Color,
)

/**
 * One drawing per step, all in the same flat style on a sunken stage: the accent, its soft tint, card white and hairlines,
 * no image assets. The part that matters (the new ledger row, the read/skipped badges, the lock) springs in when the
 * step comes into view, or keeps repeating while the first scan runs ([loop]). Still when animations are off.
 */
@Composable
private fun Illustration(page: Int, active: Boolean, loop: Boolean = false) {
    val reduced = reducedMotion
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(active, reduced, loop) {
        if (reduced) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        do {
            reveal.snapTo(0f)
            reveal.animateTo(1f, Motion.spatial())
            if (loop) delay(700)
        } while (loop)
    }
    val s = surfaces
    val ink = Ink(
        accent = MaterialTheme.colorScheme.primary,
        onAccent = MaterialTheme.colorScheme.onPrimary,
        soft = s.accentSoft,
        card = s.card,
        hairline = s.hairline,
        outline = s.outline,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        text = MaterialTheme.colorScheme.onSurface,
    )
    Box(Modifier.fillMaxWidth().height(200.dp).clip(CardShape).background(s.sunken)) {
        Canvas(Modifier.fillMaxSize().padding(Space.lg)) {
            val p = reveal.value
            fit(320f, 180f) {
                when (page) {
                    0 -> drawSmsToLedger(ink, p)
                    1 -> drawReadAndSkipped(ink, p)
                    else -> drawLockedPhone(ink, p)
                }
            }
        }
    }
}

/** Draws [block] in a [w] × [h] design space, scaled to fit and centred. */
private fun DrawScope.fit(w: Float, h: Float, block: DrawScope.() -> Unit) {
    val k = min(size.width / w, size.height / h)
    translate((size.width - w * k) / 2f, (size.height - h * k) / 2f) {
        scale(k, k, pivot = Offset.Zero) { block() }
    }
}

private fun DrawScope.bar(x: Float, y: Float, w: Float, h: Float, color: Color) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2f))

/** A speech bubble with its tail at the bottom left. */
private fun bubble(left: Float, top: Float, right: Float, bottom: Float): Path = Path().apply {
    addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(18f)))
    moveTo(left + 18f, bottom - 2f)
    lineTo(left + 12f, bottom + 18f)
    lineTo(left + 38f, bottom - 2f)
    close()
}

/** Step 1: a bank SMS becomes a new row at the top of a ledger. */
private fun DrawScope.drawSmsToLedger(c: Ink, p: Float) {
    val a = p.coerceIn(0f, 1f)
    drawPath(bubble(8f, 34f, 140f, 116f), c.soft)
    bar(26f, 50f, 84f, 8f, c.accent.copy(alpha = 0.35f))
    bar(26f, 66f, 100f, 8f, c.accent.copy(alpha = 0.35f))
    bar(26f, 86f, 52f, 14f, c.accent)

    val arrow = c.accent.copy(alpha = 0.3f + 0.7f * a)
    drawLine(arrow, Offset(152f, 76f), Offset(180f, 76f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(arrow, Offset(170f, 66f), Offset(180f, 76f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(arrow, Offset(170f, 86f), Offset(180f, 76f), strokeWidth = 4f, cap = StrokeCap.Round)

    drawRoundRect(c.card, Offset(192f, 16f), Size(120f, 148f), CornerRadius(16f))
    drawRoundRect(c.hairline, Offset(192f, 16f), Size(120f, 148f), CornerRadius(16f), style = Stroke(1.5f))
    for (i in 0..2) {
        val top = 24f + i * 46f
        if (i > 0) drawLine(c.hairline, Offset(204f, top - 4f), Offset(300f, top - 4f), strokeWidth = 1.5f)
        if (i == 0) {
            val dy = (1f - p) * -10f
            drawRoundRect(c.soft.copy(alpha = a), Offset(198f, top - 2f + dy), Size(108f, 40f), CornerRadius(10f))
            drawCircle(c.accent.copy(alpha = a), radius = 9f, center = Offset(214f, top + 18f + dy))
            bar(230f, top + 9f + dy, 38f, 7f, c.text.copy(alpha = 0.55f * a))
            bar(230f, top + 21f + dy, 26f, 6f, c.muted.copy(alpha = 0.4f * a))
            bar(276f, top + 12f + dy, 24f, 9f, c.accent.copy(alpha = a))
        } else {
            drawCircle(c.muted.copy(alpha = 0.3f), radius = 9f, center = Offset(214f, top + 18f))
            bar(230f, top + 9f, 38f, 7f, c.text.copy(alpha = 0.35f))
            bar(230f, top + 21f, 26f, 6f, c.muted.copy(alpha = 0.3f))
            bar(276f, top + 12f, 24f, 9f, c.text.copy(alpha = 0.55f))
        }
    }
}

/** Step 2: a bank alert is read (a tick); a one-time code is skipped (a cross on a dashed bubble). */
private fun DrawScope.drawReadAndSkipped(c: Ink, p: Float) {
    val a = p.coerceIn(0f, 1f)
    drawPath(bubble(24f, 14f, 224f, 82f), c.soft)
    bar(42f, 28f, 130f, 8f, c.accent.copy(alpha = 0.35f))
    bar(42f, 44f, 160f, 8f, c.accent.copy(alpha = 0.35f))
    bar(42f, 60f, 56f, 12f, c.accent)
    if (p > 0.02f) {
        drawCircle(c.accent, radius = 20f * p, center = Offset(266f, 48f))
        val tick = Path().apply { moveTo(256f, 48f); lineTo(263f, 55f); lineTo(277f, 41f) }
        drawPath(tick, c.onAccent.copy(alpha = a), style = Stroke(4f, cap = StrokeCap.Round))
    }

    drawRoundRect(
        c.outline.copy(alpha = 0.8f), Offset(24f, 108f), Size(200f, 56f), CornerRadius(18f),
        style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)),
    )
    for (i in 0..3) drawCircle(c.muted.copy(alpha = 0.5f), radius = 5f, center = Offset(46f + i * 16f, 136f))
    bar(118f, 132f, 84f, 8f, c.muted.copy(alpha = 0.3f))
    if (p > 0.02f) {
        drawCircle(c.outline, radius = 20f * p, center = Offset(266f, 136f), style = Stroke(3f))
        val x = c.muted.copy(alpha = a)
        drawLine(x, Offset(258f, 128f), Offset(274f, 144f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(x, Offset(274f, 128f), Offset(258f, 144f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

/** Step 3: a phone on a soft halo, its lock clicking shut. */
private fun DrawScope.drawLockedPhone(c: Ink, p: Float) {
    drawCircle(c.soft, radius = 84f * (0.6f + 0.4f * p), center = Offset(160f, 90f))
    drawRoundRect(c.card, Offset(116f, 6f), Size(88f, 168f), CornerRadius(18f))
    drawRoundRect(c.outline, Offset(116f, 6f), Size(88f, 168f), CornerRadius(18f), style = Stroke(2.5f))
    bar(148f, 14f, 24f, 5f, c.hairline)
    bar(128f, 30f, 64f, 8f, c.muted.copy(alpha = 0.35f))
    bar(128f, 44f, 48f, 8f, c.muted.copy(alpha = 0.25f))
    bar(128f, 146f, 64f, 8f, c.muted.copy(alpha = 0.25f))
    bar(128f, 158f, 40f, 8f, c.muted.copy(alpha = 0.2f))

    val lift = (1f - p) * -12f
    val shackle = Stroke(6f, cap = StrokeCap.Round)
    drawArc(c.accent, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(144f, 66f + lift), size = Size(32f, 36f), style = shackle)
    drawLine(c.accent, Offset(144f, 84f + lift), Offset(144f, 96f), strokeWidth = 6f, cap = StrokeCap.Round)
    drawLine(c.accent, Offset(176f, 84f + lift), Offset(176f, 96f), strokeWidth = 6f, cap = StrokeCap.Round)
    drawRoundRect(c.accent, Offset(134f, 92f), Size(52f, 44f), CornerRadius(10f))
    drawCircle(c.onAccent, radius = 5f, center = Offset(160f, 110f))
    drawRoundRect(c.onAccent, Offset(157.5f, 112f), Size(5f, 12f), CornerRadius(2.5f))
}
