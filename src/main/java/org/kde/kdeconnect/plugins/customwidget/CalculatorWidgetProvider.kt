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
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

const val ACTION_CALC_KEY = "org.kde.kdeconnect.calc.ACTION_KEY"
const val EXTRA_CALC_KEY = "key"

class CalculatorWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateCalcWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences("calculator_widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove("expr_$id")
            editor.remove("curr_$id")
            editor.remove("first_$id")
            editor.remove("op_$id")
            editor.remove("done_$id")
        }
        editor.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CALC_KEY) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val key = intent.getStringExtra(EXTRA_CALC_KEY)

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && key != null) {
                // 1. Haptic feedback / Vibration on tap
                triggerVibration(context)

                // 2. Process Calculation
                processKey(context, appWidgetId, key)

                // 3. Update Widget Display
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateCalcWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    return
                }
            }

            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25)
                }
            }
        } catch (e: Exception) {
            Log.w("CalculatorWidget", "Vibration failed", e)
        }
    }

    private fun processKey(context: Context, appWidgetId: Int, key: String) {
        val prefs = context.getSharedPreferences("calculator_widget_prefs", Context.MODE_PRIVATE)
        var expression = prefs.getString("expr_$appWidgetId", "") ?: ""
        var currentNumber = prefs.getString("curr_$appWidgetId", "0") ?: "0"
        var firstOperandStr = prefs.getString("first_$appWidgetId", null)
        var pendingOp = prefs.getString("op_$appWidgetId", null)
        var isResultCalculated = prefs.getBoolean("done_$appWidgetId", false)

        when (key) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                if (isResultCalculated || currentNumber == "Error" || currentNumber == "Can't divide by 0") {
                    currentNumber = key
                    expression = ""
                    firstOperandStr = null
                    pendingOp = null
                    isResultCalculated = false
                } else if (currentNumber == "0") {
                    currentNumber = key
                } else {
                    if (currentNumber.length < 15) {
                        currentNumber += key
                    }
                }
            }
            "." -> {
                if (isResultCalculated || currentNumber == "Error" || currentNumber == "Can't divide by 0") {
                    currentNumber = "0."
                    expression = ""
                    firstOperandStr = null
                    pendingOp = null
                    isResultCalculated = false
                } else if (!currentNumber.contains(".")) {
                    currentNumber = if (currentNumber.isEmpty()) "0." else "$currentNumber."
                }
            }
            "+", "-", "*", "/" -> {
                val displayOp = when (key) {
                    "+" -> "+"
                    "-" -> "−"
                    "*" -> "×"
                    "/" -> "÷"
                    else -> key
                }

                if (currentNumber == "Error" || currentNumber == "Can't divide by 0") {
                    currentNumber = "0"
                    expression = ""
                    firstOperandStr = null
                    pendingOp = null
                    isResultCalculated = false
                }

                if (isResultCalculated) {
                    firstOperandStr = currentNumber
                    pendingOp = key
                    expression = "$currentNumber $displayOp"
                    currentNumber = ""
                    isResultCalculated = false
                } else if (currentNumber.isNotEmpty()) {
                    if (firstOperandStr == null) {
                        firstOperandStr = currentNumber
                        pendingOp = key
                        expression = "$currentNumber $displayOp"
                        currentNumber = ""
                    } else if (pendingOp != null) {
                        // Chained calculation
                        val res = calculate(firstOperandStr, currentNumber, pendingOp)
                        if (res == "Error" || res == "Can't divide by 0") {
                            currentNumber = res
                            expression = ""
                            firstOperandStr = null
                            pendingOp = null
                            isResultCalculated = true
                        } else {
                            firstOperandStr = res
                            pendingOp = key
                            expression = "$res $displayOp"
                            currentNumber = ""
                        }
                    }
                } else if (firstOperandStr != null) {
                    // Changing operator
                    pendingOp = key
                    expression = "$firstOperandStr $displayOp"
                }
            }
            "=" -> {
                if (firstOperandStr != null && pendingOp != null && currentNumber.isNotEmpty()) {
                    val displayOp = when (pendingOp) {
                        "+" -> "+"
                        "-" -> "−"
                        "*" -> "×"
                        "/" -> "÷"
                        else -> pendingOp
                    }
                    val res = calculate(firstOperandStr, currentNumber, pendingOp)
                    expression = "$firstOperandStr $displayOp $currentNumber ="
                    currentNumber = res
                    firstOperandStr = null
                    pendingOp = null
                    isResultCalculated = true
                }
            }
            "AC" -> {
                expression = ""
                currentNumber = "0"
                firstOperandStr = null
                pendingOp = null
                isResultCalculated = false
            }
            "BACKSPACE" -> {
                if (isResultCalculated || currentNumber == "Error" || currentNumber == "Can't divide by 0") {
                    expression = ""
                    currentNumber = "0"
                    firstOperandStr = null
                    pendingOp = null
                    isResultCalculated = false
                } else if (currentNumber.isNotEmpty() && currentNumber != "0") {
                    currentNumber = currentNumber.dropLast(1)
                    if (currentNumber.isEmpty() || currentNumber == "-") {
                        currentNumber = "0"
                    }
                }
            }
            "+/-" -> {
                if (currentNumber != "0" && currentNumber != "Error" && currentNumber != "Can't divide by 0" && currentNumber.isNotEmpty()) {
                    currentNumber = if (currentNumber.startsWith("-")) {
                        currentNumber.substring(1)
                    } else {
                        "-$currentNumber"
                    }
                }
            }
            "%" -> {
                if (currentNumber.isNotEmpty() && currentNumber != "Error" && currentNumber != "Can't divide by 0") {
                    try {
                        val numBd = BigDecimal(currentNumber)
                        val pct = numBd.divide(BigDecimal("100"), MathContext.DECIMAL64)
                        currentNumber = formatBigDecimal(pct)
                    } catch (e: Exception) {
                        currentNumber = "Error"
                    }
                }
            }
        }

        prefs.edit().apply {
            putString("expr_$appWidgetId", expression)
            putString("curr_$appWidgetId", currentNumber)
            putString("first_$appWidgetId", firstOperandStr)
            putString("op_$appWidgetId", pendingOp)
            putBoolean("done_$appWidgetId", isResultCalculated)
            apply()
        }
    }

    private fun calculate(op1: String, op2: String, operation: String): String {
        return try {
            val num1 = BigDecimal(op1)
            val num2 = BigDecimal(op2)
            val result = when (operation) {
                "+" -> num1.add(num2, MathContext.DECIMAL64)
                "-" -> num1.subtract(num2, MathContext.DECIMAL64)
                "*" -> num1.multiply(num2, MathContext.DECIMAL64)
                "/" -> {
                    if (num2.compareTo(BigDecimal.ZERO) == 0) {
                        return "Can't divide by 0"
                    }
                    num1.divide(num2, 10, RoundingMode.HALF_UP)
                }
                else -> num1
            }
            formatBigDecimal(result)
        } catch (e: Exception) {
            "Error"
        }
    }

    private fun formatBigDecimal(bd: BigDecimal): String {
        val stripped = bd.stripTrailingZeros()
        val plain = stripped.toPlainString()
        return if (plain == "-0") "0" else plain
    }
}

