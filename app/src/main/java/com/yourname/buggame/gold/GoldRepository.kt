package com.yourname.buggame.gold

import android.content.Context
import android.util.Xml
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// Цена грамма золота в рублях и дата, на которую она установлена
data class GoldRate(val pricePerGram: Double, val date: String)

class GoldRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("gold_rate", Context.MODE_PRIVATE)

    private val api: GoldApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android) BugGame")
                        .build()
                )
            }
            .build()
        Retrofit.Builder()
            .baseUrl("https://www.cbr.ru/")
            .client(client)
            .build()
            .create(GoldApi::class.java)
    }

    // Последний сохранённый курс (работает без интернета)
    fun cached(): GoldRate? {
        val price = prefs.getString(KEY_PRICE, null)?.toDoubleOrNull() ?: return null
        return GoldRate(price, prefs.getString(KEY_DATE, "") ?: "")
    }

    // Загружает свежий курс с сайта ЦБ. Запрос синхронный: вызывать только из фонового потока.
    // Если загрузить не удалось, возвращает последний сохранённый курс.
    fun refreshBlocking(): GoldRate? {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val day = Calendar.getInstance()
            val to = format.format(day.time)
            // берём диапазон за 10 дней, чтобы пережить выходные и праздники
            day.add(Calendar.DAY_OF_YEAR, -10)
            val from = format.format(day.time)

            val response = api.metals(from, to).execute()
            val body = response.body()
            if (!response.isSuccessful || body == null) return cached()

            val rate = body.byteStream().use { parseGold(it) } ?: return cached()
            prefs.edit()
                .putString(KEY_PRICE, rate.pricePerGram.toString())
                .putString(KEY_DATE, rate.date)
                .apply()
            rate
        } catch (e: Exception) {
            cached()
        }
    }

    private companion object {
        const val KEY_PRICE = "price"
        const val KEY_DATE = "date"
    }
}

// Ответ ЦБ: <Record Date="дд.мм.гггг" Code="1"><Buy>..</Buy><Sell>..</Sell></Record>
// Code="1" — золото. Берём самую свежую запись по золоту.
private fun parseGold(stream: InputStream): GoldRate? {
    val parser = Xml.newPullParser()
    parser.setInput(stream, null) // кодировку (windows-1251) парсер берёт из заголовка XML

    var best: GoldRate? = null
    var bestKey = 0
    var date: String? = null
    var code: String? = null
    var buy: String? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            when (parser.name) {
                "Record" -> {
                    date = parser.getAttributeValue(null, "Date")
                    code = parser.getAttributeValue(null, "Code")
                    buy = null
                }
                "Buy" -> buy = parser.nextText()
            }
        } else if (event == XmlPullParser.END_TAG && parser.name == "Record") {
            // в числе запятая вместо точки и возможны пробелы-разделители тысяч
            val price = buy
                ?.filter { it.isDigit() || it == ',' || it == '.' }
                ?.replace(',', '.')
                ?.toDoubleOrNull()
            val key = dateKey(date)
            if (code == "1" && price != null && key > bestKey) {
                best = GoldRate(price, date ?: "")
                bestKey = key
            }
        }
        event = parser.next()
    }
    return best
}

// "дд.мм.гггг" -> число ггггммдд, чтобы сравнивать даты
private fun dateKey(date: String?): Int {
    val parts = date?.split(".") ?: return 0
    if (parts.size != 3) return 0
    val day = parts[0].toIntOrNull() ?: return 0
    val month = parts[1].toIntOrNull() ?: return 0
    val year = parts[2].toIntOrNull() ?: return 0
    return year * 10000 + month * 100 + day
}
