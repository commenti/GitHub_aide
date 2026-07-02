/*
 * GitOperations.java
 * Path: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/git/
 * 
 * Stateless-per-call execution layer for all git actions.
 * Wraps libgit2 (via JNI) or native git binary invocation.
 * Token storage backed by EncryptedSharedPreferences + Android Keystore.
 * 
 * Compatible with Android 10 (API 29) through Android 17 (API 35+).
 * 
 * Does NOT import any Android View classes.
 */
package com.adobs.ide.git;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;

/**
 * Stateless execution layer for all Git operations.
 * 
 * <p>This class provides:</p>
 * <ul>
 *   <li>Repository state queries ({@link #isGitRepo}, {@link #hasUncommittedChanges})</li>
 *   <li>Mutating operations ({@link #commit}, {@link #push}, {@link #initRepo})</li>
 *   <li>Encrypted token management ({@link #hasValidToken}, {@link #getToken}, {@link #saveToken})</li>
 * </ul>
 * 
 * <p>All network/disk-heavy operations run off the main thread via an internal
 * {@link Executor}, with results delivered via callbacks.</p>
 * 
 * <p>This class does NOT touch any UI element directly and does NOT import
 * Android View classes.</p>
 * 
 * <p>Usage pattern:</p>
 * <pre>{@code
 * GitOperations ops = GitOperations.getInstance(context);
 * 
 * // Sync query (fast, can run on caller's thread if already background)
 * boolean isRepo = ops.isGitRepo("/path/to/dir");
 * 
 * // Async operation with callback
 * ops.commit("/path/to/dir", "Initial commit", new GitOperations.GitCallback<>() {
 *     public void onSuccess(GitResult.Success result) { ... }
 *     public void onError(GitException error) { ... }
 * });
 * }</pre>
 */
public final class GitOperations {

    private static final String TAG = "GitOperations";

    // =========================================================================
    // Constants
    // =========================================================================

    /** Name of the Git directory indicator. */
    private static final String GIT_DIR_NAME = ".git";

    /** SharedPreferences file name for encrypted storage. */
    private static final String PREFS_FILE_NAME = "git_secure_prefs";

    /** Key for storing the Personal Access Token. */
    private static final String KEY_PAT = "github_personal_access_token";

    /** Key alias for the Android Keystore master key. */
    private static final String KEYSTORE_ALIAS = "git_ops_master_key";

    /** Timeout for native git commands in milliseconds. */
    private static final long GIT_COMMAND_TIMEOUT_MS = 30_000L;

    /** Number of threads for async operations. */
    private static final int EXECUTOR_POOL_SIZE = 2;

    // =========================================================================
    // Singleton
    // =========================================================================

    private static volatile GitOperations instance;
    private static final Object INSTANCE_LOCK = new Object();

