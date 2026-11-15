package com.example.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.PaymentMethod
import com.example.data.remote.CloudSaleWithItems
import com.example.data.remote.SaleItemDoc
import java.io.File

data class SoldItemSummary(
    val productId: String,
    val skuCode: String,
    val productName: String,
    val variantSummary: String = "",
    val categoryName: String,
    val totalQuantity: Int,
    val unitPrice: Long,
    val totalRevenue: Long,
    val totalCost: Long,
    val totalProfit: Long
)

data class BestSellerItemSummary(
    val productId: String,
    val skuCode: String,
    val productName: String,
    val variantSummary: String = "",
    val categoryName: String,
    val totalQuantity: Int,
    val transactionCount: Int,
    val totalRevenue: Long,
    val totalProfit: Long
)

object ReportExporter {

    fun aggregateBestSellers(
        transactions: List<CloudSaleWithItems>,
        limit: Int = 5,
        sortByRevenue: Boolean = false
    ): List<BestSellerItemSummary> {
        val groupedItems = mutableMapOf<String, MutableList<SaleItemDoc>>()
        val txIdsPerKey = mutableMapOf<String, MutableSet<String>>()
        transactions.forEach { sale ->
            val txId = sale.transaction.id.ifBlank { sale.transaction.invoiceNumber }
            sale.items.forEach { item ->
                val baseKey = item.productId.ifBlank { "${item.skuCode}_${item.productName}" }
                val key = "${baseKey}_${item.variantSummary}"
                groupedItems.getOrPut(key) { mutableListOf() }.add(item)
                txIdsPerKey.getOrPut(key) { mutableSetOf() }.add(txId)
            }
        }
        val summaries = groupedItems.entries.map { (key, list) ->
            val first = list.first()
            BestSellerItemSummary(
                productId = first.productId,
                skuCode = first.skuCode,
                productName = first.productName,
                variantSummary = first.variantSummary,
                categoryName = first.categoryName,
                totalQuantity = list.sumOf { it.quantity },
                transactionCount = txIdsPerKey[key]?.size ?: list.size,
                totalRevenue = list.sumOf { it.subtotalRevenue },
                totalProfit = list.sumOf { it.subtotalProfit }
            )
        }
        val sorted = if (sortByRevenue) {
            summaries.sortedWith(
                compareByDescending<BestSellerItemSummary> { it.totalRevenue }
                    .thenByDescending { it.totalQuantity }
                    .thenBy { it.productName.lowercase() }
            )
        } else {
            summaries.sortedWith(
                compareByDescending<BestSellerItemSummary> { it.totalQuantity }
                    .thenByDescending { it.totalRevenue }
                    .thenBy { it.productName.lowercase() }
            )
        }
        return sorted.take(limit.coerceAtLeast(1))
    }

    fun aggregateSoldItems(transactions: List<CloudSaleWithItems>): List<SoldItemSummary> {
        val grouped = mutableMapOf<String, MutableList<SaleItemDoc>>()
        transactions.forEach { tx ->
            tx.items.forEach { item ->
                val key = "${item.productId}_${item.variantSummary}_${item.unitSellingPrice}_${item.unitCostPrice}"
                grouped.getOrPut(key) { mutableListOf() }.add(item)
            }
        }
        return grouped.values.map { list ->
            val first = list.first()
            SoldItemSummary(
                productId = first.productId,
                skuCode = first.skuCode,
                productName = first.productName,
                variantSummary = first.variantSummary,
                categoryName = first.categoryName,
                totalQuantity = list.sumOf { it.quantity },
                unitPrice = first.unitSellingPrice,
                totalRevenue = list.sumOf { it.subtotalRevenue },
                totalCost = list.sumOf { it.subtotalCost },
                totalProfit = list.sumOf { it.subtotalProfit }
            )
        }.sortedByDescending { it.totalRevenue }
    }

