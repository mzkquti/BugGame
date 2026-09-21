package com.yourname.buggame

import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import java.util.Calendar

data class PlayerData(
    val fullName: String,
    val gender: String,
    val course: Int,
    val difficulty: Int,
    val birthYear: Int,
    val birthMonth: Int,
    val birthDay: Int,
    val zodiacSign: String
)

class MainActivity : ComponentActivity() {

    private var selectedYear = 0
    private var selectedMonth = 0
    private var selectedDay = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etFullName = findViewById<EditText>(R.id.etFullName)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val spinnerCourse = findViewById<Spinner>(R.id.spinnerCourse)
        val seekBarDifficulty = findViewById<SeekBar>(R.id.seekBarDifficulty)
        val calendarView = findViewById<CalendarView>(R.id.calendarViewBirthDate)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val imgZodiac = findViewById<ImageView>(R.id.imgZodiac)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        // Курс — выпадающий список 1-6
        val courses = (1..6).map { "Курс $it" }
        spinnerCourse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courses)

        // Дата рождения по умолчанию — сегодня
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
            val fullName = etFullName.text.toString()
            val genderId = rgGender.checkedRadioButtonId
            val gender = if (genderId != -1) findViewById<RadioButton>(genderId).text.toString() else "Не указан"
            val course = spinnerCourse.selectedItemPosition + 1
            val difficulty = seekBarDifficulty.progress
            val zodiac = calculateZodiac(selectedMonth + 1, selectedDay)

            val player = PlayerData(
                fullName, gender, course, difficulty,
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

    private fun calculateZodiac(month: Int, day: Int): String {
        return when (month) {
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
    }

    private fun getZodiacImageRes(zodiac: String): Int {
        // Временно используем стандартную иконку — позже заменим на свои изображения знаков зодиака
        return android.R.drawable.ic_menu_info_details
    }
}