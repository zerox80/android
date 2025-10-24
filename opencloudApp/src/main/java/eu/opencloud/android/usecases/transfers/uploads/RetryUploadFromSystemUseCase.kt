/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.usecases.transfers.uploads

import androidx.work.WorkInfo
import androidx.work.WorkManager
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.extensions.getWorkInfoByTags
import eu.opencloud.android.workers.UploadFileFromFileSystemWorker
import timber.log.Timber
import java.io.File

class RetryUploadFromSystemUseCase(
    private val workManager: WorkManager,
    private val uploadFileFromSystemUseCase: UploadFileFromSystemUseCase,
    private val transferRepository: TransferRepository,
) : BaseUseCase<Unit, RetryUploadFromSystemUseCase.Params>() {

    override fun run(params: Params) {
        val uploadToRetry = transferRepository.getTransferById(params.uploadIdInStorageManager)

        uploadToRetry ?: return

        val workInfos = workManager.getWorkInfoByTags(
            listOf(
                params.uploadIdInStorageManager.toString(),
                uploadToRetry.accountName,
                UploadFileFromFileSystemWorker::class.java.name
            )
        )

        if (workInfos.isEmpty() || workInfos.firstOrNull()?.state == WorkInfo.State.FAILED) {
            transferRepository.updateTransferStatusToEnqueuedById(params.uploadIdInStorageManager)

            val lastModifiedInSeconds = File(uploadToRetry.localPath)
                .takeIf { it.exists() && it.isFile }
                ?.lastModified()
                ?.takeIf { it > 0 }
                ?.div(1000)
                ?.toString()

            uploadFileFromSystemUseCase(
                UploadFileFromSystemUseCase.Params(
                    accountName = uploadToRetry.accountName,
                    localPath = uploadToRetry.localPath,
                    lastModifiedInSeconds = lastModifiedInSeconds,
                    behavior = uploadToRetry.localBehaviour.name,
                    uploadPath = uploadToRetry.remotePath,
                    sourcePath = uploadToRetry.sourcePath,
                    uploadIdInStorageManager = params.uploadIdInStorageManager
                )
            )
        } else {
            Timber.w("Upload $uploadToRetry is already in state ${workInfos.firstOrNull()?.state}. Won't be retried")
        }
    }

    data class Params(
        val uploadIdInStorageManager: Long,
    )
}
