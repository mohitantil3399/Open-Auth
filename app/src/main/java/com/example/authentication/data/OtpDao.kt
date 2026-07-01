package com.example.authentication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OtpDao {

    @Query("SELECT * FROM otp_accounts ORDER BY addedAtMillis DESC")
    fun getAllAccountsStream(): Flow<List<OtpAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: OtpAccount)

    @Delete
    suspend fun delete(account: OtpAccount)
}
