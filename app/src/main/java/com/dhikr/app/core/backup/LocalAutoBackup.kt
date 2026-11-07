package com.dhikr.app.core.backup

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Automatic on-device backup copies in app-private storage. Keeps the newest
 * [KEEP] files so one bad write can't be the only copy. Survives data
 * corruption and (via Android Auto Backup of app files) device transfers,
 * but not an uninstall without Auto Backup — that's what Drive is for.
 */
object LocalAutoBackup {

    const val KEEP = 3
    private const val PREFIX = "dhikr-auto-"
    private const val SUFFIX = ".json"

    /** Writes [json] atomically into [dir] and prunes old copies. Returns the file. */
    fun write(dir: File, json: String, nowMillis: Long): File {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMillis))
        val target = File(dir, "$PREFIX$stamp$SUFFIX")
        val tmp = File(dir, "$PREFIX$stamp.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("Could not finalize local backup")
        }
        prune(dir)
        return target
    }

    fun list(dir: File): List<File> =
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.name }.orEmpty()

    private fun prune(dir: File) {
        list(dir).drop(KEEP).forEach { it.delete() }
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }
}
