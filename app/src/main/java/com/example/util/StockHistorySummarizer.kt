package com.example.util

import com.example.data.local.entity.StockChangeType
import com.example.data.remote.StockHistoryDoc

enum class StockHistoryFilterType(val label: String) {
    ALL("Semua"),
    ADDITION("Penambahan (+)"),
    REDUCTION("Pengurangan (-)"),
    SALE("Terjual Kasir")
}

enum class StockHistoryPeriodFilter(val label: String) {
    TODAY("Hari Ini"),
    LAST_7_DAYS("7 Hari"),
    ALL("Semua Waktu")
}

data class StockHistorySummary(
    val totalAddedUnits: Int = 0,
    val totalAddedEvents: Int = 0,
    val totalManualReducedUnits: Int = 0,
    val totalManualReducedEvents: Int = 0,
    val totalSalesReducedUnits: Int = 0,
    val totalSalesEvents: Int = 0,
    val netDeltaUnits: Int = 0,
    val totalEvents: Int = 0
)

object StockHistorySummarizer {

    fun isAddition(doc: StockHistoryDoc): Boolean = doc.quantityDelta > 0

    fun isManualReduction(doc: StockHistoryDoc): Boolean =
        doc.quantityDelta < 0 && doc.changeTypeEnum != StockChangeType.PENJUALAN

    fun isSaleDeduction(doc: StockHistoryDoc): Boolean =
        doc.changeTypeEnum == StockChangeType.PENJUALAN && doc.quantityDelta < 0

    fun filterHistories(
        histories: List<StockHistoryDoc>,
        typeFilter: StockHistoryFilterType = StockHistoryFilterType.ALL,
        periodFilter: StockHistoryPeriodFilter = StockHistoryPeriodFilter.ALL,
        query: String = "",
        todayKey: String = DateUtils.todayKey(),
        last7DaysKeys: Set<String> = (0..6).map { DateUtils.dateKeyForOffset(it) }.toSet()
    ): List<StockHistoryDoc> {
        val q = query.trim().lowercase()
        val tokens = if (q.isEmpty()) emptyList() else q.split(Regex("\\s+")).filter { it.isNotBlank() }

        return histories
            .filter { doc ->
                val matchPeriod = when (periodFilter) {
                    StockHistoryPeriodFilter.ALL -> true
                    StockHistoryPeriodFilter.TODAY -> {
                        doc.timestampMillis > 0L && DateUtils.dateKeyFromMillis(doc.timestampMillis) == todayKey
                    }
                    StockHistoryPeriodFilter.LAST_7_DAYS -> {
                        doc.timestampMillis > 0L && DateUtils.dateKeyFromMillis(doc.timestampMillis) in last7DaysKeys
                    }
                }
                if (!matchPeriod) return@filter false

                val matchType = when (typeFilter) {
                    StockHistoryFilterType.ALL -> true
                    StockHistoryFilterType.ADDITION -> isAddition(doc)
                    StockHistoryFilterType.REDUCTION -> isManualReduction(doc)
                    StockHistoryFilterType.SALE -> isSaleDeduction(doc)
                }
                if (!matchType) return@filter false

                if (tokens.isEmpty()) {
                    true
                } else {
                    val haystack = buildString {
                        append(doc.productName.lowercase()).append(' ')
                        append(doc.actorName.lowercase()).append(' ')
                        append(doc.note.lowercase()).append(' ')
                        append(doc.changeTypeEnum.displayName.lowercase()).append(' ')
                        append(DateUtils.formatDateTime(doc.timestampMillis).lowercase())
                    }
                    tokens.all { token -> haystack.contains(token) }
                }
            }
            .sortedByDescending { it.timestampMillis }
    }

    fun summarize(histories: List<StockHistoryDoc>): StockHistorySummary {
        var addedUnits = 0
        var addedEvents = 0
        var manualReducedUnits = 0
        var manualReducedEvents = 0
        var salesReducedUnits = 0
        var salesEvents = 0
        var net = 0

        histories.forEach { doc ->
            net += doc.quantityDelta
            when {
                isAddition(doc) -> {
                    addedUnits += doc.quantityDelta
                    addedEvents++
                }
                isSaleDeduction(doc) -> {
                    salesReducedUnits += -doc.quantityDelta
                    salesEvents++
                }
                isManualReduction(doc) -> {
                    manualReducedUnits += -doc.quantityDelta
                    manualReducedEvents++
                }
            }
        }

        return StockHistorySummary(
            totalAddedUnits = addedUnits,
            totalAddedEvents = addedEvents,
            totalManualReducedUnits = manualReducedUnits,
            totalManualReducedEvents = manualReducedEvents,
            totalSalesReducedUnits = salesReducedUnits,
            totalSalesEvents = salesEvents,
            netDeltaUnits = net,
            totalEvents = histories.size
        )
    }

    fun buildRecapText(
        storeName: String,
        periodLabel: String,
        histories: List<StockHistoryDoc>
    ): String {
        val summary = summarize(histories)
        val sorted = histories.sortedByDescending { it.timestampMillis }
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("  REKAP PERGERAKAN STOK - ${storeName.ifBlank { "UM TAZKIYAH" }}")
        sb.appendLine("==========================================")
        sb.appendLine("Periode   : $periodLabel")
        sb.appendLine("Dicetak   : ${DateUtils.formatDateTime(System.currentTimeMillis())}")
        sb.appendLine("------------------------------------------")
        sb.appendLine("Total Penambahan (+)       : +${summary.totalAddedUnits} pcs (${summary.totalAddedEvents}x)")
        sb.appendLine("Pengurangan Manual (-)     : -${summary.totalManualReducedUnits} pcs (${summary.totalManualReducedEvents}x)")
        sb.appendLine("Keluar Penjualan Kasir (-) : -${summary.totalSalesReducedUnits} pcs (${summary.totalSalesEvents}x)")
        val netSign = if (summary.netDeltaUnits > 0) "+" else ""
        sb.appendLine("Perubahan Bersih Stok      : $netSign${summary.netDeltaUnits} pcs")
        sb.appendLine("==========================================")
        sb.appendLine()
        sb.appendLine("RINCIAN RIWAYAT (${sorted.size} CATATAN)")
        sb.appendLine("------------------------------------------")
        if (sorted.isEmpty()) {
            sb.appendLine("Belum ada catatan pergerakan stok.")
        } else {
            sorted.forEach { doc ->
                val deltaText = if (doc.quantityDelta >= 0) "+${doc.quantityDelta}" else "${doc.quantityDelta}"
                val notePart = if (doc.note.isNotBlank()) " | Catatan: ${doc.note}" else ""
                sb.appendLine("[${DateUtils.formatDateTime(doc.timestampMillis)}] ${doc.productName}")
                sb.appendLine("  $deltaText pcs (${doc.changeTypeEnum.displayName}) -> Sisa: ${doc.stockAfter} pcs")
                sb.appendLine("  Oleh: ${doc.actorName}$notePart")
            }
        }
        sb.appendLine("==========================================")
        return sb.toString()
    }
}
