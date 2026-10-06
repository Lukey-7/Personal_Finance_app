package com.pft.financetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shown = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

/**
 * A read-only field that opens a picker when tapped, shaped like the text fields around it.
 *
 * A read-only text field only takes focus on an accessibility click or a keyboard Enter; it never "presses". So the
 * field itself is hidden from focus and from TalkBack, and a clickable overlay with a button role does the work:
 * TalkBack reads "Date: 06 Oct 2026, button, double-tap to pick".
 */
@Composable
fun PickerField(label: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }.clearAndSetSemantics {},
        )
        Box(
            Modifier.matchParentSize()
                .clickable(onClickLabel = "Pick", role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (text.isBlank()) "$label: not set" else "$label: $text" }
        )
    }
}

/**
 * A date as a [PickerField]; tapping it opens the date picker. Nobody types "2027-10-06". [onClear] adds a clear
 * button beside it for dates that are optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PickerField(label, value?.format(shown) ?: "", { picking = true }, Modifier.weight(1f))
        if (onClear != null && value != null) IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Clear $label") }
    }
    if (picking) {
        // The picker works in UTC midnight millis.
        val state = rememberDatePickerState(initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
