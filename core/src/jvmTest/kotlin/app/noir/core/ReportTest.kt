/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportTest {

    @Test
    fun `json escapes quotes and control characters`() {
        val entry = AppEntry(
            pkg = "com.\"weird\"",
            uid = "1",
            isSystem = false,
            debuggable = false,
            installer = null,
            permissions = emptyList(),
            endpoints = listOf("https://a.example.com/x"),
            score = 0
        )
        val json = Report.json(Device("s", "device"), listOf(entry))
        assertTrue(json.contains("com.\\\"weird\\\""))
        assertTrue(json.contains("\"apps\""))
    }

    @Test
    fun `report includes reproducible environment details`() {
        val meta = ReportMeta(
            tool = "NOIR test",
            host = "macOS aarch64",
            android = "16",
            sdk = "36",
            build = "PLK110_16.0.0",
            fingerprint = "oneplus/PLK110:16/abc",
            user = "0",
            generatedAt = "2026-11-04T00:00:00Z"
        )
        val json = Report.json(Device("s", "device", "PLK110"), emptyList(), meta = meta)
        val md = Report.markdown(Device("s", "device", "PLK110"), emptyList(), meta = meta)
        assertTrue(json.contains("\"sdk\": \"36\""))
        assertTrue(json.contains("\"build\": \"PLK110_16.0.0\""))
        assertTrue(md.contains("- user: 0"))
        assertTrue(md.contains("PLK110_16.0.0"))
    }

    @Test
    fun `html report escapes app data and stays self contained`() {
        val entry = AppEntry(
            pkg = "com.example.<script>",
            uid = "1",
            isSystem = false,
            debuggable = false,
            installer = null,
            permissions = listOf("INTERNET", "READ_SMS"),
            endpoints = listOf("https://example.com/<x>"),
            score = 35,
            category = Category.TELEMETRY,
            vendor = "Example & Co"
        )
        val meta = ReportMeta(
            tool = "NOIR test",
            android = "16",
            sdk = "36",
            user = "0",
            generatedAt = "2026-11-06T00:00:00Z"
        )
        val html = Report.html(Device("s", "device", "PLK110"), listOf(entry), meta = meta)
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("Example &amp; Co"))
        assertTrue(html.contains("report.html") || html.contains("<title>NOIR report"))
        assertTrue(!html.contains("https://fonts."))
        assertTrue(html.contains("data-search="))
    }

    @Test
    fun `english report is fully translated and keeps stable keys`() {
        val entry = AppEntry(
            pkg = "com.example",
            uid = "1",
            isSystem = false,
            debuggable = false,
            installer = null,
            permissions = listOf("INTERNET", "READ_SMS"),
            endpoints = listOf("https://tracker.example.com/upload"),
            score = 30,
            category = Category.TELEMETRY,
            vendor = "Example"
        )
        val recommendation = Recommendation(
            pkg = "com.example",
            action = ActionType.APPOP_USAGE_STATS,
            risk = "MED",
            reason = "system telemetry component can read app usage statistics",
            autoSafe = true
        )
        val meta = ReportMeta(
            tool = "NOIR test",
            host = "macOS aarch64",
            android = "16",
            sdk = "36",
            build = "build",
            fingerprint = "fingerprint",
            user = "0",
            generatedAt = "2026-11-06T00:00:00Z"
        )
        val device = Device("s", "device", "PLK110", "product")
        val json = Report.json(device, listOf(entry), listOf(recommendation), meta, Lang.EN)
        val md = Report.markdown(device, listOf(entry), listOf(recommendation), meta, Lang.EN)
        val html = Report.html(device, listOf(entry), listOf(recommendation), meta, Lang.EN)

        val han = Regex("[\\u4e00-\\u9fff]")
        listOf(json, md, html).forEach { text ->
            assertTrue(!han.containsMatchIn(text), "untranslated report output")
        }
        assertTrue(json.contains("\"category\": \"telemetry\""))
        assertTrue(json.contains("\"categoryKey\": \"TELEMETRY\""))
        assertTrue(json.contains("\"actionKey\": \"APPOP_USAGE_STATS\""))
        assertTrue(json.contains("\"leaksByType\": {\"SMS\":"))
        assertTrue(html.contains("<html lang=\"en\">"))
    }

    @Test
    fun `html escapes risk class and deduplicates upload hosts`() {
        val entry = AppEntry(
            pkg = "com.example",
            uid = "1",
            isSystem = false,
            debuggable = false,
            installer = null,
            permissions = listOf("INTERNET", "READ_SMS"),
            endpoints = listOf(
                "https://tracker.example.com/upload",
                "https://tracker.example.com/report"
            ),
            score = 30,
            category = Category.TELEMETRY
        )
        val recommendation = Recommendation(
            pkg = "com.example",
            action = ActionType.REVIEW,
            risk = "x\" onclick=\"alert(1)",
            reason = "reason",
            autoSafe = false
        )
        val html = Report.html(
            Device("s", "device", "Pixel"),
            listOf(entry),
            listOf(recommendation),
            ReportMeta(),
            Lang.EN
        )
        assertTrue(html.contains("upload hosts</div><div class=\"v big\">1</div>"))
        assertTrue(!html.contains("onclick=\"alert(1)\""))
    }

    @Test
    fun `markdown escapes table pipes`() {
        val entry = AppEntry(
            pkg = "com.example",
            uid = "1",
            isSystem = false,
            debuggable = false,
            installer = null,
            permissions = emptyList(),
            endpoints = emptyList(),
            score = 0,
            category = Category.OTHER,
            vendor = "A|B"
        )
        val md = Report.markdown(Device("s", "device"), listOf(entry), lang = Lang.EN)
        assertTrue(md.contains("A\\|B"))
    }

    @Test
    fun `markdown contains the report table header`() {
        val entry = AppEntry(
            pkg = "com.example",
            uid = "1",
            isSystem = true,
            debuggable = false,
            installer = "com.android.vending",
            permissions = listOf("INTERNET"),
            endpoints = emptyList(),
            score = 3,
            category = Category.SYSTEM
        )
        val md = Report.markdown(Device("s", "device", "Pixel"), listOf(entry))
        assertTrue(md.startsWith("# NOIR report"))
        assertTrue(md.contains("| score | package |"))
        assertTrue(md.contains("`com.example`"))
    }

    @Test
    fun `csv quotes commas quotes and newlines`() {
        val entry = AppEntry(
            pkg = "com.example",
            uid = "1",
            isSystem = false,
            debuggable = false,
            installer = null,
            permissions = listOf("INTERNET", "READ_SMS"),
            endpoints = listOf("https://a.example/x,y"),
            score = 35,
            category = Category.TELEMETRY,
            vendor = "A, \"B\"\nC"
        )
        val meta = ReportMeta(android = "16", sdk = "36", generatedAt = "2026-11-12T00:00:00Z")
        val csv = Report.csv(Device("s", "device", "Pixel"), listOf(entry), meta, Lang.EN)

        assertTrue(csv.startsWith("\"device_model\",\"device_serial\""))
        assertTrue(csv.contains("\"A, \"\"B\"\"\nC\""))
        assertTrue(csv.contains("\"https://a.example/x,y\""))
        assertTrue(csv.contains("\"com.example\""))
    }
}
