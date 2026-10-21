package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AlarmEventEntity::class], version = 2, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmEventDao(): AlarmEventDao

    companion object {
        @Volatile
        private var INSTANCE: AlarmDatabase? = null

        fun getInstance(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AlarmDatabase::class.java,
                        "alarm_perimeter_iot.db"
                    )
                        .fallbackToDestructiveMigration()
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .build()
                    INSTANCE = instance
                    instance
                } catch (_: Throwable) {
                    val inMem = Room.inMemoryDatabaseBuilder(
                        context.applicationContext,
                        AlarmDatabase::class.java
                    ).build()
                    INSTANCE = inMem
                    inMem
                }
            }
        }
    }
}
