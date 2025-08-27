/**
 * openCloud Android client application
 *
 * @author Bartek Przybylski
 * @author David A. Velasco
 * @author masensio
 * @author David González Verdugo
 * Copyright (C) 2011  Bartek Przybylski
 * Copyright (C) 2020 ownCloud GmbH.
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
package eu.opencloud.android.data;

import android.provider.BaseColumns;

/**
 * Meta-Class that holds various static field information
 */
public class ProviderMeta {

    public static final String DB_NAME = "filelist";
    public static final String NEW_DB_NAME = "opencloud_database";
    public static final int DB_VERSION = 48;

    private ProviderMeta() {
    }

    static public class ProviderTableMeta implements BaseColumns {
        public static final String CAPABILITIES_TABLE_NAME = "capabilities";
        public static final String FILES_SYNC_TABLE_NAME = "files_sync";
        public static final String FILES_TABLE_NAME = "files";
        public static final String FOLDER_BACKUP_TABLE_NAME = "folder_backup";
        public static final String OCSHARES_TABLE_NAME = "ocshares";
        public static final String SPACES_TABLE_NAME = "spaces";
        public static final String SPACES_SPECIAL_TABLE_NAME = "spaces_special";
        public static final String TRANSFERS_TABLE_NAME = "transfers";
        public static final String USER_QUOTAS_TABLE_NAME = "user_quotas";

        public static final String APP_REGISTRY_TABLE_NAME = "app_registry";

        // Columns of ocshares table
        public static final String OCSHARES_ACCOUNT_OWNER = "owner_share";
        public static final String OCSHARES_EXPIRATION_DATE = "expiration_date";
        public static final String OCSHARES_ID_REMOTE_SHARED = "id_remote_shared";
        public static final String OCSHARES_IS_DIRECTORY = "is_directory";
        public static final String OCSHARES_NAME = "name";
        public static final String OCSHARES_PATH = "path";
        public static final String OCSHARES_PERMISSIONS = "permissions";
        public static final String OCSHARES_SHARED_DATE = "shared_date";
        public static final String OCSHARES_SHARE_TYPE = "share_type";
        public static final String OCSHARES_SHARE_WITH = "share_with";
        public static final String OCSHARES_SHARE_WITH_ADDITIONAL_INFO = "share_with_additional_info";
        public static final String OCSHARES_SHARE_WITH_DISPLAY_NAME = "shared_with_display_name";
        public static final String OCSHARES_TOKEN = "token";
        public static final String OCSHARES_URL = "url";

        // Columns of capabilities table
        public static final String CAPABILITIES_ACCOUNT_NAME = "account";
        public static final String CAPABILITIES_APP_PROVIDERS_PREFIX = "app_providers_";
        public static final String CAPABILITIES_CORE_POLLINTERVAL = "core_pollinterval";
        public static final String CAPABILITIES_DAV_CHUNKING_VERSION = "dav_chunking_version";
        public static final String CAPABILITIES_FILES_APP_PROVIDERS = "files_apps_providers";
        public static final String CAPABILITIES_FILES_BIGFILECHUNKING = "files_bigfilechunking";
        public static final String CAPABILITIES_FILES_PRIVATE_LINKS = "files_private_links";
        public static final String CAPABILITIES_FILES_UNDELETE = "files_undelete";
        public static final String CAPABILITIES_FILES_VERSIONING = "files_versioning";
        public static final String CAPABILITIES_SHARING_API_ENABLED = "sharing_api_enabled";
        public static final String CAPABILITIES_SHARING_FEDERATION_INCOMING = "sharing_federation_incoming";
        public static final String CAPABILITIES_SHARING_FEDERATION_OUTGOING = "sharing_federation_outgoing";
        public static final String CAPABILITIES_SHARING_PUBLIC_ENABLED = "sharing_public_enabled";
        public static final String CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_DAYS = "sharing_public_expire_date_days";
        public static final String CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENABLED = "sharing_public_expire_date_enabled";
        public static final String CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENFORCED = "sharing_public_expire_date_enforced";
        public static final String CAPABILITIES_SHARING_PUBLIC_MULTIPLE = "sharing_public_multiple";
        public static final String CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED = "sharing_public_password_enforced";
        public static final String CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED_READ_ONLY = "sharing_public_password_enforced_read_only";
        public static final String CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED_READ_WRITE = "sharing_public_password_enforced_read_write";
        public static final String CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED_UPLOAD_ONLY = "sharing_public_password_enforced_public_only";
        public static final String CAPABILITIES_SHARING_PUBLIC_SUPPORTS_UPLOAD_ONLY = "supports_upload_only";
        public static final String CAPABILITIES_SHARING_PUBLIC_UPLOAD = "sharing_public_upload";
        public static final String CAPABILITIES_SHARING_RESHARING = "sharing_resharing";
        public static final String CAPABILITIES_SHARING_USER_PROFILE_PICTURE = "sharing_user_profile_picture";
        public static final String CAPABILITIES_SPACES_PREFIX = "spaces_";
        public static final String CAPABILITIES_PASSWORD_POLICY_PREFIX = "password_policy_";
        public static final String CAPABILITIES_VERSION_EDITION = "version_edition";
        public static final String CAPABILITIES_VERSION_MAJOR = "version_major";
        public static final String CAPABILITIES_VERSION_MICRO = "version_micro";
        public static final String CAPABILITIES_VERSION_MINOR = "version_minor";
        public static final String CAPABILITIES_VERSION_STRING = "version_string";
        public static final String LEGACY_CAPABILITIES_VERSION_MAYOR = "version_mayor";

