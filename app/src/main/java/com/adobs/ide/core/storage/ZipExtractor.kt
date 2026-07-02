package com.adobs.ide.core.storage

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Utility object for securely extracting zip archives.
 *
 * Guards against the "Zip Slip" vulnerability by ensuring every extracted
 * entry's canonical path stays within the intended destination directory.
 */
object ZipExtractor {

    private const val BUFFER_SIZE = 8192

    /**
     * Extracts [zipFile] into [destDir].
     * @return true if extraction completed successfully, false otherwise.
     */
    fun extract(zipFile: File, destDir: File): Boolean {
        if (!zipFile.exists() || !zipFile.isFile) return false

        if (!destDir.exists()) {
            if (!destDir.mkdirs()) return false
        }

        val canonicalDestDir = destDir.canonicalFile

        return try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = zipSafeFile(canonicalDestDir, entry.name)
                        ?: throw SecurityException(
                            "Zip entry is trying to escape target directory: ${entry.name}"
                        )

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.let { parent ->
                            if (!parent.exists()) parent.mkdirs()
                        }
                        BufferedOutputStream(FileOutputStream(outFile), BUFFER_SIZE).use { bos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var count: Int
                            while (zis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                                bos.write(buffer, 0, count)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Resolves [entryName] against [destDir] and verifies the resulting canonical
     * path is still inside [destDir]. Returns null if the entry would escape the
     * destination directory (Zip Slip attack).
     */
    private fun zipSafeFile(destDir: File, entryName: String): File? {
        val target = File(destDir, entryName)
        val canonicalTarget = target.canonicalFile
        return if (canonicalTarget.path.startsWith(destDir.path + File.separator) ||
            canonicalTarget.path == destDir.path
        ) {
            canonicalTarget
        } else {
            null
        }
    }
}
