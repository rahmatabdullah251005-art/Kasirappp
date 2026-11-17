package com.example.util

import android.content.Context

class QrisConfigManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStoreQrisPayload(): String {
        return prefs.getString(KEY_QRIS_PAYLOAD, null) ?: DEFAULT_DEMO_QRIS
    }

    fun isCustomStoreQrisConfigured(): Boolean {
        return !prefs.getString(KEY_QRIS_PAYLOAD, null).isNullOrBlank()
    }

    fun saveStoreQrisPayload(payload: String) {
        prefs.edit().putString(KEY_QRIS_PAYLOAD, payload.trim()).apply()
    }

    fun resetToDefault() {
        prefs.edit().remove(KEY_QRIS_PAYLOAD).apply()
    }

    companion object {
        private const val PREFS_NAME = "um_tazkiyah_qris_prefs"
        private const val KEY_QRIS_PAYLOAD = "store_qris_payload"
        // Standard EMVCo-style QRIS placeholder payload for Tajir until Owner scans store QRIS
        const val DEFAULT_DEMO_QRIS =
            "00020101021126570011ID.CO.QRIS.WWW01189360091100210000010215TAJIR00000000010303UMI51440014ID.CO.QRIS.WWW0215ID10200000000010303UMI5204599953033605802ID5905TAJIR6007JAKARTA6105123406304A1B2"
    }
}
