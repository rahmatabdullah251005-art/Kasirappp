package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrencyFormatter {
    private val indonesiaLocale: Locale = Locale.Builder()
        .setLanguage("id")
        .setRegion("ID")
        .build()

    private val rupiahFormat = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(indonesiaLocale).apply {
            maximumFractionDigits = 0
        }
    }

    fun formatRupiah(amount: Long): String {
        val prefix = if (amount < 0) "-Rp " else "Rp "
        val formatter = rupiahFormat.get() ?: NumberFormat.getNumberInstance(indonesiaLocale)
        return prefix + formatter.format(kotlin.math.abs(amount))
    }

    fun formatCompactRupiah(amount: Long): String {
        val abs = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 1_000_000_000L -> "${sign}Rp ${String.format(indonesiaLocale, "%.1fM", abs / 1_000_000_000.0)}"
            abs >= 1_000_000L -> "${sign}Rp ${String.format(indonesiaLocale, "%.1fJt", abs / 1_000_000.0)}"
            abs >= 1_000L -> "${sign}Rp ${abs / 1_000}rb"
            else -> formatRupiah(amount)
        }
    }
}

object DateUtils {
    private val indonesiaLocale: Locale = Locale.Builder()
        .setLanguage("id")
        .setRegion("ID")
        .build()

    private fun threadLocalDateFormat(pattern: String, locale: Locale): ThreadLocal<SimpleDateFormat> =
        ThreadLocal.withInitial { SimpleDateFormat(pattern, locale) }

    private val keyFormatter = threadLocalDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormatter = threadLocalDateFormat("EEEE, dd MMMM yyyy", indonesiaLocale)
    private val shortDateFormatter = threadLocalDateFormat("dd MMM yyyy", indonesiaLocale)
    private val shortDayFormatter = threadLocalDateFormat("dd/MM", indonesiaLocale)
    private val timeFormatter = threadLocalDateFormat("HH:mm", indonesiaLocale)
    private val dateTimeFormatter = threadLocalDateFormat("dd MMM yyyy, HH:mm", indonesiaLocale)
    private val invoiceDateFormatter = threadLocalDateFormat("yyyyMMdd", Locale.US)

    private fun parseDateKey(dateKey: String): Date? = keyFormatter.get()?.parse(dateKey)

    fun todayKey(): String = keyFormatter.get()?.format(Date()).orEmpty()

    fun dateKeyForOffset(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return keyFormatter.get()?.format(cal.time).orEmpty()
    }

    fun dateKeyFromMillis(millis: Long): String =
        keyFormatter.get()?.format(Date(millis)).orEmpty()

    fun formatDisplayDate(dateKey: String): String {
        return try {
            val date = parseDateKey(dateKey) ?: return dateKey
            displayDateFormatter.get()?.format(date) ?: dateKey
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatShortDate(dateKey: String): String {
        return try {
            val date = parseDateKey(dateKey) ?: return dateKey
            shortDateFormatter.get()?.format(date) ?: dateKey
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatShortDay(dateKey: String): String {
        return try {
            val date = parseDateKey(dateKey) ?: return dateKey
            shortDayFormatter.get()?.format(date) ?: dateKey
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatTime(millis: Long): String =
        timeFormatter.get()?.format(Date(millis)).orEmpty()

    fun formatDateTime(millis: Long): String =
        dateTimeFormatter.get()?.format(Date(millis)).orEmpty()

    fun invoiceDatePrefix(millis: Long = System.currentTimeMillis()): String =
        invoiceDateFormatter.get()?.format(Date(millis)).orEmpty()
}
