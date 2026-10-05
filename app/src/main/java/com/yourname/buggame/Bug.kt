package com.yourname.buggame

// Тип насекомого: как выглядит, какого размера, насколько быстрое и сколько очков даёт
data class BugType(
    val emoji: String,
    val sizeDp: Float,
    val speedMultiplier: Float,
    val points: Int
)

// Одно насекомое на экране
class Bug(
    val type: BugType,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val sizePx: Float
)