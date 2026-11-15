package com.example.util

import com.example.data.remote.ProductDoc

enum class StockAlertSeverity {
    OUT_OF_STOCK,
    LOW_STOCK
}

data class StockAlertItem(
    val product: ProductDoc,
    val severity: StockAlertSeverity
) {
    val signature: String get() = "${product.id}:${product.stock}:${product.minStockAlert}"
}

data class StockAlertUiState(
    val alerts: List<StockAlertItem> = emptyList(),
    val unreadCount: Int = 0
) {
    val totalAlertCount: Int get() = alerts.size
    val outOfStockCount: Int get() = alerts.count { it.severity == StockAlertSeverity.OUT_OF_STOCK }
    val lowStockOnlyCount: Int get() = alerts.count { it.severity == StockAlertSeverity.LOW_STOCK }
    val hasAlerts: Boolean get() = alerts.isNotEmpty()
}

object StockAlertEvaluator {

    fun evaluateAlerts(products: List<ProductDoc>): List<StockAlertItem> {
        return products
            .filter { it.isLowStock }
            .map { product ->
                val severity = if (product.stock <= 0) {
                    StockAlertSeverity.OUT_OF_STOCK
                } else {
                    StockAlertSeverity.LOW_STOCK
                }
                StockAlertItem(product = product, severity = severity)
            }
            .sortedWith(
                compareBy<StockAlertItem> { it.severity.ordinal }
                    .thenBy { it.product.stock }
                    .thenBy { it.product.name.lowercase() }
            )
    }

    /**
     * Detects products that newly reached or worsened into minimum stock threshold.
     * - When [previousStocksById] is null (first Owner observation in session), returns all active alerts.
     * - On subsequent updates (e.g. Kasir checkout synced from cloud), triggers when a product
     *   transitions from above threshold to <= minStockAlert, or drops from > 0 to <= 0.
     */
    fun detectNewlyTriggeredAlerts(
        previousStocksById: Map<String, Int>?,
        currentProducts: List<ProductDoc>
    ): List<StockAlertItem> {
        val currentAlerts = evaluateAlerts(currentProducts)
        if (previousStocksById == null) {
            return currentAlerts
        }
        return currentAlerts.filter { alert ->
            val product = alert.product
            val previousStock = previousStocksById[product.id]
            when {
                previousStock == null -> true
                previousStock > product.minStockAlert -> true
                previousStock > 0 && product.stock <= 0 -> true
                else -> false
            }
        }
    }

    fun buildNotificationTitle(alerts: List<StockAlertItem>): String {
        if (alerts.isEmpty()) return "Semua Stok Aman"
        val outCount = alerts.count { it.severity == StockAlertSeverity.OUT_OF_STOCK }
        return when {
            outCount > 0 && alerts.size == 1 -> "Stok Habis: ${alerts.first().product.name}"
            alerts.size == 1 -> "Stok Menipis: ${alerts.first().product.name}"
            outCount > 0 -> "${alerts.size} Barang Perlu Restok ($outCount Habis)"
            else -> "${alerts.size} Barang Mencapai Batas Minimum"
        }
    }

    fun buildNotificationMessage(alerts: List<StockAlertItem>): String {
        if (alerts.isEmpty()) return ""
        return if (alerts.size == 1) {
            val item = alerts.first().product
            if (item.stock <= 0) {
                "Stok ${item.name} habis (0 pcs, batas min ${item.minStockAlert} pcs). Segera restok!"
            } else {
                "Stok ${item.name} tersisa ${item.stock} pcs (batas min ${item.minStockAlert} pcs)."
            }
        } else {
            val preview = alerts.take(3).joinToString(", ") { item ->
                val p = item.product
                if (p.stock <= 0) "${p.name} (Habis)" else "${p.name} (${p.stock} pcs)"
            }
            val extra = if (alerts.size > 3) " +${alerts.size - 3} lainnya" else ""
            "Perlu restok: $preview$extra"
        }
    }
}
