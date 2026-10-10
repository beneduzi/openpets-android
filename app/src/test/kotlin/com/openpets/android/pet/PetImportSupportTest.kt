package com.openpets.android.pet

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PetImportSupportTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content)
                output.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test fun extractsFlattenedBasenames() {
        val destination = temporaryFolder.newFolder("out")
        PetImportSupport.extractToDir(java.util.zip.ZipInputStream(ByteArrayInputStream(zip(
            "nested/dir/pet.json" to "{}".toByteArray(),
            "dir/spritesheet.webp" to byteArrayOf(1, 2),
        ))), destination)
        assertTrue(File(destination, "pet.json").isFile)
        assertTrue(File(destination, "spritesheet.webp").isFile)
        assertFalse(File(destination, "nested").exists())
    }

    @Test fun rejectsTooManyEntries() {
        val archive = zip(*(1..4).map { "entry$it" to byteArrayOf(1) }.toTypedArray())
        assertThrows(IOException::class.java) {
            PetImportSupport.extractToDir(java.util.zip.ZipInputStream(ByteArrayInputStream(archive)), temporaryFolder.newFolder("limited"), ImportLimits(maxEntries = 3))
        }
    }

    @Test fun rejectsOversizedTotal() {
        val archive = zip("first" to ByteArray(80), "second" to ByteArray(80))
        assertThrows(IOException::class.java) {
            PetImportSupport.extractToDir(java.util.zip.ZipInputStream(ByteArrayInputStream(archive)), temporaryFolder.newFolder("oversized"), ImportLimits(maxTotalBytes = 128))
        }
    }

    @Test fun publishReplacesExistingTarget() {
        val parent = temporaryFolder.newFolder("publish")
        val target = File(parent, "pet").apply { mkdirs() }
        File(target, "old.txt").writeText("old")
        val staging = File(parent, "staging").apply { mkdirs() }
        File(staging, "new.txt").writeText("new")
        PetImportSupport.publish(staging, target)
        assertEquals("new", File(target, "new.txt").readText())
        assertFalse(File(target, "old.txt").exists())
        assertTrue(parent.listFiles()?.none { it.name.startsWith("pet.bak-") } == true)
    }

    @Test fun publishFailsWithoutTouchingTargetWhenStagingMissing() {
        val parent = temporaryFolder.newFolder("missing")
        val target = File(parent, "pet").apply { mkdirs() }
        val sentinel = File(target, "sentinel").apply { writeText("safe") }
        assertThrows(IOException::class.java) { PetImportSupport.publish(File(parent, "absent"), target) }
        assertTrue(sentinel.isFile)
        assertEquals("safe", sentinel.readText())
    }

    @Test fun copyBoundedRejectsOverLimit() {
        assertThrows(IOException::class.java) {
            PetImportSupport.copyBounded(ByteArrayInputStream(ByteArray(4)), ByteArrayOutputStream(), 3)
        }
    }
}
