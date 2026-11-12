/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.core

/** Renders scan results as portable JSON and human-readable Markdown. */
object Report {

    fun json(
        device: Device,
        apps: List<AppEntry>,
        recommendations: List<Recommendation> = emptyList(),
        meta: ReportMeta = ReportMeta(),
        lang: Lang = Lang.ZH
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"environment\": {\n")
        sb.append("    \"tool\": \"").append(esc(meta.tool)).append("\",\n")
        sb.append("    \"host\": \"").append(esc(meta.host)).append("\",\n")
        sb.append("    \"android\": \"").append(esc(meta.android)).append("\",\n")
        sb.append("    \"sdk\": \"").append(esc(meta.sdk)).append("\",\n")
        sb.append("    \"build\": \"").append(esc(meta.build)).append("\",\n")
        sb.append("    \"fingerprint\": \"").append(esc(meta.fingerprint)).append("\",\n")
        sb.append("    \"user\": \"").append(esc(meta.user)).append("\",\n")
        sb.append("    \"generatedAt\": \"").append(esc(meta.generatedAt)).append("\",\n")
        sb.append("    \"minScore\": ").append(meta.minScore?.toString() ?: "null").append(",\n")
        sb.append("    \"limit\": ").append(meta.limit?.toString() ?: "null").append("\n")
        sb.append("  },\n")
        sb.append("  \"device\": {\"serial\": \"").append(esc(device.serial))
            .append("\", \"model\": \"").append(esc(device.model)).append("\"},\n")
        sb.append("  \"apps\": [\n")
        apps.forEachIndexed { i, a ->
            sb.append("    {\n")
            sb.append("      \"package\": \"").append(esc(a.pkg)).append("\",\n")
            sb.append("      \"uid\": \"").append(esc(a.uid)).append("\",\n")
            sb.append("      \"system\": ").append(a.isSystem).append(",\n")
            sb.append("      \"debuggable\": ").append(a.debuggable).append(",\n")
            sb.append("      \"installer\": ").append(a.installer?.let { "\"${esc(it)}\"" } ?: "null").append(",\n")
            sb.append("      \"category\": \"").append(esc(a.category.localized(lang))).append("\",\n")
            sb.append("      \"categoryKey\": \"").append(a.category.name).append("\",\n")
            sb.append("      \"vendor\": ").append(a.vendor?.let { "\"${esc(it)}\"" } ?: "null").append(",\n")
            sb.append("      \"sharedUid\": ").append(a.sharedUid?.let { "\"${esc(it)}\"" } ?: "null").append(",\n")
            sb.append("      \"dataTypes\": ").append(array(a.dataTypes.map { it.localized(lang) })).append(",\n")
            sb.append("      \"dataTypeKeys\": ").append(array(a.dataTypes.map { it.name })).append(",\n")
            sb.append("      \"leaks\": ").append(leaksJson(a, lang)).append(",\n")
            sb.append("      \"leaksByType\": ").append(leaksByTypeJson(a)).append(",\n")
            sb.append("      \"score\": ").append(a.score).append(",\n")
            sb.append("      \"permissions\": ").append(array(a.permissions)).append(",\n")
            sb.append("      \"endpoints\": ").append(array(a.endpoints)).append("\n")
            sb.append("    }").append(if (i == apps.lastIndex) "" else ",").append("\n")
        }
        sb.append("  ],\n")
        sb.append("  \"recommendations\": [\n")
        recommendations.forEachIndexed { i, r ->
            sb.append("    {\n")
            sb.append("      \"package\": \"").append(esc(r.pkg)).append("\",\n")
            sb.append("      \"action\": \"").append(esc(r.action.localized(lang))).append("\",\n")
            sb.append("      \"actionKey\": \"").append(r.action.name).append("\",\n")
            sb.append("      \"risk\": \"").append(esc(r.risk)).append("\",\n")
            sb.append("      \"reason\": \"").append(esc(r.reason)).append("\",\n")
            sb.append("      \"autoSafe\": ").append(r.autoSafe).append("\n")
            sb.append("    }").append(if (i == recommendations.lastIndex) "" else ",").append("\n")
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    fun markdown(
        device: Device,
        apps: List<AppEntry>,
        recommendations: List<Recommendation> = emptyList(),
        meta: ReportMeta = ReportMeta(),
        lang: Lang = Lang.ZH
    ): String {
        val sb = StringBuilder()
        sb.append("# NOIR report\n\n")
        sb.append("- tool: ").append(meta.tool).append("\n")
        sb.append("- host: ").append(meta.host).append("\n")
        sb.append("- device: `").append(device.model).append("` (`").append(device.serial).append("`)\n")
        sb.append("- android: ").append(meta.android).append(" (SDK ").append(meta.sdk).append(")\n")
        sb.append("- build: ").append(meta.build).append("\n")
        sb.append("- fingerprint: ").append(meta.fingerprint).append("\n")
        sb.append("- user: ").append(meta.user).append("\n")
        sb.append("- generated: ").append(meta.generatedAt).append("\n")
        sb.append("- filters: min=").append(meta.minScore ?: "none")
            .append(" limit=").append(meta.limit ?: "none").append("\n")
        sb.append("- apps flagged: ").append(apps.size).append("\n")
        sb.append("- recommendations: ").append(recommendations.size).append("\n\n")

        sb.append("## Overview\n\n")
        sb.append("| score | package | category | vendor | system | perms | upload | cmd |\n")
        sb.append("|---:|---|---|---|:---:|---:|---:|---:|\n")
        apps.forEach { a ->
            sb.append("| ").append(a.score)
                .append(" | `").append(a.pkg).append("`")
                .append(" | ").append(md(a.category.localized(lang)))
                .append(" | ").append(md(a.vendor ?: "—"))
                .append(" | ").append(if (a.isSystem) "yes" else "no")
                .append(" | ").append(a.permissions.size)
                .append(" | ").append(a.uploadEndpoints.size)
                .append(" | ").append(a.commandEndpoints.size)
                .append(" |\n")
        }

        if (recommendations.isNotEmpty()) {
            sb.append("\n## Recommended actions\n\n")
            sb.append("| risk | action | package | why |\n")
            sb.append("|:---:|---|---|---|\n")
            recommendations.forEach { r ->
                sb.append("| ").append(r.risk)
                    .append(" | ").append(md(r.action.localized(lang)))
                    .append(" | `").append(r.pkg).append("`")
                    .append(" | ").append(md(r.reason))
                    .append(" |\n")
            }
        }

        val sharedGroups = apps.filter { it.sharedUid != null }
            .groupBy { it.sharedUid!! }
            .filterValues { it.size > 1 }
        if (sharedGroups.isNotEmpty()) {
            sb.append("\n## Shared UIDs\n\n")
            sb.append("Packages below share one Linux uid, so they effectively hold the *union* of\n")
            sb.append("their permissions. A privileged component can borrow another's grants.\n\n")
            sharedGroups.toList().sortedByDescending { it.second.size }.forEach { (uid, group) ->
                sb.append("- **").append(uid).append("** — ").append(group.size).append(" packages: ")
                sb.append(group.joinToString(", ") { "`${it.pkg}`" }).append("\n")
            }
        }

        sb.append("\n## Details\n")
        apps.forEach { a ->
            sb.append("\n### ").append(a.pkg).append("  (").append(a.score).append(")\n\n")
            sb.append("- uid: ").append(a.uid)
            sb.append(" · system: ").append(a.isSystem)
            sb.append(" · debuggable: ").append(a.debuggable)
            sb.append(" · installer: ").append(a.installer ?: "unknown")
            sb.append(" · category: ").append(a.category.localized(lang))
            sb.append(" · vendor: ").append(a.vendor ?: "unknown")
            sb.append(" · shared uid: ").append(a.sharedUid ?: "—").append("\n")
            if (a.dataTypes.isNotEmpty()) {
                sb.append("- data: ").append(a.dataTypes.joinToString(", ") { it.localized(lang) }).append("\n")
            }
            if (a.leaks.isNotEmpty()) {
                sb.append("- leak paths:\n")
                a.leaks.forEach { (data, hosts) ->
                    sb.append("  - ").append(data.localized(lang)).append(" → ").append(hosts.joinToString(", ")).append("\n")
                }
            }
            if (a.permissions.isNotEmpty()) {
                sb.append("- permissions: ").append(a.permissions.joinToString(", ")).append("\n")
            }
            if (a.endpoints.isNotEmpty()) {
                sb.append("- endpoints:\n")
                a.endpoints.take(25).forEach { sb.append("  - `").append(it).append("`\n") }
            }
        }
        return sb.toString()
    }

    /**
     * One row per flagged app, with the same evidence as the JSON report.
     * Quoted CRLF rows keep the file usable in Excel and other spreadsheet tools.
     */
    fun csv(
        device: Device,
        apps: List<AppEntry>,
        meta: ReportMeta = ReportMeta(),
        lang: Lang = Lang.ZH
    ): String {
        val sb = StringBuilder()
        val header = listOf(
            "device_model", "device_serial", "android", "sdk", "generated_at",
            "package", "uid", "system", "debuggable", "score",
            "category", "category_key", "vendor", "installer", "shared_uid",
            "data_types", "data_type_keys", "permissions",
            "upload_hosts", "command_hosts", "endpoints"
        )
        sb.append(header.joinToString(",") { csvField(it) }).append("\r\n")
        apps.forEach { a ->
            val row = listOf(
                device.model, device.serial, meta.android, meta.sdk, meta.generatedAt,
                a.pkg, a.uid, a.isSystem.toString(), a.debuggable.toString(), a.score.toString(),
                a.category.localized(lang), a.category.name, a.vendor ?: "", a.installer ?: "",
                a.sharedUid ?: "",
                a.dataTypes.joinToString("; ") { it.localized(lang) },
                a.dataTypes.joinToString("; ") { it.name },
                a.permissions.joinToString("; "),
                a.uploadEndpoints.joinToString("; "),
                a.commandEndpoints.joinToString("; "),
                a.endpoints.joinToString("; ")
            )
            sb.append(row.joinToString(",") { csvField(it) }).append("\r\n")
        }
        return sb.toString()
    }

    /** A self-contained, offline HTML report. No external assets or scripts. */
    fun html(
        device: Device,
        apps: List<AppEntry>,
        recommendations: List<Recommendation> = emptyList(),
        meta: ReportMeta = ReportMeta(),
        lang: Lang = Lang.ZH
    ): String {
        val high = recommendations.count { it.risk == "HIGH" }
        val med = recommendations.count { it.risk == "MED" }
        val low = recommendations.count { it.risk == "LOW" }
        val grouped = apps.groupingBy { it.category.localized(lang) }.eachCount().toList()
            .sortedByDescending { it.second }
        val maxGroup = grouped.firstOrNull()?.second ?: 1
        val categories = apps.map { it.category.localized(lang) }.distinct().sorted()
        val shared = apps.filter { it.sharedUid != null }
            .groupBy { it.sharedUid!! }
            .filterValues { it.size > 1 }
            .toList()
            .sortedByDescending { it.second.size }
        val uploadHosts = apps.flatMap { Exposure.uploadHosts(it) }.distinct().size
        val deviceLabel = device.model.ifBlank { device.serial }
        val sb = StringBuilder()

        sb.append("""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>NOIR report — ${h(deviceLabel)}</title>
<style>
:root{--bg:#08080a;--fg:#e8e8ea;--dim:#8a8a92;--line:#24242c;--line2:#33333d;--cyan:#22d3ee;--violet:#a78bfa;--green:#34d399;--amber:#fbbf24;--red:#f87171;--mono:ui-monospace,SFMono-Regular,Menlo,Consolas,"Liberation Mono",monospace}
*{box-sizing:border-box}html{background:var(--bg)}body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.6 var(--mono);-webkit-font-smoothing:antialiased}
body:before{content:"";position:fixed;inset:0;pointer-events:none;background-image:linear-gradient(rgba(255,255,255,.018) 1px,transparent 1px);background-size:100% 3px}
.wrap{position:relative;max-width:1180px;margin:0 auto;padding:48px 24px 70px}
header{border-bottom:1px solid var(--line);padding-bottom:28px;margin-bottom:30px}
.brand{font-size:13px;font-weight:700;letter-spacing:.18em;color:var(--cyan)}.brand span{color:var(--dim);font-weight:400;letter-spacing:.04em}
h1{margin:20px 0 8px;font-size:clamp(26px,4vw,40px);line-height:1.1;letter-spacing:-.03em}.sub{margin:0;color:var(--dim)}.sub b{color:var(--fg);font-weight:500}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:1px;background:var(--line);border:1px solid var(--line);margin:24px 0 34px}.cell{background:var(--bg);padding:16px}.cell .k{color:var(--dim);font-size:11px;letter-spacing:.12em;text-transform:uppercase}.cell .v{margin-top:7px;font-size:15px;overflow-wrap:anywhere}.cell .v.big{font-size:26px;color:var(--cyan)}
section{padding:28px 0;border-top:1px solid var(--line)}h2{margin:0 0 14px;font-size:18px;letter-spacing:-.01em}h3{margin:0;font-size:13px;overflow-wrap:anywhere}
p{margin:0 0 12px;color:#b9b9c0}.dim{color:var(--dim)}.mono{font-family:var(--mono)}
.env{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:8px 24px;color:var(--dim);font-size:12px}.env b{color:#cfcfd6;font-weight:400;overflow-wrap:anywhere}
.risk-table{width:100%;border-collapse:collapse;font-size:13px}.risk-table th,.risk-table td{text-align:left;padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:top}.risk-table th{color:var(--dim);font-weight:400;font-size:11px;letter-spacing:.1em;text-transform:uppercase}.risk-table td:first-child{color:var(--fg)}
.badge{display:inline-block;padding:1px 6px;border:1px solid var(--line2);font-size:11px;letter-spacing:.04em}.badge.high{color:var(--red);border-color:#5c2b31}.badge.med{color:var(--amber);border-color:#5c4b2b}.badge.low{color:var(--green);border-color:#2b5c4b}.badge.auto{color:var(--cyan);border-color:#2b5c66}
.bars{display:grid;gap:6px;max-width:760px}.bar-row{display:grid;grid-template-columns:150px 1fr 44px;gap:12px;align-items:center;font-size:12px}.bar-row .cat{overflow-wrap:anywhere}.bar-row .count{text-align:right;color:var(--fg)}.bar{height:8px;background:#1a1a20;overflow:hidden}.bar i{display:block;height:100%;background:linear-gradient(90deg,var(--cyan),var(--violet))}
.controls{display:flex;gap:10px;flex-wrap:wrap;margin:0 0 16px}.controls input,.controls select{font:inherit;font-size:12px;background:#0c0c10;color:var(--fg);border:1px solid var(--line2);padding:7px 9px;min-width:260px}.controls input:focus,.controls select:focus{outline:1px solid var(--cyan);outline-offset:1px}
.apps{display:grid;gap:8px}.app{border:1px solid var(--line);background:#0c0c10}.app[hidden]{display:none}.app summary{list-style:none;cursor:pointer;padding:12px 14px;display:grid;grid-template-columns:44px 1fr auto;gap:12px;align-items:center}.app summary:hover{background:#111116}.app summary::-webkit-details-marker{display:none}.score{font-size:17px;font-weight:700;text-align:right}.score.high{color:var(--red)}.score.med{color:var(--amber)}.score.low{color:var(--green)}.app-title{display:block;overflow-wrap:anywhere}.app-meta{display:block;margin-top:4px;color:var(--dim);font-size:12px;overflow-wrap:anywhere}.counts{color:var(--dim);font-size:11px;text-align:right;white-space:nowrap}.counts b{color:#cfcfd6;font-weight:500}
.detail{padding:0 14px 14px 70px;border-top:1px solid var(--line);margin-top:0;padding-top:12px}.detail h4{margin:12px 0 5px;color:var(--dim);font-size:11px;letter-spacing:.12em;text-transform:uppercase}.tags{display:flex;flex-wrap:wrap;gap:5px}.tag{border:1px solid var(--line2);padding:1px 6px;font-size:11px;color:#cfcfd6;overflow-wrap:anywhere}.endpoints{margin:0;padding:0;list-style:none;font-size:12px}.endpoints li{padding:2px 0;overflow-wrap:anywhere;color:#b9b9c0}
.leak{margin:3px 0;font-size:12px;color:#b9b9c0}.leak b{color:var(--fg);font-weight:400}.empty{color:var(--dim);font-size:13px}
.shared{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.shared span{border:1px solid var(--line);padding:2px 7px;font-size:11px;color:#cfcfd6}
footer{border-top:1px solid var(--line);margin-top:24px;padding-top:18px;color:var(--dim);font-size:11px;line-height:1.8}
@media(max-width:700px){.wrap{padding:30px 16px 50px}.bar-row{grid-template-columns:105px 1fr 34px}.app summary{grid-template-columns:36px 1fr}.counts{display:none}.detail{padding-left:14px}.controls input,.controls select{width:100%}}
@media print{body:before{display:none}.app[hidden]{display:block}.controls{display:none}body{background:#fff;color:#111}section{border-color:#ddd}.cell,.app{background:#fff;border-color:#ddd}.sub,.dim{color:#555}}
</style>
</head>
<body>
<div class="wrap">
<header>
  <div class="brand">NOIR <span>— android leak audit</span></div>
  <h1>Report</h1>
  <p class="sub"><b>${h(deviceLabel)}</b> · ${h(device.serial)} · ${h(device.product.ifBlank { "unknown product" })}</p>
</header>

<div class="grid">
  <div class="cell"><div class="k">flagged apps</div><div class="v big">${apps.size}</div></div>
  <div class="cell"><div class="k">recommendations</div><div class="v big">${recommendations.size}</div></div>
  <div class="cell"><div class="k">high / med / low</div><div class="v">${high} / ${med} / ${low}</div></div>
  <div class="cell"><div class="k">upload hosts</div><div class="v big">${uploadHosts}</div></div>
  <div class="cell"><div class="k">android</div><div class="v">${h(meta.android.ifBlank { "unknown" })} <span class="dim">SDK ${h(meta.sdk.ifBlank { "?" })}</span></div></div>
  <div class="cell"><div class="k">generated</div><div class="v">${h(meta.generatedAt.ifBlank { "unknown" })}</div></div>
</div>

<section>
  <h2>Environment</h2>
  <div class="env">
    <div>tool <b>${h(meta.tool.ifBlank { "NOIR" })}</b></div>
    <div>host <b>${h(meta.host.ifBlank { "unknown" })}</b></div>
    <div>build <b>${h(meta.build.ifBlank { "unknown" })}</b></div>
    <div>user <b>${h(meta.user.ifBlank { "unknown" })}</b></div>
    <div>fingerprint <b>${h(meta.fingerprint.ifBlank { "unknown" })}</b></div>
  </div>
</section>
""")

        if (grouped.isNotEmpty()) {
            sb.append("""
<section>
  <h2>Exposure by category</h2>
  <div class="bars">
""")
            grouped.forEach { (category, count) ->
                val pct = if (maxGroup == 0) 0 else count * 100 / maxGroup
                sb.append("""    <div class="bar-row"><span class="cat">${h(category)}</span><span class="bar"><i style="width:$pct%"></i></span><span class="count">$count</span></div>
""")
            }
            sb.append("""  </div>
</section>
""")
        }

        sb.append("""
<section>
  <h2>Top findings</h2>
""")
        if (apps.isEmpty()) {
            sb.append("""  <p class="empty">No apps matched the scan filter.</p>
""")
        } else {
            sb.append("""  <table class="risk-table">
    <thead><tr><th>#</th><th>score</th><th>package</th><th>category</th><th>vendor</th><th>perms</th><th>up</th><th>cmd</th></tr></thead>
    <tbody>
""")
            apps.take(20).forEachIndexed { index, app ->
                sb.append("""      <tr><td>${index + 1}</td><td>${scoreHtml(app.score)}</td><td><span class="mono">${h(app.pkg)}</span></td><td>${h(app.category.localized(lang))}</td><td>${h(app.vendor ?: "—")}</td><td>${app.permissions.size}</td><td>${app.uploadEndpoints.size}</td><td>${app.commandEndpoints.size}</td></tr>
""")
            }
            sb.append("""    </tbody>
  </table>
""")
        }
        sb.append("""</section>
""")

        if (recommendations.isNotEmpty()) {
            sb.append("""
<section>
  <h2>Recommended actions</h2>
  <table class="risk-table">
    <thead><tr><th>risk</th><th>action</th><th>package</th><th>why</th></tr></thead>
    <tbody>
""")
            recommendations.forEach { rec ->
                val cls = when (rec.risk.uppercase()) {
                    "HIGH" -> "high"
                    "MED" -> "med"
                    "LOW" -> "low"
                    else -> "low"
                }
                sb.append("""      <tr><td><span class="badge $cls">${h(rec.risk)}</span>${if (rec.autoSafe) """ <span class="badge auto">auto</span>""" else ""}</td><td>${h(rec.action.localized(lang))}</td><td><span class="mono">${h(rec.pkg)}</span></td><td>${h(rec.reason)}</td></tr>
""")
            }
            sb.append("""    </tbody>
  </table>
</section>
""")
        }

        sb.append("""
<section>
  <h2>All apps</h2>
  <div class="controls">
    <input id="q" type="search" placeholder="search package, category or vendor" aria-label="Search apps">
    <select id="cat" aria-label="Filter by category"><option value="">all categories</option>
""")
        categories.forEach { category ->
            sb.append("""      <option value="${h(category)}">${h(category)}</option>
""")
        }
        sb.append("""    </select>
  </div>
  <div class="apps">
""")
        apps.forEach { app ->
            val scoreClass = when {
                app.score >= 40 -> "high"
                app.score >= 25 -> "med"
                else -> "low"
            }
            val search = h("${app.pkg} ${app.category.localized(lang)} ${app.vendor ?: ""}".lowercase())
            sb.append("""    <details class="app" data-search="$search" data-category="${h(app.category.localized(lang))}">
      <summary>
        <span class="score $scoreClass">${app.score}</span>
        <span><span class="app-title">${h(app.pkg)}</span><span class="app-meta">${h(app.category.localized(lang))} · ${h(app.vendor ?: "unknown vendor")} · uid ${h(app.uid)}</span></span>
        <span class="counts"><b>${app.permissions.size}</b> perms · <b>${app.uploadEndpoints.size}</b> up · <b>${app.commandEndpoints.size}</b> cmd</span>
      </summary>
      <div class="detail">
""")
            if (app.permissions.isNotEmpty()) {
                sb.append("""        <h4>Permissions</h4><div class="tags">${app.permissions.joinToString(" ") { """<span class="tag">${h(it)}</span>""" }}</div>
""")
            }
            if (app.dataTypes.isNotEmpty()) {
                sb.append("""        <h4>Data types</h4><div class="tags">${app.dataTypes.joinToString(" ") { """<span class="tag">${h(it.localized(lang))}</span>""" }}</div>
""")
            }
            if (app.leaks.isNotEmpty()) {
                sb.append("""        <h4>Leak paths</h4>
""")
                app.leaks.forEach { (data, hosts) ->
                    sb.append("""        <div class="leak"><b>${h(data.localized(lang))}</b> → ${h(hosts.joinToString(", "))}</div>
""")
                }
            }
            if (app.endpoints.isNotEmpty()) {
                sb.append("""        <h4>Endpoints</h4><ul class="endpoints">${app.endpoints.joinToString("") { """<li><span class="mono">${h(it)}</span></li>""" }}</ul>
""")
            }
            sb.append("""        <h4>Identity</h4><div class="env"><div>uid <b>${h(app.uid)}</b></div><div>system <b>${if (app.isSystem) "yes" else "no"}</b></div><div>debuggable <b>${if (app.debuggable) "yes" else "no"}</b></div><div>installer <b>${h(app.installer ?: "unknown")}</b></div><div>shared uid <b>${h(app.sharedUid ?: "—")}</b></div></div>
      </div>
    </details>
""")
        }
        sb.append("""  </div>
</section>
""")

        if (shared.isNotEmpty()) {
            sb.append("""
<section>
  <h2>Shared UIDs</h2>
  <p>Packages sharing one Linux uid hold the union of their permissions.</p>
""")
            shared.forEach { (uid, group) ->
                sb.append("""  <div class="leak"><b>${h(uid)}</b> — ${group.size} packages</div>
  <div class="shared">${group.joinToString("") { """<span>${h(it.pkg)}</span>""" }}</div>
""")
            }
            sb.append("""</section>
""")
        }

        sb.append("""
<footer>
  NOIR is a static audit: it proves capability and endpoints, not that a transfer happened.
  Report generated locally · no telemetry · MIT © 2026 Verlintas.
</footer>
</div>
<script>
(function(){
  var q=document.getElementById('q'),cat=document.getElementById('cat');
  function apply(){
    var s=(q.value||'').toLowerCase(),c=cat.value;
    document.querySelectorAll('.app').forEach(function(el){
      var ok=(!s||el.getAttribute('data-search').indexOf(s)!==-1)&&(!c||el.getAttribute('data-category')===c);
      el.hidden=!ok;
    });
  }
  q.addEventListener('input',apply);cat.addEventListener('change',apply);
})();
</script>
</body>
</html>
""")
        return sb.toString()
    }

    private fun scoreHtml(score: Int): String = when {
        score >= 40 -> """<span class="badge high">$score</span>"""
        score >= 25 -> """<span class="badge med">$score</span>"""
        else -> """<span class="badge low">$score</span>"""
    }

    private fun h(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (c in value) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun leaksJson(app: AppEntry, lang: Lang): String {
        if (app.leaks.isEmpty()) return "{}"
        return app.leaks.entries.joinToString(prefix = "{", postfix = "}") { (data, hosts) ->
            "\"${esc(data.localized(lang))}\":" + array(hosts)
        }
    }

    /** Stable-key variant; `leaks` remains for human-facing compatibility. */
    private fun leaksByTypeJson(app: AppEntry): String {
        if (app.leaks.isEmpty()) return "{}"
        return app.leaks.entries.joinToString(prefix = "{", postfix = "}") { (data, hosts) ->
            "\"${data.name}\":" + array(hosts)
        }
    }

    private fun md(value: String): String =
        value.replace("|", "\\|").replace("\r", " ").replace("\n", " ")

    private fun csvField(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun array(items: List<String>): String =
        items.joinToString(prefix = "[", postfix = "]") { "\"${esc(it)}\"" }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
            }
        }
        return sb.toString()
    }
}
