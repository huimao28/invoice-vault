package com.huimaotech.invoicevault

import org.junit.Assert.*
import org.junit.Test

class CategoryColorsTest {
    @Test fun mappingsAreCompleteAndOpaque() {
        assertEquals(Category.entries.toSet(), CategoryColorTokens.light.keys)
        assertEquals(Category.entries.toSet(), CategoryColorTokens.dark.keys)
        listOf(CategoryColorTokens.light, CategoryColorTokens.dark).forEach { map ->
            map.values.forEach { token ->
                assertEquals(0xFF, token.container ushr 24)
                assertEquals(0xFF, token.content ushr 24)
                assertEquals(0xFF, token.accent ushr 24)
                assertNotEquals(token.container, token.content)
            }
            assertEquals(Category.entries.size, map.values.map { it.accent }.distinct().size)
        }
    }

    @Test fun foregroundContrastIsReadableInLightAndDark() {
        listOf(CategoryColorTokens.light, CategoryColorTokens.dark).forEach { map ->
            map.forEach { (category, token) ->
                assertTrue("$category contrast", contrast(token.container, token.content) >= 4.5)
            }
        }
    }

    private fun contrast(a: Long, b: Long): Double {
        fun luminance(argb: Long): Double {
            fun channel(shift: Int): Double {
                val s = ((argb shr shift) and 0xFF) / 255.0
                return if (s <= .04045) s / 12.92 else Math.pow((s + .055) / 1.055, 2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