private fun bindCalculatorViews(
    context: Context,
    views: RemoteViews,
    appWidgetId: Int,
    expression: String,
    displayCurr: String
) {
    views.setTextViewText(R.id.txt_calc_expression, expression)
    views.setTextViewText(R.id.txt_calc_result, displayCurr)

    fun setKeyClick(viewId: Int, key: String, reqCode: Int) {
        val intent = Intent(context, CalculatorWidgetProvider::class.java).apply {
            action = ACTION_CALC_KEY
            putExtra(EXTRA_CALC_KEY, key)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("calc://$appWidgetId/$key")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId * 100 + reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }

    // Row 1
    setKeyClick(R.id.btn_calc_ac, "AC", 1)
    setKeyClick(R.id.btn_calc_pm, "+/-", 2)
    setKeyClick(R.id.btn_calc_percent, "%", 3)
    setKeyClick(R.id.btn_calc_div, "/", 4)

    // Row 2
    setKeyClick(R.id.btn_calc_7, "7", 5)
    setKeyClick(R.id.btn_calc_8, "8", 6)
    setKeyClick(R.id.btn_calc_9, "9", 7)
    setKeyClick(R.id.btn_calc_mul, "*", 8)

    // Row 3
    setKeyClick(R.id.btn_calc_4, "4", 9)
    setKeyClick(R.id.btn_calc_5, "5", 10)
    setKeyClick(R.id.btn_calc_6, "6", 11)
    setKeyClick(R.id.btn_calc_sub, "-", 12)

    // Row 4
    setKeyClick(R.id.btn_calc_1, "1", 13)
    setKeyClick(R.id.btn_calc_2, "2", 14)
    setKeyClick(R.id.btn_calc_3, "3", 15)
    setKeyClick(R.id.btn_calc_add, "+", 16)

    // Row 5
    setKeyClick(R.id.btn_calc_0, "0", 17)
    setKeyClick(R.id.btn_calc_dot, ".", 18)
    setKeyClick(R.id.btn_calc_backspace, "BACKSPACE", 19)
    setKeyClick(R.id.btn_calc_eq, "=", 20)
}

internal fun updateCalcWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val compactViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_calculator)
    val expandedViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_calculator_expanded)

    val prefs = context.getSharedPreferences("calculator_widget_prefs", Context.MODE_PRIVATE)
    val expression = prefs.getString("expr_$appWidgetId", "") ?: ""
    val currentNumber = prefs.getString("curr_$appWidgetId", "0") ?: "0"
    val displayCurr = if (currentNumber.isEmpty()) "0" else currentNumber

    bindCalculatorViews(context, compactViews, appWidgetId, expression, displayCurr)
    bindCalculatorViews(context, expandedViews, appWidgetId, expression, displayCurr)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val viewMapping = mapOf(
            SizeF(100f, 100f) to compactViews,
            SizeF(180f, 180f) to expandedViews
        )
        appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
    } else {
        appWidgetManager.updateAppWidget(appWidgetId, compactViews)
    }
}
