package com.adobs.ide.core.storage

import java.io.File

/**
 * Contract for all file system operations used by the IDE.
 * All methods are suspend functions and must be executed off the main thread
 * (implementations should dispatch to Dispatchers.IO).
 */
interface IFileEngine {

    /**
     * Returns the list of files/folders contained in [path].
     * Returns an empty list if the path does not exist or is not a directory.
     */
    suspend fun getFiles(path: String): List<File>

    /**
     * Creates a new file or folder at [path].
     * @param isFolder true to create a directory, false to create an empty file.
     * @return true if creation succeeded.
     */
    suspend fun createFile(path: String, isFolder: Boolean): Boolean

    /**
     * Renames the file/folder at [oldPath] to [newName] (name only, same parent directory).
     * @return true if the rename succeeded.
     */
    suspend fun renameFile(oldPath: String, newName: String): Boolean

    /**
     * Deletes the file or folder (recursively, if it's a directory) at [path].
     * @return true if deletion succeeded.
     */
    suspend fun deleteFile(path: String): Boolean

    /**
     * Extracts the zip archive at [zipPath] into [destPath].
     * @return true if extraction succeeded.
     */
    suspend fun extractZip(zipPath: String, destPath: String): Boolean
}
