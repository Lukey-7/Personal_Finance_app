package com.pft.financetracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.pft.financetracker.domain.model.CategoryChoice
import com.pft.financetracker.domain.model.CustomCategory
import com.pft.financetracker.domain.model.Transaction

/** The person's own categories by id, provided once at the top of the app so any row can show their names. */
val LocalCustomCategories = staticCompositionLocalOf<Map<Long, CustomCategory>> { emptyMap() }

/** The name [t] is filed under, as the person sees it: their own category's name, else the built-in label. */
@Composable
@ReadOnlyComposable
fun categoryName(t: Transaction): String = t.choice.label(LocalCustomCategories.current)

@Composable
@ReadOnlyComposable
fun categoryName(choice: CategoryChoice): String = choice.label(LocalCustomCategories.current)
