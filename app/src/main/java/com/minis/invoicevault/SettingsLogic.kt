package com.minis.invoicevault

import java.net.URI

enum class SettingsRoute { HOME, WEBDAV, EXPORT_EMAIL, PRIVACY, ABOUT }

enum class SettingsSemanticColor { WEBDAV_BLUE, EXPORT_TEAL, PRIVACY_PURPLE, ABOUT_ORANGE, GITHUB_BLUE, WEBSITE_TEAL, SPONSOR_ORANGE }

object SettingsSemantics {
    fun colorFor(route: SettingsRoute): SettingsSemanticColor = when (route) {
        SettingsRoute.WEBDAV -> SettingsSemanticColor.WEBDAV_BLUE
        SettingsRoute.EXPORT_EMAIL -> SettingsSemanticColor.EXPORT_TEAL
        SettingsRoute.PRIVACY -> SettingsSemanticColor.PRIVACY_PURPLE
        SettingsRoute.ABOUT -> SettingsSemanticColor.ABOUT_ORANGE
        SettingsRoute.HOME -> SettingsSemanticColor.ABOUT_ORANGE
    }
}

object ExternalLinks {
    const val GITHUB = "https://github.com/huimao28"
    const val WEBSITE = "https://huimaotech.com/"
    const val SPONSOR = "https://huimaotech.com/sponsor/"
    val allowed = setOf(GITHUB, WEBSITE, SPONSOR)

    fun isAllowedHttps(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && uri.host != null && raw in allowed
    }.getOrDefault(false)

    fun host(raw: String): String = runCatching { URI(raw).host.orEmpty() }.getOrDefault("")
}

object RouteBackLogic {
    fun destination(route: SettingsRoute, hasInvoiceDetail: Boolean): String = when {
        hasInvoiceDetail -> "invoice-list"
        route != SettingsRoute.HOME -> SettingsRoute.HOME.name
        else -> "system"
    }
}

object SettingsSummary {
    fun webDav(serverUrl: String, status: String = ""): String {
        if (serverUrl.isBlank()) return "未配置"
        val host = runCatching { URI(serverUrl.trim()).host }.getOrNull().orEmpty()
            .ifBlank { serverUrl.trim().substringBefore('/').take(48) }
        return if (status.isBlank()) host else "$host · $status"
    }

    fun email(address: String): String = address.trim().ifBlank { "未设置邮箱" }
}
