/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.cli

import app.noir.core.ActionType
import app.noir.core.AppEntry
import app.noir.core.Category
import app.noir.core.Lang
import app.noir.core.Recommendation
import kotlin.test.Test
import kotlin.test.assertTrue

class ExplainTest {

    private val app = AppEntry(
        pkg = "com.example.app",
        uid = "10200",
        isSystem = false,
        debuggable = false,
        installer = "com.android.vending",
        permissions = listOf("INTERNET", "READ_SMS", "ACCESS_FINE_LOCATION"),
        endpoints = listOf("https://api.example.com/collect"),
        score = 35,
        category = Category.TELEMETRY,
        vendor = "Example"
    )

    private val recommendation = Recommendation(
        pkg = app.pkg,
        action = ActionType.REVIEW,
        risk = "MED",
        reason = "telemetry endpoint",
        autoSafe = false
    )

    @Test
    fun `explains the evidence in text form`() {
        val text = explainReport(app, listOf(recommendation), Lang.EN)
        assertTrue(text.contains("com.example.app"))
        assertTrue(text.contains("api.example.com"))
        assertTrue(text.contains("READ_SMS"))
        assertTrue(text.contains("telemetry endpoint"))
    }

    @Test
    fun `explains the evidence in json form`() {
        val json = explainJson(app, listOf(recommendation), Lang.EN)
        assertTrue(json.contains("\"package\""))
        assertTrue(json.contains("\"dataTypes\""))
        assertTrue(json.contains("\"leaks\""))
        assertTrue(json.contains("\"actionKey\": \"REVIEW\""))
        assertTrue(json.contains("api.example.com"))
    }
}
