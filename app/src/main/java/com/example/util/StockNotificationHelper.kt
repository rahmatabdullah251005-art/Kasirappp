package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object StockNotificationHelper {

    const val CHANNEL_ID = "um_tazkiyah_stock_alerts"
    const val EXTRA_OPEN_OWNER_LOW_STOCK = "extra_open_owner_low_stock"
    private const val NOTIFICATION_ID_LOW_STOCK = 10042

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Peringatan Stok Minimum",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi otomatis untuk Owner saat stok barang mencapai batas minimum atau habis."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun notifyLowStockAlerts(
        context: Context,
        alerts: List<StockAlertItem>,
        storeName: String
    ) {
        if (alerts.isEmpty() || !hasNotificationPermission(context)) return
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_OWNER_LOW_STOCK, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_LOW_STOCK,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "${StockAlertEvaluator.buildNotificationTitle(alerts)} • $storeName"
        val summaryText = StockAlertEvaluator.buildNotificationMessage(alerts)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (alerts.size > 1) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText("${alerts.size} barang mencapai batas minimum")
            alerts.take(5).forEach { alert ->
                val p = alert.product
                val line = if (p.stock <= 0) {
                    "${p.name}: HABIS (Min: ${p.minStockAlert} pcs)"
                } else {
                    "${p.name}: sisa ${p.stock} pcs (Min: ${p.minStockAlert} pcs)"
                }
                inboxStyle.addLine(line)
            }
            if (alerts.size > 5) {
                inboxStyle.addLine("+${alerts.size - 5} barang lainnya perlu restok")
            }
            builder.setStyle(inboxStyle)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_LOW_STOCK, builder.build())
        } catch (_: SecurityException) {
            // Permission may be revoked concurrently on Android 13+
        }
    }

    fun cancelLowStockNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_LOW_STOCK)
        } catch (_: Exception) {
        }
    }
}
