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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yourname.buggame.data.AppDatabase
import com.yourname.buggame.data.Player
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Calendar

class RegistrationFragment : Fragment(R.layout.fragment_registration) {

    private var selectedYear = 0
    private var selectedMonth = 0
    private var selectedDay = 0

    private val settings: GameSettings by inject()
    private val db: AppDatabase by inject()
    private lateinit var tvCurrentPlayer: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCurrentPlayer = view.findViewById(R.id.tvCurrentPlayer)
        val btnChoosePlayer = view.findViewById<Button>(R.id.btnChoosePlayer)

        btnChoosePlayer.setOnClickListener { choosePlayer() }

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

            // сохраняем игрока в базу и делаем его текущим
            viewLifecycleOwner.lifecycleScope.launch {
                val id = db.playerDao().insert(
                    Player(
                        fullName = player.fullName,
                        gender = player.gender,
                        course = player.course,
                        difficulty = player.difficulty,
                        birthYear = player.birthYear,
                        birthMonth = player.birthMonth,
                        birthDay = player.birthDay,
                        zodiacSign = player.zodiacSign
                    )
                )
                settings.currentPlayerId = id
                refreshCurrentPlayer()
                Toast.makeText(requireContext(), "Игрок сохранён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentPlayer()
    }

    private fun refreshCurrentPlayer() {
        viewLifecycleOwner.lifecycleScope.launch {
            val id = settings.currentPlayerId
            val player = if (id > 0) db.playerDao().getById(id) else null
            tvCurrentPlayer.text = if (player != null) {
                "Текущий игрок: ${player.fullName}"
            } else {
                "Текущий игрок не выбран"
            }
        }
    }

    private fun choosePlayer() {
        viewLifecycleOwner.lifecycleScope.launch {
            val players = db.playerDao().getAll()
            if (players.isEmpty()) {
                Toast.makeText(requireContext(), "Пока нет зарегистрированных игроков", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = players.map { "${it.fullName} (курс ${it.course})" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Выберите игрока")
                .setItems(names) { _, which ->
                    settings.currentPlayerId = players[which].id
                    refreshCurrentPlayer()
                }
                .setNegativeButton("Отмена", null)
                .show()
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