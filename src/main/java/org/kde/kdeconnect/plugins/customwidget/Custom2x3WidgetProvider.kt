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
import android.util.Log
import android.widget.RemoteViews
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.plugins.lockdevice.LockDevicePlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadActivity
import org.kde.kdeconnect.plugins.share.SendFileActivity
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

const val ACTION_CUSTOM_LOCK_PC = "org.kde.kdeconnect.custom2x3.ACTION_LOCK_PC"
const val EXTRA_CUSTOM_DEVICE_ID = "org.kde.kdeconnect.custom2x3.DEVICE_ID"

class Custom2x3WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Fix: Re-register callback on update to handle background process death
        KdeConnect.getInstance().addDeviceListChangedCallback("Custom2x3Widget") {
            forceRefreshWidgets(context)
        }
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            deleteCustomWidgetDeviceIdPref(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Custom2x3Widget", "onReceive: ${intent.action}")

        if (intent.action == ACTION_CUSTOM_LOCK_PC) {
            val pendingResult = goAsync()
            Thread {
                try {
                    val targetDevice = intent.getStringExtra(EXTRA_CUSTOM_DEVICE_ID)
                    val device = KdeConnect.getInstance().getDevice(targetDevice)
                    if (device != null && device.isReachable) {
                        val plugin = device.getPlugin(LockDevicePlugin::class.java)
                        val isCurrentlyLocked = plugin?.isLocked ?: false

                        // Toggle the state (if locked, try to unlock; if unlocked, try to lock)
                        val packet = NetworkPacket("kdeconnect.lock.request")
                        packet.set("setLocked", !isCurrentlyLocked)
                        device.sendPacket(packet)

                        // Fallback: Linux DEs often block the native unlock API.
                        // If the user has a custom run command for unlocking/locking, use it too!
                        val rcPlugin = device.getPlugin(org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin::class.java)
                        if (rcPlugin != null) {
                            val targetName = if (isCurrentlyLocked) "Unlock" else "Lock"
                            val fallbackCommand = rcPlugin.commandItems.find {
                                it.name.contains(targetName, ignoreCase = true) &&
                                it.name.contains("Screen", ignoreCase = true)
                            }
                            if (fallbackCommand != null) {
                                rcPlugin.runCommand(fallbackCommand.key)
                            }
                        }
                    } else {
                        Log.w("Custom2x3Widget", "Device not available")
                    }
                } catch (ex: Exception) {
                    Log.e("Custom2x3Widget", "Error executing lock-screen command", ex)
                } finally {
                    pendingResult.finish()
                }
            }.start()
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

    val configuredDeviceId = loadCustomWidgetDeviceIdPref(context, appWidgetId)
    val device: Device? = if (configuredDeviceId != null) {
        KdeConnect.getInstance().getDevice(configuredDeviceId)?.takeIf { it.isReachable }
    } else {
        // Fallback for widgets created before the config activity was added
        KdeConnect.getInstance().devices.values.firstOrNull { it.isReachable }
    }

    if (device != null) {
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

        val lockPlugin = device.getPlugin(LockDevicePlugin::class.java)
        if (lockPlugin != null && lockPlugin.isLocked) {
            views.setTextViewText(R.id.txt_lock_btn, "Unlock PC")
            views.setTextViewText(R.id.txt_device_name, "🔒 ${device.name}")
            views.setImageViewResource(R.id.img_lock_icon, R.drawable.ic_lock)
        } else {
            views.setTextViewText(R.id.txt_lock_btn, "Lock PC")
            views.setTextViewText(R.id.txt_device_name, device.name)
            views.setImageViewResource(R.id.img_lock_icon, R.drawable.ic_lock_open)
        }

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
        views.setTextViewText(R.id.txt_lock_btn, "Lock PC")
        views.setImageViewResource(R.id.img_lock_icon, R.drawable.ic_lock)

        // Fix: Wire up buttons to open KDE Connect MainActivity when offline
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId * 10 + 4,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_view_monitor, mainPendingIntent)
        views.setOnClickPendingIntent(R.id.btn_lock, mainPendingIntent)
        views.setOnClickPendingIntent(R.id.btn_share_files, mainPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
