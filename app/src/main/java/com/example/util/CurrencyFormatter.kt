package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrencyFormatter {
    private val indonesiaLocale = Locale("id", "ID")
    private val rupiahFormat = NumberFormat.getNumberInstance(indonesiaLocale).apply {
        maximumFractionDigits = 0
    }

    fun formatRupiah(amount: Long): String {
        val prefix = if (amount < 0) "-Rp " else "Rp "
        return prefix + rupiahFormat.format(kotlin.math.abs(amount))
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
    private val indonesiaLocale = Locale("id", "ID")
    private val keyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormatter = SimpleDateFormat("EEEE, dd MMMM yyyy", indonesiaLocale)
    private val shortDateFormatter = SimpleDateFormat("dd MMM yyyy", indonesiaLocale)
    private val shortDayFormatter = SimpleDateFormat("dd/MM", indonesiaLocale)
    private val timeFormatter = SimpleDateFormat("HH:mm", indonesiaLocale)
    private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", indonesiaLocale)
    private val invoiceDateFormatter = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun todayKey(): String = keyFormatter.format(Date())

    fun dateKeyForOffset(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return keyFormatter.format(cal.time)
    }

    fun dateKeyFromMillis(millis: Long): String = keyFormatter.format(Date(millis))

    fun formatDisplayDate(dateKey: String): String {
        return try {
            val date = keyFormatter.parse(dateKey) ?: return dateKey
            displayDateFormatter.format(date)
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatShortDate(dateKey: String): String {
        return try {
            val date = keyFormatter.parse(dateKey) ?: return dateKey
            shortDateFormatter.format(date)
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatShortDay(dateKey: String): String {
        return try {
            val date = keyFormatter.parse(dateKey) ?: return dateKey
            shortDayFormatter.format(date)
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatTime(millis: Long): String = timeFormatter.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFormatter.format(Date(millis))

    fun invoiceDatePrefix(millis: Long = System.currentTimeMillis()): String =
        invoiceDateFormatter.format(Date(millis))
}
