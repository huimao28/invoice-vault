package com.huimaotech.invoicevault

/** Pure route mapping used by the Navigation Compose graph and click audit tests. */
object VaultRoutes {
    const val OVERVIEW = "overview"
    const val INVOICES = "invoices"
    const val SETTINGS = "settings"
    const val INVOICE = "invoice/{id}"
    const val WEBDAV = "settings/webdav"
    const val EXPORT = "settings/export"
    const val PRIVACY = "settings/privacy"
    const val ABOUT = "settings/about"
    val ROOTS = listOf(OVERVIEW, INVOICES, SETTINGS)

    fun invoice(id: Long) = "invoice/$id"
    fun forSetting(route: SettingsRoute) = when (route) {
        SettingsRoute.HOME -> SETTINGS
        SettingsRoute.WEBDAV -> WEBDAV
        SettingsRoute.EXPORT_EMAIL -> EXPORT
        SettingsRoute.PRIVACY -> PRIVACY
        SettingsRoute.ABOUT -> ABOUT
    }
}

/** Legacy pure reducer retained for compatibility tests; production navigation is NavController-owned. */
data class VaultUiState(
    val tab: Int = TAB_OVERVIEW,
    val settingsRoute: SettingsRoute = SettingsRoute.HOME,
    val invoiceId: Long? = null,
) {
    companion object {
        const val TAB_OVERVIEW = 0
        const val TAB_INVOICES = 1
        const val TAB_SETTINGS = 2
    }
}

sealed interface VaultClick {
    data class SelectTab(val index: Int) : VaultClick
    data class OpenStatus(val status: String) : VaultClick
    data class OpenInvoice(val id: Long) : VaultClick
    data class OpenSetting(val route: SettingsRoute) : VaultClick
    data object Back : VaultClick
}

object VaultNavigation {
    fun reduce(state: VaultUiState, click: VaultClick): VaultUiState? = when (click) {
        is VaultClick.SelectTab -> state.copy(
            tab = click.index.coerceIn(VaultUiState.TAB_OVERVIEW, VaultUiState.TAB_SETTINGS),
            settingsRoute = SettingsRoute.HOME,
            invoiceId = null,
        )
        is VaultClick.OpenStatus -> state.copy(
            tab = VaultUiState.TAB_INVOICES,
            settingsRoute = SettingsRoute.HOME,
            invoiceId = null,
        )
        is VaultClick.OpenInvoice -> state.copy(invoiceId = click.id)
        is VaultClick.OpenSetting -> state.copy(
            tab = VaultUiState.TAB_SETTINGS,
            settingsRoute = click.route,
            invoiceId = null,
        )
        VaultClick.Back -> when {
            state.invoiceId != null -> state.copy(invoiceId = null)
            state.settingsRoute != SettingsRoute.HOME -> state.copy(settingsRoute = SettingsRoute.HOME)
            else -> null // Root is deliberately delegated to the Android system.
        }
    }

    fun isSecondary(state: VaultUiState) =
        state.invoiceId != null || state.settingsRoute != SettingsRoute.HOME
}
