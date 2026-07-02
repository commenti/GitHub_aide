package com.adobs.ide.presentation.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adobs.ide.core.storage.IFileEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * One-shot UI events the Activity should react to (toasts, error dialogs, etc).
 */
sealed class ExplorerUiEvent {
    data class Error(val message: String) : ExplorerUiEvent()
    data class OperationSuccess(val message: String) : ExplorerUiEvent()
}

/**
 * UI state for the Explorer screen.
 */
data class ExplorerUiState(
    val currentPath: String = "",
    val files: List<File> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val fileEngine: IFileEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<ExplorerUiEvent?>(null)
    val events: StateFlow<ExplorerUiEvent?> = _events.asStateFlow()

    /** Loads the contents of [path] and updates the UI state. */
    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, currentPath = path)
            val files = fileEngine.getFiles(path)
            _uiState.value = _uiState.value.copy(files = files, isLoading = false)
        }
    }

    /** Refreshes the currently displayed directory. */
    fun refresh() {
        val path = _uiState.value.currentPath
        if (path.isNotEmpty()) {
            loadDirectory(path)
        }
    }

    fun createEntry(name: String, isFolder: Boolean) {
        viewModelScope.launch {
            val currentPath = _uiState.value.currentPath
            val targetPath = File(currentPath, name).absolutePath
            val success = fileEngine.createFile(targetPath, isFolder)
            if (success) {
                _events.value = ExplorerUiEvent.OperationSuccess("Created $name")
                refresh()
            } else {
                _events.value = ExplorerUiEvent.Error("Failed to create $name")
            }
        }
    }

    fun renameEntry(oldPath: String, newName: String) {
        viewModelScope.launch {
            val success = fileEngine.renameFile(oldPath, newName)
            if (success) {
                _events.value = ExplorerUiEvent.OperationSuccess("Renamed to $newName")
                refresh()
            } else {
                _events.value = ExplorerUiEvent.Error("Failed to rename")
            }
        }
    }

    fun deleteEntry(path: String) {
        viewModelScope.launch {
            val success = fileEngine.deleteFile(path)
            if (success) {
                _events.value = ExplorerUiEvent.OperationSuccess("Deleted")
                refresh()
            } else {
                _events.value = ExplorerUiEvent.Error("Failed to delete")
            }
        }
    }

    fun extractZip(zipPath: String, destPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val success = fileEngine.extractZip(zipPath, destPath)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (success) {
                _events.value = ExplorerUiEvent.OperationSuccess("Extraction complete")
                refresh()
            } else {
                _events.value = ExplorerUiEvent.Error("Failed to extract zip")
            }
        }
    }

    /** Call after the Activity has consumed an event, to avoid re-firing on rotation. */
    fun consumeEvent() {
        _events.value = null
    }
}
