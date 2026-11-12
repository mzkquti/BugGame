package com.yourname.buggame

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yourname.buggame.gold.GoldRate
import com.yourname.buggame.gold.GoldRepository
import java.util.Locale
import kotlin.concurrent.thread

// Виджет в форме золотого шара: показывает курс золота ЦБ (₽ за грамм).
// Обновляется системой раз в полчаса и по нажатию на шар.
class GoldWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GoldWidgetProvider::class.java))
            refresh(context, manager, ids)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val repo = GoldRepository(context)
        // сразу показываем сохранённый курс, потом подгружаем свежий
        draw(context, manager, ids, repo.cached(), loading = true)

        // загрузка идёт в фоне, а receiver держим живым до её окончания
        val pending = goAsync()
        thread {
            try {
                draw(context, manager, ids, repo.refreshBlocking(), loading = false)
            } finally {
                pending.finish()
            }
        }
    }

    private fun draw(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        rate: GoldRate?,
        loading: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_gold)
        views.setTextViewText(
            R.id.tvWidgetPrice,
            when {
                rate != null -> String.format(Locale("ru", "RU"), "%,.0f", rate.pricePerGram)
                loading -> "…"
                else -> "нет данных"
            }
        )
        views.setTextViewText(R.id.tvWidgetDate, rate?.date ?: "")

        val intent = Intent(context, GoldWidgetProvider::class.java).setAction(ACTION_REFRESH)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        manager.updateAppWidget(ids, views)
    }

    private companion object {
        const val ACTION_REFRESH = "com.yourname.buggame.ACTION_REFRESH_GOLD"
    }
}