    fun buildReadableTextReport(
        periodLabel: String,
        transactions: List<CloudSaleWithItems>
    ): String {
        val totalRevenue = transactions.sumOf { it.transaction.finalRevenue }
        val totalGrossRevenue = transactions.sumOf { it.transaction.totalRevenue }
        val totalDiscount = transactions.sumOf { it.transaction.discountAmount }
        val totalCost = transactions.sumOf { it.transaction.totalCost }
        val totalProfit = transactions.sumOf { it.transaction.netProfit }
        val totalItems = transactions.sumOf { it.transaction.totalItems }
        val marginPercent = if (totalRevenue > 0L) (totalProfit.toDouble() / totalRevenue.toDouble()) * 100.0 else 0.0
        val soldItems = aggregateSoldItems(transactions)
        val bestSellers = aggregateBestSellers(transactions, limit = 5)

        val sb = StringBuilder()
        sb.appendLine("================================================")
        sb.appendLine("         LAPORAN PENJUALAN HARIAN")
        sb.appendLine("               UM TAZKIYAH")
        sb.appendLine("    Toko Baju, Buku & Kebutuhan Muslim")
        sb.appendLine("================================================")
        sb.appendLine("Periode       : $periodLabel")
        sb.appendLine("Dicetak pada  : ${DateUtils.formatDateTime(System.currentTimeMillis())}")
        sb.appendLine("------------------------------------------------")
        sb.appendLine("RINGKASAN KEUANGAN")
        sb.appendLine("  Jumlah Transaksi   : ${transactions.size} transaksi")
        sb.appendLine("  Total Barang       : $totalItems pcs")
        sb.appendLine("  Penjualan Kotor    : ${CurrencyFormatter.formatRupiah(totalGrossRevenue)}")
        if (totalDiscount > 0L) {
            sb.appendLine("  Total Diskon       : -${CurrencyFormatter.formatRupiah(totalDiscount)}")
        }
        sb.appendLine("  Total Pendapatan   : ${CurrencyFormatter.formatRupiah(totalRevenue)}")
        sb.appendLine("  Total Modal (HPP)  : ${CurrencyFormatter.formatRupiah(totalCost)}")
        sb.appendLine("  TOTAL KEUNTUNGAN   : ${CurrencyFormatter.formatRupiah(totalProfit)} (${String.format("%.1f", marginPercent)}%)")
        sb.appendLine("------------------------------------------------")
        sb.appendLine("METODE PEMBAYARAN")
        PaymentMethod.entries.forEach { method ->
            val methodTxs = transactions.filter { it.transaction.paymentMethodEnum == method }
            val sum = methodTxs.sumOf { it.transaction.finalRevenue }
            sb.appendLine("  %-10s : %3d trx | %s".format(method.displayName, methodTxs.size, CurrencyFormatter.formatRupiah(sum)))
        }
        if (bestSellers.isNotEmpty()) {
            sb.appendLine("------------------------------------------------")
            sb.appendLine("BARANG BEST SELLER (TERLARIS)")
            bestSellers.forEachIndexed { idx, item ->
                val varSuffix = if (item.variantSummary.isNotBlank()) " (${item.variantSummary})" else ""
                sb.appendLine("  #${idx + 1} ${item.productName}$varSuffix")
                sb.appendLine("     Terjual: ${item.totalQuantity} pcs (${item.transactionCount} trx) | Omzet: ${CurrencyFormatter.formatRupiah(item.totalRevenue)} | Untung: ${CurrencyFormatter.formatRupiah(item.totalProfit)}")
            }
        }
        sb.appendLine("------------------------------------------------")
        sb.appendLine("DAFTAR ITEM TERJUAL")
        if (soldItems.isEmpty()) {
            sb.appendLine("  (Belum ada item terjual pada periode ini)")
        } else {
            soldItems.forEachIndexed { idx, item ->
                val varSuffix = if (item.variantSummary.isNotBlank()) " (${item.variantSummary})" else ""
                sb.appendLine("${idx + 1}. ${item.productName}$varSuffix [${item.skuCode}]")
                sb.appendLine("   Kategori : ${item.categoryName}")
                sb.appendLine("   Qty      : ${item.totalQuantity} x ${CurrencyFormatter.formatRupiah(item.unitPrice)} = ${CurrencyFormatter.formatRupiah(item.totalRevenue)}")
                sb.appendLine("   Modal    : ${CurrencyFormatter.formatRupiah(item.totalCost)} | Untung: ${CurrencyFormatter.formatRupiah(item.totalProfit)}")
            }
        }
        sb.appendLine("------------------------------------------------")
        sb.appendLine("RINCIAN TRANSAKSI")
        if (transactions.isEmpty()) {
            sb.appendLine("  (Tidak ada transaksi)")
        } else {
            transactions.forEach { txWithItems ->
                val tx = txWithItems.transaction
                val custTag = if (tx.customerName.isNotBlank()) " | Pelanggan: ${tx.customerName}" else ""
                sb.appendLine("- ${tx.invoiceNumber} | ${DateUtils.formatDateTime(tx.timestampMillis)} | ${tx.cashierName} (${tx.cashierRoleEnum.displayName})$custTag")
                sb.appendLine("  Bayar: ${tx.paymentMethodEnum.displayName} | Omzet: ${CurrencyFormatter.formatRupiah(tx.finalRevenue)} | Modal: ${CurrencyFormatter.formatRupiah(tx.totalCost)} | Untung: ${CurrencyFormatter.formatRupiah(tx.netProfit)}")
            }
        }
        sb.appendLine("================================================")
        return sb.toString()
    }

