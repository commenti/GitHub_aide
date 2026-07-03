package com.adobs.ide.presentation.explorer;

import androidx.lifecycle.ViewModel;
import com.adobs.ide.core.storage.IFileEngine;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.StateFlow;
import java.io.File;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u000b\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\u0010\u001a\u00020\u0011J\u0016\u0010\u0012\u001a\u00020\u00112\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0016J\u000e\u0010\u0017\u001a\u00020\u00112\u0006\u0010\u0018\u001a\u00020\u0014J\u0016\u0010\u0019\u001a\u00020\u00112\u0006\u0010\u001a\u001a\u00020\u00142\u0006\u0010\u001b\u001a\u00020\u0014J\u000e\u0010\u001c\u001a\u00020\u00112\u0006\u0010\u0018\u001a\u00020\u0014J\u0006\u0010\u001d\u001a\u00020\u0011J\u0016\u0010\u001e\u001a\u00020\u00112\u0006\u0010\u001f\u001a\u00020\u00142\u0006\u0010 \u001a\u00020\u0014R\u0016\u0010\u0005\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\t0\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\r\u00a8\u0006!"}, d2 = {"Lcom/adobs/ide/presentation/explorer/ExplorerViewModel;", "Landroidx/lifecycle/ViewModel;", "fileEngine", "Lcom/adobs/ide/core/storage/IFileEngine;", "(Lcom/adobs/ide/core/storage/IFileEngine;)V", "_events", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/adobs/ide/presentation/explorer/ExplorerUiEvent;", "_uiState", "Lcom/adobs/ide/presentation/explorer/ExplorerUiState;", "events", "Lkotlinx/coroutines/flow/StateFlow;", "getEvents", "()Lkotlinx/coroutines/flow/StateFlow;", "uiState", "getUiState", "consumeEvent", "", "createEntry", "name", "", "isFolder", "", "deleteEntry", "path", "extractZip", "zipPath", "destPath", "loadDirectory", "refresh", "renameEntry", "oldPath", "newName", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class ExplorerViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.adobs.ide.core.storage.IFileEngine fileEngine = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.adobs.ide.presentation.explorer.ExplorerUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.adobs.ide.presentation.explorer.ExplorerUiState> uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.adobs.ide.presentation.explorer.ExplorerUiEvent> _events = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.adobs.ide.presentation.explorer.ExplorerUiEvent> events = null;
    
    @javax.inject.Inject()
    public ExplorerViewModel(@org.jetbrains.annotations.NotNull()
    com.adobs.ide.core.storage.IFileEngine fileEngine) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.adobs.ide.presentation.explorer.ExplorerUiState> getUiState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.adobs.ide.presentation.explorer.ExplorerUiEvent> getEvents() {
        return null;
    }
    
    /**
     * Loads the contents of [path] and updates the UI state.
     */
    public final void loadDirectory(@org.jetbrains.annotations.NotNull()
    java.lang.String path) {
    }
    
    /**
     * Refreshes the currently displayed directory.
     */
    public final void refresh() {
    }
    
    public final void createEntry(@org.jetbrains.annotations.NotNull()
    java.lang.String name, boolean isFolder) {
    }
    
    public final void renameEntry(@org.jetbrains.annotations.NotNull()
    java.lang.String oldPath, @org.jetbrains.annotations.NotNull()
    java.lang.String newName) {
    }
    
    public final void deleteEntry(@org.jetbrains.annotations.NotNull()
    java.lang.String path) {
    }
    
    public final void extractZip(@org.jetbrains.annotations.NotNull()
    java.lang.String zipPath, @org.jetbrains.annotations.NotNull()
    java.lang.String destPath) {
    }
    
    /**
     * Call after the Activity has consumed an event, to avoid re-firing on rotation.
     */
    public final void consumeEvent() {
    }
}