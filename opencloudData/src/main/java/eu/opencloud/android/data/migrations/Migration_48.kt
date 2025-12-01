package eu.opencloud.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta

val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE ${ProviderTableMeta.CAPABILITIES_TABLE_NAME} " +
                "ADD COLUMN `${ProviderTableMeta.CAPABILITIES_TUS_SUPPORT_VERSION}` TEXT"
        )
        database.execSQL(
            "ALTER TABLE ${ProviderTableMeta.CAPABILITIES_TABLE_NAME} " +
                "ADD COLUMN `${ProviderTableMeta.CAPABILITIES_TUS_SUPPORT_RESUMABLE}` TEXT"
        )
        database.execSQL(
            "ALTER TABLE ${ProviderTableMeta.CAPABILITIES_TABLE_NAME} " +
                "ADD COLUMN `${ProviderTableMeta.CAPABILITIES_TUS_SUPPORT_EXTENSION}` TEXT"
        )
        database.execSQL(
            "ALTER TABLE ${ProviderTableMeta.CAPABILITIES_TABLE_NAME} " +
                "ADD COLUMN `${ProviderTableMeta.CAPABILITIES_TUS_SUPPORT_MAX_CHUNK_SIZE}` INTEGER"
        )
        database.execSQL(
            "ALTER TABLE ${ProviderTableMeta.CAPABILITIES_TABLE_NAME} " +
                "ADD COLUMN `${ProviderTableMeta.CAPABILITIES_TUS_SUPPORT_HTTP_METHOD_OVERRIDE}` TEXT"
        )

        database.execSQL("ALTER TABLE ${ProviderTableMeta.TRANSFERS_TABLE_NAME} ADD COLUMN `tusUploadUrl` TEXT")
        database.execSQL("ALTER TABLE ${ProviderTableMeta.TRANSFERS_TABLE_NAME} ADD COLUMN `tusUploadLength` INTEGER")
        database.execSQL("ALTER TABLE ${ProviderTableMeta.TRANSFERS_TABLE_NAME} ADD COLUMN `tusUploadMetadata` TEXT")
        database.execSQL("ALTER TABLE ${ProviderTableMeta.TRANSFERS_TABLE_NAME} ADD COLUMN `tusUploadChecksum` TEXT")
        database.execSQL("ALTER TABLE ${ProviderTableMeta.TRANSFERS_TABLE_NAME} ADD COLUMN `tusResumableVersion` TEXT")
        database.execSQL("ALTER TABLE ${ProviderTableMeta.TRANSFERS_TABLE_NAME} ADD COLUMN `tusUploadExpires` INTEGER")
        database.execSQL("ALTER TABLE ${ProviderTableMeta.TRANSFERS_TABLE_NAME} ADD COLUMN `tusUploadConcat` TEXT")
    }
}
