/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
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

package eu.opencloud.android.domain.transfers.model

import android.os.Parcelable
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class OCTransfer(
    var id: Long? = null,
    val localPath: String,
    val remotePath: String,
    val accountName: String,
    val fileSize: Long,
    var status: TransferStatus,
    val localBehaviour: UploadBehavior,
    val forceOverwrite: Boolean,
    val transferEndTimestamp: Long? = null,
    val lastResult: TransferResult? = null,
    val createdBy: UploadEnqueuedBy,
    val transferId: String? = null,
    val spaceId: String? = null,
    val sourcePath: String? = null,
    // TUS protocol state
    val tusUploadUrl: String? = null,
    val tusUploadOffset: Long? = null,
    val tusUploadLength: Long? = null,
    val tusUploadMetadata: String? = null,
    val tusUploadChecksum: String? = null,
    val tusResumableVersion: String? = null,
    val tusUploadExpires: Long? = null,
    val tusUploadConcat: String? = null,
) : Parcelable {
    init {
        if (!remotePath.startsWith(File.separator)) throw IllegalArgumentException("Remote path must be an absolute path in the local file system")
        if (accountName.isEmpty()) throw IllegalArgumentException("Invalid account name")
    }
}
