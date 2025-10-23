/**
 * openCloud Android client application
 *
 * @author Fernando Sanz Velasco
 * @author Juan Carlos Garrote Gascón
 * @author Manuel Plazas Palacio
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.presentation.files.filelist

import android.accounts.Account
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.opencloud.android.R
import eu.opencloud.android.databinding.GridItemBinding
import eu.opencloud.android.databinding.ItemFileListBinding
import eu.opencloud.android.databinding.ListFooterBinding
import eu.opencloud.android.datamodel.ThumbnailsCacheManager
import eu.opencloud.android.domain.files.model.FileListOption
import eu.opencloud.android.domain.files.model.OCFileWithSyncInfo
import eu.opencloud.android.domain.files.model.OCFooterFile
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.utils.DisplayUtils
import eu.opencloud.android.utils.MimetypeIconUtil
import eu.opencloud.android.utils.PreferenceUtils

class FileListAdapter(
    private val context: Context,
    private val isPickerMode: Boolean,
    private val layoutManager: StaggeredGridLayoutManager,
    private val listener: FileListAdapterListener,
) : SelectableAdapter<RecyclerView.ViewHolder>() {

    var files = mutableListOf<Any>()
    private var account: Account? = AccountUtils.getCurrentOpenCloudAccount(context)
    private var fileListOption: FileListOption = FileListOption.ALL_FILES
    private val disallowTouchesWithOtherWindows =
        PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)

    init {
        setHasStableIds(true)
    }

    fun updateFileList(filesToAdd: List<OCFileWithSyncInfo>, fileListOption: FileListOption) {

        val listWithFooter = mutableListOf<Any>()
        listWithFooter.addAll(filesToAdd)

        if (listWithFooter.isNotEmpty() && !isPickerMode) {
            listWithFooter.add(OCFooterFile(manageListOfFilesAndGenerateText(filesToAdd)))
        }

        val diffUtilCallback = FileListDiffCallback(
            oldList = files,
            newList = listWithFooter,
            oldFileListOption = this.fileListOption,
            newFileListOption = fileListOption,
        )
        val diffResult = DiffUtil.calculateDiff(diffUtilCallback)

        files.clear()
        files.addAll(listWithFooter)
        this.fileListOption = fileListOption

        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        val item = files.getOrNull(position)
        return when (item) {
            is OCFileWithSyncInfo -> item.file.id ?: item.file.remotePath.hashCode().toLong()
            is OCFooterFile -> Long.MIN_VALUE + position
            else -> position.toLong()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            ViewType.LIST_ITEM.ordinal -> {
                val binding = ItemFileListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                binding.root.apply {
                    tag = ViewType.LIST_ITEM
                    filterTouchesWhenObscured = disallowTouchesWithOtherWindows
                }
                ListViewHolder(binding)
            }

            ViewType.GRID_IMAGE.ordinal -> {
                val binding = GridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                binding.root.apply {
                    tag = ViewType.GRID_IMAGE
                    filterTouchesWhenObscured = disallowTouchesWithOtherWindows
                }
                GridImageViewHolder(binding)
            }

            ViewType.GRID_ITEM.ordinal -> {
                val binding = GridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                binding.root.apply {
                    tag = ViewType.GRID_ITEM
                    filterTouchesWhenObscured = disallowTouchesWithOtherWindows
                }
                GridViewHolder(binding)
            }

            else -> {
                val binding = ListFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                binding.root.apply {
                    tag = ViewType.FOOTER
                    filterTouchesWhenObscured = disallowTouchesWithOtherWindows
                }
                FooterViewHolder(binding)
            }
        }

    override fun getItemCount(): Int = files.size

    private fun hasFooter(): Boolean = files.lastOrNull() is OCFooterFile

    private fun isFooter(position: Int) = files.getOrNull(position) is OCFooterFile

    private fun selectableItemCount(): Int = files.size - if (hasFooter()) 1 else 0

    override fun getItemViewType(position: Int): Int =

        if (isFooter(position)) {
            ViewType.FOOTER.ordinal
        } else {
            when {
                layoutManager.spanCount == 1 -> {
                    ViewType.LIST_ITEM.ordinal
                }

                (files[position] as OCFileWithSyncInfo).file.isImage -> {
                    ViewType.GRID_IMAGE.ordinal
                }

                else -> {
                    ViewType.GRID_ITEM.ordinal
                }
            }
        }

    fun getCheckedItems(): List<OCFileWithSyncInfo> {
        val checkedItems = mutableListOf<OCFileWithSyncInfo>()
        val checkedPositions = getSelectedItems()

        for (i in checkedPositions) {
            val checkedFile: Any? = files.getOrNull(i)
            if (checkedFile is OCFileWithSyncInfo) {
                checkedItems.add(checkedFile)
            }
        }

        return checkedItems
    }

    fun selectAll() {
        // Last item on list is the footer, so that element must be excluded from selection
        selectAll(totalItems = selectableItemCount())
    }

    fun selectInverse() {
        // Last item on list is the footer, so that element must be excluded from selection
        toggleSelectionInBulk(totalItems = selectableItemCount())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val viewType = getItemViewType(position)

        AccountUtils.getCurrentOpenCloudAccount(context)?.let { currentAccount ->
            if (currentAccount != account) {
                account = currentAccount
            }
        } ?: run {
            if (account != null) {
                account = null
            }
        }

        if (viewType != ViewType.FOOTER.ordinal) { // Is Item

            val hasActiveSelection = selectedItemCount > 0
            val fileWithSyncInfo = files[position] as OCFileWithSyncInfo
            val file = fileWithSyncInfo.file
            val name = file.fileName
            val fileIcon = holder.itemView.findViewById<ImageView>(R.id.thumbnail).apply {
                tag = file.id
            }
            val thumbnail: Bitmap? = ThumbnailsCacheManager.getBitmapFromDiskCache(file)

            holder.itemView.findViewById<LinearLayout>(R.id.ListItemLayout)?.apply {
                contentDescription = "LinearLayout-$name"

                // Allow or disallow touches with other visible windows
                filterTouchesWhenObscured = disallowTouchesWithOtherWindows
            }

            holder.itemView.findViewById<LinearLayout>(R.id.share_icons_layout).isVisible =
                file.sharedByLink || file.sharedWithSharee == true || file.isSharedWithMe
            holder.itemView.findViewById<ImageView>(R.id.shared_by_link_icon).isVisible = file.sharedByLink
            holder.itemView.findViewById<ImageView>(R.id.shared_via_users_icon).isVisible =
                file.sharedWithSharee == true || file.isSharedWithMe

            setSpecificViewHolder(viewType, holder, fileWithSyncInfo, thumbnail, hasActiveSelection)

            setIconPinAccordingToFilesLocalState(holder.itemView.findViewById(R.id.localFileIndicator), fileWithSyncInfo)

            holder.itemView.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }
                val currentItem = files.getOrNull(adapterPosition) as? OCFileWithSyncInfo ?: return@setOnClickListener
                listener.onItemClick(
                    ocFileWithSyncInfo = currentItem,
                    position = adapterPosition
                )
            }

            holder.itemView.setOnLongClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return@setOnLongClickListener false
                }
                listener.onLongItemClick(
                    position = adapterPosition
                )
            }
            holder.itemView.setBackgroundColor(Color.WHITE)

            val checkBoxV = holder.itemView.findViewById<ImageView>(R.id.custom_checkbox).apply {
                isVisible = hasActiveSelection
            }

            if (isSelected(position)) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.selected_item_background))
                checkBoxV.setImageResource(R.drawable.ic_checkbox_marked)
            } else {
                holder.itemView.setBackgroundColor(Color.WHITE)
                checkBoxV.setImageResource(R.drawable.ic_checkbox_blank_outline)
            }

            if (file.isFolder) {
                // Folder
                fileIcon.setImageResource(R.drawable.ic_menu_archive)
                fileIcon.setBackgroundColor(Color.TRANSPARENT)
            } else {
                // Set file icon depending on its mimetype. Ask for thumbnail later.
                fileIcon.setImageResource(MimetypeIconUtil.getFileTypeIconId(file.mimeType, file.fileName))

                if (thumbnail != null) {
                    fileIcon.setImageBitmap(thumbnail)
                }

                if (file.needsToUpdateThumbnail) {
                    val canStartTask = ThumbnailsCacheManager.cancelPotentialThumbnailWork(file, fileIcon)
                    val activeAccount = account
                    if (activeAccount != null && canStartTask) {
                        // generate new Thumbnail
                        val task = ThumbnailsCacheManager.ThumbnailGenerationTask(fileIcon, activeAccount)
                        val placeholder = thumbnail ?: ThumbnailsCacheManager.mDefaultImg
                        val asyncDrawable = ThumbnailsCacheManager.AsyncThumbnailDrawable(context.resources, placeholder, task)

                        // If drawable is not visible, do not update it.
                        if (asyncDrawable.minimumHeight > 0 && asyncDrawable.minimumWidth > 0) {
                            fileIcon.setImageDrawable(asyncDrawable)
                        }
                        ThumbnailsCacheManager.executeThumbnailTask(task, file)
                    }
                }

                if (file.mimeType.equals("image/png", ignoreCase = true)) {
                    fileIcon.setBackgroundColor(ContextCompat.getColor(context, R.color.background_color))
                } else {
                    fileIcon.setBackgroundColor(Color.TRANSPARENT)
                }
            }

        } else { // Is Footer
            if (!isPickerMode) {
                val view = holder as FooterViewHolder
                val file = files[position] as OCFooterFile
                (view.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).apply {
                    isFullSpan = true
                }
                view.binding.footerText.text = file.text
            }
        }
    }

    private fun setSpecificViewHolder(
        viewType: Int,
        holder: RecyclerView.ViewHolder,
        fileWithSyncInfo: OCFileWithSyncInfo,
        thumbnail: Bitmap?,
        hasActiveSelection: Boolean,
    ) {
        val file = fileWithSyncInfo.file

        when (viewType) {
            ViewType.LIST_ITEM.ordinal -> {
                val view = holder as ListViewHolder
                view.binding.let {
                    it.fileListConstraintLayout.filterTouchesWhenObscured = disallowTouchesWithOtherWindows
                    it.Filename.text = file.fileName
                    it.fileListSize.text = DisplayUtils.bytesToHumanReadable(file.length, context, true)
                    it.fileListLastMod.text = DisplayUtils.getRelativeTimestamp(context, file.modificationTimestamp)
                    it.threeDotMenu.isVisible = !hasActiveSelection
                    it.threeDotMenu.contentDescription = context.getString(R.string.content_description_file_operations, file.fileName)
                    if (fileListOption.isAvailableOffline() || (fileListOption.isSharedByLink() && fileWithSyncInfo.space == null)) {
                        it.spacePathLine.path.apply {
                            text = file.getParentRemotePath()
                            isVisible = true
                        }
                        fileWithSyncInfo.space?.let { space ->
                            it.spacePathLine.spaceIcon.isVisible = true
                            it.spacePathLine.spaceName.isVisible = true
                            if (space.isPersonal) {
                                it.spacePathLine.spaceIcon.setImageResource(R.drawable.ic_folder)
                                it.spacePathLine.spaceName.setText(R.string.bottom_nav_personal)
                            } else {
                                it.spacePathLine.spaceName.text = space.name
                            }
                        }
                    } else {
                        it.spacePathLine.path.isVisible = false
                        it.spacePathLine.spaceIcon.isVisible = false
                        it.spacePathLine.spaceName.isVisible = false
                    }
                    it.threeDotMenu.setOnClickListener {
                        listener.onThreeDotButtonClick(fileWithSyncInfo = fileWithSyncInfo)
                    }
                }
            }

            ViewType.GRID_ITEM.ordinal -> {
                // Filename
                val view = holder as GridViewHolder
                view.binding.Filename.text = file.fileName
            }

            ViewType.GRID_IMAGE.ordinal -> {
                val view = holder as GridImageViewHolder
                val fileIcon = holder.itemView.findViewById<ImageView>(R.id.thumbnail)
                val layoutParams = fileIcon.layoutParams as ViewGroup.MarginLayoutParams

                if (thumbnail == null) {
                    view.binding.Filename.apply {
                        text = file.fileName
                        isVisible = true
                    }
                    // Reset layout params values default
                    manageGridLayoutParams(
                        layoutParams = layoutParams,
                        marginVertical = 0,
                        height = context.resources.getDimensionPixelSize(R.dimen.item_file_grid_height),
                        width = context.resources.getDimensionPixelSize(R.dimen.item_file_grid_width),
                    )
                } else {
                    view.binding.Filename.apply {
                        text = ""
                        isVisible = false
                    }
                    manageGridLayoutParams(
                        layoutParams = layoutParams,
                        marginVertical = context.resources.getDimensionPixelSize(R.dimen.item_file_image_grid_margin),
                        height = ViewGroup.LayoutParams.MATCH_PARENT,
                        width = ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            }
        }
    }

    private fun manageGridLayoutParams(layoutParams: ViewGroup.MarginLayoutParams, marginVertical: Int, height: Int, width: Int) {
        val marginHorizontal = context.resources.getDimensionPixelSize(R.dimen.item_file_image_grid_margin)
        layoutParams.setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical)
        layoutParams.height = height
        layoutParams.width = width
    }

    private fun manageListOfFilesAndGenerateText(list: List<OCFileWithSyncInfo>): String {
        var filesCount = 0
        var foldersCount = 0
        for (fileWithSyncInfo in list) {
            if (fileWithSyncInfo.file.isFolder) {
                foldersCount++
            } else {
                if (!fileWithSyncInfo.file.isHidden) {
                    filesCount++
                }
            }
        }

        return generateFooterText(filesCount, foldersCount)
    }

    private fun setIconPinAccordingToFilesLocalState(localStateView: ImageView, fileWithSyncInfo: OCFileWithSyncInfo) {
        // local state
        localStateView.bringToFront()
        localStateView.isVisible = false

        val file = fileWithSyncInfo.file
        if (fileWithSyncInfo.isSynchronizing) {
            localStateView.setImageResource(R.drawable.sync_pin)
            localStateView.visibility = View.VISIBLE
        } else if (file.etagInConflict != null) {
            // conflict
            localStateView.setImageResource(R.drawable.error_pin)
            localStateView.visibility = View.VISIBLE
        } else if (file.isAvailableOffline) {
            localStateView.visibility = View.VISIBLE
            localStateView.setImageResource(R.drawable.offline_available_pin)
        } else if (file.isAvailableLocally) {
            localStateView.visibility = View.VISIBLE
            localStateView.setImageResource(R.drawable.downloaded_pin)
        }
    }

    private fun generateFooterText(filesCount: Int, foldersCount: Int): String =
        when {
            filesCount <= 0 -> {
                when {
                    foldersCount <= 0 -> {
                        ""
                    }

                    foldersCount == 1 -> {
                        context.getString(R.string.file_list__footer__folder)
                    }

                    else -> { // foldersCount > 1
                        context.getString(R.string.file_list__footer__folders, foldersCount)
                    }
                }
            }

            filesCount == 1 -> {
                 when {
                    foldersCount <= 0 -> {
                        context.getString(R.string.file_list__footer__file)
                    }

                    foldersCount == 1 -> {
                        context.getString(R.string.file_list__footer__file_and_folder)
                    }

                    else -> { // foldersCount > 1
                        context.getString(R.string.file_list__footer__file_and_folders, foldersCount)
                    }
                }
            }

            else -> {    // filesCount > 1
                when {
                    foldersCount <= 0 -> {
                        context.getString(R.string.file_list__footer__files, filesCount)
                    }

                    foldersCount == 1 -> {
                        context.getString(R.string.file_list__footer__files_and_folder, filesCount)
                    }

                    else -> { // foldersCount > 1
                        context.getString(
                            R.string.file_list__footer__files_and_folders, filesCount, foldersCount
                        )
                    }
                }
            }
        }

    interface FileListAdapterListener {
        fun onItemClick(ocFileWithSyncInfo: OCFileWithSyncInfo, position: Int)
        fun onLongItemClick(position: Int): Boolean = true
        fun onThreeDotButtonClick(fileWithSyncInfo: OCFileWithSyncInfo)
    }

    inner class GridViewHolder(val binding: GridItemBinding) : RecyclerView.ViewHolder(binding.root)
    inner class GridImageViewHolder(val binding: GridItemBinding) : RecyclerView.ViewHolder(binding.root)
    inner class ListViewHolder(val binding: ItemFileListBinding) : RecyclerView.ViewHolder(binding.root)
    inner class FooterViewHolder(val binding: ListFooterBinding) : RecyclerView.ViewHolder(binding.root)

    enum class ViewType {
        LIST_ITEM, GRID_IMAGE, GRID_ITEM, FOOTER
    }
}
