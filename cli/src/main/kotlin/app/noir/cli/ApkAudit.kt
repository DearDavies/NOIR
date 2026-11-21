/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.cli

import app.noir.core.AppEntry
import app.noir.core.Classifier
import app.noir.core.IgnoreRules
import app.noir.core.Rules
import app.noir.core.apk.ApkScan
import app.noir.core.apk.ManifestAnalyzer
import java.io.File

internal data class OfflineAudit(
    val apps: List<AppEntry>,
    val files: Int,
    val skipped: Int,
    val targetSdk: String?
)

/**
 * Audits APK files without a device: manifest permissions + static dex URLs.
 * Split APKs are merged by package name.
 */
internal fun auditApks(
    files: List<File>,
    ignore: IgnoreRules = IgnoreRules.EMPTY,
    minScore: Int = 0,
    limit: Int = 500
): OfflineAudit {
    data class Acc(
        val permissions: LinkedHashSet<String> = LinkedHashSet(),
        val endpoints: LinkedHashSet<String> = LinkedHashSet(),
        var debuggable: Boolean = false,
        var targetSdk: String? = null
    )

    val grouped = LinkedHashMap<String, Acc>()
    var scanned = 0
    var skipped = 0

    files.forEach { file ->
        val facts = runCatching { ManifestAnalyzer.analyze(file) }.getOrNull()
        if (facts == null) {
            skipped++
            return@forEach
        }
        val pkg = facts.packageName?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        if (ignore.ignoresPackage(pkg)) {
            skipped++
            return@forEach
        }
        val urls = runCatching { ApkScan.urls(file) }.getOrDefault(emptyList())
        val acc = grouped.getOrPut(pkg) { Acc() }
        acc.permissions += facts.permissions
        acc.endpoints += urls
        acc.debuggable = acc.debuggable || facts.debuggable == true
        if (acc.targetSdk == null) acc.targetSdk = facts.targetSdk
        scanned++
    }

    val apps = grouped.map { (pkg, acc) ->
        val permissions = acc.permissions.filter { it in Rules.SENSITIVE }
        val endpoints = ignore.filterEndpoints(acc.endpoints.toList())
        AppEntry(
            pkg = pkg,
            uid = "",
            isSystem = false,
            debuggable = acc.debuggable,
            installer = null,
            permissions = permissions,
            endpoints = endpoints,
            score = Rules.score(permissions, endpoints, isSystem = false, debuggable = acc.debuggable),
            category = Classifier.category(pkg, endpoints, permissions, isSystem = false),
            vendor = Classifier.vendor(pkg, endpoints)
        )
    }
        .filter { it.score >= minScore }
        .sortedByDescending { it.score }
        .take(limit)

    return OfflineAudit(
        apps = apps,
        files = scanned,
        skipped = skipped,
        targetSdk = grouped.values.firstNotNullOfOrNull { it.targetSdk }
    )
}
