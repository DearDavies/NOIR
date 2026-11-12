/*
 * NOIR — Android leak audit
 * Copyright (c) 2026 Verlintas
 * SPDX-License-Identifier: MIT
 */
package app.noir.core.adb

import app.noir.core.apk.ApkScan
import java.io.File

data class PackagePath(val pkg: String, val path: String)

/** Successful URL scans plus packages whose extraction failed and must not be cached. */
data class UrlScan(
    val urls: Map<String, List<String>>,
    val failed: Set<String> = emptySet()
)

/**
 * Collects URL strings from every installed APK.
 *
 * The fast path runs entirely on the device (unzip + strings), so only a few KB
 * cross the USB link. Progress is streamed per package: the shell script prints
 * one marker after each APK, which lets the CLI redraw its bar for every app
 * instead of every batch.
 *
 * If the device's `unzip` cannot read an APK at all, the script reports it and
 * the host pulls that file and parses it locally.
 */
object DeviceScan {

    private const val FALLBACK = "F "
    private const val RESULT = "R "
    private const val PROGRESS = "P "

    fun packagePaths(adb: Adb, serial: String): Map<String, String> {
        val out = adb.shell(serial, "pm list packages -f").output
        val map = HashMap<String, String>()
        out.lineSequence().forEach { line ->
            if (!line.startsWith("package:")) return@forEach
            val body = line.removePrefix("package:")
            val idx = body.lastIndexOf('=')
            if (idx <= 0) return@forEach
            val path = body.substring(0, idx)
            val pkg = body.substring(idx + 1).trim()
            if (pkg.isNotEmpty()) map[pkg] = path
        }
        return map
    }

    fun hasDeviceTools(adb: Adb, serial: String): Boolean {
        val out = adb.shell(serial, "which unzip strings").output
        return out.contains("unzip") && out.contains("strings")
    }

