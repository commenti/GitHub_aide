package com.adobs.ide.core.storage;

import java.io.File;

/**
 * Contract for all file system operations used by the IDE.
 * All methods are suspend functions and must be executed off the main thread
 * (implementations should dispatch to Dispatchers.IO).
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\bf\u0018\u00002\u00020\u0001J\u001e\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0003H\u00a6@\u00a2\u0006\u0002\u0010\u0007J\u0016\u0010\b\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\tJ\u001e\u0010\n\u001a\u00020\u00032\u0006\u0010\u000b\u001a\u00020\u00052\u0006\u0010\f\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\rJ\u001c\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\tJ\u001e\u0010\u0011\u001a\u00020\u00032\u0006\u0010\u0012\u001a\u00020\u00052\u0006\u0010\u0013\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\r\u00a8\u0006\u0014"}, d2 = {"Lcom/adobs/ide/core/storage/IFileEngine;", "", "createFile", "", "path", "", "isFolder", "(Ljava/lang/String;ZLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteFile", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "extractZip", "zipPath", "destPath", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getFiles", "", "Ljava/io/File;", "renameFile", "oldPath", "newName", "app_debug"})
public abstract interface IFileEngine {
    
    /**
     * Returns the list of files/folders contained in [path].
     * Returns an empty list if the path does not exist or is not a directory.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getFiles(@org.jetbrains.annotations.NotNull()
    java.lang.String path, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<? extends java.io.File>> $completion);
    
    /**
     * Creates a new file or folder at [path].
     * @param isFolder true to create a directory, false to create an empty file.
     * @return true if creation succeeded.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object createFile(@org.jetbrains.annotations.NotNull()
    java.lang.String path, boolean isFolder, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
    
    /**
     * Renames the file/folder at [oldPath] to [newName] (name only, same parent directory).
     * @return true if the rename succeeded.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object renameFile(@org.jetbrains.annotations.NotNull()
    java.lang.String oldPath, @org.jetbrains.annotations.NotNull()
    java.lang.String newName, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
    
    /**
     * Deletes the file or folder (recursively, if it's a directory) at [path].
     * @return true if deletion succeeded.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteFile(@org.jetbrains.annotations.NotNull()
    java.lang.String path, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
    
    /**
     * Extracts the zip archive at [zipPath] into [destPath].
     * @return true if extraction succeeded.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object extractZip(@org.jetbrains.annotations.NotNull()
    java.lang.String zipPath, @org.jetbrains.annotations.NotNull()
    java.lang.String destPath, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
}