package com.example.authentication.data

import kotlinx.coroutines.flow.Flow

class AccountRepository(private val dao: OtpDao) {

    fun getAllAccountsStream(): Flow<List<OtpAccount>> = dao.getAllAccountsStream()

    suspend fun insert(account: OtpAccount) = dao.insert(account)

    suspend fun delete(account: OtpAccount) = dao.delete(account)
}
