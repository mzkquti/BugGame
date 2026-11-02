package com.yourname.buggame.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlayerDao {

    @Insert
    suspend fun insert(player: Player): Long

    @Query("SELECT * FROM players ORDER BY fullName")
    suspend fun getAll(): List<Player>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getById(id: Long): Player?
}