    fun buildCsvReport(
        periodLabel: String,
        transactions: List<CloudSaleWithItems>
    ): String {
        val totalRevenue = transactions.sumOf { it.transaction.finalRevenue }
        val totalCost = transactions.sumOf { it.transaction.totalCost }
        val totalProfit = transactions.sumOf { it.transaction.netProfit }
        val totalItems = transactions.sumOf { it.transaction.totalItems }

        val sb = StringBuilder()
        // UTF-8 BOM for Excel compatibility
        sb.append('\uFEFF')
        sb.appendLine("Laporan Penjualan Harian UM TAZKIYAH")
        sb.appendLine("Periode,\"${escapeCsv(periodLabel)}\"")
        sb.appendLine("Jumlah Transaksi,${transactions.size}")
        sb.appendLine("Total Barang Terjual,$totalItems")
        sb.appendLine("Total Pendapatan (Rp),$totalRevenue")
        sb.appendLine("Total Modal (Rp),$totalCost")
        sb.appendLine("Total Keuntungan Bersih (Rp),$totalProfit")
        sb.appendLine()
        sb.appendLine("No Invoice,Tanggal & Jam,Kasir,Peran,Metode Bayar,Kode QR/SKU,Nama Barang,Varian/Warna/Ukuran,Kategori,Qty,Harga Satuan (Rp),Modal Satuan (Rp),Subtotal Pendapatan (Rp),Subtotal Modal (Rp),Keuntungan (Rp),Diskon Transaksi (Rp)")

        transactions.forEach { txWithItems ->
            val tx = txWithItems.transaction
            val timeStr = DateUtils.formatDateTime(tx.timestampMillis)
            txWithItems.items.forEach { item ->
                sb.appendLine(
                    listOf(
                        escapeCsv(tx.invoiceNumber),
                        escapeCsv(timeStr),
                        escapeCsv(tx.cashierName),
                        escapeCsv(tx.cashierRoleEnum.displayName),
                        escapeCsv(tx.paymentMethodEnum.displayName),
                        escapeCsv(item.skuCode),
                        escapeCsv(item.productName),
                        escapeCsv(item.variantSummary),
                        escapeCsv(item.categoryName),
                        item.quantity.toString(),
                        item.unitSellingPrice.toString(),
                        item.unitCostPrice.toString(),
                        item.subtotalRevenue.toString(),
                        item.subtotalCost.toString(),
                        item.subtotalProfit.toString(),
                        tx.discountAmount.toString()
                    ).joinToString(",")
                )
            }
        }
        return sb.toString()
    }