        // Columns of filelist table (legacy)
        public static final String FILE_ACCOUNT_OWNER = "file_owner";
        public static final String FILE_CONTENT_LENGTH = "content_length";
        public static final String FILE_CONTENT_TYPE = "content_type";
        public static final String FILE_CREATION = "created";
        public static final String FILE_ETAG = "etag";
        public static final String FILE_ETAG_IN_CONFLICT = "etag_in_conflict";
        public static final String FILE_IS_DOWNLOADING = "is_downloading";
        public static final String FILE_KEEP_IN_SYNC = "keep_in_sync";
        public static final String FILE_LAST_SYNC_DATE_FOR_DATA = "last_sync_date_for_data";
        public static final String FILE_MODIFIED = "modified";
        public static final String FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA = "modified_at_last_sync_for_data";
        public static final String FILE_NAME = "filename";
        public static final String FILE_PARENT = "parent";
        public static final String FILE_PATH = "path";
        public static final String FILE_PERMISSIONS = "permissions";
        public static final String FILE_PRIVATE_LINK = "private_link";
        public static final String FILE_REMOTE_ID = "remote_id";
        public static final String FILE_SHARED_VIA_LINK = "share_by_link";
        public static final String FILE_SHARED_WITH_SHAREE = "shared_via_users";
        public static final String FILE_STORAGE_PATH = "media_path";
        public static final String FILE_TREE_ETAG = "tree_etag";
        public static final String FILE_UPDATE_THUMBNAIL = "update_thumbnail";

        // Columns of list_of_uploads table
        public static final String UPLOAD_ACCOUNT_NAME = "account_name";
        public static final String UPLOAD_CREATED_BY = "created_by";
        public static final String UPLOAD_FILE_SIZE = "file_size";
        public static final String UPLOAD_FORCE_OVERWRITE = "force_overwrite";
        public static final String UPLOAD_LAST_RESULT = "last_result";
        public static final String UPLOAD_LOCAL_BEHAVIOUR = "local_behaviour";
        public static final String UPLOAD_LOCAL_PATH = "local_path";
        public static final String UPLOAD_REMOTE_PATH = "remote_path";
        public static final String UPLOAD_STATUS = "status";
        public static final String UPLOAD_TRANSFER_ID = "transfer_id";
        public static final String UPLOAD_UPLOAD_END_TIMESTAMP = "upload_end_timestamp";

        // Columns of files table
        public static final String FILE_OWNER = "owner";
        public static final String FILE_SPACE_ID = "spaceId";
    }
}
