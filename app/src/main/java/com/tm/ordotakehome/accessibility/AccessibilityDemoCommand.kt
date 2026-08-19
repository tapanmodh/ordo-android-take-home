package com.tm.ordotakehome.accessibility

import android.content.Context
import androidx.core.content.edit

object AccessibilityDemoCommand {

    fun requestBluetoothNavigation(context: Context) {
        preferences(context).edit {
            putBoolean(KEY_NAVIGATE_TO_BLUETOOTH, true)
        }
    }

    fun shouldNavigateToBluetooth(context: Context): Boolean {
        return preferences(context).getBoolean(KEY_NAVIGATE_TO_BLUETOOTH, false)
    }

    fun clearBluetoothNavigation(context: Context) {
        preferences(context).edit {
            remove(KEY_NAVIGATE_TO_BLUETOOTH)
        }
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private const val PREFS_NAME = "accessibility_demo"

    private const val KEY_NAVIGATE_TO_BLUETOOTH = "navigate_to_bluetooth"
}