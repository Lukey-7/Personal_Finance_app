package com.pft.financetracker.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.data.categories.CategoryStore
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CustomCategory
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.CategoryIcon
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.categoryName
import com.pft.financetracker.ui.components.rememberFinSheet
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Categories: the person's own categories (each counting as a built-in one), and the category they gave each payee,
 * which new payments from that payee follow.
 */
@Composable
internal fun CategoriesSection(vm: AppViewModel, snackbar: SnackbarHostState) {
    val customs by vm.customCategories.collectAsState()
    val rules by vm.payeeRules.collectAsState()
    val scope = rememberCoroutineScope()
    /** null: closed; a category to change; [NEW] to add one. */
    var editing by remember { mutableStateOf<CustomCategory?>(null) }
    var deleting by remember { mutableStateOf<CustomCategory?>(null) }

    SettingsGroup("Your categories") {
        val list = customs.values.sortedBy { it.name.lowercase() }
        if (list.isEmpty()) Note("Add your own, such as Pets or Parents. Each counts as one of the built-in categories for splits and tax, and gets its own line on Home, in Insights and in Budgets.")
        list.forEachIndexed { i, c ->
            if (i > 0) Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Change") { editing = c },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(c.parent)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Counts as ${c.parent.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextAction("Add a category", { editing = NEW }, alignStart = true)
    }

    SettingsGroup("Payee rules") {
        if (rules.isEmpty()) Note("When you move a payment to another category, its payee's next payments go there too. Those payees appear here.")
        else Note("New payments from these payees go straight to the category shown.")
        rules.forEachIndexed { i, (key, choice) ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(key, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(categoryName(choice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    vm.forgetPayeeRule(key)
                    scope.launch { snackbar.showSnackbar("New payments from $key will be categorised automatically again") }
                }) { Icon(Icons.Outlined.Close, "Remove rule for $key") }
            }
        }
    }

    editing?.let { c ->
        CategorySheet(
            existing = c.takeIf { it !== NEW },
            onSave = { name, parent, done ->
                if (c === NEW) vm.addCategory(name, parent) { id -> done(id != null) }
                else vm.updateCategory(c.id, name, parent) { ok -> done(ok) }
            },
            onDelete = { deleting = c },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${c.name}?") },
            text = { Text("Its payments go back to ${c.parent.label}, and its budget and payee rules are removed. No payment is deleted.") },
            confirmButton = { TextButton(onClick = { vm.deleteCategory(c.id); deleting = null; editing = null }) { Text("Delete", color = Expense) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Marks "add a new one" in [CategoriesSection]'s editing state. */
private val NEW = CustomCategory(-1, "", Category.OTHER)

/** Add or change one of the person's own categories: a name, and the built-in category it counts as. */
@Composable
private fun CategorySheet(
    existing: CustomCategory?,
    onSave: (String, Category, (Boolean) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val sheet = rememberFinSheet()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var parent by remember { mutableStateOf(existing?.parent ?: Category.SHOPPING) }
    var error by remember { mutableStateOf<String?>(null) }
    FinSheet(onDismiss, if (existing == null) "Add a category" else "Change ${existing.name}", sheet = sheet) {
        OutlinedTextField(
            name, { if (it.length <= CategoryStore.NAME_MAX) { name = it; error = null } },
            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = error != null, supportingText = error?.let { e -> { Text(e) } },
        )
        Text("Counts as", style = MaterialTheme.typography.labelLarge)
        ChipFlow {
            Category.entries.filter { it != Category.OTHER || parent == Category.OTHER }.forEach { c ->
                PillChip(parent == c, c.label, icon = categoryIcon(c)) { parent = c }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            PrimaryButton(if (existing == null) "Add category" else "Save", {
                if (name.isBlank()) { error = "Type a name"; return@PrimaryButton }
                onSave(name, parent) { ok ->
                    if (ok) { haptics.confirm(); sheet.close(onDismiss) } else error = "That name is already used"
                }
            }, icon = if (existing == null) Icons.Outlined.Add else null)
            if (existing != null) TextAction("Delete category", { sheet.close(onDelete) }, alignStart = true)
        }
    }
}
