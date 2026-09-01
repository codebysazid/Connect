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
import android.os.Build
import android.provider.AlarmClock
import android.util.SizeF
import android.widget.RemoteViews
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

class AestheticClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateClockWidget(context, appWidgetManager, appWidgetId)
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

    // Tapping clock opens system Clock / Alarm
    val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val targetIntent = if (clockIntent.resolveActivity(context.packageManager) != null) {
        clockIntent
    } else {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId * 100 + 42,
        targetIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    compactViews.setOnClickPendingIntent(R.id.clock_widget_root, pendingIntent)
    expandedViews.setOnClickPendingIntent(R.id.clock_widget_root_expanded, pendingIntent)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val viewMapping = mapOf(
            SizeF(100f, 100f) to compactViews,
            SizeF(180f, 120f) to expandedViews
        )
        appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
    } else {
        appWidgetManager.updateAppWidget(appWidgetId, compactViews)
    }
}
