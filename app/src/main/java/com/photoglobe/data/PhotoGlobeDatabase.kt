package com.photoglobe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoEntity::class, ScanStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PhotoGlobeDatabase : RoomDatabase() {

    abstract fun photoDao(): PhotoDao
    abstract fun scanStateDao(): ScanStateDao

    companion object {
        @Volatile private var instance: PhotoGlobeDatabase? = null

        fun get(context: Context): PhotoGlobeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotoGlobeDatabase::class.java,
                    "photoglobe.db"
                ).build().also { instance = it }
            }
    }
}