    /**
     * Gets the singleton instance.
     * 
     * @param context Application context (will not be leaked).
     * @return Singleton instance.
     */
    public static GitOperations getInstance(Context context) {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new GitOperations(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Gets instance without context (for cases where context was already set).
     * Throws if not initialized.
     * 
     * @return Singleton instance.
     * @throws IllegalStateException If not initialized with context.
     */
    public static GitOperations getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                "GitOperations not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }

    // =========================================================================
    // Instance Fields
    // =========================================================================

    /** Application context (safe to hold). */
    private final Context appContext;

    /** Executor for async operations — never runs on main thread. */
    private final Executor asyncExecutor;

    /** Cached encrypted preferences — lazy initialized. */
    private volatile SharedPreferences encryptedPrefs;

    /** Whether libgit2 native library is available. */
    private final boolean isLibgit2Available;

    /** Whether native git binary is available. */
    private volatile Boolean isNativeGitAvailable;

    // =========================================================================
    // Result Types
    // =========================================================================

    /**
     * Sealed result type for Git operations.
     * Either {@link Success} or {@link Failure}.
     */
    public sealed interface GitResult<T> permits GitResult.Success, GitResult.Failure {
        
        /**
         * Successful result with data.
         */
        record Success<T>(T data, String message) implements GitResult<T> {
            public Success(T data) {
                this(data, "Operation completed successfully");
            }
        }

        /**
         * Failed result with error information.
         */
        record Failure<T>(GitException error) implements GitResult<T> {
            /**
             * Gets the user-displayable error message.
             */
            public String getUserMessage() {
                return error.getUserMessage();
            }
        }
    }

    /**
     * Callback for async Git operations.
     * 
     * @param <T> The success result data type.
     */
    public interface GitCallback<T> {
        /**
         * Called on success. Runs on background thread — caller must post to UI if needed.
         * 
         * @param result The success result.
         */
        void onSuccess(GitResult.Success<T> result);

        /**
         * Called on error. Runs on background thread — caller must post to UI if needed.
         * 
         * @param error The error with user-displayable message.
         */
        void onError(GitException error);
    }

    /**
     * Simplified callback without result data.
     */
    public interface SimpleGitCallback {
        void onSuccess(String message);
        void onError(GitException error);
    }

    // =========================================================================
    // GitException
    // =========================================================================

    /**
     * Exception for Git operation failures with a user-displayable message.
     * Never contains stack traces or internal details in the user message.
     */
    public static class GitException extends Exception {
        
        private final String userMessage;
        private final ErrorType errorType;

        /**
         * Error type classification for UI handling.
         */
        public enum ErrorType {
            /** Network connectivity issue. */
            NETWORK,
            /** Authentication failure (bad token, expired, etc.). */
            AUTH,
            /** Repository not found or not a git repo. */
            REPO_STATE,
            /** File system permission or I/O error. */
            IO,
            /** Git operation failed (merge conflict, etc.). */
            GIT_OPERATION,
            /** Unknown or uncategorized error. */
            UNKNOWN
        }

        public GitException(String userMessage, ErrorType errorType) {
            super(userMessage);
            this.userMessage = userMessage;
            this.errorType = errorType;
        }

        public GitException(String userMessage, ErrorType errorType, Throwable cause) {
            super(userMessage, cause);
            this.userMessage = userMessage;
            this.errorType = errorType;
        }

        /**
         * Gets the user-displayable message (no stack traces or internal details).
         */
        public String getUserMessage() {
            return userMessage;
        }

        /**
         * Gets the error type for UI-specific handling.
         */
        public ErrorType getErrorType() {
            return errorType;
        }
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    private GitOperations(Context appContext) {
        this.appContext = appContext;
        this.asyncExecutor = Executors.newFixedThreadPool(EXECUTOR_POOL_SIZE, r -> {
            Thread t = new Thread(r, "GitOps-Worker");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.setDaemon(true);
            return t;
        });
        this.isLibgit2Available = checkLibgit2Availability();
        Log.d(TAG, "GitOperations initialized — libgit2: " + isLibgit2Available);
    }

    // =========================================================================
    // Repository State Queries (sync — caller manages threading)
    // =========================================================================

    /**
     * Checks if the given path contains a Git repository.
     * 
     * <p>This is a fast filesystem check suitable for polling.</p>
     * 
     * @param path The directory path to check.
     * @return {@code true} if {@code path/.git} exists and is a directory,
     *         or if {@code path} itself is the .git directory (bare repo).
     */
    public boolean isGitRepo(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        // Check for .git subdirectory (standard non-bare repo)
        File gitDir = new File(dir, GIT_DIR_NAME);
        if (gitDir.exists() && gitDir.isDirectory()) {
            return true;
        }

        // Check for .git file (worktree or submodule reference)
        File gitFile = new File(dir, GIT_DIR_NAME);
        if (gitFile.exists() && gitFile.isFile()) {
            // Could be a worktree — basic validation
            return true;
        }

        // Check if path itself is a .git directory (bare repo)
        if (dir.getName().equals(GIT_DIR_NAME)) {
            // Verify it has git-specific structure
            File headFile = new File(dir, "HEAD");
            File objectsDir = new File(dir, "objects");
            return headFile.isFile() || objectsDir.isDirectory();
        }

        return false;
    }

    /**
     * Checks if the repository has uncommitted changes.
     * 
     * <p>This runs {@code git status --porcelain} and checks for any output.</p>
     * 
     * @param path The repository path.
     * @return {@code true} if there are staged, unstaged, or untracked changes.
     * @throws GitException If the check fails.
     */
    public boolean hasUncommittedChanges(String path) throws GitException {
        if (!isGitRepo(path)) {
            return false;
        }

        // Try libgit2 first if available
        if (isLibgit2Available) {
            try {
                return nativeHasUncommittedChanges(path);
            } catch (GitException e) {
                Log.w(TAG, "libgit2 check failed, falling back to native git", e);
                // Fall through to native git
            }
        }

        // Fallback to native git binary
        return nativeGitHasUncommittedChanges(path);
    }

    /**
     * Gets a summary of uncommitted changes.
     * 
     * @param path The repository path.
     * @return List of change descriptions, or empty list if clean.
     * @throws GitException If the check fails.
     */
    public List<String> getUncommittedChangesSummary(String path) throws GitException {
        if (!isGitRepo(path)) {
            return new ArrayList<>();
        }

        String output = executeGitCommand(path, "status", "--porcelain", "-b");
        
        List<String> changes = new ArrayList<>();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("##")) {
                changes.add(parseStatusLine(trimmed));
            }
        }
        return changes;
    }

    /**
     * Gets the current branch name.
     * 
     * @param path The repository path.
     * @return Branch name, or "HEAD" if detached.
     * @throws GitException If the check fails.
     */
    public String getCurrentBranch(String path) throws GitException {
        if (!isGitRepo(path)) {
            throw new GitException("Not a Git repository", GitException.ErrorType.REPO_STATE);
        }

        String output = executeGitCommand(path, "rev-parse", "--abbrev-ref", "HEAD");
        return output.trim();
    }

    /**
     * Gets the remote URL for the repository.
     * 
     * @param path The repository path.
     * @return Remote URL, or empty string if none configured.
     * @throws GitException If the check fails.
     */
    public String getRemoteUrl(String path) throws GitException {
        if (!isGitRepo(path)) {
            return "";
        }

        try {
            return executeGitCommand(path, "config", "--get", "remote.origin.url").trim();
        } catch (GitException e) {
            // No remote configured is not an error
            return "";
        }
    }

    // =========================================================================
    // Token Management (encrypted storage)
    // =========================================================================

    /**
     * Checks if a valid (non-empty) token is stored.
     * 
     * @return {@code true} if a token exists and is non-empty.
     */
    public boolean hasValidToken() {
        try {
            String token = getToken();
            return token != null && !token.trim().isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "Error checking token validity", e);
            return false;
        }
    }

