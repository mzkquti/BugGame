package com.yourname.buggame.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val gender: String,
    val course: Int,
    val difficulty: Int,
    val birthYear: Int,
    val birthMonth: Int,
    val birthDay: Int,
    val zodiacSign: String
)
