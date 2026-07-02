package com.adobs.ide.presentation.explorer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adobs.ide.databinding.ItemFileFolderBinding
import java.io.File

class FileAdapter(
    private val onItemClick: (File) -> Unit,
    private val onMenuClick: (File, android.view.View) -> Unit
) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileFolderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(
        private val binding: ItemFileFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            binding.textFileName.text = file.name
            binding.imageIcon.setImageResource(
                if (file.isDirectory) {
                    android.R.drawable.ic_menu_agenda
                } else {
                    android.R.drawable.ic_menu_save
                }
            )

            binding.root.setOnClickListener { onItemClick(file) }
            binding.buttonMenuAnchor.setOnClickListener { view -> onMenuClick(file, view) }
        }
    }

    private class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
            oldItem.absolutePath == newItem.absolutePath

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
            oldItem.absolutePath == newItem.absolutePath &&
                oldItem.lastModified() == newItem.lastModified() &&
                oldItem.length() == newItem.length()
    }
}
