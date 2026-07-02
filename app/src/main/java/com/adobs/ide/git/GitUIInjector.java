/*
 * GitUIInjector.java
 * Path: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/git/
 * 
 * Pure rendering/injection layer for Git UI elements.
 * Receives state instructions from GitObserver only — contains no independent decision logic.
 * 
 * Compatible with Android 10 (API 29) through Android 17 (API 35+).
 */
package com.adobs.ide.git;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.function.Supplier;

/**
 * Pure rendering layer responsible for injecting and managing the Git UI button.
 * 
 * <p>This class:</p>
 * <ul>
 *   <li>Receives state ({@link GitObserver.GitUiState}) from {@link GitObserver}.</li>
 *   <li>Inflates or hides a {@link MaterialButton} in the provided anchor {@link ViewGroup}.</li>
 *   <li>Delegates click actions to {@link GitOperations}.</li>
 *   <li>Displays transient errors via {@link Snackbar}.</li>
 * </ul>
 * 
 * <p>It never searches the view hierarchy for its anchor; the anchor is injected once
 * via {@link #initialize(ViewGroup, Supplier)}.</p>
 */
public final class GitUIInjector {

    private static final String TAG = "GitUIInjector";

    /** Default commit message if user skips the prompt. */
    private static final String DEFAULT_COMMIT_MSG = "Quick commit from IDE";

    /** Primary Blue color for the buttons. */
    private static final int BUTTON_BLUE = Color.parseColor("#2196F3");

    // =========================================================================
    // State
    // =========================================================================

    /** The anchor ViewGroup to inject the button into. */
    private static volatile ViewGroup anchorView;

    /** Supplier to fetch the current directory path without coupling to MainActivity. */
    private static volatile Supplier<String> pathProvider;

    /** Reference to the currently injected button to manage its state (e.g., disable while loading). */
    private static volatile MaterialButton currentButton;

    /** Flag to prevent multiple concurrent Git operations from button mashing. */
    private static volatile boolean isOperationRunning = false;

    /** Main thread handler for posting UI updates safely. */
    private static final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Prevent instantiation. */
    private GitUIInjector() {}

    // =========================================================================
    // Initialization & Lifecycle
    // =========================================================================

    /**
     * Initializes the injector with the required view anchor and path provider.
     * Called once during Phase 2 bootstrap (e.g., from {@link GitObserver.GitBootstrap}).
     * 
     * @param anchor      The ViewGroup to inject UI elements into.
     * @param pathSupplier A supplier that returns the current directory path.
     */
    public static void initialize(ViewGroup anchor, Supplier<String> pathSupplier) {
        // Post to main thread to ensure view operations are safe
        mainHandler.post(() -> {
            anchorView = anchor;
            pathProvider = pathSupplier;
            currentButton = null;
            isOperationRunning = false;
            Log.d(TAG, "Initialized with anchor: " + (anchor != null ? anchor.getId() : "null"));
        });
    }

    /**
     * Overloaded initialization for cases where path provider is set later or handled internally.
     * 
     * @param anchor The ViewGroup to inject UI elements into.
     */
    public static void initialize(ViewGroup anchor) {
        initialize(anchor, null);
    }

    /**
     * Clears all references and removes injected views.
     * Must be called when the hosting Activity is destroyed.
     */
    public static void cleanup() {
        mainHandler.post(() -> {
            if (anchorView != null) {
                anchorView.removeAllViews();
            }
            anchorView = null;
            pathProvider = null;
            currentButton = null;
            isOperationRunning = false;
        });
    }

    // =========================================================================
    // Public State API (Called by GitObserver)
    // =========================================================================

