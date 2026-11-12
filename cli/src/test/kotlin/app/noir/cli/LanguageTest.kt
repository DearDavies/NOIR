/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.cli

import app.noir.core.Device
import app.noir.core.Lang
import app.noir.core.adb.Adb
import app.noir.core.platform.Platform
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageTest {

    private val han = Regex("[\\u4e00-\\u9fff]")

    @AfterTest
    fun resetLanguage() {
        LANG = Lang.ZH
        LANG_LOCKED = false
    }

    @Test
    fun `host locale selects the interface language`() {
        assertEquals(Lang.ZH, detectHostLanguage(mapOf("LANG" to "zh_CN.UTF-8"), Locale.US))
        assertEquals(Lang.EN, detectHostLanguage(mapOf("LANG" to "en_US.UTF-8"), Locale.SIMPLIFIED_CHINESE))
        assertEquals(Lang.ZH, detectHostLanguage(emptyMap(), Locale.SIMPLIFIED_CHINESE))
        assertEquals(Lang.EN, detectHostLanguage(mapOf("LANG" to "C"), Locale.US))
    }

    @Test
    fun `explicit language wins over the host locale`() {
        initLanguage(listOf("--lang", "en"))
        assertEquals(Lang.EN, LANG)
        assertTrue(LANG_LOCKED)

        initLanguage(listOf("--lang=zh"))
        assertEquals(Lang.ZH, LANG)
        assertTrue(LANG_LOCKED)
    }

    @Test
    fun `device locale is read from the device`() {
        if (Platform.isWindows) return
        val script = File(Files.createTempDirectory("noir-lang").toFile(), "fake-adb")
        script.writeText("#!/bin/sh\necho zh-CN\n")
        script.setExecutable(true)

        assertEquals(
            Lang.ZH,
            detectDeviceLanguage(Adb(script.absolutePath), Device("serial", "device", "Pixel"))
        )
    }

    @Test
    fun `device language overrides host locale unless pinned`() {
        if (Platform.isWindows) return
        val script = File(Files.createTempDirectory("noir-lang").toFile(), "fake-adb")
        script.writeText("#!/bin/sh\necho en-US\n")
        script.setExecutable(true)
        val adb = Adb(script.absolutePath)
        val device = Device("serial", "device", "Pixel")

        LANG = Lang.ZH
        LANG_LOCKED = false
        applyDeviceLanguage(adb, device)
        assertEquals(Lang.EN, LANG)

        LANG = Lang.ZH
        LANG_LOCKED = true
        applyDeviceLanguage(adb, device)
        assertEquals(Lang.ZH, LANG)
    }

    @Test
    fun `ascii mode removes box glyphs from guidance`() {
        ASCII = true
        try {
            val about = Guidance.about("0.4.3")
            val error = Guidance.error("x")
            val guide = Guidance.deviceGuide(Lang.EN)
            assertFalse(about.contains("╭"))
            assertFalse(error.contains("╭"))
            assertFalse(guide.contains("╭"))
            assertFalse(about.contains("█"))
        } finally {
            ASCII = false
        }
    }

    @Test
    fun `english guidance has no chinese characters`() {
        val blocks = listOf(
            Guidance.deviceGuide(Lang.EN),
            Guidance.unauthorizedGuide(Lang.EN),
            Guidance.adbMissing(Lang.EN),
            Guidance.jadxMissing(Lang.EN),
            Guidance.about("0.4.3")
        )
        blocks.forEach { block ->
            assertFalse(han.containsMatchIn(block), "untranslated guidance:\n$block")
        }
    }
}
