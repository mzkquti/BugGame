package com.yourname.buggame

import android.content.Context

class GameSettings(context: Context) {

    private val prefs = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE)

    var speed: Int
        get() = prefs.getInt("speed", 5)
        set(value) = prefs.edit().putInt("speed", value).apply()

    var maxBugs: Int
        get() = prefs.getInt("max_bugs", 10)
        set(value) = prefs.edit().putInt("max_bugs", value).apply()

    var bonusIntervalSec: Int
        get() = prefs.getInt("bonus_interval", 15)
        set(value) = prefs.edit().putInt("bonus_interval", value).apply()

    var roundSeconds: Int
        get() = prefs.getInt("round_seconds", 60)
        set(value) = prefs.edit().putInt("round_seconds", value).apply()
}