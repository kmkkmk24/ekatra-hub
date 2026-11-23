package com.kaushikkm.ekatrahub.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The on-device SQLite database (file: ekatra.db, inside the app's private storage).
 * Other apps cannot read it, and it is deleted automatically when the app is uninstalled.
 *
 * When NotificationEntity changes, bump `version` and add a Migration below, so people
 * who already have the app keep their history instead of losing it.
 */
@Database(entities = [NotificationEntity::class], version = 3, exportSchema = false)
abstract class EkatraDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        /** v2: read / unread. Existing notifications start as unread. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: full message text + whether the app offered Reply. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN fullText TEXT")
                db.execSQL("ALTER TABLE notifications ADD COLUMN hadReply INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
