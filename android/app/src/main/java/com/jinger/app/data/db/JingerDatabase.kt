package com.jinger.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [JobEntity::class], version = 1, exportSchema = false)
abstract class JingerDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        fun create(context: Context): JingerDatabase =
            Room.databaseBuilder(context, JingerDatabase::class.java, "jinger.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
