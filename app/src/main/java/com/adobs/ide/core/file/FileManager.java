/*
 * ============================================================================
 *  FileManager.java  —  AobsIDE Core Filesystem Layer
 * ============================================================================
 *
 *  Package : com.adobs.ide.core.file
 *  Path   : .../com/adobs/ide/core/file/FileManager.java
 *
 *  FULLY OPTIMISED FOR ANDROID 10 (API 29) THROUGH ANDROID 17 (API 37).
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │  API   Android   Key Storage Changes Handled                        │
 *  ├──────────────────────────────────────────────────────────────────────┤
 *  │  29    10        Scoped storage introduced; requestLegacyExternal-   │
 *  │                 Storage opt-out; MediaStore.Downloads added.        │
 *  │  30    11        Scoped storage ENFORCED; WRITE_EXTERNAL_STORAGE     │
 *  │                 dead; MANAGE_EXTERNAL_STORAGE (all-files); direct   │
 *  │                 file-path access to MediaStore; SAF can't access    │
 *  │                 root, Download, Android/data, Android/obb.          │
 *  │  31    12        App auto-migrate data; getAllocatableBytes;        │
 *  │                 StorageVolume improved.                              │
 *  │  32    12L       Large-screen layout awareness.                     │
 *  │  33    13        Granular media perms (READ_MEDIA_IMAGES / VIDEO /  │
 *  │                 AUDIO); Photo Picker API.                           │
 *  │  34    14        Photo Picker default for visual media;             │
 *  │                 ACTION_PICK deprecated for images/video.            │
 *  │  35    15        Edge-to-edge enforced; BAL hardening; partial      │
 *  │                 photo-picker; 16KB page size.                       │
 *  │  36    16        Major+minor SDK cadence; orientation/resizability │
 *  │                 restrictions ignored on sw≥600dp (opt-out avail.);  │
 *  │                 LOCAL_NETWORK permission opt-in.                    │
 *  │  37    17        LOCAL_NETWORK mandatory; orientation opt-out GONE; │
 *  │                 static-final unmodifiable via reflection; ECH TLS;  │
 *  │                 background audio hardening; Safer DCL native libs;  │
 *  │                 CT enabled by default.                              │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 *  Design principles:
 *   1.  Sole owner of filesystem state — UI never touches java.io.File.
 *   2.  All mutations return Result<T>; no checked exceptions leak upward.
 *   3.  Zero git knowledge — Phase 2 GitObserver reads public getters only.
 *   4.  Scoped-storage-safe: defaults to app-private external storage (no
 *       permission needed on ANY API level).  Supports SAF tree-URIs for
 *       user-granted external access and MANAGE_EXTERNAL_STORAGE for the
 *       "all files" use case (documented, Play-Store-restricted).
 *   5.  Uses java.nio.file (Files, Path, DirectoryStream, walkFileTree)
 *       on API ≥ 26 for better error semantics and performance.
 *   6.  Thread-safe: mutations are serialised on a single-thread executor;
 *       reads use volatile + synchronized.
 *
 * ============================================================================
 */

