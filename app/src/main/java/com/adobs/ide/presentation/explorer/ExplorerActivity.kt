package com.adobs.ide.presentation.explorer

import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.adobs.ide.R
import com.adobs.ide.core.monetization.IAdManager
import com.adobs.ide.databinding.ActivityExplorerBinding
import com.adobs.ide.databinding.DialogAdOptInBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ExplorerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExplorerBinding
    private val viewModel: ExplorerViewModel by viewModels()

    @Inject
    lateinit var adManager: IAdManager

    private lateinit var fileAdapter: FileAdapter

    /** Holds a pending gated action (e.g. extraction) awaiting reward-ad completion. */
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adManager.initAds(this)
        adManager.loadRewardedInterstitial(this)
        binding.adViewBanner.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())

        setupRecyclerView()
        setupFab()
        observeViewModel()
        setupBackNavigation()

        val rootPath = getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().absolutePath
        viewModel.loadDirectory(rootPath)
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onItemClick = { file -> onFileClicked(file) },
            onMenuClick = { file, anchor -> showItemMenu(file, anchor) }
        )
        binding.recyclerFiles.adapter = fileAdapter
    }

    private fun setupFab() {
        binding.fabCreateNew.setOnClickListener {
            showCreateEntryDialog()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.textCurrentPath.text = state.currentPath
                        binding.progressLoading.visibility =
                            if (state.isLoading) View.VISIBLE else View.GONE
                        binding.textEmptyState.visibility =
                            if (!state.isLoading && state.files.isEmpty()) View.VISIBLE else View.GONE
                        fileAdapter.submitList(state.files)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ExplorerUiEvent.Error ->
                                Toast.makeText(this@ExplorerActivity, event.message, Toast.LENGTH_SHORT).show()
                            is ExplorerUiEvent.OperationSuccess ->
                                Toast.makeText(this@ExplorerActivity, event.message, Toast.LENGTH_SHORT).show()
                            null -> Unit
                        }
                        if (event != null) viewModel.consumeEvent()
                    }
                }
            }
        }
    }

    private fun onFileClicked(file: File) {
        if (file.isDirectory) {
            viewModel.loadDirectory(file.absolutePath)
        } else if (file.extension.equals("zip", ignoreCase = true)) {
            requestExtractZip(file)
        } else {
            Toast.makeText(this, "Opened: ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showItemMenu(file: File, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.rename))
        popup.menu.add(0, 2, 1, getString(R.string.delete))
        if (file.isFile && file.extension.equals("zip", ignoreCase = true)) {
            popup.menu.add(0, 3, 2, getString(R.string.extract))
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showRenameDialog(file)
                    true
                }
                2 -> {
                    confirmDelete(file)
                    true
                }
                3 -> {
                    requestExtractZip(file)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showCreateEntryDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.new_file)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_new)
            .setView(editText)
            .setPositiveButton(R.string.new_file) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createEntry(name, isFolder = false)
            }
            .setNeutralButton(R.string.new_folder) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createEntry(name, isFolder = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(file: File) {
        val editText = EditText(this).apply {
            setText(file.name)
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) viewModel.renameEntry(file.absolutePath, newName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage("Delete \"${file.name}\"? This cannot be undone.")
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteEntry(file.absolutePath)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Gated action: extracting a zip requires the user to opt in and watch
     * a rewarded interstitial ad before the extraction actually runs.
     */
    private fun requestExtractZip(zipFile: File) {
        val destDir = File(zipFile.parentFile, zipFile.nameWithoutExtension)

        pendingAction = {
            viewModel.extractZip(zipFile.absolutePath, destDir.absolutePath)
        }

        showAdOptInDialog(
            onAccept = {
                adManager.showRewardedInterstitial(
                    activity = this,
                    onRewardEarned = {
                        pendingAction?.invoke()
                        pendingAction = null
                    },
                    onAdDismissed = {
                        // If reward wasn't earned, pendingAction stays null-safe (no-op).
                    }
                )
            },
            onCancel = {
                pendingAction = null
            }
        )
    }

    private fun showAdOptInDialog(onAccept: () -> Unit, onCancel: () -> Unit) {
        val dialogBinding = DialogAdOptInBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.buttonWatchAd.setOnClickListener {
            dialog.dismiss()
            onAccept()
        }
        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
            onCancel()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        binding.adViewBanner.resume()
    }

    override fun onPause() {
        binding.adViewBanner.pause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.adViewBanner.destroy()
        super.onDestroy()
    }

    /**
     * Registers an [OnBackPressedCallback] to navigate up to the parent directory
     * instead of closing the Activity. Uses the AndroidX back-press dispatcher,
     * which is the correct API for Android 13+ (API 33) and higher.
     */
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentPath = viewModel.uiState.value.currentPath
                    val parent = File(currentPath).parentFile
                    val rootPath = getExternalFilesDir(null)?.absolutePath
                        ?: Environment.getExternalStorageDirectory().absolutePath

                    if (parent != null && currentPath != rootPath) {
                        viewModel.loadDirectory(parent.absolutePath)
                    } else {
                        // Disable this callback so the system back stack handles it
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }
}
