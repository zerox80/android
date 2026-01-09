/**
 * openCloud Android client application
 *
 * @author OpenCloud Development Team
 *
 * Copyright (C) 2026 OpenCloud.
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

package eu.opencloud.android.workers

import android.accounts.AccountManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.opencloud.android.MainApp
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.usecases.synchronization.SynchronizeFileUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker that periodically syncs locally modified files to the cloud.
 * This is an opt-in feature that can be enabled in Security Settings.
 * 
 * It monitors all downloaded files and checks if they have been modified locally.
 * If a file has been modified, it uploads the new version to the server.
 */
class LocalFileSyncWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private val fileRepository: FileRepository by inject()
    private val synchronizeFileUseCase: SynchronizeFileUseCase by inject()

    override suspend fun doWork(): Result {
        Timber.i("LocalFileSyncWorker started")

        return try {
            val accountManager = AccountManager.get(appContext)
            val accounts = accountManager.getAccountsByType(MainApp.accountType)

            Timber.i("Checking ${accounts.size} accounts for local file changes")

            var totalFilesChecked = 0
            var totalFilesUpdated = 0

            accounts.forEach { account ->
                val accountName = account.name
                Timber.d("Checking locally downloaded files for account: $accountName")

                // Get all downloaded files for this account
                val downloadedFiles = fileRepository.getDownloadedFilesForAccount(accountName)
                Timber.d("Found ${downloadedFiles.size} downloaded files for account $accountName")

                downloadedFiles.forEach { file ->
                    if (!file.isFolder) {
                        totalFilesChecked++
                        try {
                            val useCaseResult = synchronizeFileUseCase(SynchronizeFileUseCase.Params(file))
                            when (useCaseResult) {
                                is UseCaseResult.Success -> {
                                    when (val syncResult = useCaseResult.data) {
                                        is SynchronizeFileUseCase.SyncType.UploadEnqueued -> {
                                            Timber.i("File ${file.fileName} has local changes, upload enqueued")
                                            totalFilesUpdated++
                                        }
                                        is SynchronizeFileUseCase.SyncType.DownloadEnqueued -> {
                                            Timber.i("File ${file.fileName} has remote changes, download enqueued")
                                            totalFilesUpdated++
                                        }
                                        is SynchronizeFileUseCase.SyncType.ConflictDetected -> {
                                            Timber.w("File ${file.fileName} has a conflict with etag: ${syncResult.etagInConflict}")
                                        }
                                        is SynchronizeFileUseCase.SyncType.AlreadySynchronized -> {
                                            Timber.d("File ${file.fileName} is already synchronized")
                                        }
                                        is SynchronizeFileUseCase.SyncType.FileNotFound -> {
                                            Timber.w("File ${file.fileName} was not found on server")
                                        }
                                    }
                                }
                                is UseCaseResult.Error -> {
                                    Timber.e(useCaseResult.throwable, "Error syncing file ${file.fileName}")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error syncing file ${file.fileName}")
                        }
                    }
                }
            }

            Timber.i("LocalFileSyncWorker completed: checked $totalFilesChecked files, updated $totalFilesUpdated")
            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "LocalFileSyncWorker failed")
            Result.failure()
        }
    }

    companion object {
        const val LOCAL_FILE_SYNC_WORKER = "LOCAL_FILE_SYNC_WORKER"
        const val repeatInterval: Long = 5L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.MINUTES
    }
}
