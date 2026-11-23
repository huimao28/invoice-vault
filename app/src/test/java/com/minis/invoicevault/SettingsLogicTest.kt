package com.minis.invoicevault

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLogicTest {
    @Test fun routeNamesRoundTrip() {
        SettingsRoute.entries.forEach { assertEquals(it, SettingsRoute.valueOf(it.name)) }
    }

    @Test fun webDavSummaryUsesHostAndStatus() {
        assertEquals("未配置", SettingsSummary.webDav(""))
        assertEquals("dav.example.com", SettingsSummary.webDav("https://dav.example.com/path"))
        assertEquals("dav.example.com · 连接正常", SettingsSummary.webDav("https://dav.example.com/path", "连接正常"))
    }

    @Test fun emailSummaryHandlesWhitespace() {
        assertEquals("未设置邮箱", SettingsSummary.email("  "))
        assertEquals("me@example.com", SettingsSummary.email(" me@example.com "))
    }
}


class ExternalLinksTest {
    @Test fun onlyExactPredefinedHttpsLinksAreAllowed() {
        ExternalLinks.allowed.forEach { org.junit.Assert.assertTrue(ExternalLinks.isAllowedHttps(it)) }
        listOf("http://huimaotech.com/", "https://evil.example/", "https://huimaotech.com/sponsor/evil", "javascript:alert(1)", "https://user@huimaotech.com/").forEach {
            org.junit.Assert.assertFalse(it, ExternalLinks.isAllowedHttps(it))
        }
    }

    @Test fun externalIntentContractDoesNotDependOnPackageResolution() {
        val activity = ExternalIntentFactory.contract(ExternalLinks.GITHUB, contextIsActivity = true)!!
        assertEquals(android.content.Intent.ACTION_VIEW, activity.action)
        assertEquals(android.content.Intent.CATEGORY_BROWSABLE, activity.category)
        assertEquals(ExternalLinks.GITHUB, activity.uri)
        org.junit.Assert.assertFalse(activity.newTask)

        val appContext = ExternalIntentFactory.contract(ExternalLinks.SPONSOR, contextIsActivity = false)!!
        org.junit.Assert.assertTrue(appContext.newTask)
        org.junit.Assert.assertNull(ExternalIntentFactory.contract("https://evil.example/", false))
    }

    @Test fun semanticRoutesAreStable() {
        assertEquals(SettingsSemanticColor.WEBDAV_BLUE, SettingsSemantics.colorFor(SettingsRoute.WEBDAV))
        assertEquals(SettingsSemanticColor.EXPORT_TEAL, SettingsSemantics.colorFor(SettingsRoute.EXPORT_EMAIL))
        assertEquals(SettingsSemanticColor.PRIVACY_PURPLE, SettingsSemantics.colorFor(SettingsRoute.PRIVACY))
        assertEquals(SettingsSemanticColor.ABOUT_ORANGE, SettingsSemantics.colorFor(SettingsRoute.ABOUT))
    }

    @Test fun routeBackLogicLeavesRootToSystem() {
        assertEquals("system", RouteBackLogic.destination(SettingsRoute.HOME, false))
        assertEquals(SettingsRoute.HOME.name, RouteBackLogic.destination(SettingsRoute.ABOUT, false))
        assertEquals("invoice-list", RouteBackLogic.destination(SettingsRoute.HOME, true))
    }
}
