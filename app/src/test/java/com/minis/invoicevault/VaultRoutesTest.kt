package com.minis.invoicevault

import org.junit.Assert.*
import org.junit.Test

class VaultRoutesTest {
    @Test fun graphHasExactlyThreeRootsAndFiveSecondaryPatterns() {
        assertEquals(listOf("overview", "invoices", "settings"), VaultRoutes.ROOTS)
        assertEquals("invoice/{id}", VaultRoutes.INVOICE)
        assertEquals(setOf("settings/webdav", "settings/export", "settings/privacy", "settings/about"), setOf(VaultRoutes.WEBDAV, VaultRoutes.EXPORT, VaultRoutes.PRIVACY, VaultRoutes.ABOUT))
    }

    @Test fun everyInvoiceRowMapsStableDatabaseIdIntoRoute() {
        listOf(1L, 42L, Long.MAX_VALUE).forEach { assertEquals("invoice/$it", VaultRoutes.invoice(it)) }
    }

    @Test fun everySettingsEntryMapsToFormalDestination() {
        val expected = mapOf(
            SettingsRoute.HOME to "settings",
            SettingsRoute.WEBDAV to "settings/webdav",
            SettingsRoute.EXPORT_EMAIL to "settings/export",
            SettingsRoute.PRIVACY to "settings/privacy",
            SettingsRoute.ABOUT to "settings/about",
        )
        expected.forEach { (entry, route) -> assertEquals(route, VaultRoutes.forSetting(entry)) }
    }

    @Test fun overviewStatusActionsSelectInvoiceRoot() {
        listOf("待确认", "重复").forEach { status ->
            val state = VaultNavigation.reduce(VaultUiState(), VaultClick.OpenStatus(status))!!
            assertEquals(VaultUiState.TAB_INVOICES, state.tab)
            assertEquals(VaultRoutes.INVOICES, VaultRoutes.ROOTS[state.tab])
        }
    }

    @Test fun allThreeTabsMapDirectlyToRootRoutes() {
        (0..2).forEach { tab ->
            val state = VaultNavigation.reduce(VaultUiState(invoiceId = 8), VaultClick.SelectTab(tab))!!
            assertEquals(VaultRoutes.ROOTS[tab], VaultRoutes.ROOTS[state.tab])
            assertNull(state.invoiceId)
        }
    }
}
