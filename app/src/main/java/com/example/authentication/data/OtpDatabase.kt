package com.example.authentication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OtpAccount::class], version = 1, exportSchema = false)
abstract class OtpDatabase : RoomDatabase() {

    abstract fun otpDao(): OtpDao

    companion object {
        @Volatile
        private var INSTANCE: OtpDatabase? = null

        fun getInstance(context: Context): OtpDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    OtpDatabase::class.java,
                    "otp_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
