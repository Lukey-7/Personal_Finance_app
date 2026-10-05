package com.pft.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.Expense

/**
 * Asks for the backup passphrase. Making a backup: typed twice, at least 8 characters. Restoring: typed once, plus the
 * word RESTORE, because a restore replaces everything on this phone.
 */
@Composable
fun BackupPassphraseDialog(restoring: Boolean, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var word by remember { mutableStateOf("") }
    val ok = if (restoring) pw.isNotEmpty() && word.trim() == "RESTORE" else pw.length >= 8 && pw == again

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (restoring) "Restore a backup" else "Lock your backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (restoring) Text("Everything on this phone is replaced by the backup. This cannot be undone.", color = Expense, style = MaterialTheme.typography.bodySmall)
                else Text("Choose a passphrase of at least 8 characters. Write it down: if it is lost, the backup cannot be opened.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pw, { pw = it }, label = { Text("Passphrase") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (!restoring) OutlinedTextField(again, { again = it }, label = { Text("Passphrase again") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    isError = again.isNotEmpty() && again != pw)
                if (restoring) OutlinedTextField(word, { word = it }, label = { Text("Type RESTORE to confirm") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(pw.toCharArray()) }) { Text(if (restoring) "Restore" else "Save backup") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
