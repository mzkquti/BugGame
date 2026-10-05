package com.yourname.buggame

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.Calendar

class RegistrationFragment : Fragment(R.layout.fragment_registration) {

    private var selectedYear = 0
    private var selectedMonth = 0
    private var selectedDay = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etFullName = view.findViewById<EditText>(R.id.etFullName)
        val rgGender = view.findViewById<RadioGroup>(R.id.rgGender)
        val spinnerCourse = view.findViewById<Spinner>(R.id.spinnerCourse)
        val seekBarDifficulty = view.findViewById<SeekBar>(R.id.seekBarDifficulty)
        val calendarView = view.findViewById<CalendarView>(R.id.calendarViewBirthDate)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)
        val imgZodiac = view.findViewById<ImageView>(R.id.imgZodiac)
        val tvResult = view.findViewById<TextView>(R.id.tvResult)

        val courses = (1..6).map { "Курс $it" }
        spinnerCourse.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, courses
        )

        val today = Calendar.getInstance()
        selectedYear = today.get(Calendar.YEAR)
        selectedMonth = today.get(Calendar.MONTH)
        selectedDay = today.get(Calendar.DAY_OF_MONTH)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedYear = year
            selectedMonth = month
            selectedDay = dayOfMonth
        }

        btnSubmit.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            if (fullName.isEmpty()) {
                etFullName.error = "Введите ФИО"
                etFullName.requestFocus()
                return@setOnClickListener
            }

            val genderId = rgGender.checkedRadioButtonId
            if (genderId == -1) {
                Toast.makeText(requireContext(), "Выберите пол", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val gender = view.findViewById<RadioButton>(genderId).text.toString()

            val zodiac = calculateZodiac(selectedMonth + 1, selectedDay)

            val player = PlayerData(
                fullName, gender,
                spinnerCourse.selectedItemPosition + 1, seekBarDifficulty.progress,
                selectedYear, selectedMonth + 1, selectedDay, zodiac
            )

            tvResult.text = "ФИО: ${player.fullName}\n" +
                    "Пол: ${player.gender}\n" +
                    "Курс: ${player.course}\n" +
                    "Сложность: ${player.difficulty}\n" +
                    "Дата рождения: ${player.birthDay}.${player.birthMonth}.${player.birthYear}\n" +
                    "Знак зодиака: ${player.zodiacSign}"

            imgZodiac.setImageResource(getZodiacImageRes(zodiac))
        }
    }

    private fun calculateZodiac(month: Int, day: Int): String = when (month) {
        1 -> if (day < 20) "Козерог" else "Водолей"
        2 -> if (day < 19) "Водолей" else "Рыбы"
        3 -> if (day < 21) "Рыбы" else "Овен"
        4 -> if (day < 20) "Овен" else "Телец"
        5 -> if (day < 21) "Телец" else "Близнецы"
        6 -> if (day < 21) "Близнецы" else "Рак"
        7 -> if (day < 23) "Рак" else "Лев"
        8 -> if (day < 23) "Лев" else "Дева"
        9 -> if (day < 23) "Дева" else "Весы"
        10 -> if (day < 23) "Весы" else "Скорпион"
        11 -> if (day < 22) "Скорпион" else "Стрелец"
        12 -> if (day < 22) "Стрелец" else "Козерог"
        else -> "Неизвестно"
    }

    private fun getZodiacImageRes(zodiac: String): Int = when (zodiac) {
        "Овен" -> R.drawable.zodiac_aries
        "Телец" -> R.drawable.zodiac_taurus
        "Близнецы" -> R.drawable.zodiac_gemini
        "Рак" -> R.drawable.zodiac_cancer
        "Лев" -> R.drawable.zodiac_leo
        "Дева" -> R.drawable.zodiac_virgo
        "Весы" -> R.drawable.zodiac_libra
        "Скорпион" -> R.drawable.zodiac_scorpio
        "Стрелец" -> R.drawable.zodiac_sagittarius
        "Козерог" -> R.drawable.zodiac_capricorn
        "Водолей" -> R.drawable.zodiac_aquarius
        "Рыбы" -> R.drawable.zodiac_pisces
        else -> android.R.drawable.ic_menu_info_details
    }
}