package com.pft.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.theme.rememberHaptics

/**
 * Asks for the backup passphrase, in a sheet. Making a backup: typed twice, at least 8 characters. Restoring: typed
 * once, plus the word RESTORE, because a restore replaces everything on this phone (so its button is red).
 */
@Composable
fun BackupPassphraseDialog(restoring: Boolean, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var word by remember { mutableStateOf("") }
    val ok = if (restoring) pw.isNotEmpty() && word.trim() == "RESTORE" else pw.length >= 8 && pw == again
    val haptics = rememberHaptics()
    val submit = {
        if (ok) {
            haptics.confirm()
            onConfirm(pw.toCharArray())
        } else haptics.reject()
    }
    val secret = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)

    FinSheet(onDismiss, title = if (restoring) "Restore a backup" else "Lock your backup") {
        if (restoring) {
            Text("Everything on this phone is replaced by the backup. This cannot be undone.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Choose a passphrase of at least 8 characters. Write it down: if it is lost, the backup cannot be opened.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            pw, { pw = it },
            label = { Text("Passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = secret,
            supportingText = if (!restoring) ({ Text(if (pw.length >= 8) "Long enough" else "At least 8 characters") }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!restoring) OutlinedTextField(
            again, { again = it },
            label = { Text("Passphrase again") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = again.isNotEmpty() && again != pw,
            supportingText = if (again.isNotEmpty() && again != pw) ({ Text("The two don't match yet") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (restoring) OutlinedTextField(
            word, { word = it },
            label = { Text("Type RESTORE to confirm") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (restoring) DangerButton("Restore", submit, enabled = ok)
        else PrimaryButton("Save backup", submit, enabled = ok)
        TextAction("Cancel", onDismiss, Modifier.fillMaxWidth())
    }
}
