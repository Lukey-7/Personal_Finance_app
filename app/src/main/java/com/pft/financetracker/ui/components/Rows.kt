package com.pft.financetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.surfaces

/** Row avatar size and the gap after it. */
val RowIconSize = 40.dp
val RowIconGap = 14.dp

/** Where a list row's text starts on a full-width screen list; hairlines start here so they line up with it. */
val RowTextInset = Gutter + RowIconSize + RowIconGap

/** Space a list leaves at its end so its last row can be scrolled clear of the Add button (56dp plus margin). */
val FabClearance = 96.dp

/** True while [state] is at (or very near) its top: the Add button shows its label then, and folds to a circle once you scroll. */
@Composable
fun rememberAtTop(state: LazyListState): Boolean {
    val atTop by remember(state) { derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 24 } }
    return atTop
}

/**
 * The Add button: "+ Add" while the list is at its top, folding into a circle as you scroll so it never sits on an
 * amount for long. The same accent on every screen, raised on a soft shadow. TalkBack always hears [text].
 */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier, expanded: Boolean = true, text: String = "Add") {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        modifier = modifier.semantics { contentDescription = text },
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 2.dp, focusedElevation = 4.dp, hoveredElevation = 6.dp),
        icon = { Icon(Icons.Filled.Add, null) },
        text = { Text(text, style = MaterialTheme.typography.titleSmall) },
    )
}

/** The one search-field style: a sunken well with a leading search glyph and a clear button once there is text. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onValueChange("") }) { Icon(Icons.Outlined.Close, "Clear search") } }) else null,
        singleLine = true,
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surfaces.sunken,
            unfocusedContainerColor = surfaces.sunken,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Puts [count] equal-width children on one line when each can have at least [minItemWidth], and stacks
 * them full-width otherwise. For text fields, which cannot go in a FlowRow.
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

/** Lets a child of a gutter-padded column span the full screen width (a scrolling chip row). */
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
 * A label and a figure on one line: the label takes the flexible space and wraps, the figure never does. Tappable rows
 * are a full 48dp touch target.
 */
@Composable
fun AmountRow(
    label: String,
    paise: Long,
    color: Color,
    modifier: Modifier = Modifier,
    sign: String = "",
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.heightIn(min = 48.dp).clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = labelColor)
        Spacer(Modifier.width(Space.md))
        Text(
            if (sign.isEmpty()) money(paise) else "$sign${money(paise)}",
            style = MoneyType.small, fontWeight = FontWeight.SemiBold, color = color, softWrap = false, maxLines = 1,
        )
    }
}

/**
 * Merchant names come out of SMS title-cased ("Atm", "Irctc"). Very short all-letter names are
 * almost always acronyms, so show those in capitals. Display only; the stored name is unchanged.
 */
fun displayMerchant(name: String): String =
    if (name.length in 2..3 && name.all { it.isLetter() }) name.uppercase() else name
