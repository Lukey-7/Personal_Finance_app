package com.pft.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.components.ButtonHeight
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.reducedMotion
import com.pft.financetracker.ui.theme.surfaces

/*
 * Small pieces shared by the settings pages: a labelled group, a whole-row switch, the red button and the typed
 * confirmation that guards "Clear all data".
 */

/** A group of settings: an optional caps heading over one card. */
@Composable
internal fun SettingsGroup(label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (label != null) CapsLabel(label, Modifier.padding(start = Space.xs))
        FinCard(content = content)
    }
}

/**
 * A setting that is on or off. The whole row is the switch, so TalkBack reads "<title>, switch, on" rather than an
 * unnamed switch, and the label is a tap target too. 56dp tall at least.
 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.md))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A quiet sentence under a control, in the muted ink. */
@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A busy line ("Reading your bank messages…"): a small spinner, or a still glyph when animations are off. */
@Composable
internal fun BusyLine(text: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reducedMotion) Icon(Icons.Outlined.HourglassEmpty, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The one red button: for an action that removes or replaces data. Full width, 52dp. */
@Composable
internal fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonHeight),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = surfaces.sunken,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.sm),
    ) { Text(text, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center) }
}

/**
 * "Clear all data", guarded: says what goes, and the red button only wakes up once DELETE is typed (any case). Pressing
 * Done on the keyboard with anything else buzzes and says why.
 */
@Composable
internal fun ClearAllSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val ok = typed.trim().equals("DELETE", ignoreCase = true)
    val attempt = {
        if (ok) {
            haptics.confirm()
            onConfirm()
        } else {
            wrong = true
            haptics.reject()
        }
    }

    FinSheet(onDismiss, title = "Delete everything?") {
        Text(
            "This permanently erases from this phone:",
            style = MaterialTheme.typography.bodyLarge,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            listOf(
                "Every transaction, budget and split",
                "The SMS log, review items and learned message shapes",
                "Your settings and the saved API key",
            ).forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.RemoveCircleOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Space.md))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Note("A backup file you saved somewhere else is not touched.")
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it; wrong = false },
            label = { Text("Type DELETE to confirm") },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) ({ Text("That isn't DELETE. Type it exactly, then try again.") }) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { attempt() }),
            modifier = Modifier.fillMaxWidth(),
        )
        DangerButton("Delete everything", attempt, enabled = ok)
        TextAction("Cancel", onDismiss, Modifier.fillMaxWidth())
    }
}
