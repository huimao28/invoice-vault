package com.minis.invoicevault

import android.content.Context
import com.google.gson.Gson
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.*

data class BackupManifest(
    val format: String = "invoice-vault-full-backup",
    val schemaVersion: Int = 3,
    val appVersion: String = "0.9.0",
    val createdAt: Long = 0,
    val database: String = "database/invoice-vault.db",
    val checksums: Map<String, String> = emptyMap()
)
data class RestoreSummary(val added: Int, val skipped: Int, val failed: Int)

class SecureBackup(private val context: Context, private val db: VaultDatabase) {
    private val gson = Gson()
    companion object {
        const val MAX_ENTRIES = 10_000
        const val MAX_ENTRY = 500L * 1024 * 1024
        const val MAX_TOTAL = 500L * 1024 * 1024
        private val SAFE_ENTRY = Regex("(?:database/invoice-vault\\.db|originals/[A-Za-z0-9._-]+)")
        fun backupFileName(prefix: String, now: Date = Date()): String = "$prefix-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(now)}.zip"
        fun isSafeZipPath(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && !name.startsWith('\\') && !name.contains('\\') && name.split('/').none { it == ".." || it == "." }
    }

    /** Creates a self-contained Room snapshot. A FULL checkpoint folds committed WAL pages into the DB. */
    suspend fun create(prefix: String = "invoice-vault"): File {
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(outDir, backupFileName(prefix))
        val stage = File(context.cacheDir, "backup-stage-${System.nanoTime()}").apply { mkdirs() }
        try {
            val sqlite = db.openHelper.writableDatabase
            sqlite.query("PRAGMA wal_checkpoint(FULL)").use { while (it.moveToNext()) Unit }
            val dbCopy = File(stage, "database/invoice-vault.db").apply { parentFile!!.mkdirs() }
            context.getDatabasePath("invoice-vault.db").copyTo(dbCopy, overwrite = true)
            val items = linkedMapOf("database/invoice-vault.db" to dbCopy)
            val originals = File(context.filesDir, "originals")
            originals.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { file ->
                require(file.name.matches(Regex("[A-Za-z0-9._-]+"))) { "原件文件名不安全：${file.name}" }
                items["originals/${file.name}"] = file
            }
            val checksums = items.mapValues { sha256(it.value) }
            val manifest = BackupManifest(createdAt = System.currentTimeMillis(), checksums = checksums)
            ZipOutputStream(BufferedOutputStream(out.outputStream())).use { zip ->
                putBytes(zip, "manifest.json", gson.toJson(manifest).toByteArray(Charsets.UTF_8))
                putBytes(zip, "checksums.sha256", checksums.entries.joinToString("\n", postfix = "\n") { "${it.value}  ${it.key}" }.toByteArray())
                items.forEach { (name, file) -> putFile(zip, name, file) }
            }
            return out
        } catch (e: Exception) {
            out.delete(); throw e
        } finally { stage.deleteRecursively() }
    }

    /** Validates before touching live data, snapshots local state, then atomically swaps DB/files. */
    suspend fun validateAndRestore(zip: File): RestoreSummary {
        require(zip.isFile && zip.length() <= MAX_TOTAL) { "备份超过 500MB 限制" }
        val stage = File(context.cacheDir, "restore-stage-${System.nanoTime()}").apply { mkdirs() }
        var safety: File? = null
        try {
            extractSafe(zip, stage)
            val manifestFile = File(stage, "manifest.json")
            val checksumFile = File(stage, "checksums.sha256")
            require(manifestFile.isFile && checksumFile.isFile) { "备份缺少 manifest/checksum" }
            val manifest = gson.fromJson(manifestFile.readText(), BackupManifest::class.java)
            require(manifest.format == "invoice-vault-full-backup" && manifest.schemaVersion in 2..3) { "不支持的备份格式" }
            require(manifest.database == "database/invoice-vault.db") { "数据库清单无效" }
            val textChecksums = parseChecksumFile(checksumFile)
            require(textChecksums == manifest.checksums) { "manifest 与 checksum 不一致" }
            require(textChecksums.isNotEmpty() && textChecksums.keys.all { SAFE_ENTRY.matches(it) }) { "清单包含不安全条目" }
            textChecksums.forEach { (name, sum) ->
                require(sum.matches(Regex("[0-9a-f]{64}"))) { "checksum 格式无效" }
                val file = File(stage, name)
                require(file.isFile && sha256(file) == sum) { "文件校验失败：$name" }
            }
            require(File(stage, manifest.database).length() > 0) { "数据库快照为空" }

            safety = create("pre-restore-safety")
            val dbFile = context.getDatabasePath("invoice-vault.db")
            val originals = File(context.filesDir, "originals")
            val oldDb = File(dbFile.parentFile, "${dbFile.name}.restore-old")
            val oldOriginals = File(context.filesDir, "originals.restore-old")
            db.close()
            try {
                listOf("-wal", "-shm").forEach { File(dbFile.path + it).delete() }
                oldDb.delete(); if (dbFile.exists()) require(dbFile.renameTo(oldDb)) { "无法保存旧数据库" }
                oldOriginals.deleteRecursively(); if (originals.exists()) require(originals.renameTo(oldOriginals)) { "无法保存旧原件" }
                val newDbTmp = File(dbFile.parentFile, "${dbFile.name}.restore-new")
                File(stage, manifest.database).copyTo(newDbTmp, overwrite = true)
                require(newDbTmp.renameTo(dbFile)) { "无法安装数据库快照" }
                val newOriginals = File(context.filesDir, "originals.restore-new").apply { mkdirs() }
                File(stage, "originals").listFiles()?.forEach { it.copyTo(File(newOriginals, it.name), overwrite = false) }
                require(newOriginals.renameTo(originals)) { "无法安装原件快照" }
                oldDb.delete(); oldOriginals.deleteRecursively()
            } catch (e: Exception) {
                dbFile.delete(); originals.deleteRecursively()
                if (oldDb.exists()) oldDb.renameTo(dbFile)
                if (oldOriginals.exists()) oldOriginals.renameTo(originals)
                throw IllegalStateException("恢复失败，已回滚到恢复前状态", e)
            }
            return RestoreSummary(textChecksums.keys.count { it.startsWith("originals/") }, 0, 0)
        } finally { stage.deleteRecursively() }
    }

    fun extractSafe(zip: File, dest: File) {
        var entries = 0; var total = 0L
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                require(++entries <= MAX_ENTRIES) { "ZIP 条目过多" }
                val name = entry.name
                require(isSafeZipPath(name)) { "ZIP 路径不安全" }
                val out = File(dest, name)
                require(out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) { "ZIP Slip 路径越界" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs(); var one = 0L
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val n = input.read(buffer); if (n < 0) break; one += n; total += n
                            require(one <= MAX_ENTRY && total <= MAX_TOTAL) { "ZIP 解压内容超过 500MB 限制" }; output.write(buffer, 0, n) }
                    }
                }
                input.closeEntry()
            }
        }
    }

    private fun parseChecksumFile(file: File): Map<String, String> = file.readLines().filter { it.isNotBlank() }.associate { line ->
        val parts = line.split(Regex("  "), limit = 2); require(parts.size == 2) { "checksum 文件格式无效" }; parts[1] to parts[0]
    }
    private fun putBytes(z: ZipOutputStream, name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
    private fun putFile(z: ZipOutputStream, name: String, file: File) { z.putNextEntry(ZipEntry(name)); file.inputStream().buffered().use { it.copyTo(z) }; z.closeEntry() }
    private fun sha256(file: File): String { val digest = MessageDigest.getInstance("SHA-256"); file.inputStream().buffered().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }; return digest.digest().joinToString("") { "%02x".format(it) } }
}