    fun urls(
        adb: Adb,
        serial: String,
        targets: List<PackagePath>,
        workDir: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): UrlScan {
        if (targets.isEmpty()) return UrlScan(emptyMap())
        workDir.mkdirs()

        if (!hasDeviceTools(adb, serial)) {
            return urlsByPulling(adb, serial, targets, workDir, onProgress)
        }

        val result = HashMap<String, List<String>>()
        val processed = HashSet<String>()
        val failed = HashSet<String>()
        val pendingFallback = ArrayList<String>()

        // Keep remote paths unique: two scans on the same device must not
        // overwrite each other's script, target list, or dex scratch file.
        val remoteId = "noir-${System.nanoTime()}"
        val remoteScript = "/data/local/tmp/$remoteId.sh"
        val remoteList = "/data/local/tmp/$remoteId.txt"
        val remoteTmp = "/data/local/tmp/$remoteId.bin"
        fun cleanRemote() {
            runCatching {
                adb.shell(serial, "rm -f $remoteScript $remoteList $remoteTmp", timeoutSec = 15)
            }
        }

        val list = File(workDir, "targets.txt")
        list.writeText(targets.joinToString("\n", postfix = "\n") { "${it.pkg} ${it.path}" })

        // Ask unzip for the dex entries that actually exist instead of probing
        // classes.dex..classes99.dex one by one. On a 500-app device that cuts
        // hundreds of thousands of unzip invocations. Listing first also avoids
        // relying on vendor unzip builds to expand globs.
        val script = File(workDir, "scan.sh")
        script.writeText(
            """
            #!/system/bin/sh
            TMP=${remoteTmp}
            while read p f; do
              : > ${'$'}TMP
              # pm path returns every split APK, not just the base.
              APKS=${'$'}(pm path "${'$'}p" 2>/dev/null | sed 's/^package://')
              [ -z "${'$'}APKS" ] && APKS="${'$'}f"
              for apk in ${'$'}APKS; do
                DEXES=${'$'}(unzip -l "${'$'}apk" 2>/dev/null \
                  | sed -n 's/.* \(classes[0-9]*\.dex\)[[:space:]]*${'$'}/\1/p')
                [ -z "${'$'}DEXES" ] && DEXES=classes.dex
                unzip -p "${'$'}apk" ${'$'}DEXES 2>/dev/null >> ${'$'}TMP
              done
              if [ ! -s ${'$'}TMP ]; then
                echo "F ${'$'}p"
              else
                u=${'$'}(strings < ${'$'}TMP | grep -oE 'https?://[A-Za-z0-9._~:/?#@!&()*+,;=%-]+' \
                    | sort -u | head -1000 | tr '\n' ' ')
                [ -n "${'$'}u" ] && echo "R ${'$'}p|${'$'}u"
              fi
              rm -f ${'$'}TMP
              echo "P ${'$'}p"
            done < ${remoteList}
            echo "#DONE"
            """.trimIndent()
        )

        val pushedScript = adb.push(serial, script, remoteScript)
        val pushedList = adb.push(serial, list, remoteList)
        if (!pushedScript.ok || !pushedList.ok) {
            println("  ! could not push the device-side scanner; pulling APKs instead")
            cleanRemote()
            return urlsByPulling(adb, serial, targets, workDir, onProgress)
        }

        var done = 0
        val total = targets.size

        val execution = adb.execStreaming(
            listOf("-s", serial, "shell", "sh $remoteScript"),
            timeoutSec = 1_800
        ) { line ->
            when {
                line.startsWith(RESULT) -> {
                    val body = line.removePrefix(RESULT)
                    val pkg = body.substringBefore('|')
                    val found = body.substringAfter('|', "").split(' ').filter { it.isNotBlank() }
                    if (pkg.isNotEmpty()) {
                        processed += pkg
                        result[pkg] = found
                    }
                }
                line.startsWith(FALLBACK) -> pendingFallback.add(line.removePrefix(FALLBACK).trim())
                line.startsWith(PROGRESS) -> {
                    val pkg = line.removePrefix(PROGRESS).trim()
                    if (pkg.isNotEmpty()) processed += pkg
                    done++
                    onProgress(done, total)
                }
            }
        }

        if (done == 0 && targets.isNotEmpty()) {
            // The script never ran. Fall back to pulling everything rather than
            // returning a plausible-looking empty report.
            println("  ! device-side scan produced no progress; pulling ${targets.size} APKs instead")
            cleanRemote()
            return urlsByPulling(adb, serial, targets, workDir, onProgress)
        }

        if (execution.code != 0) {
            println("  ! device-side scan exited with ${execution.code}; results may be partial")
        }

        if (pendingFallback.isNotEmpty()) {
            println("  ! ${pendingFallback.size} APK(s) could not be read by the device's unzip; pulling instead")
            val fallbackTargets = targets.filter { it.pkg in pendingFallback }
            val fallback = urlsByPulling(adb, serial, fallbackTargets, workDir) { index, count ->
                print("\r  pulling $index/$count")
                System.out.flush()
            }
            result.putAll(fallback.urls)
            failed.addAll(fallback.failed)
            println()
        }

        // A package that reached its P marker but produced no R line is a
        // successful "no endpoints" result. Missing progress means failure.
        targets.forEach { target ->
            when {
                target.pkg in failed -> Unit
                target.pkg in result -> Unit
                target.pkg in processed -> result[target.pkg] = emptyList()
                else -> failed += target.pkg
            }
        }
        if (execution.code != 0) {
            targets.forEach { target ->
                if (target.pkg !in result) failed += target.pkg
            }
        }

        cleanRemote()
        return UrlScan(result, failed)
    }

    private fun urlsByPulling(
        adb: Adb,
        serial: String,
        targets: List<PackagePath>,
        workDir: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): UrlScan {
        val result = HashMap<String, List<String>>()
        val failed = HashSet<String>()
        val apkDir = File(workDir, "apks").apply { mkdirs() }
        targets.forEachIndexed { index, target ->
            val paths = adb.shell(serial, "pm path ${target.pkg}", timeoutSec = 30).output
                .lineSequence()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .ifEmpty { listOf(target.path) }

            val found = LinkedHashSet<String>()
            var complete = true
            paths.forEachIndexed { split, path ->
                val local = File(apkDir, "${target.pkg}-$split.apk")
                local.delete()
                val pulled = adb.pull(serial, path, local)
                if (!pulled.ok || !local.isFile) {
                    complete = false
                } else {
                    val urls = runCatching { ApkScan.urls(local) }
                    if (urls.isSuccess) {
                        found += urls.getOrDefault(emptyList())
                    } else {
                        complete = false
                    }
                    local.delete()
                }
            }
            if (complete) result[target.pkg] = found.toList() else failed += target.pkg
            onProgress(index + 1, targets.size)
        }
        return UrlScan(result, failed)
    }
}
