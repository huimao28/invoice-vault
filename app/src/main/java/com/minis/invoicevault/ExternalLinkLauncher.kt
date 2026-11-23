package com.minis.invoicevault

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Pure, unit-testable contract: package visibility is deliberately not consulted. */
data class ExternalIntentContract(
    val action: String,
    val category: String,
    val uri: String,
    val newTask: Boolean
)

object ExternalIntentFactory {
    fun contract(raw: String, contextIsActivity: Boolean): ExternalIntentContract? {
        if (!ExternalLinks.isAllowedHttps(raw)) return null
        return ExternalIntentContract(
            action = Intent.ACTION_VIEW,
            category = Intent.CATEGORY_BROWSABLE,
            uri = raw,
            newTask = !contextIsActivity
        )
    }

    fun build(context: Context, raw: String): Intent? {
        val spec = contract(raw, context is Activity) ?: return null
        return Intent(spec.action, Uri.parse(spec.uri)).addCategory(spec.category).apply {
            if (spec.newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

fun safeOpenExternal(context: Context, raw: String, onMessage: (String) -> Unit) {
    val intent = ExternalIntentFactory.build(context, raw)
    if (intent == null) {
        onMessage("链接未获允许")
        return
    }
    try {
        // Never gate launch on PackageManager.resolveActivity/queryIntentActivities:
        // Android 11 package visibility may hide a perfectly valid browser.
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onMessage("没有可打开此链接的应用")
    } catch (_: SecurityException) {
        onMessage("系统阻止了外部链接")
    } catch (_: RuntimeException) {
        onMessage("无法打开外部链接")
    }
}
