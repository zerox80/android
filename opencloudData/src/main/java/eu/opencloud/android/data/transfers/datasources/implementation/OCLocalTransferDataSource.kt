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

package eu.opencloud.android.data.transfers.datasources.implementation

import androidx.annotation.VisibleForTesting
import eu.opencloud.android.data.transfers.datasources.LocalTransferDataSource
import eu.opencloud.android.data.transfers.db.OCTransferEntity
import eu.opencloud.android.data.transfers.db.TransferDao
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferResult
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.domain.transfers.model.UploadEnqueuedBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OCLocalTransferDataSource(
    private val transferDao: TransferDao
) : LocalTransferDataSource {
    override fun saveTransfer(transfer: OCTransfer): Long =
        transferDao.insertOrReplace(transfer.toEntity())

    override fun updateTransfer(transfer: OCTransfer) {
        transferDao.insertOrReplace(transfer.toEntity())
    }

    override fun updateTransferStatusToInProgressById(id: Long) {
        transferDao.updateTransferStatusWithId(id, TransferStatus.TRANSFER_IN_PROGRESS.value)
    }

    override fun updateTransferStatusToEnqueuedById(id: Long) {
        transferDao.updateTransferStatusWithId(id, TransferStatus.TRANSFER_QUEUED.value)
    }

    override fun updateTransferWhenFinished(
        id: Long,
        status: TransferStatus,
        transferEndTimestamp: Long,
        lastResult: TransferResult
    ) {
        transferDao.updateTransferWhenFinished(id, status.value, transferEndTimestamp, lastResult.value)
    }

    override fun updateTransferLocalPath(id: Long, localPath: String) {
        transferDao.updateTransferLocalPath(id, localPath)
    }

    override fun updateTransferSourcePath(id: Long, sourcePath: String) {
        transferDao.updateTransferSourcePath(id, sourcePath)
    }

    override fun updateTransferStorageDirectoryInLocalPath(
        id: Long,
        oldDirectory: String,
        newDirectory: String
    ) {
        transferDao.updateTransferStorageDirectoryInLocalPath(id, oldDirectory, newDirectory)
    }

    override fun deleteTransferById(id: Long) {
        transferDao.deleteTransferWithId(id)
    }

    override fun deleteAllTransfersFromAccount(accountName: String) {
        transferDao.deleteTransfersWithAccountName(accountName)
    }

    override fun getTransferById(id: Long): OCTransfer? =
        transferDao.getTransferWithId(id)?.toModel()

    override fun getAllTransfers(): List<OCTransfer> =
        transferDao.getAllTransfers().map { transferEntity ->
            transferEntity.toModel()
        }

    override fun getAllTransfersAsStream(): Flow<List<OCTransfer>> =
        transferDao.getAllTransfersAsStream().map { transferEntitiesList ->
            val transfers = transferEntitiesList.map { transferEntity ->
                transferEntity.toModel()
            }
            val transfersGroupedByStatus = transfers.groupBy { it.status }
            val transfersGroupedByStatusOrdered = Array<List<OCTransfer>>(4) { emptyList() }
            val newTransfersList = mutableListOf<OCTransfer>()
            transfersGroupedByStatus.forEach { transferMap ->
                val order = when (transferMap.key) {
                    TransferStatus.TRANSFER_IN_PROGRESS -> 0
                    TransferStatus.TRANSFER_QUEUED -> 1
                    TransferStatus.TRANSFER_FAILED -> 2
                    TransferStatus.TRANSFER_SUCCEEDED -> 3
                }
                transfersGroupedByStatusOrdered[order] = transferMap.value
            }
            for (items in transfersGroupedByStatusOrdered) {
                newTransfersList.addAll(items)
            }
            newTransfersList
        }

    override fun getLastTransferFor(remotePath: String, accountName: String): OCTransfer? =
        transferDao.getLastTransferWithRemotePathAndAccountName(remotePath, accountName)?.toModel()

    override fun getCurrentAndPendingTransfers(): List<OCTransfer> =
        transferDao.getTransfersWithStatus(
            listOf(TransferStatus.TRANSFER_IN_PROGRESS.value, TransferStatus.TRANSFER_QUEUED.value)
        ).map { it.toModel() }

    override fun getFailedTransfers(): List<OCTransfer> =
        transferDao.getTransfersWithStatus(
            listOf(TransferStatus.TRANSFER_FAILED.value)
        ).map { it.toModel() }

    override fun getFinishedTransfers(): List<OCTransfer> =
        transferDao.getTransfersWithStatus(
            listOf(TransferStatus.TRANSFER_SUCCEEDED.value)
        ).map { it.toModel() }

    override fun clearFailedTransfers() {
        transferDao.deleteTransfersWithStatus(TransferStatus.TRANSFER_FAILED.value)
    }

    override fun clearSuccessfulTransfers() {
        transferDao.deleteTransfersWithStatus(TransferStatus.TRANSFER_SUCCEEDED.value)
    }

    // TUS state management
    override fun updateTusState(
        id: Long,
        tusUploadUrl: String?,
        tusUploadOffset: Long?,
        tusUploadLength: Long?,
        tusUploadMetadata: String?,
        tusUploadChecksum: String?,
        tusResumableVersion: String?,
        tusUploadExpires: Long?,
        tusUploadConcat: String?,
    ) {
        transferDao.updateTusState(
            id = id,
            tusUploadUrl = tusUploadUrl,
            tusUploadOffset = tusUploadOffset,
            tusUploadLength = tusUploadLength,
            tusUploadMetadata = tusUploadMetadata,
            tusUploadChecksum = tusUploadChecksum,
            tusResumableVersion = tusResumableVersion,
            tusUploadExpires = tusUploadExpires,
            tusUploadConcat = tusUploadConcat,
        )
    }

    override fun updateTusOffset(id: Long, tusUploadOffset: Long?) {
        transferDao.updateTusOffset(id = id, tusUploadOffset = tusUploadOffset)
    }

    override fun updateTusUrl(id: Long, tusUploadUrl: String?) {
        transferDao.updateTusUrl(id = id, tusUploadUrl = tusUploadUrl)
    }


    companion object {

        @VisibleForTesting
        fun OCTransferEntity.toModel() = OCTransfer(
            id = id,
            localPath = localPath,
            remotePath = remotePath,
            accountName = accountName,
            fileSize = fileSize,
            status = TransferStatus.fromValue(status),
            localBehaviour = if (localBehaviour > 1) UploadBehavior.MOVE else UploadBehavior.values()[localBehaviour],
            forceOverwrite = forceOverwrite,
            transferEndTimestamp = transferEndTimestamp,
            lastResult = lastResult?.let { TransferResult.fromValue(it) },
            createdBy = UploadEnqueuedBy.values()[createdBy],
            transferId = transferId,
            spaceId = spaceId,
            sourcePath = sourcePath,
            tusUploadUrl = tusUploadUrl,
            tusUploadOffset = tusUploadOffset,
            tusUploadLength = tusUploadLength,
            tusUploadMetadata = tusUploadMetadata,
            tusUploadChecksum = tusUploadChecksum,
            tusResumableVersion = tusResumableVersion,
            tusUploadExpires = tusUploadExpires,
            tusUploadConcat = tusUploadConcat,
        )
        @VisibleForTesting
        fun OCTransfer.toEntity() = OCTransferEntity(
            localPath = localPath,
            remotePath = remotePath,
            accountName = accountName,
            fileSize = fileSize,
            status = status.value,
            localBehaviour = localBehaviour.ordinal,
            forceOverwrite = forceOverwrite,
            transferEndTimestamp = transferEndTimestamp,
            lastResult = lastResult?.value,
            createdBy = createdBy.ordinal,
            transferId = transferId,
            spaceId = spaceId,
            sourcePath = sourcePath,
            tusUploadUrl = tusUploadUrl,
            tusUploadOffset = tusUploadOffset,
            tusUploadLength = tusUploadLength,
            tusUploadMetadata = tusUploadMetadata,
            tusUploadChecksum = tusUploadChecksum,
            tusResumableVersion = tusResumableVersion,
            tusUploadExpires = tusUploadExpires,
            tusUploadConcat = tusUploadConcat,
        ).apply { this@toEntity.id?.let { this.id = it } }
    }
}
