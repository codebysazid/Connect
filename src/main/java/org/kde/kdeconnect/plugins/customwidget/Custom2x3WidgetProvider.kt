/*
 * SPDX-FileCopyrightText: 2026 KDE Connect Contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.customwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadActivity
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.plugins.share.SendFileActivity
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

const val ACTION_CUSTOM_LOCK_PC = "org.kde.kdeconnect.custom2x3.ACTION_LOCK_PC"
const val EXTRA_CUSTOM_DEVICE_ID = "org.kde.kdeconnect.custom2x3.DEVICE_ID"

class Custom2x3WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        KdeConnect.getInstance().addDeviceListChangedCallback("Custom2x3Widget") {
            forceRefreshWidgets(context)
        }
    }

    override fun onDisabled(context: Context) {
        KdeConnect.getInstance().removeDeviceListChangedCallback("Custom2x3Widget")
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Custom2x3Widget", "onReceive: ${intent.action}")

        if (intent.action == ACTION_CUSTOM_LOCK_PC) {
            val targetDevice = intent.getStringExtra(EXTRA_CUSTOM_DEVICE_ID)
            val plugin = KdeConnect.getInstance().getDevicePlugin(targetDevice, RunCommandPlugin::class.java)
            if (plugin != null) {
                try {
                    plugin.runCommand("lock-screen")
                } catch (ex: Exception) {
                    Log.e("Custom2x3Widget", "Error executing lock-screen command", ex)
                }
            } else {
                Log.w("Custom2x3Widget", "Device not available or RunCommand plugin disabled")
            }
        } else {
            super.onReceive(context, intent)
        }
    }
}

fun getAllCustomWidgetIds(context: Context): IntArray {
    return AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, Custom2x3WidgetProvider::class.java)
    )
}

fun forceRefreshWidgets(context: Context) {
    val intent = Intent(context, Custom2x3WidgetProvider::class.java)
    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, getAllCustomWidgetIds(context))
    context.sendBroadcast(intent)
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_pc_2x3_custom)

    val device: Device? = KdeConnect.getInstance().devices.values.firstOrNull { it.isReachable }

    if (device != null) {
        views.setTextViewText(R.id.txt_device_name, device.name)

        // 1. Live PC Battery
        val batteryPlugin = device.getPlugin(BatteryPlugin::class.java)
        val batteryInfo = batteryPlugin?.remoteBatteryInfo
        if (batteryInfo != null && batteryInfo.currentCharge >= 0) {
            val chargingIcon = if (batteryInfo.isCharging) "⚡ " else ""
            views.setTextViewText(R.id.txt_battery, "$chargingIcon${batteryInfo.currentCharge}%")
        } else {
            views.setTextViewText(R.id.txt_battery, "--")
        }

        // 2. Button: View (Opens MousePadActivity with deviceId)
        val viewIntent = Intent(context, MousePadActivity::class.java).apply {
            putExtra("deviceId", device.deviceId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId * 10 + 1,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_view_monitor, viewPendingIntent)

        // 3. Button: Lock PC (Broadcast intent)
        val lockIntent = Intent(context, Custom2x3WidgetProvider::class.java).apply {
            action = ACTION_CUSTOM_LOCK_PC
            putExtra(EXTRA_CUSTOM_DEVICE_ID, device.deviceId)
        }
        val lockPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId * 10 + 2,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_lock, lockPendingIntent)

        // 4. Button: Share Files (Opens SendFileActivity with deviceId)
        val shareIntent = Intent(context, SendFileActivity::class.java).apply {
            putExtra("deviceId", device.deviceId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId * 10 + 3,
            shareIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_share_files, sharePendingIntent)
    } else {
        views.setTextViewText(R.id.txt_device_name, "Offline")
        views.setTextViewText(R.id.txt_battery, "--")
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val viewMapping = mapOf(
            SizeF(100f, 120f) to views,
            SizeF(180f, 220f) to views
        )
        appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
    } else {
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
