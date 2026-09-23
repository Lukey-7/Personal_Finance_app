package com.pft.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Row avatar/icon size and the gap after it, from the reference's list rows. */
val RowIconSize = 40.dp
val RowIconGap = 16.dp

/** Where a list row's text starts on a full-width screen list; hairlines start here so they line up with it. */
val RowTextInset = Gutter + RowIconSize + RowIconGap

/** Space a list leaves at its end so its last row can be scrolled clear of a + button (56dp plus margin). */
val FabClearance = 88.dp

/** The + button: the same solid accent on every screen that has one, and flat like the rest of the UI. */
@Composable
fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Add") {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
    ) { Icon(Icons.Filled.Add, label) }
}

/** Extended variant of [AddFab], for screens where the action needs naming ("New split"). */
@Composable
fun AddFabExtended(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        // The visible text is not exposed to accessibility services on this component; name it explicitly.
        modifier = modifier.semantics { contentDescription = text },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
        icon = { Icon(Icons.Filled.Add, null) },
        text = { Text(text) },
    )
}

/** The one search-field style: filled soft pill with a leading search glyph, no outline until focused. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Puts [count] equal-width children on one line when each can have at least [minItemWidth], and stacks
 * them full-width otherwise. For text fields, which cannot go in a FlowRow: an OutlinedTextField asks
 * for 280dp by default, so a FlowRow gives every field its own line even when three would fit.
 */
@Composable
fun AdaptiveRow(
    count: Int,
    minItemWidth: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable (itemModifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= minItemWidth * count + spacing * (count - 1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) { content(Modifier.weight(1f)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) { content(Modifier.fillMaxWidth()) }
        }
    }
}

/**
 * Lets a child of a gutter-padded column (a chip row) span the full screen width, so it scrolls from edge
 * to edge; pair with [ChipRow]'s own inset so its first chip still lines up with the column's content.
 */
fun Modifier.edgeToEdge(gutter: Dp = Gutter): Modifier = layout { measurable, constraints ->
    val extra = (gutter * 2).roundToPx()
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val p = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

/**
 * Merchant names come out of SMS title-cased ("Atm", "Irctc"). Very short all-letter names are
 * almost always acronyms, so show those in capitals. Display only; the stored name is unchanged.
 */
fun displayMerchant(name: String): String =
    if (name.length in 2..3 && name.all { it.isLetter() }) name.uppercase() else name
