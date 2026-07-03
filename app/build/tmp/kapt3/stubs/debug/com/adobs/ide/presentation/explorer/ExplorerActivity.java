package com.adobs.ide.presentation.explorer;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import com.adobs.ide.R;
import com.adobs.ide.core.monetization.IAdManager;
import com.adobs.ide.databinding.ActivityExplorerBinding;
import com.adobs.ide.databinding.DialogAdOptInBinding;
import dagger.hilt.android.AndroidEntryPoint;
import java.io.File;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0016\u001a\u00020\u000f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002J\b\u0010\u0019\u001a\u00020\u000fH\u0002J\b\u0010\u001a\u001a\u00020\u000fH\u0016J\u0012\u0010\u001b\u001a\u00020\u000f2\b\u0010\u001c\u001a\u0004\u0018\u00010\u001dH\u0014J\b\u0010\u001e\u001a\u00020\u000fH\u0014J\u0010\u0010\u001f\u001a\u00020\u000f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002J\b\u0010 \u001a\u00020\u000fH\u0014J\b\u0010!\u001a\u00020\u000fH\u0014J\u0010\u0010\"\u001a\u00020\u000f2\u0006\u0010#\u001a\u00020\u0018H\u0002J\b\u0010$\u001a\u00020\u000fH\u0002J\b\u0010%\u001a\u00020\u000fH\u0002J$\u0010&\u001a\u00020\u000f2\f\u0010\'\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000e2\f\u0010(\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000eH\u0002J\b\u0010)\u001a\u00020\u000fH\u0002J\u0018\u0010*\u001a\u00020\u000f2\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010+\u001a\u00020,H\u0002J\u0010\u0010-\u001a\u00020\u000f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082.\u00a2\u0006\u0002\n\u0000R\u0016\u0010\r\u001a\n\u0012\u0004\u0012\u00020\u000f\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0010\u001a\u00020\u00118BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0014\u0010\u0015\u001a\u0004\b\u0012\u0010\u0013\u00a8\u0006."}, d2 = {"Lcom/adobs/ide/presentation/explorer/ExplorerActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "adManager", "Lcom/adobs/ide/core/monetization/IAdManager;", "getAdManager", "()Lcom/adobs/ide/core/monetization/IAdManager;", "setAdManager", "(Lcom/adobs/ide/core/monetization/IAdManager;)V", "binding", "Lcom/adobs/ide/databinding/ActivityExplorerBinding;", "fileAdapter", "Lcom/adobs/ide/presentation/explorer/FileAdapter;", "pendingAction", "Lkotlin/Function0;", "", "viewModel", "Lcom/adobs/ide/presentation/explorer/ExplorerViewModel;", "getViewModel", "()Lcom/adobs/ide/presentation/explorer/ExplorerViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "confirmDelete", "file", "Ljava/io/File;", "observeViewModel", "onBackPressed", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "onFileClicked", "onPause", "onResume", "requestExtractZip", "zipFile", "setupFab", "setupRecyclerView", "showAdOptInDialog", "onAccept", "onCancel", "showCreateEntryDialog", "showItemMenu", "anchor", "Landroid/view/View;", "showRenameDialog", "app_debug"})
public final class ExplorerActivity extends androidx.appcompat.app.AppCompatActivity {
    private com.adobs.ide.databinding.ActivityExplorerBinding binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    @javax.inject.Inject()
    public com.adobs.ide.core.monetization.IAdManager adManager;
    private com.adobs.ide.presentation.explorer.FileAdapter fileAdapter;
    
    /**
     * Holds a pending gated action (e.g. extraction) awaiting reward-ad completion.
     */
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> pendingAction;
    
    public ExplorerActivity() {
        super();
    }
    
    private final com.adobs.ide.presentation.explorer.ExplorerViewModel getViewModel() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.adobs.ide.core.monetization.IAdManager getAdManager() {
        return null;
    }
    
    public final void setAdManager(@org.jetbrains.annotations.NotNull()
    com.adobs.ide.core.monetization.IAdManager p0) {
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void setupFab() {
    }
    
    private final void observeViewModel() {
    }
    
    private final void onFileClicked(java.io.File file) {
    }
    
    private final void showItemMenu(java.io.File file, android.view.View anchor) {
    }
    
    private final void showCreateEntryDialog() {
    }
    
    private final void showRenameDialog(java.io.File file) {
    }
    
    private final void confirmDelete(java.io.File file) {
    }
    
    /**
     * Gated action: extracting a zip requires the user to opt in and watch
     * a rewarded interstitial ad before the extraction actually runs.
     */
    private final void requestExtractZip(java.io.File zipFile) {
    }
    
    private final void showAdOptInDialog(kotlin.jvm.functions.Function0<kotlin.Unit> onAccept, kotlin.jvm.functions.Function0<kotlin.Unit> onCancel) {
    }
    
    @java.lang.Override()
    protected void onResume() {
    }
    
    @java.lang.Override()
    protected void onPause() {
    }
    
    @java.lang.Override()
    protected void onDestroy() {
    }
    
    /**
     * Navigates up to the parent directory, if any, instead of closing the Activity.
     */
    @java.lang.Override()
    public void onBackPressed() {
    }
}