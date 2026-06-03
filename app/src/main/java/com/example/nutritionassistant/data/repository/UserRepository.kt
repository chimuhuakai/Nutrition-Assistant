package com.example.nutritionassistant.data.repository

import com.example.nutritionassistant.data.local.dao.UserProfileDao
import com.example.nutritionassistant.data.local.entity.UserProfileEntity
import javax.inject.Inject

class UserRepository @Inject constructor(private val userProfileDao: UserProfileDao) {

    suspend fun getProfile(): UserProfileEntity? = userProfileDao.getProfile()
    suspend fun saveProfile(profile: UserProfileEntity) = userProfileDao.upsertProfile(profile)
    suspend fun getBlacklist(): List<String> {
        val profile = userProfileDao.getProfile()
        return profile?.blacklist?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }
}