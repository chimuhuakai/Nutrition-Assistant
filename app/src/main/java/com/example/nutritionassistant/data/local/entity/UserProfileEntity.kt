package com.example.nutritionassistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey (autoGenerate = true) val id: Int = 1,   // 核心：固定为1，单用户模式
    val name: String,        // 用户姓名
    val age: Int,           // 用户年龄
    val gender: String,     // 性别：male(男) / female(女)
    val weight: Float,      // 体重，单位：kg(公斤)
    val height: Float,      // 身高，单位：cm(厘米)
    val goal: String,        // 健康目标
    val blacklist: String = ""
)
