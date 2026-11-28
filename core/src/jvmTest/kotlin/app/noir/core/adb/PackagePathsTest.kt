/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.core.adb

import app.noir.core.platform.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackagePathsTest {

    @Test
    fun `parses standard pm list packages -f output`() {
        val parsed = DeviceScan.parsePackagePaths(
            "package:/data/app/~~abc==/com.example.a-1==/base.apk=com.example.a\n" +
                "package:/product/app/B/com.example.b/B.apk=com.example.b\n"
        )
        assertEquals("/data/app/~~abc==/com.example.a-1==/base.apk", parsed["com.example.a"])
        assertEquals("/product/app/B/com.example.b/B.apk", parsed["com.example.b"])
    }

    @Test
    fun `parses CRLF and ignores malformed lines`() {
        val parsed = DeviceScan.parsePackagePaths(
            "package:/data/app/com.example/base.apk=com.example\r\n" +
                "package:com.example.no-path\r\n" +
                "not a package line\r\n"
        )
        assertEquals(1, parsed.size)
        assertEquals("/data/app/com.example/base.apk", parsed["com.example"])
    }

    @Test
    fun `empty output means the fallback must run`() {
        assertTrue(DeviceScan.parsePackagePaths("").isEmpty())
        assertTrue(DeviceScan.parsePackagePaths("package:com.example.no-path\n").isEmpty())
    }

    @Test
    fun `falls back to pm list packages plus pm path`() {
        if (Platform.isWindows) return
        val script = File(Files.createTempDirectory("noir-adb").toFile(), "adb")
        script.writeText(
            """
            #!/bin/sh
            case "${'$'}4" in
              "pm list packages -f"|"cmd package list packages -f") exit 0 ;;
              "pm list packages") printf 'package:com.example.a\npackage:com.example.b\n' ;;
              "pm path com.example.a") printf 'package:/data/app/a/base.apk\n' ;;
              "pm path com.example.b") printf 'package:/data/app/b/base.apk\n' ;;
            esac
            """.trimIndent() + "\n"
        )
        script.setExecutable(true)

        val parsed = DeviceScan.packagePaths(Adb(script.absolutePath), "serial")
        assertEquals("/data/app/a/base.apk", parsed["com.example.a"])
        assertEquals("/data/app/b/base.apk", parsed["com.example.b"])
    }
}
