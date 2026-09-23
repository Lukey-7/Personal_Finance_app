package com.pft.financetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.ui.graphics.vector.ImageVector
import com.pft.financetracker.domain.model.Category

/**
 * One outline glyph per category, used everywhere a category is shown (Budgets, Top merchants, the
 * donut legend, the category chips), so a category is recognisable by shape as well as by name.
 */
fun categoryIcon(c: Category): ImageVector = when (c) {
    Category.FOOD -> Icons.Outlined.Restaurant
    Category.SHOPPING -> Icons.Outlined.ShoppingBag
    Category.BILLS -> Icons.AutoMirrored.Outlined.ReceiptLong
    Category.TRANSPORT -> Icons.Outlined.DirectionsCar
    Category.ENTERTAINMENT -> Icons.Outlined.Movie
    Category.HEALTH -> Icons.Outlined.LocalHospital
    Category.EDUCATION -> Icons.Outlined.School
    Category.INVESTMENT -> Icons.Outlined.TrendingUp
    Category.ATM -> Icons.Outlined.Payments
    Category.TRANSFER -> Icons.Outlined.SwapHoriz
    Category.INCOME -> Icons.Outlined.AccountBalanceWallet
    Category.OTHER -> Icons.Outlined.Category
}
