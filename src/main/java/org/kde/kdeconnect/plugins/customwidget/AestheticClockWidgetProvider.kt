/*
 * SPDX-FileCopyrightText: 2026 KDE Connect Contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.customwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.util.SizeF
import android.widget.RemoteViews
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R
import java.util.Calendar

private val CALENDAR_CELL_IDS = arrayOf(
    intArrayOf(R.id.cal_d00, R.id.cal_d01, R.id.cal_d02, R.id.cal_d03, R.id.cal_d04, R.id.cal_d05, R.id.cal_d06),
    intArrayOf(R.id.cal_d10, R.id.cal_d11, R.id.cal_d12, R.id.cal_d13, R.id.cal_d14, R.id.cal_d15, R.id.cal_d16),
    intArrayOf(R.id.cal_d20, R.id.cal_d21, R.id.cal_d22, R.id.cal_d23, R.id.cal_d24, R.id.cal_d25, R.id.cal_d26),
    intArrayOf(R.id.cal_d30, R.id.cal_d31, R.id.cal_d32, R.id.cal_d33, R.id.cal_d34, R.id.cal_d35, R.id.cal_d36),
    intArrayOf(R.id.cal_d40, R.id.cal_d41, R.id.cal_d42, R.id.cal_d43, R.id.cal_d44, R.id.cal_d45, R.id.cal_d46),
    intArrayOf(R.id.cal_d50, R.id.cal_d51, R.id.cal_d52, R.id.cal_d53, R.id.cal_d54, R.id.cal_d55, R.id.cal_d56)
)

class AestheticClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateClockWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, AestheticClockWidgetProvider::class.java)
            )
            for (appWidgetId in appWidgetIds) {
                updateClockWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}

internal fun updateClockWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val compactViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_aesthetic_clock)
    val expandedViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_aesthetic_clock_expanded)

    // 1. Setup Click Intents
    // Clock section -> open Clock / Alarm app
    val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val targetClockIntent = if (clockIntent.resolveActivity(context.packageManager) != null) {
        clockIntent
    } else {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    val clockPendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId * 100 + 42,
        targetClockIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Calendar section -> open Calendar app
    val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val targetCalIntent = if (calendarIntent.resolveActivity(context.packageManager) != null) {
        calendarIntent
    } else {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALENDAR)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    val calPendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId * 100 + 43,
        targetCalIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    compactViews.setOnClickPendingIntent(R.id.layout_clock_section, clockPendingIntent)
    compactViews.setOnClickPendingIntent(R.id.layout_calendar_section, calPendingIntent)

    expandedViews.setOnClickPendingIntent(R.id.layout_clock_section_expanded, clockPendingIntent)
    expandedViews.setOnClickPendingIntent(R.id.layout_calendar_section_expanded, calPendingIntent)

    // 2. Populate Full Monthly Calendar Grid
    populateCalendar(compactViews)
    populateCalendar(expandedViews)

    // 3. Update Widget with Multi-Size Support
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val viewMapping = mapOf(
            SizeF(100f, 100f) to compactViews,
            SizeF(180f, 140f) to expandedViews
        )
        appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
    } else {
        appWidgetManager.updateAppWidget(appWidgetId, compactViews)
    }
}

private fun populateCalendar(views: RemoteViews) {
    val now = Calendar.getInstance()
    val todayDay = now.get(Calendar.DAY_OF_MONTH)

    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, etc.
    val daysInCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val prevCal = (cal.clone() as Calendar).apply {
        add(Calendar.MONTH, -1)
    }
    val daysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val startOffset = firstDayOfWeek - 1 // 0 for Sunday start

    var cellIndex = 0
    for (row in 0 until 6) {
        for (col in 0 until 7) {
            val cellId = CALENDAR_CELL_IDS[row][col]
            if (cellIndex < startOffset) {
                // Previous month's trailing days (dimmed)
                val dayNum = daysInPrevMonth - startOffset + cellIndex + 1
                views.setTextViewText(cellId, dayNum.toString())
                views.setTextColor(cellId, Color.parseColor("#44FFFFFF"))
                views.setInt(cellId, "setBackgroundResource", 0)
            } else if (cellIndex < startOffset + daysInCurrentMonth) {
                // Current month's days
                val dayNum = cellIndex - startOffset + 1
                views.setTextViewText(cellId, dayNum.toString())
                if (dayNum == todayDay) {
                    // Today's prominent soft-blue pill badge!
                    views.setInt(cellId, "setBackgroundResource", R.drawable.cal_today_badge)
                    views.setTextColor(cellId, Color.parseColor("#101C2E"))
                } else {
                    views.setInt(cellId, "setBackgroundResource", 0)
                    views.setTextColor(cellId, Color.parseColor("#FFFFFF"))
                }
            } else {
                // Next month's leading days (dimmed)
                val dayNum = cellIndex - startOffset - daysInCurrentMonth + 1
                views.setTextViewText(cellId, dayNum.toString())
                views.setTextColor(cellId, Color.parseColor("#44FFFFFF"))
                views.setInt(cellId, "setBackgroundResource", 0)
            }
            cellIndex++
        }
    }
}
