package com.pft.financetracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF0E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F1D8),
    secondary = Color(0xFF4C6357),
    tertiary = Color(0xFF3F6375),
    background = Color(0xFFF7FBF7),
    surface = Color(0xFFF7FBF7),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7ED9B4),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A),
    secondary = Color(0xFFB2CCBE),
    tertiary = Color(0xFFA7CCE1),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
)

/** Stable per-category palette used by charts. */
val CategoryColors: List<Color> = listOf(
    Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFEF6C00), Color(0xFF6A1B9A), Color(0xFFC62828),
    Color(0xFF00838F), Color(0xFF4E342E), Color(0xFF9E9D24), Color(0xFF283593), Color(0xFFAD1457),
    Color(0xFF00695C), Color(0xFF616161),
)

val Income = Color(0xFF2E7D32)
val Expense = Color(0xFFC62828)

@Composable
fun FinTrackTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
