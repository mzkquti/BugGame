package com.yourname.buggame.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ResultDao {

    @Insert
    suspend fun insert(result: GameResult): Long

    @Query(
        """
        SELECT p.fullName AS fullName, p.course AS course, p.difficulty AS difficulty,
               r.score AS score, r.hits AS hits, r.misses AS misses, r.playedAt AS playedAt
        FROM results r
        INNER JOIN players p ON p.id = r.playerId
        ORDER BY r.score DESC, r.playedAt ASC
        LIMIT 50
        """
    )
    suspend fun getTopRecords(): List<RecordRow>
}
