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
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFile.Companion.ROOT_PATH
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalAndProjectSpacesForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.usecases.synchronization.SynchronizeFolderUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker that downloads all files from all accounts for offline access.
 * This is an opt-in feature that can be enabled in Security Settings.
 */
class DownloadEverythingWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase by inject()
    private val refreshSpacesFromServerAsyncUseCase: RefreshSpacesFromServerAsyncUseCase by inject()
    private val getPersonalAndProjectSpacesForAccountUseCase: GetPersonalAndProjectSpacesForAccountUseCase by inject()
    private val getFileByRemotePathUseCase: GetFileByRemotePathUseCase by inject()
    private val synchronizeFolderUseCase: SynchronizeFolderUseCase by inject()

    override suspend fun doWork(): Result {
        Timber.i("DownloadEverythingWorker started")

        return try {
            val accountManager = AccountManager.get(appContext)
            val accounts = accountManager.getAccountsByType(MainApp.accountType)

            Timber.i("Found ${accounts.size} accounts to sync")

            accounts.forEach { account ->
                val accountName = account.name
                Timber.i("Syncing all files for account: $accountName")

                // Get capabilities for account
                val capabilities = getStoredCapabilitiesUseCase(GetStoredCapabilitiesUseCase.Params(accountName))
                val spacesAvailableForAccount = AccountUtils.isSpacesFeatureAllowedForAccount(appContext, account, capabilities)

                if (!spacesAvailableForAccount) {
                    // Account does not support spaces - sync legacy root
                    val rootLegacyFolder = getFileByRemotePathUseCase(
                        GetFileByRemotePathUseCase.Params(accountName, ROOT_PATH, null)
                    ).getDataOrNull()
                    rootLegacyFolder?.let {
                        syncFolderRecursively(it)
                    }
                } else {
                    // Account supports spaces - sync all spaces
                    refreshSpacesFromServerAsyncUseCase(RefreshSpacesFromServerAsyncUseCase.Params(accountName))
                    val spaces = getPersonalAndProjectSpacesForAccountUseCase(
                        GetPersonalAndProjectSpacesForAccountUseCase.Params(accountName)
                    )

                    Timber.i("Found ${spaces.size} spaces for account $accountName")

                    spaces.forEach { space ->
                        val rootFolderForSpace = getFileByRemotePathUseCase(
                            GetFileByRemotePathUseCase.Params(accountName, ROOT_PATH, space.root.id)
                        ).getDataOrNull()

                        rootFolderForSpace?.let {
                            Timber.i("Syncing space: ${space.name}")
                            syncFolderRecursively(it)
                        }
                    }
                }
            }

            Timber.i("DownloadEverythingWorker completed successfully")
            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "DownloadEverythingWorker failed")
            Result.failure()
        }
    }

    private fun syncFolderRecursively(folder: OCFile) {
        synchronizeFolderUseCase(
            SynchronizeFolderUseCase.Params(
                accountName = folder.owner,
                remotePath = folder.remotePath,
                spaceId = folder.spaceId,
                syncMode = SynchronizeFolderUseCase.SyncFolderMode.SYNC_FOLDER_RECURSIVELY
            )
        )
    }

    companion object {
        const val DOWNLOAD_EVERYTHING_WORKER = "DOWNLOAD_EVERYTHING_WORKER"
        const val repeatInterval: Long = 6L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.HOURS
    }
}
