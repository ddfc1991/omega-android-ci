package com.openminis.app.backup

import android.content.Context
import com.openminis.app.logging.AppLogger
import java.io.File

/**
 * [fork/backup-discovery] Finds existing backup packages so a restore can be
 * reached without hunting for the file by hand.
 *
 * A `.minisbak` normally lives in `<filesDir>/Backups`. That directory is
 * inside /data/data, so an uninstall takes it with the app — the very moment
 * a backup would be needed. Copies also land in the sandbox (workspace,
 * attachments shredded by share sheets) and in app-owned external dirs.
 *
 * This scans those places, read-only. Importing stays in [BackupImporter], so
 * recovery is still an explicit user action: discovery only offers candidates.
 */
object BackupRecoveryScanner {
    private const val TAG = "BackupRecoveryScanner"

    data class FoundBackup(
        val file: File,
        val location: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
        val verified: Boolean,
        val backupId: String? = null,
        val createdAt: String? = null,
        val deviceName: String? = null,
        val encrypted: Boolean = false,
        val categories: Map<String, Int> = emptyMap(),
    ) {
        val sizeMb: Long get() = sizeBytes / (1024L * 1024L)
    }

    private data class Candidate(val label: String, val dir: File)

    private fun candidates(ctx: Context): List<Candidate> {
        val out = LinkedHashMap<String, Candidate>()
        fun add(label: String, dir: File) {
            out.putIfAbsent(dir.absolutePath, Candidate(label, dir))
        }
        // In-app library, written by BackupExporter.
        add("Backups", BackupExporter.backupsDirectory(ctx))
        // Sandbox-side copies: the same directories the agent can reach.
        for (name in listOf("workspace", "attachments", "shared", "offloads")) {
            add(name, File("/var/minis/${'$'}name"))
        }
        // App-owned external storage, survives a reinstall on most devices.
        ctx.getExternalFilesDir(null)?.let { ext ->
            for (name in listOf("Backups", "backups", "downloads")) {
                add("External/${'$'}name", File(ext, name))
            }
        }
        return out.values.toList()
    }

    fun scan(ctx: Context): List<FoundBackup> {
        val found = mutableListOf<FoundBackup>()
        val seen = mutableSetOf<String>()
        for (c in candidates(ctx)) {
            if (!c.dir.isDirectory) continue
            val files = try {
                c.dir.listFiles() ?: continue
            } catch (e: Exception) {
                AppLogger.info(TAG, "[scan] cannot list ${'$'}{c.dir}: ${'$'}{e.message}")
                continue
            }
            for (f in files) {
                if (!f.isFile) continue
                if (!isPackageName(f.name)) continue
                if (!seen.add(f.absolutePath)) continue
                found += describe(f, c.label, verify = false)
            }
        }
        AppLogger.info(TAG, "[scan] found ${'$'}{found.size} candidate package(s)")
        return found.sortedByDescending { it.modifiedAt }
    }

    fun isPackageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith("." + BackupFormat.FILE_EXTENSION) || n.endsWith(".minisbak")
    }

    private fun describe(file: File, label: String, verify: Boolean): FoundBackup =
        FoundBackup(
            file = file,
            location = label,
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
            verified = verify,
        )
}
