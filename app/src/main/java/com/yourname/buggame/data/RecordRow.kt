package com.yourname.buggame.data

// Строка таблицы рекордов: результат раунда вместе с данными игрока
data class RecordRow(
    val fullName: String,
    val course: Int,
    val difficulty: Int,
    val score: Int,
    val hits: Int,
    val misses: Int,
    val playedAt: Long
)
