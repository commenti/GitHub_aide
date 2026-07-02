/*
 * GitObserver.java
 * Path: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/git/
 * 
 * Passive watcher that monitors directory changes and determines Git UI state.
 * Read-only with respect to Phase 1 — never mutates MainActivity/FileManager state.
 * 
 * Compatible with Android 10 (API 29) through Android 17 (API 35+).
 */
package com.adobs.ide.git;

import android.app.Activity;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Passive watcher that polls or listens for directory changes and determines
 * the appropriate Git UI state based on repository and token status.
 * 
 * <p>Decision logic resides here; rendering is delegated to {@link GitUIInjector}.
 * This class never calls any setters on MainActivity or FileManager.</p>
 * 
 * <p>Lifecycle is managed externally via {@link GitBootstrap#attach(Activity, ViewGroup)}
 * and {@link GitBootstrap#onResume(Activity)} / {@link GitBootstrap#onPause(Activity)}.</p>
 */
public class GitObserver {

    private static final String TAG = "GitObserver";
    
    /** Fallback poll interval when FileObserver is unreliable (e.g., some OEMs, SAF paths). */
    private static final long POLL_INTERVAL_MS = 2000L;
    
    /** Debounce rapid directory switches to avoid redundant checks. */
    private static final long DEBOUNCE_MS = 350L;
    
    // =========================================================================
    // UI State Enum — consumed by GitUIInjector
    // =========================================================================

    /**
     * Possible UI states that GitObserver can request GitUIInjector to render.
     */
    public enum GitUiState {
        /** No Git button should be visible. */
        NONE,
        /** Show upload/init button — repo exists but may need remote setup. */
        SHOW_UPLOAD,
        /** Show commit button — repo has local uncommitted changes. */
        SHOW_COMMIT
    }

    // =========================================================================
    // Instance fields
    // =========================================================================

    /** Weak reference to hosting Activity to avoid memory leaks. */
    private final WeakReference<Activity> activityRef;
    
    /** Handler bound to main looper for UI callbacks. */
    private final Handler mainHandler;
    
    /** Background thread for file I/O and Git operations. */
    private HandlerThread backgroundThread;
    
    /** Handler bound to background thread. */
    private Handler backgroundHandler;
    
    /** FileObserver watching the .git directory for changes. */
    private FileObserver gitDirObserver;
    
    /** FileObserver watching parent directory for navigation events. */
    private FileObserver parentDirObserver;
    
    /** Runnable for periodic polling fallback. */
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            checkCurrentDirectory();
            if (isRunning) {
                backgroundHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };
    
    /** Debounced check to avoid rapid-fire directory evaluations. */
    private final Runnable debouncedCheck = new Runnable() {
        @Override
        public void run() {
            String path = getCurrentDirectoryPath();
            if (path != null) {
                performGitStateCheck(path);
            }
        }
    };
    
    /** Last path we evaluated — used to skip redundant checks. */
    private String lastCheckedPath;
    
    /** Whether we've already shown the "no token" error for the current path. */
    private boolean tokenErrorShownForCurrentPath;
    
    /** Current UI state to avoid redundant updates. */
    private GitUiState lastRenderedState = GitUiState.NONE;
    
    /** Lifecycle flag. */
    private volatile boolean isRunning = false;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Creates a new GitObserver bound to the given Activity.
     * 
     * @param activity The hosting activity (accessed read-only via getters).
     *                 May implement {@link DirectoryProvider} for type-safe access.
     */
    public GitObserver(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
        this.mainHandler = new Handler(Looper.getMainLooper());
        ensureBackgroundThread();
    }

    // =========================================================================
    // Public Lifecycle
    // =========================================================================

    /**
     * Starts observing directory changes. Call from Activity.onResume()
     * via {@link GitBootstrap#onResume(Activity)}.
     */
    public void start() {
        if (isRunning) {
            Log.w(TAG, "start() called but already running");
            return;
        }
        isRunning = true;
        Log.d(TAG, "GitObserver starting");

        setupFileObservers();
        schedulePolling();
        runInitialCheck();
    }

    /**
     * Stops observing directory changes. Call from Activity.onPause()
     * via {@link GitBootstrap#onPause(Activity)}.
     */
    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        Log.d(TAG, "GitObserver stopping");

        teardownFileObservers();
        cancelPolling();
        mainHandler.removeCallbacks(debouncedCheck);

        // Reset tracking state
        lastCheckedPath = null;
        tokenErrorShownForCurrentPath = false;
        safeUpdateUiState(GitUiState.NONE);
    }

    /**
     * Releases all resources. Call when observer is permanently no longer needed.
     * After calling this, the instance should not be reused.
     */
    public void destroy() {
        stop();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    // =========================================================================
    // Directory Change Detection
    // =========================================================================

    /**
     * Runs the initial check when observer starts, forcing re-evaluation
     * even if the path hasn't changed since last check.
     */
    private void runInitialCheck() {
        String path = getCurrentDirectoryPath();
        if (path != null) {
            // Clear last path to force check
            lastCheckedPath = null;
            tokenErrorShownForCurrentPath = false;
            handleDirectoryChanged(path);
        }
    }

    /**
     * Called when a directory change is detected (from FileObserver, polling, or external trigger).
     * 
     * @param newPath The new current directory path.
     */
    private void handleDirectoryChanged(String newPath) {
        if (newPath == null) return;

        String normalizedPath = normalizePath(newPath);
        
        // Reset token error flag on path change
        if (!normalizedPath.equals(lastCheckedPath)) {
            tokenErrorShownForCurrentPath = false;
        }

        if (normalizedPath.equals(lastCheckedPath)) {
            // Same directory — only recheck if we're showing COMMIT state
            // (user might have committed changes)
            if (lastRenderedState == GitUiState.SHOW_COMMIT) {
                scheduleDebouncedCheck();
            }
            return;
        }

        lastCheckedPath = normalizedPath;
        Log.d(TAG, "Directory changed → " + normalizedPath);
        scheduleDebouncedCheck();
    }

    /**
     * Schedules a debounced check to avoid rapid evaluations.
     */
    private void scheduleDebouncedCheck() {
        mainHandler.removeCallbacks(debouncedCheck);
        mainHandler.postDelayed(debouncedCheck, DEBOUNCE_MS);
    }

    /**
     * Called by polling to check current directory.
     */
    private void checkCurrentDirectory() {
        if (!isRunning) return;
        String path = getCurrentDirectoryPath();
        if (path != null) {
            handleDirectoryChanged(path);
        }
    }

    // =========================================================================
    // Core Git State Decision Logic
    // =========================================================================

    /**
     * Performs the Git state check on the given path.
     * All GitOperations calls are wrapped in try/catch.
     * 
     * @param path The directory path to check.
     */
    private void performGitStateCheck(String path) {
        if (!isRunning || path == null) return;

        // Post to background thread for I/O operations
        backgroundHandler.post(() -> {
            GitUiState determinedState;
            String errorMessage = null;

            try {
                determinedState = determineGitUiState(path);
            } catch (Exception e) {
                Log.e(TAG, "Error checking Git state for: " + path, e);
                determinedState = GitUiState.NONE;
                errorMessage = "Git check failed: " + e.getMessage();
            }

            final GitUiState finalState = determinedState;
            final String finalError = errorMessage;

            // Post result back to main thread
            mainHandler.post(() -> {
                if (!isRunning) return;
                safeUpdateUiState(finalState);
                if (finalError != null) {
                    safeShowError(finalError);
                }
            });
        });
    }

    /**
     * Core decision logic: determines the appropriate UI state based on
     * {isGitRepo, hasValidToken, hasUncommittedChanges}.
     * 
     * <p>Decision table:</p>
     * <pre>
     * isGitRepo | hasToken | hasChanges | Result
     * ---------+----------+------------+--------
     *   false  |    *     |     *      | NONE
     *   true   |   false  |     *      | NONE + show error (once per path)
     *   true   |   true   |   true     | SHOW_COMMIT
     *   true   |   true   |   false    | SHOW_UPLOAD
     * </pre>
     * 
     * @param path The directory path to evaluate.
     * @return The determined UI state.
     * @throws Exception If GitOperations calls fail.
     */
    private GitUiState determineGitUiState(String path) throws Exception {
        // Step 1: Is this a Git repository?
        boolean isGitRepo = GitOperations.isGitRepo(path);
        if (!isGitRepo) {
            Log.d(TAG, "Not a Git repository: " + path);
            return GitUiState.NONE;
        }

        // Step 2: Is there a valid auth token?
        boolean hasValidToken = GitOperations.hasValidToken();
        if (!hasValidToken) {
            Log.d(TAG, "No valid auth token");
            // Show error only once per path to avoid spam
            if (!tokenErrorShownForCurrentPath) {
                tokenErrorShownForCurrentPath = true;
                // Schedule error on main thread
                mainHandler.post(() -> {
                    if (isRunning) {
                        safeShowError("No authentication token found.\nPlease connect your Git account in settings.");
                    }
                });
            }
            return GitUiState.NONE;
        }

        // Step 3: Are there uncommitted changes?
        boolean hasUncommittedChanges = GitOperations.hasUncommittedChanges(path);
        
        if (hasUncommittedChanges) {
            Log.d(TAG, "Detected uncommitted changes");
            return GitUiState.SHOW_COMMIT;
        } else {
            Log.d(TAG, "Repository clean — ready for upload/sync");
            return GitUiState.SHOW_UPLOAD;
        }
    }

    // =========================================================================
    // FileObserver Setup
    // =========================================================================

    /**
     * Sets up FileObservers to watch for directory and .git changes.
     * Uses both .git directory watcher and parent directory watcher.
     */
    private void setupFileObservers() {
        teardownFileObservers();

        String path = getCurrentDirectoryPath();
        if (path == null) return;

        try {
            // Watch .git directory for changes (commit, status changes)
            File gitDir = new File(path, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                int gitMask = FileObserver.CREATE |
                              FileObserver.DELETE |
                              FileObserver.MODIFY |
                              FileObserver.MOVED_FROM |
                              FileObserver.MOVED_TO |
                              FileObserver.DELETE_SELF;

                gitDirObserver = createFileObserver(gitDir.getAbsolutePath(), gitMask,
                    (event, name) -> {
                        if (!isRunning) return;
                        // .git changed — re-evaluate current path
                        mainHandler.post(() -> {
                            if (isRunning && lastCheckedPath != null) {
                                // Force recheck by clearing last path
                                String currentPath = getCurrentDirectoryPath();
                                lastCheckedPath = null;
                                if (currentPath != null) {
                                    handleDirectoryChanged(currentPath);
                                }
                            }
                        });
                    }
                );
                if (gitDirObserver != null) {
                    gitDirObserver.startWatching();
                    Log.d(TAG, "Watching .git directory: " + gitDir.getAbsolutePath());
                }
            }

            // Watch parent directory for navigation events
            File parentDir = new File(path).getParentFile();
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
                int parentMask = FileObserver.MOVED_TO |
                                 FileObserver.CREATE |
                                 FileObserver.MODIFY |
                                 FileObserver.DELETE;

                parentDirObserver = createFileObserver(parentDir.getAbsolutePath(), parentMask,
                    (event, name) -> {
                        if (!isRunning || name == null) return;
                        // Parent changed — check if current directory changed
                        mainHandler.post(() -> {
                            if (isRunning) {
                                checkCurrentDirectory();
                            }
                        });
                    }
                );
                if (parentDirObserver != null) {
                    parentDirObserver.startWatching();
                    Log.d(TAG, "Watching parent directory: " + parentDir.getAbsolutePath());
                }
            }

        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException setting up FileObserver (scoped storage?)", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to setup FileObservers, relying on polling", e);
        }
    }

    /**
     * Creates a FileObserver compatible with Android 10-17.
     * Uses legacy constructor for broader compatibility.
     * 
     * @param path Path to observe.
     * @param mask Event mask.
     * @param callback Callback for events.
     * @return FileObserver instance, or null if creation fails.
     */
    private FileObserver createFileObserver(String path, int mask, 
            FileEventCallback callback) {
        try {
            // Legacy constructor works on all API levels
            return new FileObserver(path, mask) {
                @Override
                public void onEvent(int event, String name) {
                    // Filter out irrelevant events
                    if ((event & FileObserver.DELETE_SELF) != 0) {
                        // Directory itself deleted — will be re-setup on next check
                        return;
                    }
                    callback.onEvent(event, name);
                }
            };
        } catch (Exception e) {
            Log.w(TAG, "Failed to create FileObserver for: " + path, e);
            return null;
        }
    }

    /**
     * Callback interface for FileObserver events.
     */
    @FunctionalInterface
    private interface FileEventCallback {
        void onEvent(int event, String name);
    }

    /**
     * Tears down all FileObservers.
     */
    private void teardownFileObservers() {
        safeStopObserver(gitDirObserver);
        gitDirObserver = null;
        safeStopObserver(parentDirObserver);
        parentDirObserver = null;
    }

    /**
     * Safely stops a FileObserver, catching any exceptions.
     */
    private void safeStopObserver(FileObserver observer) {
        if (observer == null) return;
        try {
            observer.stopWatching();
        } catch (Exception e) {
            Log.w(TAG, "Error stopping FileObserver", e);
        }
    }

    // =========================================================================
    // Polling
    // =========================================================================

    private void schedulePolling() {
        cancelPolling();
        if (backgroundHandler != null) {
            backgroundHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }
    }

    private void cancelPolling() {
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacks(pollRunnable);
        }
    }

    // =========================================================================
    // Thread Management
    // =========================================================================

    private void ensureBackgroundThread() {
        if (backgroundThread != null && backgroundThread.isAlive()) {
            return;
        }
        backgroundThread = new HandlerThread("GitObserver-Background", 
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    // =========================================================================
    // Read-Only Access to MainActivity/FileManager
    // =========================================================================

    /**
     * Gets the current directory path from MainActivity (read-only).
     * Never calls any setters or mutates state.
     * 
     * @return The current directory path, or null if unavailable.
     */
    private String getCurrentDirectoryPath() {
        Activity activity = activityRef.get();
        if (activity == null) {
            Log.d(TAG, "Activity reference lost");
            return null;
        }

        try {
            // Type-safe approach via interface
            if (activity instanceof DirectoryProvider) {
                return ((DirectoryProvider) activity).getCurrentDirectoryPath();
            }
            
            // Fallback: direct static call to MainActivity
            // Safe because we only read, never write
            return MainActivity.getCurrentDirectoryPath();
            
        } catch (Exception e) {
            Log.w(TAG, "Could not get current directory path", e);
            return null;
        }
    }

    // =========================================================================
    // UI Updates (delegated to GitUIInjector)
    // =========================================================================

    /**
     * Updates the UI state via GitUIInjector. Called on main thread.
     * 
     * @param state The desired UI state.
     */
    private void safeUpdateUiState(GitUiState state) {
        if (state == lastRenderedState) return;
        lastRenderedState = state;
        
        try {
            GitUIInjector.updateButtonState(state);
            Log.d(TAG, "UI state updated: " + state);
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI state via GitUIInjector", e);
        }
    }

    /**
     * Shows an error popup via GitUIInjector. Called on main thread.
     * 
     * @param message The error message to display.
     */
    private void safeShowError(String message) {
        try {
            GitUIInjector.showErrorPopup(message);
        } catch (Exception e) {
            Log.e(TAG, "Error showing error popup via GitUIInjector", e);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Normalizes a file path for consistent comparison.
     * Handles trailing slashes and path separators.
     * 
     * @param path The path to normalize.
     * @return Normalized path string.
     */
    private String normalizePath(String path) {
        if (path == null) return "";
        // Normalize separators and remove trailing slashes
        return path.replace('\\', '/')
                   .replaceAll("/+", "/")
                   .replaceAll("/+$", "");
    }

    // =========================================================================
    // DirectoryProvider Interface
    // =========================================================================

    /**
     * Interface that MainActivity can optionally implement for type-safe
     * directory path access without reflection.
     * 
     * <p>If MainActivity implements this interface, GitObserver will use it
     * instead of the static method fallback.</p>
     */
    public interface DirectoryProvider {
        /**
         * Returns the current directory path being browsed.
         * 
         * @return Absolute path to current directory, or null if none.
         */
        String getCurrentDirectoryPath();
    }

    // =========================================================================
    // GitBootstrap — Static Helper for External Attachment
    // =========================================================================

    /**
     * Static bootstrap helper for attaching {@link GitObserver} to an Activity
     * without the Activity needing to import GitObserver's type.
     * 
     * <p>This enables Phase 1 to compile standalone. The wiring happens in
     * Phase 2 bootstrap, Application class, or an optional attachment call.</p>
     * 
     * <p>Usage example in Application class or composition root:</p>
     * <pre>{@code
     * // In Application.onCreate() or first Activity.onCreate()
     * GitBootstrap.attach(activity, toolbarContainer);
     * 
     * // Register lifecycle callbacks
     * registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
     *     public void onActivityResumed(Activity a) { GitBootstrap.onResume(a); }
     *     public void onActivityPaused(Activity a) { GitBootstrap.onPause(a); }
     *     public void onActivityDestroyed(Activity a) { GitBootstrap.onDestroy(a); }
     *     // ... other required methods can be empty
     * });
     * }</pre>
     */
    public static final class GitBootstrap {

        private static final String TAG = "GitBootstrap";
        private static final Object LOCK = new Object();

        /** Weak-keyed map tracking observers per activity to avoid leaks. */
        private static final WeakHashMap<Activity, GitObserver> observerMap = new WeakHashMap<>();

        /** Whether the Git module is available and initialized. */
        private static volatile boolean isInitialized = false;

        /** Last anchor view group for re-initialization scenarios. */
        private static volatile ViewGroup lastAnchor;

        /** Prevent instantiation. */
        private GitBootstrap() {}

        /**
         * Checks if the Git module is available and initialized.
         * 
         * @return {@code true} if Git operations and UI injection are ready.
         */
        public static boolean isGitAvailable() {
            return isInitialized;
        }

        /**
         * Initializes the Git module and creates an observer for the given activity.
         * 
         * <p>This method is safe to call multiple times — subsequent calls with the
         * same activity will replace the existing observer.</p>
         * 
         * @param activity The main activity to observe. Must not be null.
         * @param anchor   The ViewGroup to inject Git UI elements into. May be null
         *                 if UI injection is handled separately.
         * @return {@code true} if attachment was successful, {@code false} otherwise.
         */
        public static boolean attach(Activity activity, ViewGroup anchor) {
            if (activity == null) {
                Log.w(TAG, "attach() called with null activity");
                return false;
            }

            synchronized (LOCK) {
                try {
                    // Initialize GitUIInjector with anchor if provided
                    if (anchor != null) {
                        GitUIInjector.initialize(anchor);
                        lastAnchor = anchor;
                    }

                    // Clean up any existing observer for this activity
                    GitObserver existing = observerMap.get(activity);
                    if (existing != null) {
                        existing.destroy();
                    }

                    // Create and store new observer
                    GitObserver observer = new GitObserver(activity);
                    observerMap.put(activity, observer);

                    isInitialized = true;
                    Log.d(TAG, "GitBootstrap attached to: " + activity.getClass().getSimpleName());
                    return true;

                } catch (Exception e) {
                    Log.e(TAG, "Failed to attach GitBootstrap", e);
                    isInitialized = false;
                    return false;
                }
            }
        }

        /**
         * Called when the activity resumes — starts observation.
         * 
         * @param activity The activity that resumed.
         */
        public static void onResume(Activity activity) {
            if (activity == null) return;

            synchronized (LOCK) {
                GitObserver observer = observerMap.get(activity);
                if (observer != null) {
                    Log.d(TAG, "onResume → starting observer");
                    observer.start();
                }
            }
        }

        /**
         * Called when the activity pauses — stops observation.
         * 
         * @param activity The activity that paused.
         */
        public static void onPause(Activity activity) {
            if (activity == null) return;

            synchronized (LOCK) {
                GitObserver observer = observerMap.get(activity);
                if (observer != null) {
                    Log.d(TAG, "onPause → stopping observer");
                    observer.stop();
                }
            }
        }

        /**
         * Called when the activity is destroyed — cleans up observer.
         * 
         * @param activity The activity being destroyed.
         */
        public static void onDestroy(Activity activity) {
            if (activity == null) return;

            synchronized (LOCK) {
                GitObserver observer = observerMap.remove(activity);
                if (observer != null) {
                    Log.d(TAG, "onDestroy → destroying observer");
                    observer.destroy();
                }
            }
        }

        /**
         * Manually triggers a directory check. Useful after programmatic navigation
         * or when you know the directory has changed but FileObserver didn't catch it.
         * 
         * @param activity The activity to check.
         */
        public static void triggerCheck(Activity activity) {
            if (activity == null) return;

            synchronized (LOCK) {
                GitObserver observer = observerMap.get(activity);
                if (observer != null && observer.isRunning) {
                    Log.d(TAG, "Manual check triggered");
                    observer.checkCurrentDirectory();
                }
            }
        }

        /**
         * Updates the anchor view group (e.g., after configuration change).
         * 
         * @param activity The activity.
         * @param newAnchor The new anchor ViewGroup.
         */
        public static void updateAnchor(Activity activity, ViewGroup newAnchor) {
            if (activity == null || newAnchor == null) return;

            synchronized (LOCK) {
                GitUIInjector.initialize(newAnchor);
                lastAnchor = newAnchor;
                
                // Force UI re-render
                GitObserver observer = observerMap.get(activity);
                if (observer != null && observer.isRunning) {
                    observer.lastRenderedState = GitUiState.NONE; // Force re-render
                    observer.checkCurrentDirectory();
                }
            }
        }

        /**
         * Detaches all observers and cleans up all Git UI resources.
         * Call this when the application is terminating.
         */
        public static void detachAll() {
            synchronized (LOCK) {
                Log.d(TAG, "Detaching all observers");

                for (GitObserver observer : observerMap.values()) {
                    if (observer != null) {
                        observer.destroy();
                    }
                }
                observerMap.clear();

                isInitialized = false;

                if (lastAnchor != null) {
                    try {
                        GitUIInjector.cleanup();
                    } catch (Exception e) {
                        Log.w(TAG, "Error during GitUIInjector cleanup", e);
                    }
                    lastAnchor = null;
                }
            }
        }

        /**
         * Gets the current UI state for an activity (for testing/debugging).
         * 
         * @param activity The activity.
         * @return Current GitUiState, or NONE if not available.
         */
        public static GitUiState getCurrentState(Activity activity) {
            if (activity == null) return GitUiState.NONE;
            
            synchronized (LOCK) {
                GitObserver observer = observerMap.get(activity);
                return observer != null ? observer.lastRenderedState : GitUiState.NONE;
            }
        }
    }
}