    /**
     * Retrieves the stored Personal Access Token.
     * 
     * <p>Token is stored in EncryptedSharedPreferences backed by Android Keystore.
     * Never returns plaintext from regular SharedPreferences.</p>
     * 
     * @return The PAT string, or null if not set or on error.
     */
    public String getToken() {
        try {
            SharedPreferences prefs = getEncryptedPrefs();
            return prefs.getString(KEY_PAT, null);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving token from encrypted storage", e);
            return null;
        }
    }

    /**
     * Saves a Personal Access Token to encrypted storage.
     * 
     * <p>The token is stored using EncryptedSharedPreferences with Android Keystore
     * master key. It is NEVER stored in plaintext.</p>
     * 
     * @param pat The Personal Access Token to save.
     * @return {@code true} if saved successfully, {@code false} on error.
     */
    public boolean saveToken(String pat) {
        if (pat == null) {
            Log.w(TAG, "Attempted to save null token");
            return false;
        }

        try {
            SharedPreferences prefs = getEncryptedPrefs();
            prefs.edit()
                 .putString(KEY_PAT, pat.trim())
                 .apply();
            Log.d(TAG, "Token saved to encrypted storage");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving token to encrypted storage", e);
            return false;
        }
    }

    /**
     * Removes the stored token.
     * 
     * @return {@code true} if removed successfully.
     */
    public boolean removeToken() {
        try {
            SharedPreferences prefs = getEncryptedPrefs();
            prefs.edit()
                 .remove(KEY_PAT)
                 .apply();
            Log.d(TAG, "Token removed from encrypted storage");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error removing token", e);
            return false;
        }
    }

