/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * Copyright (C) 2020 ownCloud GmbH.
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

package eu.opencloud.android.data.capabilities.datasources.implementation

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import eu.opencloud.android.data.capabilities.datasources.LocalCapabilitiesDataSource
import eu.opencloud.android.data.capabilities.db.OCCapabilityDao
import eu.opencloud.android.data.capabilities.db.OCCapabilityEntity
import eu.opencloud.android.domain.capabilities.model.CapabilityBooleanType
import eu.opencloud.android.domain.capabilities.model.OCCapability

class OCLocalCapabilitiesDataSource(
    private val ocCapabilityDao: OCCapabilityDao,
) : LocalCapabilitiesDataSource {

    override fun getCapabilitiesForAccountAsLiveData(accountName: String): LiveData<OCCapability?> =
        ocCapabilityDao.getCapabilitiesForAccountAsLiveData(accountName).map { ocCapabilityEntity ->
            ocCapabilityEntity?.toModel()
        }

    override fun getCapabilitiesForAccount(accountName: String): OCCapability? =
        ocCapabilityDao.getCapabilitiesForAccount(accountName)?.toModel()

    override fun insertCapabilities(ocCapabilities: List<OCCapability>) {
        ocCapabilityDao.replace(
            ocCapabilities.map { ocCapability -> ocCapability.toEntity() }
        )
    }

    override fun deleteCapabilitiesForAccount(accountName: String) {
        ocCapabilityDao.deleteByAccountName(accountName)
    }

    companion object {
        @VisibleForTesting
        fun OCCapabilityEntity.toModel(): OCCapability =
            OCCapability(
                id = id,
                accountName = accountName,
                versionMajor = versionMajor,
                versionMinor = versionMinor,
                versionMicro = versionMicro,
                versionString = versionString,
                versionEdition = versionEdition,
                corePollInterval = corePollInterval,
                davChunkingVersion = davChunkingVersion,
                filesSharingApiEnabled = CapabilityBooleanType.fromValue(filesSharingApiEnabled),
                filesSharingPublicEnabled = CapabilityBooleanType.fromValue(filesSharingPublicEnabled),
                filesSharingPublicPasswordEnforced = CapabilityBooleanType.fromValue(filesSharingPublicPasswordEnforced),
                filesSharingPublicPasswordEnforcedReadOnly = CapabilityBooleanType.fromValue(filesSharingPublicPasswordEnforcedReadOnly),
                filesSharingPublicPasswordEnforcedReadWrite = CapabilityBooleanType.fromValue(filesSharingPublicPasswordEnforcedReadWrite),
                filesSharingPublicPasswordEnforcedUploadOnly = CapabilityBooleanType.fromValue(filesSharingPublicPasswordEnforcedUploadOnly),
                filesSharingPublicExpireDateEnabled = CapabilityBooleanType.fromValue(filesSharingPublicExpireDateEnabled),
                filesSharingPublicExpireDateDays = filesSharingPublicExpireDateDays,
                filesSharingPublicExpireDateEnforced = CapabilityBooleanType.fromValue(filesSharingPublicExpireDateEnforced),
                filesSharingPublicUpload = CapabilityBooleanType.fromValue(filesSharingPublicUpload),
                filesSharingPublicMultiple = CapabilityBooleanType.fromValue(filesSharingPublicMultiple),
                filesSharingPublicSupportsUploadOnly = CapabilityBooleanType.fromValue(filesSharingPublicSupportsUploadOnly),
                filesSharingResharing = CapabilityBooleanType.fromValue(filesSharingResharing),
                filesSharingFederationOutgoing = CapabilityBooleanType.fromValue(filesSharingFederationOutgoing),
                filesSharingFederationIncoming = CapabilityBooleanType.fromValue(filesSharingFederationIncoming),
                filesSharingUserProfilePicture = CapabilityBooleanType.fromValue(filesSharingUserProfilePicture),
                filesBigFileChunking = CapabilityBooleanType.fromValue(filesBigFileChunking),
                filesUndelete = CapabilityBooleanType.fromValue(filesUndelete),
                filesVersioning = CapabilityBooleanType.fromValue(filesVersioning),
                filesPrivateLinks = CapabilityBooleanType.fromValue(filesPrivateLinks),
                filesAppProviders = appProviders,
                filesTusSupport = tusSupport,
                spaces = spaces,
                passwordPolicy = passwordPolicy,
            )

        @VisibleForTesting
        fun OCCapability.toEntity(): OCCapabilityEntity =
            OCCapabilityEntity(
                accountName = accountName,
                versionMajor = versionMajor,
                versionMinor = versionMinor,
                versionMicro = versionMicro,
                versionString = versionString,
                versionEdition = versionEdition,
                corePollInterval = corePollInterval,
                davChunkingVersion = davChunkingVersion,
                filesSharingApiEnabled = filesSharingApiEnabled.value,
                filesSharingPublicEnabled = filesSharingPublicEnabled.value,
                filesSharingPublicPasswordEnforced = filesSharingPublicPasswordEnforced.value,
                filesSharingPublicPasswordEnforcedReadOnly = filesSharingPublicPasswordEnforcedReadOnly.value,
                filesSharingPublicPasswordEnforcedReadWrite = filesSharingPublicPasswordEnforcedReadWrite.value,
                filesSharingPublicPasswordEnforcedUploadOnly = filesSharingPublicPasswordEnforcedUploadOnly.value,
                filesSharingPublicExpireDateEnabled = filesSharingPublicExpireDateEnabled.value,
                filesSharingPublicExpireDateDays = filesSharingPublicExpireDateDays,
                filesSharingPublicExpireDateEnforced = filesSharingPublicExpireDateEnforced.value,
                filesSharingPublicUpload = filesSharingPublicUpload.value,
                filesSharingPublicMultiple = filesSharingPublicMultiple.value,
                filesSharingPublicSupportsUploadOnly = filesSharingPublicSupportsUploadOnly.value,
                filesSharingResharing = filesSharingResharing.value,
                filesSharingFederationOutgoing = filesSharingFederationOutgoing.value,
                filesSharingFederationIncoming = filesSharingFederationIncoming.value,
                filesSharingUserProfilePicture = filesSharingUserProfilePicture.value,
                filesBigFileChunking = filesBigFileChunking.value,
                filesUndelete = filesUndelete.value,
                filesVersioning = filesVersioning.value,
                filesPrivateLinks = filesPrivateLinks.value,
                appProviders = filesAppProviders,
                tusSupport = filesTusSupport,
                spaces = spaces,
                passwordPolicy = passwordPolicy,
            )
    }
}