package com.adobs.ide.core.file;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sole owner of all filesystem state for AobsIDE.
 *
 * <h2>Storage modes (auto-detected at construction)</h2>
 * <pre>
 *  ┌─────────────────────┬──────────────────────────────────────────────┐
 *  │ StorageMode         │ When active                                   │
 *  ├─────────────────────┼──────────────────────────────────────────────┤
 *  │ APP_SPECIFIC        │ Default — getExternalFilesDir(null).         │
 *  │                     │ No permission on any API level.               │
 *  │ LEGACY_EXTERNAL     │ API ≤ 28 OR API 29 with                      │
 *  │                     │ requestLegacyExternalStorage=true.           │
 *  │ ALL_FILES_ACCESS    │ API ≥ 30 + MANAGE_EXTERNAL_STORAGE granted.  │
 *  │ SAF_TREE            │ User granted a tree URI via SAF.             │
 *  └─────────────────────┴──────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Thread-safety</h2>
 * Read methods ({@link #listFiles}, {@link #getCurrentDirectory},
 * {@link #getCurrentDirectoryPath}) are safe from any thread.
 * Mutation methods are synchronous and return {@link Result}; async
 * overloads dispatch to a single-thread executor.
 *
 * <h2>Git isolation</h2>
 * This class imports <em>nothing</em> from any git package.  Phase 2's
 * {@code GitObserver} may read {@link #getCurrentDirectory()} and
 * {@link #getCurrentDirectoryPath()} only.
 */
public class FileManager {

    private static final String TAG = "FileManager";

    /** Cap the back-stack to avoid memory bloat on very deep navigation. */
    private static final int MAX_BACK_STACK = 256;

    // ========================================================================
    //  StorageMode
    // ========================================================================

    /** How this FileManager accesses the filesystem. */
    public enum StorageMode {
        /** App-private external dir — no permission, scoped-storage-safe. */
        APP_SPECIFIC,
        /** Legacy model — pre-API-29 or API-29 with requestLegacyExternalStorage. */
        LEGACY_EXTERNAL,
        /** MANAGE_EXTERNAL_STORAGE granted — "all files" access (API ≥ 30). */
        ALL_FILES_ACCESS,
        /** User granted a SAF tree URI — access via DocumentFile / ContentResolver. */
        SAF_TREE
    }

    // ========================================================================
    //  FileItem — immutable model
    // ========================================================================

    /**
     * Immutable snapshot of a single filesystem entry.
     *
     * <p>When the backing store is a SAF tree URI, {@code absolutePath}
     * holds the DocumentFile URI string and {@code fileUri} is non-null.
     * For regular File-backed entries, {@code absolutePath} is the
     * canonical filesystem path and {@code fileUri} is null.</p>
     */
    public static final class FileItem {

        private final String name;
        private final String absolutePath;   // File path OR Document URI string
        private final boolean isDirectory;
        private final long sizeBytes;
        private final long lastModified;
        @Nullable private final Uri fileUri;  // Non-null only for SAF entries
        @Nullable private final String mimeType;

        public FileItem(@NonNull String name,
                        @NonNull String absolutePath,
                        boolean isDirectory,
                        long sizeBytes,
                        long lastModified) {
            this(name, absolutePath, isDirectory, sizeBytes, lastModified,
                 null, null);
        }

        public FileItem(@NonNull String name,
                        @NonNull String absolutePath,
                        boolean isDirectory,
                        long sizeBytes,
                        long lastModified,
                        @Nullable Uri fileUri,
                        @Nullable String mimeType) {
            this.name         = name;
            this.absolutePath = absolutePath;
            this.isDirectory  = isDirectory;
            this.sizeBytes    = sizeBytes;
            this.lastModified = lastModified;
            this.fileUri      = fileUri;
            this.mimeType     = mimeType;
        }

        // ---- Factory: from java.io.File (NIO-backed) ----

        @NonNull
        static FileItem from(@NonNull File file) {
            long size = 0L;
            long modified = file.lastModified();
            String mime = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Path p = file.toPath();
                    BasicFileAttributes attrs =
                        Files.readAttributes(p, BasicFileAttributes.class);
                    size = attrs.isDirectory() ? 0L : attrs.size();
                    modified = attrs.lastModifiedTime().toMillis();
                    // NIO doesn't provide MIME; leave null for File-backed items.
                } catch (IOException | SecurityException ignored) {
                    if (!file.isDirectory()) size = file.length();
                    modified = file.lastModified();
                }
            } else {
                // API < 26 — legacy File API only.
                if (!file.isDirectory()) size = file.length();
                modified = file.lastModified();
            }

            return new FileItem(
                file.getName(),
                file.getAbsolutePath(),
                file.isDirectory(),
                size,
                modified,
                null,    // fileUri
                mime
            );
        }

        // ---- Factory: from DocumentFile (SAF) ----

        @NonNull
        static FileItem from(@NonNull DocumentFile doc) {
            long size = 0L;
            long modified = 0L;
            try {
                size = doc.length();
                modified = doc.lastModified();
            } catch (SecurityException ignored) { }
            return new FileItem(
                doc.getName() != null ? doc.getName() : "",
                doc.getUri().toString(),
                doc.isDirectory(),
                size,
                modified,
                doc.getUri(),
                doc.getType()
            );
        }

        // ---- Getters ----

        @NonNull  public String getName()           { return name; }
        @NonNull  public String getAbsolutePath()   { return absolutePath; }
        public boolean isDirectory()                 { return isDirectory; }
        public long getSizeBytes()                   { return sizeBytes; }
        public long getLastModified()                { return lastModified; }
        @Nullable public Uri getFileUri()            { return fileUri; }
        @Nullable public String getMimeType()        { return mimeType; }

        /** True if this item is backed by a SAF DocumentFile (not a raw File). */
        public boolean isSafBacked() { return fileUri != null; }

        @Nullable
        public String getParentPath() {
            if (fileUri != null) return null; // SAF: parent is implicit
            int idx = absolutePath.lastIndexOf(File.separatorChar);
            return idx <= 0 ? null : absolutePath.substring(0, idx);
        }

        @NonNull @Override
        public String toString() {
            return "FileItem{name='" + name + "', path='" + absolutePath +
                   "', dir=" + isDirectory + ", size=" + sizeBytes +
                   (fileUri != null ? ", uri=" + fileUri : "") + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FileItem)) return false;
            FileItem that = (FileItem) o;
            return isDirectory == that.isDirectory
                && sizeBytes == that.sizeBytes
                && lastModified == that.lastModified
                && name.equals(that.name)
                && absolutePath.equals(that.absolutePath)
                && Objects.equals(fileUri, that.fileUri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, absolutePath, isDirectory,
                                sizeBytes, lastModified, fileUri);
        }
    }

    // ========================================================================
    //  Result<T> + ErrorType
    // ========================================================================

    public enum ErrorType {
        PERMISSION_DENIED,
        IO_ERROR,
        FILE_NOT_FOUND,
        ALREADY_EXISTS,
        INVALID_PATH,
        SECURITY,
        INSUFFICIENT_SPACE,
        UNSUPPORTED_OPERATION,  // e.g. SAF path where File API is required
        UNKNOWN
    }

    public static final class Result<T> {

        @Nullable private final T data;
        @Nullable private final ErrorType errorType;
        @Nullable private final String message;

        private Result(@Nullable T d, @Nullable ErrorType e, @Nullable String m) {
            this.data = d; this.errorType = e; this.message = m;
        }

        public boolean isSuccess()               { return errorType == null; }
        public boolean isError()                 { return errorType != null; }
        @Nullable public T getData()             { return data; }
        @Nullable public ErrorType getErrorType() { return errorType; }
        @Nullable public String getMessage()     { return message; }

        @NonNull static <U> Result<U> success(@Nullable U d) {
            return new Result<>(d, null, null);
        }
        @NonNull static <U> Result<U> error(@NonNull ErrorType e) {
            return new Result<>(null, e, null);
        }
        @NonNull static <U> Result<U> error(@NonNull ErrorType e, @Nullable String m) {
            return new Result<>(null, e, m);
        }
    }

    public interface OperationCallback<T> {
        void onResult(@NonNull Result<T> result);
    }

    // ========================================================================
    //  Internal state
    // ========================================================================

    private final Context appContext;

    /** Current storage mode — set once at construction, re-checked on demand. */
    private volatile StorageMode storageMode;

    /** Back-stack of previously visited directories. */
    private final Deque<File> backStack = new ArrayDeque<>();

   
