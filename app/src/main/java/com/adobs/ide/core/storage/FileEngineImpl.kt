package com.adobs.ide.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [IFileEngine] backed by java.io.File.
 * All operations execute on Dispatchers.IO.
 */
@Singleton
class FileEngineImpl @Inject constructor() : IFileEngine {

    override suspend fun getFiles(path: String): List<File> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return@withContext emptyList()
        }
        dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    override suspend fun createFile(path: String, isFolder: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val target = File(path)
                if (target.exists()) return@withContext false

                if (isFolder) {
                    target.mkdirs()
                } else {
                    target.parentFile?.let { parent ->
                        if (!parent.exists()) parent.mkdirs()
                    }
                    target.createNewFile()
                }
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun renameFile(oldPath: String, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val source = File(oldPath)
                if (!source.exists()) return@withContext false

                val target = File(source.parentFile, newName)
                if (target.exists()) return@withContext false

                source.renameTo(target)
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(path)
            if (!target.exists()) return@withContext false
            deleteRecursively(target)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractZip(zipPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val zipFile = File(zipPath)
                val destDir = File(destPath)
                ZipExtractor.extract(zipFile, destDir)
            } catch (e: Exception) {
                false
            }
        }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        return file.delete()
    }
}
