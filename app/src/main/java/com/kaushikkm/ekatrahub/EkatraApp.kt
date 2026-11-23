package com.kaushikkm.ekatrahub

import android.app.Application
import androidx.room.Room
import com.kaushikkm.ekatrahub.data.EkatraDatabase

/**
 * Runs once when the app's process starts, before any screen or service.
 * It owns the single database instance shared by the UI and the listener service
 * (opening a database is expensive, so we only ever want one).
 */
class EkatraApp : Application() {
    val database: EkatraDatabase by lazy {
        Room.databaseBuilder(this, EkatraDatabase::class.java, "ekatra.db")
            .addMigrations(EkatraDatabase.MIGRATION_1_2, EkatraDatabase.MIGRATION_2_3)
            .build()
    }
}