    /**
     * Updates the visual state of the Git button based on instructions from GitObserver.
     * 
     * <p>This method is thread-safe and will post to the main thread if necessary.</p>
     * 
     * @param state The desired UI state.
     */
    public static void updateButtonState(GitObserver.GitUiState state) {
        // Ensure UI manipulation happens on the main thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            applyButtonState(state);
        } else {
            mainHandler.post(() -> applyButtonState(state));
        }
    }

    /**
     * Shows a non-blocking error popup.
     * 
     * <p>Uses a Snackbar anchored to the root CoordinatorLayout (if available)
     * or the injected anchor view as a fallback. Never uses blocking AlertDialogs for errors.</p>
     * 
     * @param message The user-displayable error message.
     */
    public static void showErrorPopup(String message) {
        if (message == null || message.isEmpty()) return;

        // Ensure UI manipulation happens on the main thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            displayErrorSnackbar(message);
        } else {
            mainHandler.post(() -> displayErrorSnackbar(message));
        }
    }

    // =========================================================================
    // Private UI Implementation
    // =========================================================================

    /**
     * Applies the button state. Must run on the Main Thread.
     */
    private static void applyButtonState(GitObserver.GitUiState state) {
        if (anchorView == null) {
            Log.w(TAG, "Cannot apply state: anchor view is null");
            return;
        }

        // Rule: Never add more than one button. Always clear previous children.
        anchorView.removeAllViews();
        currentButton = null;

        if (state == GitObserver.GitUiState.NONE) {
            return; // Just clear the view
        }

        // Inflate a new MaterialButton dynamically
        Context context = anchorView.getContext();
        MaterialButton button = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle);
        
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        button.setLayoutParams(layoutParams);
        button.setCornerRadius(16);
        button.setAllCaps(false);
        
        // Set Blue color
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BUTTON_BLUE));
        button.setTextColor(Color.WHITE);
        button.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));

        if (state == GitObserver.GitUiState.SHOW_UPLOAD) {
            button.setText("Upload");
            button.setIcon(context.getDrawable(android.R.drawable.ic_menu_upload));
            button.setOnClickListener(v -> handleUploadAction());
        } else if (state == GitObserver.GitUiState.SHOW_COMMIT) {
            button.setText("Commit");
            button.setIcon(context.getDrawable(android.R.drawable.ic_menu_save));
            button.setOnClickListener(v -> handleCommitAction());
        }

        // Add to anchor
        anchorView.addView(button);
        currentButton = button;
    }

    /**
     * Displays the error Snackbar. Must run on the Main Thread.
     */
    private static void displayErrorSnackbar(String message) {
        if (anchorView == null) return;

        // Try to find the root CoordinatorLayout for proper Snackbar anchoring
        View snackbarAnchor = findCoordinatorLayout(anchorView);
        if (snackbarAnchor == null) {
            snackbarAnchor = anchorView;
        }

        try {
            Snackbar.make(snackbarAnchor, message, Snackbar.LENGTH_LONG)
                    .setAction("DISMISS", v -> {})
                    .setActionTextColor(Color.YELLOW)
                    .show();
        } catch (Exception e) {
            // Fallback if Snackbar fails (e.g., detached view)
            Log.e(TAG, "Failed to show Snackbar", e);
        }
    }

    /**
     * Traverses up the view hierarchy to find a CoordinatorLayout for Snackbar anchoring.
     */
    private static View findCoordinatorLayout(View view) {
        if (view instanceof CoordinatorLayout) {
            return view;
        }
        if (view.getParent() instanceof View) {
            return findCoordinatorLayout((View) view.getParent());
        }
        return null;
    }

    // =========================================================================
    // Click Action Handlers
    // =========================================================================

    /**
     * Handles the "Upload" button click.
     * Flow: Prompt for Remote URL -> Init Repo -> Push.
     */
    private static void handleUploadAction() {
        if (isOperationRunning || anchorView == null) return;
        
        String path = getCurrentPath();
        if (path == null) {
            showErrorPopup("Cannot determine current directory.");
            return;
        }

        // If it's already a repo, we just need to push. If not, we need a remote URL to init and push.
        // To be safe and cover the "untracked repo needing init+push" case, we ask for the URL.
        promptForRemoteUrl(url -> {
            if (url == null || url.trim().isEmpty()) return; // User cancelled
            
            setButtonLoadingState(true, "Uploading...");
            GitOperations ops = GitOperations.getInstance(anchorView.getContext());

            ops.initAndPush(path, url.trim(), "Initial commit", new GitOperations.SimpleGitCallback() {
                @Override
                public void onSuccess(String message) {
                    setButtonLoadingState(false, "Upload");
                    showErrorPopup("Success: " + message); // Reusing popup for success feedback briefly, or could trigger a refresh
                }

                @Override
                public void onError(GitException error) {
                    setButtonLoadingState(false, "Upload");
                    showErrorPopup(error.getUserMessage());
                }
            });
        });
    }

    /**
     * Handles the "Commit" button click.
     * Flow: Prompt for Commit Message -> Commit -> Push.
     */
    private static void handleCommitAction() {
        if (isOperationRunning || anchorView == null) return;

        String path = getCurrentPath();
        if (path == null) {
            showErrorPopup("Cannot determine current directory.");
            return;
        }

        promptForCommitMessage(message -> {
            if (message == null) return; // User cancelled

            setButtonLoadingState(true, "Committing...");
            GitOperations ops = GitOperations.getInstance(anchorView.getContext());

            ops.commit(path, message, new GitOperations.SimpleGitCallback() {
                @Override
                public void onSuccess(String commitMsg) {
                    setButtonLoadingState(true, "Pushing...");
                    
                    ops.push(path, new GitOperations.SimpleGitCallback() {
                        @Override
                        public void onSuccess(String pushMsg) {
                            setButtonLoadingState(false, "Commit");
                            showErrorPopup("Success: " + pushMsg);
                        }

                        @Override
                        public void onError(GitException error) {
                            setButtonLoadingState(false, "Commit");
                            showErrorPopup("Committed locally, but push failed: " + error.getUserMessage());
                        }
                    });
                }

                @Override
                public void onError(GitException error) {
                    setButtonLoadingState(false, "Commit");
                    showErrorPopup(error.getUserMessage());
                }
            });
        });
    }

    // =========================================================================
    // Non-Blocking Dialog Prompts
    // =========================================================================

    /**
     * Shows a non-blocking dialog to ask for the remote repository URL.
     */
    private static void promptForRemoteUrl(final UrlCallback callback) {
        if (anchorView == null) {
            callback.onResult(null);
            return;
        }
        
        Context context = anchorView.getContext();
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://github.com/user/repo.git");

        new MaterialAlertDialogBuilder(context)
                .setTitle("Initialize Remote Repository")
                .setMessage("Enter the GitHub repository URL to upload to:")
                .setView(input)
                .setPositiveButton("Upload", (dialog, which) -> callback.onResult(input.getText().toString()))
                .setNegativeButton("Cancel", (dialog, which) -> callback.onResult(null))
                .setOnCancelListener(dialog -> callback.onResult(null))
                .show();
    }

    /**
     * Shows a non-blocking dialog to ask for the commit message.
     */
    private static void promptForCommitMessage(final MessageCallback callback) {
        if (anchorView == null) {
            callback.onResult(null);
            return;
        }

        Context context = anchorView.getContext();
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SUBJECT);
        input.setText(DEFAULT_COMMIT_MSG);
        input.selectAll(); // Select all so user can easily overwrite

        new MaterialAlertDialogBuilder(context)
                .setTitle("Commit Changes")
                .setMessage("Enter a commit message:")
                .setView(input)
                .setPositiveButton("Commit & Push", (dialog, which) -> {
                    String msg = input.getText().toString().trim();
                    callback.onResult(msg.isEmpty() ? DEFAULT_COMMIT_MSG : msg);
                })
                .setNegativeButton("Cancel", (dialog, which) -> callback.onResult(null))
                .setOnCancelListener(dialog -> callback.onResult(null))
                .show();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Gets the current directory path safely.
     */
    private static String getCurrentPath() {
        if (pathProvider != null) {
            try {
                return pathProvider.get();
            } catch (Exception e) {
                Log.e(TAG, "Error getting path from provider", e);
            }
        }
        return null;
    }

    /**
     * Toggles the loading state of the injected button.
     */
    private static void setButtonLoadingState(boolean isLoading, String text) {
        mainHandler.post(() -> {
            isOperationRunning = isLoading;
            if (currentButton != null) {
                currentButton.setEnabled(!isLoading);
                currentButton.setText(isLoading ? text : text);
                if (isLoading) {
                    currentButton.setAlpha(0.6f);
                } else {
                    currentButton.setAlpha(1.0f);
                }
            }
        });
    }

    // =========================================================================
    // Inner Callback Interfaces
    // =========================================================================

    private interface UrlCallback {
        void onResult(String url);
    }

    private interface MessageCallback {
        void onResult(String message);
    }
}