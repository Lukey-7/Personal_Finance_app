package com.pft.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SecondaryButton

/**
 * Shown instead of the app when Android's secure key store can't give FinTrack the key to its database. Nothing is
 * deleted: Try again often works after a restart; Start fresh keeps the old file on the phone and opens an empty one.
 */
@Composable
fun KeyProblemScreen(onRetry: () -> Unit, onStartFresh: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("FinTrack can't open your data", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Android's secure key store didn't give FinTrack the key to its encrypted database. This can happen " +
                    "after a system update or a restore. Your data is still on this phone and nothing has been deleted.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Restart the phone and tap Try again. If it keeps happening, you can start fresh and restore your " +
                    "last backup in Settings › Backup.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failed) Text("Still can't open it.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Try again", { onRetry(); failed = true })
            SecondaryButton("Start fresh", { confirm = true })
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Start fresh?") },
        text = {
            Text(
                "FinTrack opens with no payments. The old database stays on this phone, set aside, but it can't be " +
                    "read without its key. Restore a backup afterwards to get your payments back."
            )
        },
        confirmButton = { TextButton(onClick = { confirm = false; onStartFresh() }) { Text("Start fresh") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