    fun buildCustomerDigitalReceipt(
        receipt: CloudSaleWithItems,
        storeName: String = "UM TAZKIYAH"
    ): String {
        val tx = receipt.transaction
        val effectiveStore = storeName.ifBlank { "UM TAZKIYAH" }
        val sb = StringBuilder()
        sb.appendLine("*STRUK DIGITAL - $effectiveStore*")
        sb.appendLine("_Toko Baju, Buku & Kebutuhan Muslim_")
        sb.appendLine("--------------------------------")
        sb.appendLine("No. Struk : ${tx.invoiceNumber}")
        sb.appendLine("Tanggal   : ${DateUtils.formatDateTime(tx.timestampMillis)}")
        sb.appendLine("Kasir     : ${tx.cashierName}")
        if (tx.customerName.isNotBlank()) {
            sb.appendLine("Pelanggan : ${tx.customerName}")
        }
        sb.appendLine("--------------------------------")
        receipt.items.forEach { item ->
            sb.appendLine("• ${item.productName}")
            if (item.variantSummary.isNotBlank()) {
                sb.appendLine("  _${item.variantSummary}_")
            }
            sb.appendLine("  ${item.quantity} x ${CurrencyFormatter.formatRupiah(item.unitSellingPrice)} = ${CurrencyFormatter.formatRupiah(item.subtotalRevenue)}")
        }
        sb.appendLine("--------------------------------")
        if (tx.discountAmount > 0L) {
            sb.appendLine("Subtotal  : ${CurrencyFormatter.formatRupiah(tx.totalRevenue)}")
            sb.appendLine("Diskon    : -${CurrencyFormatter.formatRupiah(tx.discountAmount)}")
        }
        sb.appendLine("*Total    : ${CurrencyFormatter.formatRupiah(tx.finalRevenue)}*")
        sb.appendLine("Metode    : ${tx.paymentMethodEnum.displayName}")
        if (tx.qrisReference.isNotBlank()) {
            sb.appendLine("Ref QRIS  : ${tx.qrisReference.take(32)}")
        }
        if (tx.paymentMethodEnum == PaymentMethod.TUNAI) {
            sb.appendLine("Dibayar   : ${CurrencyFormatter.formatRupiah(tx.amountPaid)}")
            sb.appendLine("Kembalian : ${CurrencyFormatter.formatRupiah(tx.changeAmount)}")
        }
        if (tx.notes.isNotBlank()) {
            sb.appendLine("Catatan   : ${tx.notes}")
        }
        sb.appendLine("--------------------------------")
        sb.append("Jazakumullahu khairan telah berbelanja di $effectiveStore.")
        return sb.toString()
    }

