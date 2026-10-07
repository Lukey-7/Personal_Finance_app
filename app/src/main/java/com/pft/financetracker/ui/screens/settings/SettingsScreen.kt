package com.pft.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pft.financetracker.BuildConfig
import com.pft.financetracker.data.ai.NanoAi
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSnackbarHost
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.bottomPadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The settings pages, by route key (`settings/{section}`), in index order. */
private val sectionTitles = linkedMapOf(
    "sms" to "SMS & import",
    "calculation" to "Calculation",
    "splits" to "Splits",
    "reminders" to "Reminders",
    "backup" to "Backup",
    "widget" to "Widget",
    "ai" to "AI",
    "data" to "Your data",
)

private val sectionIcons: Map<String, ImageVector> = mapOf(
    "sms" to Icons.Outlined.Sms,
    "calculation" to Icons.Outlined.Calculate,
    "splits" to Icons.AutoMirrored.Outlined.CallSplit,
    "reminders" to Icons.Outlined.NotificationsActive,
    "backup" to Icons.Outlined.Backup,
    "widget" to Icons.Outlined.Widgets,
    "ai" to Icons.Outlined.AutoAwesome,
    "data" to Icons.Outlined.Storage,
)

private val dayMonth = SimpleDateFormat("d MMM", Locale.ENGLISH)

/**
 * Settings: a grouped index. Each row names a page and says, in one line, how that part is set right now; tapping it
 * opens the page ([onOpenSection] with its key).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onOpenSection: (String) -> Unit) {
    val autoImport by vm.autoImport.collectAsState()
    val cashAsSpend by vm.countCashAsSpend.collectAsState()
    val myName by vm.myName.collectAsState()
    val splitAi by vm.splitAi.collectAsState()
    val hasKey by vm.hasApiKey.collectAsState()
    val remindersOn by vm.remindersEnabled.collectAsState()
    val lastBackup by vm.lastBackupAt.collectAsState()
    val widgetHide by vm.widgetHideAmounts.collectAsState()
    val useNano by vm.useNano.collectAsState()
    val nanoStatus by vm.nanoStatus.collectAsState()
    // Read again each time the index is shown, so a permission granted on the SMS page shows here on the way back.
    val smsGranted = remember { vm.hasSmsPermission() }
    LaunchedEffect(Unit) { vm.refreshNano() }

    val subtitles = mapOf(
        "sms" to when {
            !smsGranted -> "SMS access off"
            autoImport -> "Auto-scan on"
            else -> "Auto-scan off"
        },
        "calculation" to if (cashAsSpend) "Cash counts as spend" else "Cash kept out of spend",
        "splits" to listOfNotNull(
            myName.trim().takeIf { it.isNotEmpty() }?.let { "You appear as $it" } ?: "Your name isn't set",
            if (splitAi && hasKey) "AI for unclear cases" else null,
        ).joinToString(" · "),
        "reminders" to if (remindersOn) "On – before bills and renewals" else "Off",
        "backup" to if (lastBackup == 0L) "No backup yet" else "Last backup ${dayMonth.format(Date(lastBackup))}",
        "widget" to if (widgetHide) "Amounts hidden" else "Amounts shown",
        "ai" to listOfNotNull(
            if (hasKey) "OpenAI key saved" else null,
            if (nanoStatus == NanoAi.Status.READY && useNano) "On-device AI on" else null,
        ).joinToString(" · ").ifEmpty { "Off" },
        "data" to "Export as CSV or clear everything",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Gutter, top = Space.xs, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            SoftPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TintedSquare(Icons.Outlined.Lock)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("Everything stays on this phone", style = MaterialTheme.typography.titleSmall)
                        Text("No account, no cloud, no analytics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IndexGroup("Tracking", listOf("sms", "calculation", "splits"), subtitles, onOpenSection)
            IndexGroup("Everyday", listOf("reminders", "backup", "widget"), subtitles, onOpenSection)
            IndexGroup("Privacy", listOf("ai", "data"), subtitles, onOpenSection)
            Text(
                "FinTrack ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One card of index rows under a caps heading, hairlines between the rows. */
@Composable
private fun IndexGroup(label: String, keys: List<String>, subtitles: Map<String, String>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        CapsLabel(label, Modifier.padding(start = Space.xs))
        FinCard(padding = PaddingValues(horizontal = Space.lg, vertical = Space.xs), spacing = 0.dp) {
            keys.forEachIndexed { i, key ->
                if (i > 0) Hairline(startInset = 36.dp + Space.lg)
                ActionRow(
                    label = sectionTitles.getValue(key),
                    icon = sectionIcons.getValue(key),
                    onClick = { onOpen(key) },
                    subtitle = subtitles[key],
                )
            }
        }
    }
}

/**
 * One settings page ([section] is the key from the index): a back arrow, its title, and only the settings for that
 * part of the app. An unknown key shows a way back instead of a blank page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    vm: AppViewModel,
    section: String,
    onBack: () -> Unit,
    onOpenSmsLog: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(sectionTitles[section] ?: "Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { FinSnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomBarPadding.current)) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Gutter, top = Space.sm, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            when (section) {
                "sms" -> SmsSection(vm, snackbar, onOpenSmsLog, onOpenImport)
                "calculation" -> CalculationSection(vm)
                "splits" -> SplitsSection(vm)
                "reminders" -> RemindersSection(vm, snackbar)
                "backup" -> BackupSection(vm, snackbar)
                "widget" -> WidgetSection(vm, snackbar)
                "ai" -> AiSection(vm)
                "data" -> DataSection(vm, snackbar)
                else -> EmptyState(
                    Icons.Outlined.Settings,
                    "This page isn't here",
                    "It may have moved in an update. Everything is still in Settings.",
                ) { SecondaryButton("Back to Settings", onBack) }
            }
        }
    }
}