    /**
     * Gets or creates the EncryptedSharedPreferences instance.
     * 
     * <p>Uses Android Keystore master key with AES256-GCM for encryption.
     * Compatible with Android 10+ (API 29+).</p>
     * 
     * @return Encrypted SharedPreferences instance.
     * @throws Exception If initialization fails.
     */
    private SharedPreferences getEncryptedPrefs() throws Exception {
        if (encryptedPrefs != null) {
            return encryptedPrefs;
        }

        synchronized (this) {
            if (encryptedPrefs != null) {
                return encryptedPrefs;
            }

            MasterKey masterKey;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ — use newer MasterKey.Builder
                masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .setKeyAlias(KEYSTORE_ALIAS)
                    .build();
            } else {
                // Android 10 — use KeyGenParameterSpec directly
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPTION | KeyProperties.PURPOSE_DECRYPTION
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();

                masterKey = new MasterKey.Builder(appContext)
                    .setKeyGenParameterSpec(spec)
                    .build();
            }

            encryptedPrefs = EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            return encryptedPrefs;
        }
    }

    /**
     * Clears the encrypted preferences cache.
     * Called on logout or when keystore state changes.
     */
    public void resetEncryptedStorage() {
        synchronized (this) {
            encryptedPrefs = null;
        }
        try {
            // Delete the prefs file to force re-creation
            File prefsFile = new File(appContext.getApplicationInfo().dataDir, 
                "shared_prefs/" + PREFS_FILE_NAME + ".xml");
            if (prefsFile.exists()) {
                prefsFile.delete();
            }
            // Also delete the journal file if it exists
            File journalFile = new File(appContext.getApplicationInfo().dataDir,
                "shared_prefs/" + PREFS_FILE_NAME + ".xml-journal");
            if (journalFile.exists()) {
                journalFile.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error clearing encrypted storage files", e);
        }
    }

    // =========================================================================
    // Mutating Operations (async with callbacks)
    // =========================================================================

    /**
     * Initializes a new Git repository at the given path.
     * 
     * @param path The directory path.
     * @param callback Callback for result (runs on background thread).
     */
    public void initRepo(String path, SimpleGitCallback callback) {
        asyncExecutor.execute(() -> {
            try {
                File dir = new File(path);
                if (!dir.exists()) {
                    if (!dir.mkdirs()) {
                        throw new GitException(
                            "Failed to create directory: " + path,
                            GitException.ErrorType.IO
                        );
                    }
                }

                // Try libgit2 first
                if (isLibgit2Available) {
                    try {
                        nativeInitRepo(path);
                        notifySuccess(callback, "Repository initialized");
                        return;
                    } catch (GitException e) {
                        Log.w(TAG, "libgit2 init failed, falling back", e);
                    }
                }

                // Fallback to native git
                executeGitCommand(path, "init");
                notifySuccess(callback, "Repository initialized");
                
            } catch (GitException e) {
                notifyError(callback, e);
            } catch (Exception e) {
                notifyError(callback, new GitException(
                    "Failed to initialize repository: " + e.getMessage(),
                    GitException.ErrorType.GIT_OPERATION,
                    e
                ));
            }
        });
    }

    /**
     * Commits all staged changes with the given message.
     * 
     * @param path The repository path.
     * @param message The commit message.
     * @param callback Callback for result (runs on background thread).
     */
    public void commit(String path, String message, SimpleGitCallback callback) {
        if (message == null || message.trim().isEmpty()) {
            notifyError(callback, new GitException(
                "Commit message cannot be empty",
                GitException.ErrorType.GIT_OPERATION
            ));
            return;
        }

        asyncExecutor.execute(() -> {
            try {
                validateGitRepo(path);

                // Stage all changes
                executeGitCommand(path, "add", "-A");

                // Check if there's anything to commit
                String status = executeGitCommand(path, "status", "--porcelain");
                if (status.trim().isEmpty()) {
                    notifyError(callback, new GitException(
                        "Nothing to commit — working tree is clean",
                        GitException.ErrorType.GIT_OPERATION
                    ));
                    return;
                }

                // Commit
                String output = executeGitCommand(path, "commit", "-m", message);
                
                // Extract short hash for message
                String shortHash = extractShortHash(output);
                String successMsg = shortHash != null 
                    ? "Committed: " + shortHash + " — " + message
                    : "Committed: " + message;
                    
                notifySuccess(callback, successMsg);

            } catch (GitException e) {
                notifyError(callback, e);
            } catch (Exception e) {
                notifyError(callback, new GitException(
                    "Commit failed: " + e.getMessage(),
                    GitException.ErrorType.GIT_OPERATION,
                    e
                ));
            }
        });
    }

    /**
     * Pushes commits to the remote repository.
     * 
     * @param path The repository path.
     * @param callback Callback for result (runs on background thread).
     */
    public void push(String path, SimpleGitCallback callback) {
        asyncExecutor.execute(() -> {
            try {
                validateGitRepo(path);
                validateTokenForPush();

                // Get current branch
                String branch = executeGitCommand(path, "rev-parse", "--abbrev-ref", "HEAD").trim();

                // Check if remote is configured
                String remoteUrl = getRemoteUrl(path);
                if (remoteUrl.isEmpty()) {
                    throw new GitException(
                        "No remote repository configured.\nPlease add a remote with: git remote add origin <url>",
                        GitException.ErrorType.REPO_STATE
                    );
                }

                // Push
                String output = executeGitCommandWithAuth(
                    path, 
                    "push", "-u", "origin", branch
                );

                notifySuccess(callback, "Pushed to origin/" + branch);

            } catch (GitException e) {
                // Enhance auth errors
                if (e.getErrorType() == GitException.ErrorType.AUTH) {
                    notifyError(callback, new GitException(
                        "Push failed: Authentication error.\nPlease check your access token.",
                        GitException.ErrorType.AUTH,
                        e
                    ));
                } else {
                    notifyError(callback, e);
                }
            } catch (Exception e) {
                notifyError(callback, new GitException(
                    "Push failed: " + e.getMessage(),
                    GitException.ErrorType.NETWORK,
                    e
                ));
            }
        });
    }

    /**
     * Adds a remote to the repository.
     * 
     * @param path The repository path.
     * @param name The remote name (typically "origin").
     * @param url The remote URL.
     * @param callback Callback for result.
     */
    public void addRemote(String path, String name, String url, SimpleGitCallback callback) {
        if (name == null || url == null || name.isEmpty() || url.isEmpty()) {
            notifyError(callback, new GitException(
                "Remote name and URL are required",
                GitException.ErrorType.GIT_OPERATION
            ));
            return;
        }

        asyncExecutor.execute(() -> {
            try {
                validateGitRepo(path);
                executeGitCommand(path, "remote", "add", name, url);
                notifySuccess(callback, "Remote '" + name + "' added: " + url);
            } catch (GitException e) {
                // Check if remote already exists
                if (e.getUserMessage().contains("already exists")) {
                    notifyError(callback, new GitException(
                        "Remote '" + name + "' already exists. Use 'setRemoteUrl' to update it.",
                        GitException.ErrorType.GIT_OPERATION,
                        e
                    ));
                } else {
                    notifyError(callback, e);
                }
            } catch (Exception e) {
                notifyError(callback, new GitException(
                    "Failed to add remote: " + e.getMessage(),
                    GitException.ErrorType.GIT_OPERATION,
                    e
                ));
            }
        });
    }

    /**
     * Sets the URL for an existing remote.
     * 
     * @param path The repository path.
     * @param name The remote name.
     * @param url The new URL.
     * @param callback Callback for result.
     */
    public void setRemoteUrl(String path, String name, String url, SimpleGitCallback callback) {
        asyncExecutor.execute(() -> {
            try {
                validateGitRepo(path);
                executeGitCommand(path, "remote", "set-url", name, url);
                notifySuccess(callback, "Remote '" + name + "' URL updated");
            } catch (Exception e) {
                notifyError(callback, new GitException(
                    "Failed to set remote URL: " + e.getMessage(),
                    GitException.ErrorType.GIT_OPERATION,
                    e
                ));
            }
        });
    }

    /**
     * Performs init + add + commit + push in one operation for new repos.
     * 
     * @param path The directory path.
     * @param remoteUrl The remote repository URL.
     * @param message The initial commit message.
     * @param callback Callback for result.
     */
    public void initAndPush(String path, String remoteUrl, String message, 
                           SimpleGitCallback callback) {
        asyncExecutor.execute(() -> {
            try {
                // Step 1: Init if needed
                if (!isGitRepo(path)) {
                    executeGitCommand(path, "init");
                }

                // Step 2: Add remote
                try {
                    executeGitCommand(path, "remote", "add", "origin", remoteUrl);
                } catch (GitException e) {
                    // Remote might already exist, try to update
                    if (e.getUserMessage().contains("already exists")) {
                        executeGitCommand(path, "remote", "set-url", "origin", remoteUrl);
                    } else {
                        throw e;
                    }
                }

                // Step 3: Stage and commit
                executeGitCommand(path, "add", "-A");
                String status = executeGitCommand(path, "status", "--porcelain");
                if (!status.trim().isEmpty()) {
                    executeGitCommand(path, "commit", "-m", 
                        message != null ? message : "Initial commit");
                }

                // Step 4: Push
                String branch = executeGitCommand(path, "rev-parse", "--abbrev-ref", "HEAD").trim();
                executeGitCommandWithAuth(path, "push", "-u", "origin", branch);

                notifySuccess(callback, "Repository initialized and pushed to origin/" + branch);

            } catch (GitException e) {
                notifyError(callback, e);
            } catch (Exception e) {
                notifyError(callback, new GitException(
                    "Init and push failed: " + e.getMessage(),
                    GitException.ErrorType.GIT_OPERATION,
                    e
                ));
            }
        });
    }

    // =========================================================================
    // Native Git Binary Execution
    // =========================================================================

    /**
     * Executes a git command in the given directory.
     * 
     * @param workDir The working directory.
     * @param args Command arguments (e.g., "status", "--porcelain").
     * @return Command output (stdout).
     * @throws GitException If the command fails.
     */
    private String executeGitCommand(String workDir, String... args) throws GitException {
        return executeGitCommandWithAuth(workDir, args);
    }

    /**
     * Executes a git command with authentication token injected.
     * 
     * @param workDir The working directory.
     * @param args Command arguments.
     * @return Command output (stdout).
     * @throws GitException If the command fails.
     */
    private String executeGitCommandWithAuth(String workDir, String... args) throws GitException {
        ensureNativeGitAvailable();

        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        return executeCommand(workDir, command, true);
    }

    /**
     * Executes a native git command to check for uncommitted changes.
     */
    private boolean nativeGitHasUncommittedChanges(String path) throws GitException {
        String output = executeGitCommand(path, "status", "--porcelain");
        return output != null && !output.trim().isEmpty();
    }

    /**
     * Executes a generic command with timeout.
     * 
     * @param workDir Working directory.
     * @param command Command and arguments.
     * @param injectAuth Whether to inject auth token via environment.
     * @return Command stdout.
     * @throws GitException On failure.
     */
    private String executeCommand(String workDir, List<String> command, 
                                  boolean injectAuth) throws GitException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(false);

        // Inject authentication via environment for HTTPS URLs
        if (injectAuth) {
            String token = getToken();
            if (token != null && !token.isEmpty()) {
                // Use GIT_ASKPASS approach for auth
                // This avoids passing token in URL which is insecure
                pb.environment().put("GIT_TERMINAL_PROMPT", "0");
                pb.environment().put("GIT_ASKPASS", GitAskPass.class.getName());
                pb.environment().put("GIT_ASKPASS_TOKEN", token);
            }
        }

        Process process = null;
        try {
            process = pb.start();

            // Read stdout
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            }

            // Read stderr
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            // Wait with timeout
            boolean finished = process.waitFor(GIT_COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitException(
                    "Git command timed out after " + (GIT_COMMAND_TIMEOUT_MS / 1000) + " seconds",
                    GitException.ErrorType.UNKNOWN
                );
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = stderr.toString().trim();
                throw new GitException(
                    parseGitErrorMessage(errorMsg, exitCode),
                    classifyGitError(errorMsg, exitCode)
                );
            }

            return stdout.toString();

        } catch (IOException e) {
            throw new GitException(
                "Failed to execute git command: " + e.getMessage(),
                GitException.ErrorType.IO,
                e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new GitException(
                "Git command was interrupted",
                GitException.ErrorType.UNKNOWN,
                e
            );
        }
    }

    /**
     * Checks if native git binary is available.
     * 
     * @throws GitException If git is not found.
     */
    private void ensureNativeGitAvailable() throws GitException {
        if (isNativeGitAvailable == null) {
            synchronized (this) {
                if (isNativeGitAvailable == null) {
                    isNativeGitAvailable = checkNativeGitAvailable();
                }
            }
        }
        if (!isNativeGitAvailable) {
            throw new GitException(
                "Git is not installed or not in PATH.\n" +
                "Please install git or use a device with git available.",
                GitException.ErrorType.UNKNOWN
            );
        }
    }

    /**
     * Checks for native git binary availability.
     */
    private boolean checkNativeGitAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
                String version = reader.readLine();
                Log.d(TAG, "Native git found: " + version);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.d(TAG, "Native git not available: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // libgit2 Native Methods (JNI)
    // =========================================================================

    /**
     * Checks if libgit2 native library is available.
     */
    private boolean checkLibgit2Availability() {
        try {
            System.loadLibrary("git2");
            Log.d(TAG, "libgit2 native library loaded");
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.d(TAG, "libgit2 native library not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * Native method: check for uncommitted changes via libgit2.
     */
    private native boolean nativeHasUncommittedChanges(String path) throws GitException;

    /**
     * Native method: initialize repository via libgit2.
     */
    private native void nativeInitRepo(String path) throws GitException;

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Validates that the path is a Git repository.
     * 
     * @throws GitException If not a valid repo.
     */
    private void validateGitRepo(String path) throws GitException {
        if (!isGitRepo(path)) {
            throw new GitException(
                "Not a Git repository: " + path + "\nPlease initialize a repository first.",
                GitException.ErrorType.REPO_STATE
            );
        }
    }

    /**
     * Validates that a token is available for push operations.
     * 
     * @throws GitException If no valid token.
     */
    private void validateTokenForPush() throws GitException {
        if (!hasValidToken()) {
            throw new GitException(
                "No authentication token found.\nPlease add your GitHub Personal Access Token in settings.",
                GitException.ErrorType.AUTH
            );
        }
    }

    /**
     * Parses a git status line into a human-readable format.
     */
    private String parseStatusLine(String line) {
        if (line.length() < 2) return line;

        char index = line.charAt(0);
        char workTree = line.charAt(1);
        String file = line.length() > 3 ? line.substring(3) : "unknown";

        // Untracked files start with '?'
        if (index == '?' && workTree == '?') {
            return "Untracked: " + file;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(file);

        if (index == 'M') sb.append(" (staged modified)");
        else if (index == 'A') sb.append(" (staged added)");
        else if (index == 'D') sb.append(" (staged deleted)");
        else if (index == 'R') sb.append(" (staged renamed)");

        if (workTree == 'M' && index != 'M') sb.append(" (modified)");
        else if (workTree == 'D' && index != 'D') sb.append(" (deleted)");

        return sb.toString();
    }

    /**
     * Extracts short commit hash from git output.
     */
    private String extractShortHash(String output) {
        if (output == null) return null;
        // Pattern like "[abc1234]" or "create mode 100644 file"
        Pattern pattern = Pattern.compile("\\[([a-f0-9]{7,})\\]");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Parses a git error message into a user-friendly format.
     */
    private String parseGitErrorMessage(String stderr, int exitCode) {
        if (stderr == null || stderr.isEmpty()) {
            return "Git command failed with exit code " + exitCode;
        }

        // Common error patterns
        String lower = stderr.toLowerCase();

        if (lower.contains("authentication failed") || 
            lower.contains("401") || 
            lower.contains("invalid credentials")) {
            return "Authentication failed. Please check your access token.";
        }
        if (lower.contains("403") || lower.contains("permission denied")) {
            return "Permission denied. Your token may lack necessary permissions.";
        }
        if (lower.contains("404") || lower.contains("not found")) {
            return "Repository not found. Please check the remote URL.";
        }
        if (lower.contains("ssl") || lower.contains("certificate")) {
            return "SSL/TLS error. Please check your network connection.";
        }
        if (lower.contains("connection refused") || lower.contains("could not resolve")) {
            return "Network error. Please check your internet connection.";
        }
        if (lower.contains("nothing to commit")) {
            return "Nothing to commit — working tree is clean.";
        }
        if (lower.contains("nothing to push") || lower.contains("up-to-date")) {
            return "Everything up-to-date. Nothing to push.";
        }
        if (lower.contains("non-fast-forward") || lower.contains("fetch first")) {
            return "Push rejected. Please pull remote changes first.";
        }
        if (lower.contains("conflict")) {
            return "Merge conflict detected. Please resolve conflicts before committing.";
        }

        // Return first line of stderr, truncated
        String firstLine = stderr.split("\n")[0];
        if (firstLine.length() > 100) {
            firstLine = firstLine.substring(0, 100) + "...";
        }
        return firstLine;
    }

    /**
     * Classifies a git error into an ErrorType.
     */
    private GitException.ErrorType classifyGitError(String stderr, int exitCode) {
        if (stderr == null) return GitException.ErrorType.UNKNOWN;

        String lower = stderr.toLowerCase();

        if (lower.contains("authentication") || lower.contains("401") || 
            lower.contains("403") || lower.contains("permission") ||
            lower.contains("credentials")) {
            return GitException.ErrorType.AUTH;
        }
        if (lower.contains("not found") || lower.contains("404") ||
            lower.contains("not a git repository") || lower.contains("does not appear")) {
            return GitException.ErrorType.REPO_STATE;
        }
        if (lower.contains("connection") || lower.contains("resolve") ||
            lower.contains("timeout") || lower.contains("network") ||
            lower.contains("ssl")) {
            return GitException.ErrorType.NETWORK;
        }
        if (lower.contains("permission") || lower.contains("denied") ||
            lower.contains("i/o error") || lower.contains("no such file")) {
            return GitException.ErrorType.IO;
        }

        return GitException.ErrorType.GIT_OPERATION;
    }

    /**
     * Safely notifies callback of success.
     */
    private void notifySuccess(SimpleGitCallback callback, String message) {
        if (callback != null) {
            try {
                callback.onSuccess(message);
            } catch (Exception e) {
                Log.e(TAG, "Callback onSuccess threw exception", e);
            }
        }
    }

    /**
     * Safely notifies callback of error.
     */
    private void notifyError(SimpleGitCallback callback, GitException error) {
        if (callback != null) {
            try {
                callback.onError(error);
            } catch (Exception e) {
                Log.e(TAG, "Callback onError threw exception", e);
            }
        }
    }

    // =========================================================================
    // GIT_ASKPASS Helper Class
    // =========================================================================

    /**
     * Helper class invoked by git as an askpass program.
     * 
     * <p>This is used to provide credentials to git without exposing the token
     * in the URL or command line. Git calls this with a prompt, and we echo
     * the token from the environment variable.</p>
     * 
     * <p>Entry point: {@code main(String[])} — git invokes this externally.</p>
     */
    public static final class GitAskPass {
        
        private static final String TAG = "GitAskPass";

        /**
         * Entry point called by git when GIT_ASKPASS is set.
         * Reads token from GIT_ASKPASS_TOKEN environment variable and prints it.
         * 
         * @param args Git's prompt arguments (ignored).
         */
        public static void main(String[] args) {
            String token = System.getenv("GIT_ASKPASS_TOKEN");
            if (token != null && !token.isEmpty()) {
                System.out.print(token);
                System.out.flush();
            }
            // Exit silently if no token — git will handle auth failure
            System.exit(0);
        }

        private GitAskPass() {} // Prevent instantiation
    }
}