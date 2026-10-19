package com.dhikr.app.core.sync

import java.io.File

/** [SyncFiles] over a private directory; writes go through a temp file + rename. */
class FileSyncFiles(private val dir: File) : SyncFiles {
    private val baseFile get() = File(dir, "base.json")
    private val snapshotFile get() = File(dir, "snapshot.json")

    override fun readBase(): String? = read(baseFile)
    override fun writeBase(text: String) = write(baseFile, text)
    override fun deleteBase() {
        baseFile.delete()
    }

    override fun readSnapshot(): String? = read(snapshotFile)
    override fun writeSnapshot(text: String) = write(snapshotFile, text)

    private fun read(f: File): String? = if (f.exists()) f.readText(Charsets.UTF_8) else null

    private fun write(f: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}
