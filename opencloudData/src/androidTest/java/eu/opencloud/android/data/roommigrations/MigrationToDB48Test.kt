/*
 * openCloud Android client application
 *
 * Copyright (C) 2024 OpenCloud GmbH.
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
 *
 */

package eu.opencloud.android.data.roommigrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.filters.SmallTest
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.TRANSFERS_TABLE_NAME
import eu.opencloud.android.data.migrations.MIGRATION_27_28
import eu.opencloud.android.data.migrations.MIGRATION_28_29
import eu.opencloud.android.data.migrations.MIGRATION_29_30
import eu.opencloud.android.data.migrations.MIGRATION_30_31
import eu.opencloud.android.data.migrations.MIGRATION_31_32
import eu.opencloud.android.data.migrations.MIGRATION_32_33
import eu.opencloud.android.data.migrations.MIGRATION_33_34
import eu.opencloud.android.data.migrations.MIGRATION_34_35
import eu.opencloud.android.data.migrations.MIGRATION_35_36
import eu.opencloud.android.data.migrations.MIGRATION_37_38
import eu.opencloud.android.data.migrations.MIGRATION_41_42
import eu.opencloud.android.data.migrations.MIGRATION_42_43
import eu.opencloud.android.data.migrations.MIGRATION_47_48
import org.junit.Assert
import org.junit.Test

@SmallTest
class MigrationToDB48Test : MigrationTest() {

    @Test
    fun migrationFrom47to48_containsCorrectData() {
        performMigrationTest(
            previousVersion = 47,
            currentVersion = 48,
            insertData = { database -> insertDataToTest(database) },
            validateMigration = { database -> validateMigrationTo48(database) },
            listOfMigrations = arrayOf(
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                MIGRATION_37_38,
                MIGRATION_41_42,
                MIGRATION_42_43,
                MIGRATION_47_48
            )
        )
    }

    private fun insertDataToTest(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO `$TRANSFERS_TABLE_NAME`" +
                    "(" +
                    "localPath, " +
                    "remotePath, " +
                    "accountName, " +
                    "fileSize, " +
                    "status, " +
                    "localBehaviour, " +
                    "forceOverwrite, " +
                    "createdBy" +
                    ")" +
                    " VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                "/storage/emulated/0/test.txt",
                "/test.txt",
                "user@example.com",
                1024,
                0,
                0,
                0,
                0
            )
        )
    }

    private fun validateMigrationTo48(database: SupportSQLiteDatabase) {
        val cursor = database.query("SELECT * FROM $TRANSFERS_TABLE_NAME")
        Assert.assertTrue(cursor.moveToFirst())

        // Check if new columns exist
        val tusUploadUrlIndex = cursor.getColumnIndex("tusUploadUrl")
        val tusUploadLengthIndex = cursor.getColumnIndex("tusUploadLength")
        val tusUploadMetadataIndex = cursor.getColumnIndex("tusUploadMetadata")
        val tusUploadChecksumIndex = cursor.getColumnIndex("tusUploadChecksum")
        val tusResumableVersionIndex = cursor.getColumnIndex("tusResumableVersion")
        val tusUploadExpiresIndex = cursor.getColumnIndex("tusUploadExpires")
        val tusUploadConcatIndex = cursor.getColumnIndex("tusUploadConcat")

        Assert.assertTrue(tusUploadUrlIndex != -1)
        Assert.assertTrue(tusUploadLengthIndex != -1)
        Assert.assertTrue(tusUploadMetadataIndex != -1)
        Assert.assertTrue(tusUploadChecksumIndex != -1)
        Assert.assertTrue(tusResumableVersionIndex != -1)
        Assert.assertTrue(tusUploadExpiresIndex != -1)
        Assert.assertTrue(tusUploadConcatIndex != -1)

        // Check if existing data is preserved
        val localPathIndex = cursor.getColumnIndex("localPath")
        Assert.assertEquals("/storage/emulated/0/test.txt", cursor.getString(localPathIndex))

        cursor.close()
        database.close()
    }
}
