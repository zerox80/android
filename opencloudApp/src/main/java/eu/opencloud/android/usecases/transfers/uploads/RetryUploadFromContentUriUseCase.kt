/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2022 ownCloud GmbH.
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

import androidx.core.net.toUri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.extensions.getWorkInfoByTags
import eu.opencloud.android.workers.UploadFileFromContentUriWorker
import timber.log.Timber
import java.io.File

class RetryUploadFromContentUriUseCase(
    private val workManager: WorkManager,
    private val uploadFileFromContentUriUseCase: UploadFileFromContentUriUseCase,
    private val transferRepository: TransferRepository,
) : BaseUseCase<Unit, RetryUploadFromContentUriUseCase.Params>() {

    override fun run(params: Params) {
        val uploadToRetry = transferRepository.getTransferById(params.uploadIdInStorageManager)

        uploadToRetry ?: return

        val workInfos = workManager.getWorkInfoByTags(
            listOf(
                params.uploadIdInStorageManager.toString(),
                uploadToRetry.accountName,
                UploadFileFromContentUriWorker::class.java.name
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

            uploadFileFromContentUriUseCase(
                UploadFileFromContentUriUseCase.Params(
                    accountName = uploadToRetry.accountName,
                    contentUri = uploadToRetry.localPath.toUri(),
                    lastModifiedInSeconds = lastModifiedInSeconds,
                    behavior = uploadToRetry.localBehaviour.name,
                    uploadPath = uploadToRetry.remotePath,
                    uploadIdInStorageManager = params.uploadIdInStorageManager,
                    wifiOnly = false,
                    chargingOnly = false
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