    fun buildCashierShiftRecapText(
        storeName: String,
        cashierName: String,
        periodLabel: String,
        transactions: List<CloudSaleWithItems>
    ): String {
        val effectiveStore = storeName.ifBlank { "UM TAZKIYAH" }
        val totalRevenue = transactions.sumOf { it.transaction.finalRevenue }
        val totalGrossRevenue = transactions.sumOf { it.transaction.totalRevenue }
        val totalDiscount = transactions.sumOf { it.transaction.discountAmount }
        val totalItems = transactions.sumOf { it.transaction.totalItems }
        val soldItems = aggregateSoldItems(transactions)
        val bestSellers = aggregateBestSellers(transactions, limit = 5)

        val sb = StringBuilder()
        sb.appendLine("*REKAP TRANSAKSI PEGAWAI - $effectiveStore*")
        sb.appendLine("Petugas : $cashierName")
        sb.appendLine("Tanggal : $periodLabel")
        sb.appendLine("Dikirim : ${DateUtils.formatDateTime(System.currentTimeMillis())}")
        sb.appendLine("--------------------------------")
        sb.appendLine("RINGKASAN SHIFT")
        sb.appendLine("• Jumlah Transaksi : ${transactions.size} transaksi")
        sb.appendLine("• Total Terjual    : $totalItems pcs")
        if (totalDiscount > 0L) {
            sb.appendLine("• Subtotal Kotor   : ${CurrencyFormatter.formatRupiah(totalGrossRevenue)}")
            sb.appendLine("• Total Diskon     : -${CurrencyFormatter.formatRupiah(totalDiscount)}")
        }
        sb.appendLine("• *Total Omzet     : ${CurrencyFormatter.formatRupiah(totalRevenue)}*")
        sb.appendLine("--------------------------------")
        sb.appendLine("RINCIAN METODE BAYAR")
        PaymentMethod.entries.forEach { method ->
            val matching = transactions.filter { it.transaction.paymentMethodEnum == method }
            val sum = matching.sumOf { it.transaction.finalRevenue }
            sb.appendLine("• ${method.displayName} (${matching.size}x) : ${CurrencyFormatter.formatRupiah(sum)}")
        }
        if (bestSellers.isNotEmpty()) {
            sb.appendLine("--------------------------------")
            sb.appendLine("BARANG BEST SELLER SHIFT")
            bestSellers.forEachIndexed { idx, item ->
                val varSuffix = if (item.variantSummary.isNotBlank()) " (${item.variantSummary})" else ""
                sb.appendLine("#${idx + 1} ${item.productName}$varSuffix — ${item.totalQuantity} pcs (${CurrencyFormatter.formatRupiah(item.totalRevenue)})")
            }
        }
        if (soldItems.isNotEmpty()) {
            sb.appendLine("--------------------------------")
            sb.appendLine("BARANG TERJUAL")
            soldItems.forEachIndexed { idx, item ->
                val varSuffix = if (item.variantSummary.isNotBlank()) " (${item.variantSummary})" else ""
                sb.appendLine("${idx + 1}. ${item.productName}$varSuffix")
                sb.appendLine("   ${item.totalQuantity} x ${CurrencyFormatter.formatRupiah(item.unitPrice)} = ${CurrencyFormatter.formatRupiah(item.totalRevenue)}")
            }
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("DAFTAR INVOICE")
        if (transactions.isEmpty()) {
            sb.appendLine("(Belum ada transaksi pada periode ini)")
        } else {
            transactions.forEach { txWithItems ->
                val tx = txWithItems.transaction
                val custTag = if (tx.customerName.isNotBlank()) " • Plg: ${tx.customerName}" else ""
                sb.appendLine("• ${tx.invoiceNumber} (${DateUtils.formatTime(tx.timestampMillis)})")
                sb.appendLine("  ${tx.paymentMethodEnum.displayName} • ${tx.totalItems} pcs • ${CurrencyFormatter.formatRupiah(tx.finalRevenue)}$custTag")
            }
        }
        return sb.toString().trimEnd()
    }

    fun buildCashierShiftRecapCsv(
        storeName: String,
        cashierName: String,
        periodLabel: String,
        transactions: List<CloudSaleWithItems>
    ): String {
        val effectiveStore = storeName.ifBlank { "UM TAZKIYAH" }
        val totalRevenue = transactions.sumOf { it.transaction.finalRevenue }
        val totalItems = transactions.sumOf { it.transaction.totalItems }

        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine("Rekap Transaksi Pegawai - ${escapeCsv(effectiveStore)}")
        sb.appendLine("Petugas,\"${escapeCsv(cashierName)}\"")
        sb.appendLine("Tanggal,\"${escapeCsv(periodLabel)}\"")
        sb.appendLine("Jumlah Transaksi,${transactions.size}")
        sb.appendLine("Total Barang Terjual,$totalItems")
        sb.appendLine("Total Omzet (Rp),$totalRevenue")
        sb.appendLine()
        sb.appendLine("No Invoice,Tanggal & Jam,Kasir,Pelanggan,Metode Bayar,Kode QR/SKU,Nama Barang,Varian/Warna/Ukuran,Kategori,Qty,Harga Satuan (Rp),Subtotal (Rp),Diskon Transaksi (Rp)")

        transactions.forEach { txWithItems ->
            val tx = txWithItems.transaction
            val timeStr = DateUtils.formatDateTime(tx.timestampMillis)
            if (txWithItems.items.isEmpty()) {
                sb.appendLine(
                    listOf(
                        escapeCsv(tx.invoiceNumber),
                        escapeCsv(timeStr),
                        escapeCsv(tx.cashierName),
                        escapeCsv(tx.customerName),
                        escapeCsv(tx.paymentMethodEnum.displayName),
                        "\"\"",
                        "\"\"",
                        "\"\"",
                        "\"\"",
                        tx.totalItems.toString(),
                        "0",
                        tx.finalRevenue.toString(),
                        tx.discountAmount.toString()
                    ).joinToString(",")
                )
            } else {
                txWithItems.items.forEach { item ->
                    sb.appendLine(
                        listOf(
                            escapeCsv(tx.invoiceNumber),
                            escapeCsv(timeStr),
                            escapeCsv(tx.cashierName),
                            escapeCsv(tx.customerName),
                            escapeCsv(tx.paymentMethodEnum.displayName),
                            escapeCsv(item.skuCode),
                            escapeCsv(item.productName),
                            escapeCsv(item.variantSummary),
                            escapeCsv(item.categoryName),
                            item.quantity.toString(),
                            item.unitSellingPrice.toString(),
                            item.subtotalRevenue.toString(),
                            tx.discountAmount.toString()
                        ).joinToString(",")
                    )
                }
            }
        }
        return sb.toString()
    }

    fun saveTempReportFile(
        context: Context,
        fileName: String,
        content: String
    ): Uri {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
