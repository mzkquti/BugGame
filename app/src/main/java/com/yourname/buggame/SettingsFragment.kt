package com.yourname.buggame

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = GameSettings(requireContext())

        setupSeekBar(view, R.id.sbSpeed, R.id.tvSpeed, "Скорость игры",
            1, 10, settings.speed, "") { settings.speed = it }
        setupSeekBar(view, R.id.sbMaxBugs, R.id.tvMaxBugs, "Максимум тараканов на экране",
            1, 20, settings.maxBugs, "") { settings.maxBugs = it }
        setupSeekBar(view, R.id.sbBonusInterval, R.id.tvBonusInterval, "Интервал появления бонусов",
            5, 60, settings.bonusIntervalSec, "сек") { settings.bonusIntervalSec = it }
        setupSeekBar(view, R.id.sbRoundDuration, R.id.tvRoundDuration, "Длительность раунда",
            10, 120, settings.roundSeconds, "сек") { settings.roundSeconds = it }
    }

    private fun setupSeekBar(
        view: View, seekBarId: Int, labelId: Int,
        title: String, min: Int, max: Int, initial: Int, unit: String,
        onValueChanged: (Int) -> Unit
    ) {
        val seekBar = view.findViewById<SeekBar>(seekBarId)
        val label = view.findViewById<TextView>(labelId)

        fun showValue(value: Int) {
            label.text = "$title: $value $unit".trim()
        }

        // SeekBar всегда начинается с 0, поэтому сдвигаем диапазон на min
        seekBar.max = max - min
        seekBar.progress = initial - min
        showValue(initial)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = min + progress
                showValue(value)
                onValueChanged(value)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }
}