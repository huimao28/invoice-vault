package com.minis.invoicevault

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Stable semantic tones. They intentionally do not derive from the dynamic primary color. */
data class CategoryColorToken(val container: Long, val content: Long, val accent: Long)

object CategoryColorTokens {
    val light: Map<Category, CategoryColorToken> = mapOf(
        Category.FOOD to token(0xFFFFDAD1, 0xFF3B0A00, 0xFFB93818),
        Category.TRANSPORT to token(0xFFD7E3FF, 0xFF001B3F, 0xFF2862A8),
        Category.LODGING to token(0xFFE1E0FF, 0xFF17164B, 0xFF5555A5),
        Category.COMMUNICATION to token(0xFFBDEBF2, 0xFF002F35, 0xFF007C8A),
        Category.OFFICE to token(0xFFF1DFD3, 0xFF332018, 0xFF815443),
        Category.SOFTWARE to token(0xFFEDDCFF, 0xFF2B0A46, 0xFF7B45A6),
        Category.LIFE to token(0xFFD4E8D0, 0xFF102F14, 0xFF3F7B48),
        Category.MEDICAL to token(0xFFFFD9E2, 0xFF3F001B, 0xFFB52E5A),
        Category.EDUCATION to token(0xFFC5EDE4, 0xFF002F2A, 0xFF167C70),
        Category.VEHICLE to token(0xFFFFDF9E, 0xFF3D2B00, 0xFFA26300),
        Category.DIGITAL to token(0xFFDCE1FF, 0xFF101A46, 0xFF465DAD),
        Category.OTHER to token(0xFFE3E2E6, 0xFF29282B, 0xFF6F6E73)
    )
    val dark: Map<Category, CategoryColorToken> = mapOf(
        Category.FOOD to token(0xFF70220F, 0xFFFFDBD2, 0xFFFF8C70),
        Category.TRANSPORT to token(0xFF143A68, 0xFFD7E3FF, 0xFFA8C7FA),
        Category.LODGING to token(0xFF38376E, 0xFFE1E0FF, 0xFFC1C1FF),
        Category.COMMUNICATION to token(0xFF004E57, 0xFFBDEBF2, 0xFF72D5E2),
        Category.OFFICE to token(0xFF52372C, 0xFFF1DFD3, 0xFFD9B8A8),
        Category.SOFTWARE to token(0xFF4F276B, 0xFFEDDCFF, 0xFFD7B8F5),
        Category.LIFE to token(0xFF294E2E, 0xFFD4E8D0, 0xFFA8D5AD),
        Category.MEDICAL to token(0xFF6B1737, 0xFFFFD9E2, 0xFFFFB0C5),
        Category.EDUCATION to token(0xFF005048, 0xFFC5EDE4, 0xFF8BD8CC),
        Category.VEHICLE to token(0xFF5C4100, 0xFFFFDF9E, 0xFFFFBE48),
        Category.DIGITAL to token(0xFF29386E, 0xFFDCE1FF, 0xFFB8C4FF),
        Category.OTHER to token(0xFF454448, 0xFFE3E2E6, 0xFFC7C5CA)
    )
    private fun token(container: Long, content: Long, accent: Long) = CategoryColorToken(container, content, accent)
}

@Immutable
data class CategoryColorScheme(val container: Color, val content: Color, val accent: Color)

/** The single Compose mapping used by list, detail, picker and filters. Null is uncategorized. */
@Composable
fun CategoryColors(category: Category?): CategoryColorScheme {
    val dark = isSystemInDarkTheme()
    val token = category?.let { (if (dark) CategoryColorTokens.dark else CategoryColorTokens.light).getValue(it) }
        ?: if (dark) CategoryColorToken(0xFF454448, 0xFFE3E2E6, 0xFFC7C5CA)
        else CategoryColorToken(0xFFE3E2E6, 0xFF29282B, 0xFF6F6E73)
    return CategoryColorScheme(Color(token.container.toInt()), Color(token.content.toInt()), Color(token.accent.toInt()))
}
