package com.example.authentication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "otp_accounts")
data class OtpAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val issuer: String,           // e.g. "GitHub"
    val accountName: String,      // e.g. "alex@example.com"
    val secretKey: String,        // Raw Base32 string from QR code
    val addedAtMillis: Long = System.currentTimeMillis()
)
