package com.adobs.ide.core.file;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Standalone utility for zip extraction and creation.
 *
 * This class has no knowledge of Android UI widgets or FileManager internals.
 * It operates purely on java.io.File paths and reports progress/results via
 * callback interfaces, which are always invoked on the main thread.
 *
 * All work is performed on a background executor; callers must not assume
 * synchronous completion.
 */
public class ZipExtractor {

    private static final int BUFFER_SIZE = 8192;

    // Single shared executor for extraction/compression work. Using a fixed
    // small pool avoids spawning unbounded threads if multiple operations
    // are triggered in quick succession.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ZipExtractor() {
        // Not instantiable; all functionality is exposed via static methods.
    }

    /**
     * Callback for reporting extraction/compression progress and completion.
     * All methods are invoked on the main (UI) thread.
     */
    public interface ProgressCallback {
        /**
         * Called periodically as entries are processed.
         *
         * @param processedEntries number of entries processed so far
         * @param totalEntries     total number of entries, or -1 if unknown
         * @param processedBytes   total bytes written/read so far
         */
        void onProgress(int processedEntries, int totalEntries, long processedBytes);

        /** Called once when the operation completes successfully. */
        void onComplete(File outputLocation);

        /** Called once if the operation fails. */
        void onError(Exception e);
    }

    /**
     * Extracts {@code zipFile} into {@code destinationDir} on a background thread.
     * Guards against zip-slip path traversal by validating that every entry's
     * resolved destination path stays within destinationDir.
     *
     * @param zipFile        the .zip file to extract
     * @param destinationDir directory to extract into (created if it doesn't exist)
     * @param callback       progress/completion/error callback (may be null)
     */
    public static void extract(final File zipFile, final File destinationDir,
                                final ProgressCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    doExtract(zipFile, destinationDir, callback);
                    postComplete(callback, destinationDir);
                } catch (Exception e) {
                    postError(callback, e);
                }
            }
        });
    }

    /**
     * Compresses the given files into a single zip archive on a background thread.
     * Files are added at the root of the archive using their file names; directories
     * are recursed into and their relative structure preserved.
     *
     * @param files     files/directories to include
     * @param outputZip destination zip file to create (overwritten if it exists)
     * @param callback  progress/completion/error callback (may be null)
     */
    public static void compress(final List<File> files, final File outputZip,
                                 final ProgressCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    doCompress(files, outputZip, callback);
                    postComplete(callback, outputZip);
                } catch (Exception e) {
                    postError(callback, e);
                }
            }
        });
    }

    // ---------------------------------------------------------------------
    // Extraction implementation
    // ---------------------------------------------------------------------

    private static void doExtract(File zipFile, File destinationDir, ProgressCallback callback)
            throws IOException {
        if (!zipFile.isFile()) {
            throw new IOException("Zip file does not exist: " + zipFile.getAbsolutePath());
        }
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Could not create destination directory: "
                    + destinationDir.getAbsolutePath());
        }

        final String destinationCanonicalPath = destinationDir.getCanonicalPath() + File.separator;

        int processedEntries = 0;
        long processedBytes = 0;
        int totalEntries = countEntries(zipFile);

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {

            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                File outFile = resolveSafeEntryPath(destinationDir, destinationCanonicalPath, entry);

                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Could not create directory: " + outFile.getAbsolutePath());
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create directory: " + parent.getAbsolutePath());
                    }

                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            processedBytes += read;
                        }
                    }
                }

                processedEntries++;
                zis.closeEntry();
                postProgress(callback, processedEntries, totalEntries, processedBytes);
            }
        }
    }

    /**
     * Resolves the destination file for a zip entry and validates that it does not
     * escape the destination directory (zip-slip protection). Throws IOException
     * if the entry's path would extract outside destinationDir.
     */
    private static File resolveSafeEntryPath(File destinationDir, String destinationCanonicalPath,
                                              ZipEntry entry) throws IOException {
        File targetFile = new File(destinationDir, entry.getName());
        String targetCanonicalPath = targetFile.getCanonicalPath();

        boolean isWithinDestination = targetCanonicalPath.startsWith(destinationCanonicalPath)
                || (entry.isDirectory() && (targetCanonicalPath + File.separator)
                        .equals(destinationCanonicalPath));

        if (!isWithinDestination) {
            throw new IOException("Zip entry attempted path traversal outside destination: "
                    + entry.getName());
        }

        return targetFile;
    }

    /** Counts total entries in the zip for progress reporting; returns -1 if unavailable. */
    private static int countEntries(File zipFile) {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            int count = 0;
            while (zis.getNextEntry() != null) {
                count++;
                zis.closeEntry();
            }
            return count;
        } catch (IOException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------------
    // Compression implementation
    // ---------------------------------------------------------------------

    private static void doCompress(List<File> files, File outputZip, ProgressCallback callback)
            throws IOException {
        File parent = outputZip.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory: " + parent.getAbsolutePath());
        }

        int totalEntries = 0;
        for (File f : files) {
            totalEntries += countFileEntries(f);
        }

        int processedEntries = 0;
        long processedBytes = 0;

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputZip)))) {
            byte[] buffer = new byte[BUFFER_SIZE];

            for (File file : files) {
                processedEntries = addFileToZip(file, file.getName(), zos, buffer,
                        callback, processedEntries, totalEntries, processedBytes);
            }
        }
    }

    /** Recursively adds a file or directory to the zip output stream. */
    private static int addFileToZip(File file, String entryName, ZipOutputStream zos, byte[] buffer,
                                     ProgressCallback callback, int processedEntries,
                                     int totalEntries, long processedBytes) throws IOException {
        if (file.isDirectory()) {
            String dirEntryName = entryName.endsWith("/") ? entryName : entryName + "/";
            zos.putNextEntry(new ZipEntry(dirEntryName));
            zos.closeEntry();

            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    processedEntries = addFileToZip(child, dirEntryName + child.getName(), zos,
                            buffer, callback, processedEntries, totalEntries, processedBytes);
                }
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                    processedBytes += read;
                }
            }
            zos.closeEntry();
        }

        processedEntries++;
        postProgress(callback, processedEntries, totalEntries, processedBytes);
        return processedEntries;
    }

    /** Counts how many zip entries a file/directory will contribute (for progress totals). */
    private static int countFileEntries(File file) {
        if (file.isDirectory()) {
            int count = 1; // the directory entry itself
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    count += countFileEntries(child);
                }
            }
            return count;
        }
        return 1;
    }

    // ---------------------------------------------------------------------
    // Main-thread callback dispatch
    // ---------------------------------------------------------------------

    private static void postProgress(final ProgressCallback callback, final int processedEntries,
                                       final int totalEntries, final long processedBytes) {
        if (callback == null) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                callback.onProgress(processedEntries, totalEntries, processedBytes);
            }
        });
    }

    private static void postComplete(final ProgressCallback callback, final File outputLocation) {
        if (callback == null) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                callback.onComplete(outputLocation);
            }
        });
    }

    private static void postError(final ProgressCallback callback, final Exception e) {
        if (callback == null) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(e);
            }
        });
    }
}
