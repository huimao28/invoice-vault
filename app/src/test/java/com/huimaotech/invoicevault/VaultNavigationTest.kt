package com.huimaotech.invoicevault

import org.junit.Assert.*
import org.junit.Test

class VaultNavigationTest {
    @Test fun bottomTabsReturnToStableTopLevelState() {
        for (tab in 0..2) {
            val next = VaultNavigation.reduce(
                VaultUiState(invoiceId = 9, settingsRoute = SettingsRoute.ABOUT),
                VaultClick.SelectTab(tab),
            )!!
            assertEquals(tab, next.tab)
            assertEquals(SettingsRoute.HOME, next.settingsRoute)
            assertNull(next.invoiceId)
        }
    }

    @Test fun overviewStatusEntryOpensFilteredInvoiceTab() {
        val next = VaultNavigation.reduce(VaultUiState(), VaultClick.OpenStatus("待确认"))!!
        assertEquals(VaultUiState.TAB_INVOICES, next.tab)
        assertNull(next.invoiceId)
    }

    @Test fun recentAndListRowsOpenInvoiceByStableDatabaseId() {
        val next = VaultNavigation.reduce(VaultUiState(), VaultClick.OpenInvoice(42))!!
        assertEquals(42L, next.invoiceId)
        assertTrue(VaultNavigation.isSecondary(next))
    }

    @Test fun everySettingsEntryOpensAndBackReturnsHome() {
        SettingsRoute.entries.filter { it != SettingsRoute.HOME }.forEach { route ->
            val opened = VaultNavigation.reduce(VaultUiState(), VaultClick.OpenSetting(route))!!
            assertEquals(VaultUiState.TAB_SETTINGS, opened.tab)
            assertEquals(route, opened.settingsRoute)
            assertEquals(SettingsRoute.HOME, VaultNavigation.reduce(opened, VaultClick.Back)!!.settingsRoute)
        }
    }

    @Test fun detailBackClearsOnlyDetailAndRootBackIsSystemOwned() {
        val detail = VaultUiState(tab = 1, invoiceId = 7)
        val popped = VaultNavigation.reduce(detail, VaultClick.Back)!!
        assertNull(popped.invoiceId)
        assertNull(VaultNavigation.reduce(popped, VaultClick.Back))
    }
}
