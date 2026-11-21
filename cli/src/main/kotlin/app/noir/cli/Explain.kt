/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.cli

import app.noir.core.AppEntry
import app.noir.core.Lang
import app.noir.core.Recommendation
import app.noir.core.localized
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/** "Why is this app flagged?" rendered from an existing report. */
internal fun explainReport(app: AppEntry, recommendations: List<Recommendation>, lang: Lang): String = buildString {
    appendLine("NOIR explain · ${app.pkg}")
    appendLine("  score      ${app.score} · ${app.category.localized(lang)}${app.vendor?.let { " · $it" } ?: ""}")
    appendLine(
        "  kind       ${if (app.isSystem) t("系统", "system") else t("用户", "user")}" +
            " · debuggable ${app.debuggable}" +
            " · installer ${app.installer ?: "unknown"}"
    )
    appendLine("  uid        ${app.uid}${app.sharedUid?.let { " · shared ${it}" } ?: ""}")
    appendLine()
    appendLine(t("  能读到的数据", "  can read"))
    if (app.dataTypes.isEmpty()) {
        appendLine("    —")
    } else {
        app.dataTypes.sortedBy { it.ordinal }.forEach { appendLine("    ${it.localized(lang)}") }
    }
    appendLine()
    appendLine(t("  上报路径", "  upload paths"))
    if (app.leaks.isEmpty()) {
        appendLine("    —")
    } else {
        app.leaks.entries.sortedBy { it.key.ordinal }.forEach { (data, hosts) ->
            appendLine("    ${data.localized(lang)} → ${hosts.joinToString(", ")}")
        }
    }
    appendLine()
    appendLine(t("  权限 (${app.permissions.size})", "  permissions (${app.permissions.size})"))
    if (app.permissions.isEmpty()) appendLine("    —") else app.permissions.forEach { appendLine("    $it") }
    appendLine()
    appendLine(t("  端点 (${app.endpoints.size})", "  endpoints (${app.endpoints.size})"))
    if (app.endpoints.isEmpty()) {
        appendLine("    —")
    } else {
        app.endpoints.take(40).forEach { appendLine("    $it") }
        if (app.endpoints.size > 40) appendLine("    … ${app.endpoints.size - 40} more")
    }
    if (recommendations.isNotEmpty()) {
        appendLine()
        appendLine(t("  建议", "  recommendations"))
        recommendations.forEach { r ->
            appendLine("    ${r.risk.padEnd(5)} ${r.action.localized(lang)} · ${r.reason}")
        }
    }
}

/** Compact form used when explaining several apps at once. */
internal fun explainBrief(app: AppEntry, recommendations: List<Recommendation>, lang: Lang): String = buildString {
    appendLine("NOIR explain · ${app.pkg}")
    appendLine("  score      ${app.score} · ${app.category.localized(lang)}${app.vendor?.let { " · $it" } ?: ""}")
    appendLine(
        "  can read   " +
            if (app.dataTypes.isEmpty()) "—" else app.dataTypes.sortedBy { it.ordinal }.joinToString(", ") { it.localized(lang) }
    )
    if (app.leaks.isNotEmpty()) {
        appendLine("  uploads")
        app.leaks.entries.sortedBy { it.key.ordinal }.forEach { (data, hosts) ->
            appendLine("    ${data.localized(lang)} → ${hosts.joinToString(", ")}")
        }
    }
    recommendations.forEach { r ->
        appendLine("  action     ${r.risk} · ${r.action.localized(lang)} · ${r.reason}")
    }
}

/** Machine-readable form of [explainReport]. */
internal fun explainJson(app: AppEntry, recommendations: List<Recommendation>, lang: Lang): String {
    val data = linkedMapOf<String, Any?>(
        "package" to app.pkg,
        "score" to app.score,
        "category" to app.category.localized(lang),
        "categoryKey" to app.category.name,
        "vendor" to app.vendor,
        "system" to app.isSystem,
        "debuggable" to app.debuggable,
        "installer" to app.installer,
        "uid" to app.uid,
        "sharedUid" to app.sharedUid,
        "permissions" to app.permissions,
        "dataTypes" to app.dataTypes.sortedBy { it.ordinal }.map {
            mapOf("key" to it.name, "label" to it.localized(lang))
        },
        "leaks" to app.leaks.entries.sortedBy { it.key.ordinal }.map {
            mapOf(
                "dataType" to it.key.name,
                "label" to it.key.localized(lang),
                "hosts" to it.value
            )
        },
        "endpoints" to app.endpoints,
        "uploadEndpoints" to app.uploadEndpoints,
        "commandEndpoints" to app.commandEndpoints,
        "recommendations" to recommendations.map {
            mapOf(
                "actionKey" to it.action.name,
                "action" to it.action.localized(lang),
                "risk" to it.risk,
                "reason" to it.reason,
                "autoSafe" to it.autoSafe
            )
        }
    )
    return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data)
}

internal fun explainJsonBatch(
    items: List<Pair<AppEntry, List<Recommendation>>>,
    lang: Lang
): String {
    val array = JsonArray()
    items.forEach { (app, recommendations) ->
        array.add(JsonParser.parseString(explainJson(app, recommendations, lang)))
    }
    return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(array)
}
