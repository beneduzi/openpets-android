package com.openpets.android.pet

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

data class ImportLimits(
    val maxEntries: Int = 64,
    val maxEntryBytes: Long = 16L * 1024 * 1024,
    val maxTotalBytes: Long = 32L * 1024 * 1024,
)

/** JVM-only helpers for safely importing and publishing pet packages. */
object PetImportSupport {
    fun extractToDir(zip: ZipInputStream, destDir: File, limits: ImportLimits = ImportLimits()) {
        destDir.mkdirs()
        var entries = 0
        var totalBytes = 0L
        var entry = zip.nextEntry
        while (entry != null) {
            try {
                if (!entry.isDirectory) {
                    val basename = entry.name.replace('\\', '/').substringAfterLast('/')
                    if (basename.isNotBlank()) {
                        entries++
                        if (entries > limits.maxEntries) throw IOException("Pet package has too many entries.")
                        File(destDir, basename).outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var entryBytes = 0L
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                if (entryBytes + count > limits.maxEntryBytes || totalBytes + count > limits.maxTotalBytes) {
                                    throw IOException("Pet package exceeds size limits.")
                                }
                                output.write(buffer, 0, count)
                                entryBytes += count
                                totalBytes += count
                            }
                        }
                    }
                }
            } finally {
                zip.closeEntry()
            }
            entry = zip.nextEntry
        }
    }

    fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(8192)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            if (copied + count > maxBytes) throw IOException("Input exceeds size limit.")
            output.write(buffer, 0, count)
            copied += count
        }
    }

    fun publish(stagingDir: File, targetDir: File) {
        if (!stagingDir.isDirectory) throw IOException("Staging directory is missing.")
        val parent = targetDir.parentFile ?: throw IOException("Failed to publish pet package.")
        val backup = File(parent, targetDir.name + ".bak-" + System.nanoTime())
        if (targetDir.exists() && !moveDirectory(targetDir, backup)) {
            throw IOException("Failed to publish pet package.")
        }
        if (!moveDirectory(stagingDir, targetDir)) {
            if (backup.exists()) moveDirectory(backup, targetDir)
            throw IOException("Failed to publish pet package.")
        }
        if (backup.exists()) backup.deleteRecursively()
    }

    private fun moveDirectory(source: File, destination: File): Boolean {
        if (source.renameTo(destination)) return true
        return try {
            source.copyRecursively(destination, overwrite = true)
            if (!source.deleteRecursively()) {
                destination.deleteRecursively()
                false
            } else true
        } catch (_: Exception) {
            false
        }
    }
}
