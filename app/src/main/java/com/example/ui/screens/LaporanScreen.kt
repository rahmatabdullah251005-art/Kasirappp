package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.StockChangeType
import com.example.data.remote.CloudSaleWithItems
import com.example.data.remote.ProductDoc
import com.example.data.remote.StockHistoryDoc
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.RestockDialog
import com.example.ui.components.StockHistoryDialog
import com.example.ui.components.ThermalPrintDialog
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.DangerCrimsonContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.DailyReportUiState
import com.example.ui.viewmodel.DailyTrendPoint
import com.example.ui.viewmodel.ReportPeriodMode
import com.example.util.BestSellerItemSummary
import com.example.util.CurrencyFormatter
import com.example.util.DateUtils
import com.example.util.ReportExporter
import com.example.util.StockAlertSeverity
import com.example.util.StockAlertUiState
import com.example.util.StockHistoryFilterType
import com.example.util.StockHistoryPeriodFilter
import com.example.util.StockHistorySummarizer
import com.example.util.TransactionSearchHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaporanScreen(
    storeName: String,
    reportState: DailyReportUiState,
    allOwnerTransactions: List<CloudSaleWithItems> = reportState.transactions,
    stockHistories: List<StockHistoryDoc> = emptyList(),
    selectedDateKey: String,
    periodMode: ReportPeriodMode,
    sevenDayTrend: List<DailyTrendPoint>,
    stockAlertState: StockAlertUiState = StockAlertUiState(),
    totalCatalogProducts: Int = 0,
    hasSystemNotificationPermission: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onRestockProduct: (ProductDoc, Int, Long, String, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onOpenLowStockTab: () -> Unit = {},
    onSelectDateKey: (String) -> Unit,
    onSelectPeriodMode: (ReportPeriodMode) -> Unit,
    onShowMessage: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showStockHistoryDialog by remember { mutableStateOf(false) }
    var selectedTransactionDetail by remember { mutableStateOf<CloudSaleWithItems?>(null) }
    var restockTargetProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var historySearchQuery by remember { mutableStateOf("") }
    var printTargetTransaction by remember { mutableStateOf<CloudSaleWithItems?>(null) }

    val displayedHistoryTransactions = remember(
        historySearchQuery,
        reportState.transactions,
        allOwnerTransactions
    ) {
        if (historySearchQuery.isBlank()) {
            reportState.transactions
        } else {
            TransactionSearchHelper.filterTransactions(allOwnerTransactions, historySearchQuery)
        }
    }

    val todayKey = remember { DateUtils.todayKey() }
    val yesterdayKey = remember { DateUtils.dateKeyForOffset(1) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real-time Remote Monitoring Status Strip
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = ProfitGreenContainer.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = ProfitGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Pantau Online Aktif • $storeName (Sinkronisasi Real-Time Lintas HP)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ProfitGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Automatic Owner Low-Stock Notification Panel
        item {
            OwnerLowStockAlertCard(
                stockAlertState = stockAlertState,
                totalCatalogProducts = totalCatalogProducts,
                hasSystemNotificationPermission = hasSystemNotificationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onQuickRestock = { product -> restockTargetProduct = product },
                onOpenLowStockTab = onOpenLowStockTab
            )
        }

        // Period & Export Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Laporan Penjualan & Laba",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = reportState.periodLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showExportDialog = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("open_export_report_button")
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ekspor")
                }
            }
        }

        // Date Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = periodMode == ReportPeriodMode.SELECTED_DAY && selectedDateKey == todayKey,
                        onClick = { onSelectDateKey(todayKey) },
                        label = { Text("Hari Ini") }
                    )
                }
                item {
                    FilterChip(
                        selected = periodMode == ReportPeriodMode.SELECTED_DAY && selectedDateKey == yesterdayKey,
                        onClick = { onSelectDateKey(yesterdayKey) },
                        label = { Text("Kemarin") }
                    )
                }
                item {
                    FilterChip(
                        selected = periodMode == ReportPeriodMode.LAST_7_DAYS,
                        onClick = { onSelectPeriodMode(ReportPeriodMode.LAST_7_DAYS) },
                        label = { Text("7 Hari Terakhir") }
                    )
                }
                item {
                    FilterChip(
                        selected = periodMode == ReportPeriodMode.SELECTED_DAY &&
                            selectedDateKey != todayKey &&
                            selectedDateKey != yesterdayKey,
                        onClick = { showDatePicker = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Pilih Tanggal") }
                    )
                }
            }
        }

        // 4 KPI Summary Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        title = "Total Pendapatan",
                        value = CurrencyFormatter.formatRupiah(reportState.totalRevenue),
                        subtitle = if (reportState.totalDiscount > 0L) {
                            "Diskon -${CurrencyFormatter.formatCompactRupiah(reportState.totalDiscount)}"
                        } else {
                            "${reportState.transactionCount} transaksi"
                        },
                        icon = Icons.Rounded.Insights,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Total Keuntungan",
                        value = CurrencyFormatter.formatRupiah(reportState.totalProfit),
                        subtitle = "Margin ${String.format("%.1f", reportState.marginPercent)}%",
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        containerColor = ProfitGreenContainer.copy(alpha = 0.55f),
                        accentColor = ProfitGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        title = "Total Modal (HPP)",
                        value = CurrencyFormatter.formatRupiah(reportState.totalCost),
                        subtitle = "Modal barang terjual",
                        icon = Icons.Rounded.AccountBalanceWallet,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Barang Terjual",
                        value = "${reportState.totalItemsSold} pcs",
                        subtitle = "${reportState.soldItems.size} jenis produk",
                        icon = Icons.Rounded.ShoppingBag,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 7-Day Trend Bar Chart
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Grafik Pendapatan vs Keuntungan (7 Hari)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(color = EmeraldPrimary, label = "Pendapatan")
                        LegendDot(color = GoldSecondary, label = "Keuntungan")
                    }
                    RevenueProfitBarChart(points = sevenDayTrend)
                }
            }
        }

        // Payment Method Split
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Rincian Metode Pembayaran",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    PaymentMethod.entries.forEach { method ->
                        val (count, sum) = reportState.paymentTotals[method] ?: (0 to 0L)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${method.displayName} ($count trx)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.formatRupiah(sum),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }

        // Category Performance
        if (reportState.categoryBreakdowns.isNotEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Penjualan per Kategori Toko",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        val maxCatRev = reportState.categoryBreakdowns.maxOf { it.revenue }.coerceAtLeast(1L)
                        reportState.categoryBreakdowns.forEach { cat ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${cat.categoryName} (${cat.quantity} pcs)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "${CurrencyFormatter.formatRupiah(cat.revenue)} • Untung ${CurrencyFormatter.formatCompactRupiah(cat.profit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ProfitGreen
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (cat.revenue.toFloat() / maxCatRev.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Best Seller Ranking List
        item {
            BestSellerRankingCard(
                periodLabel = reportState.periodLabel,
                transactions = reportState.transactions
            )
        }

        // Stock Additions & Reductions Recap Card (Owner History)
        item {
            OwnerStockMovementRecapCard(
                stockHistories = stockHistories,
                periodMode = periodMode,
                onOpenFullHistory = { showStockHistoryDialog = true }
            )
        }

        // Sold Items Detailed List
        item {
            Text(
                text = "Daftar Item Terjual (${reportState.soldItems.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (reportState.soldItems.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Belum ada penjualan pada periode ini",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Setiap transaksi dari perangkat kasir mana pun akan langsung tampil di sini secara real-time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(reportState.soldItems, key = { "${it.productId}_${it.variantSummary}_${it.unitPrice}" }) { sold ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sold.productName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sold.variantSummary.isNotBlank()) {
                                    Text(
                                        text = sold.variantSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${sold.categoryName} • ${sold.skuCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${sold.totalQuantity} pcs",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${sold.totalQuantity} x ${CurrencyFormatter.formatRupiah(sold.unitPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.formatRupiah(sold.totalRevenue),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Modal: ${CurrencyFormatter.formatRupiah(sold.totalCost)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Untung: ${CurrencyFormatter.formatRupiah(sold.totalProfit)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = ProfitGreen
                            )
                        }
                    }
                }
            }
        }

        // Transaction History + Search Bar (search across date, customer name, items sold, or invoice)
        item {
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (historySearchQuery.isBlank()) {
                        "Riwayat Transaksi (${displayedHistoryTransactions.size})"
                    } else {
                        "Hasil Pencarian (${displayedHistoryTransactions.size} dari ${allOwnerTransactions.size} pesanan)"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedTextField(
                    value = historySearchQuery,
                    onValueChange = { historySearchQuery = it },
                    placeholder = { Text("Cari tanggal, pelanggan, item terjual, atau no. struk…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Cari transaksi") },
                    trailingIcon = {
                        if (historySearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { historySearchQuery = "" },
                                modifier = Modifier.testTag("clear_transaction_search_button")
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_history_search_input")
                )
            }
        }

        if (displayedHistoryTransactions.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (historySearchQuery.isNotBlank()) {
                                "Tidak ditemukan pesanan untuk \"$historySearchQuery\""
                            } else {
                                "Belum ada riwayat transaksi pada periode ini"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (historySearchQuery.isNotBlank()) {
                            Text(
                                text = "Coba kata kunci tanggal (cth: 15 Nov), nama pelanggan, atau nama barang yang terjual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(displayedHistoryTransactions, key = { it.transaction.id }) { txWithItems ->
                val tx = txWithItems.transaction
                val itemSummary = txWithItems.items.joinToString(", ") { "${it.productName} (${it.quantity}x)" }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { selectedTransactionDetail = txWithItems }
                        .testTag("history_tx_card_${tx.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tx.invoiceNumber,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${DateUtils.formatDateTime(tx.timestampMillis)} • ${tx.paymentMethodEnum.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val metaLine = buildString {
                                if (tx.customerName.isNotBlank()) {
                                    append("Pelanggan: ${tx.customerName} • ")
                                }
                                append("Kasir: ${tx.cashierName} (${tx.totalItems} pcs)")
                            }
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (itemSummary.isNotBlank()) {
                                Text(
                                    text = itemSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyFormatter.formatRupiah(tx.finalRevenue),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "+Untung ${CurrencyFormatter.formatCompactRupiah(tx.netProfit)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ProfitGreen
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onSelectDateKey(DateUtils.dateKeyFromMillis(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    selectedTransactionDetail?.let { detail ->
        TransactionDetailDialog(
            txWithItems = detail,
            onPrintReceipt = {
                selectedTransactionDetail = null
                printTargetTransaction = detail
            },
            onDismiss = { selectedTransactionDetail = null }
        )
    }

    printTargetTransaction?.let { target ->
        ThermalPrintDialog(
            receipt = target,
            storeName = storeName,
            onShowMessage = onShowMessage,
            onDismiss = { printTargetTransaction = null }
        )
    }

    if (showExportDialog) {
        ExportReportDialog(
            storeName = storeName,
            reportState = reportState,
            selectedDateKey = selectedDateKey,
            periodMode = periodMode,
            onDismiss = { showExportDialog = false },
            onShowMessage = onShowMessage
        )
    }

    restockTargetProduct?.let { product ->
        RestockDialog(
            product = product,
            onDismiss = { restockTargetProduct = null },
            onConfirm = { addQty, newCost, note ->
                onRestockProduct(product, addQty, newCost, note) {
                    restockTargetProduct = null
                }
            }
        )
    }

    if (showStockHistoryDialog) {
        StockHistoryDialog(
            storeName = storeName,
            histories = stockHistories,
            onShowMessage = onShowMessage,
            onDismiss = { showStockHistoryDialog = false }
        )
    }
}

@Composable
private fun OwnerLowStockAlertCard(
    stockAlertState: StockAlertUiState,
    totalCatalogProducts: Int,
    hasSystemNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onQuickRestock: (ProductDoc) -> Unit,
    onOpenLowStockTab: () -> Unit
) {
    if (!stockAlertState.hasAlerts) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_stock_safe_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ProfitGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Monitor Stok Otomatis Aktif",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (totalCatalogProducts > 0) {
                                "Semua $totalCatalogProducts produk di atas batas minimum."
                            } else {
                                "Belum ada produk terdaftar di katalog toko."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!hasSystemNotificationPermission) {
                    TextButton(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Aktifkan Notif HP")
                    }
                }
            }
        }
        return
    }

    val hasOutOfStock = stockAlertState.outOfStockCount > 0
    val accentColor = if (hasOutOfStock) DangerCrimson else WarningAmber
    val containerColor = if (hasOutOfStock) DangerCrimsonContainer.copy(alpha = 0.45f) else WarningAmberContainer.copy(alpha = 0.55f)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_low_stock_alert_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = if (hasOutOfStock) Icons.Rounded.WarningAmber else Icons.Rounded.NotificationsActive,
                            contentDescription = "Peringatan stok minimum",
                            tint = accentColor,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Notifikasi Stok Minimum (${stockAlertState.totalAlertCount})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitleParts = buildList {
                            if (stockAlertState.outOfStockCount > 0) add("${stockAlertState.outOfStockCount} habis")
                            if (stockAlertState.lowStockOnlyCount > 0) add("${stockAlertState.lowStockOnlyCount} menipis")
                        }
                        Text(
                            text = "${subtitleParts.joinToString(" • ")} — segera restok agar stok tidak habis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!hasSystemNotificationPermission) {
                    FilledTonalButton(
                        onClick = onRequestNotificationPermission,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Notif HP", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            stockAlertState.alerts.take(4).forEach { alert ->
                val p = alert.product
                val isOut = alert.severity == StockAlertSeverity.OUT_OF_STOCK
                val itemAccent = if (isOut) DangerCrimson else WarningAmber
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isOut) {
                                    "HABIS (0 pcs) • Batas min ${p.minStockAlert} pcs"
                                } else {
                                    "Sisa ${p.stock} pcs (Batas min ${p.minStockAlert} pcs)"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = itemAccent
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { onQuickRestock(p) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("dashboard_restock_button_${p.id}")
                        ) {
                            Icon(Icons.Rounded.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restok")
                        }
                    }
                }
            }

            if (stockAlertState.alerts.size > 4) {
                Text(
                    text = "+${stockAlertState.alerts.size - 4} barang lainnya mencapai batas minimum",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onOpenLowStockTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("dashboard_open_low_stock_button")
            ) {
                Text("Lihat & Kelola Semua Stok Menipis")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RevenueProfitBarChart(points: List<DailyTrendPoint>) {
    if (points.isEmpty()) return
    val maxValue = points.maxOf { maxOf(it.revenue, it.profit) }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val groupCount = points.size.coerceAtLeast(1)
            val groupWidth = size.width / groupCount
            val barWidth = (groupWidth * 0.28f).coerceAtMost(24.dp.toPx())
            val gap = 4.dp.toPx()

            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )

            points.forEachIndexed { index, point ->
                val centerX = groupWidth * index + groupWidth / 2f
                val revHeight = ((point.revenue.toFloat() / maxValue.toFloat()) * (size.height - 8.dp.toPx())).coerceAtLeast(
                    if (point.revenue > 0L) 6.dp.toPx() else 2.dp.toPx()
                )
                val profitHeight = ((point.profit.toFloat() / maxValue.toFloat()) * (size.height - 8.dp.toPx())).coerceAtLeast(
                    if (point.profit > 0L) 6.dp.toPx() else 2.dp.toPx()
                )

                drawRoundRect(
                    color = EmeraldPrimary,
                    topLeft = Offset(centerX - barWidth - gap / 2f, size.height - revHeight),
                    size = Size(barWidth, revHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = GoldSecondary,
                    topLeft = Offset(centerX + gap / 2f, size.height - profitHeight),
                    size = Size(barWidth, profitHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailDialog(
    txWithItems: CloudSaleWithItems,
    onPrintReceipt: () -> Unit,
    onDismiss: () -> Unit
) {
    val tx = txWithItems.transaction
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = tx.invoiceNumber,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${DateUtils.formatDateTime(tx.timestampMillis)} • Kasir: ${tx.cashierName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tx.customerName.isNotBlank()) {
                    Text(
                        text = "Pelanggan: ${tx.customerName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (tx.qrisReference.isNotBlank()) {
                    Text(
                        text = "Ref QRIS: ${tx.qrisReference}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(txWithItems.items, key = { it.id }) { item ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.productName} (${item.quantity}x)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = CurrencyFormatter.formatRupiah(item.subtotalRevenue),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            if (item.variantSummary.isNotBlank()) {
                                Text(
                                    text = item.variantSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Harga: ${CurrencyFormatter.formatRupiah(item.unitSellingPrice)} • Modal: ${CurrencyFormatter.formatRupiah(item.subtotalCost)} • Untung: ${CurrencyFormatter.formatRupiah(item.subtotalProfit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ProfitGreen
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Pendapatan", style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyFormatter.formatRupiah(tx.finalRevenue), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Modal (HPP)", style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyFormatter.formatRupiah(tx.totalCost), style = MaterialTheme.typography.bodyMedium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Keuntungan Bersih", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(CurrencyFormatter.formatRupiah(tx.netProfit), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ProfitGreen)
                }
                if (tx.notes.isNotBlank()) {
                    Text(
                        text = "Catatan: ${tx.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPrintReceipt,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cetak Struk")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportReportDialog(
    storeName: String,
    reportState: DailyReportUiState,
    selectedDateKey: String,
    periodMode: ReportPeriodMode,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val textReport = remember(reportState, storeName) {
        ReportExporter.buildReadableTextReport(reportState.periodLabel, reportState.transactions, storeName)
    }
    val csvReport = remember(reportState, storeName) {
        ReportExporter.buildCsvReport(reportState.periodLabel, reportState.transactions, storeName)
    }
    val fileStamp = if (periodMode == ReportPeriodMode.SELECTED_DAY) selectedDateKey else "7hari_${DateUtils.todayKey()}"

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csvReport.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("Laporan CSV berhasil disimpan")
            } catch (e: Exception) {
                onShowMessage("Gagal menyimpan file: ${e.message}")
            }
        }
    }

    val saveTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(textReport.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("Laporan TXT berhasil disimpan")
            } catch (e: Exception) {
                onShowMessage("Gagal menyimpan file: ${e.message}")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ekspor Laporan Penjualan",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Format siap baca (.TXT) & Excel (.CSV)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = textReport,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        try {
                            val fileName = "Laporan_Tajir_$fileStamp.txt"
                            val uri = ReportExporter.saveTempReportFile(context, fileName, textReport)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Laporan Penjualan Tajir - ${reportState.periodLabel}")
                                putExtra(Intent.EXTRA_TEXT, textReport)
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Laporan Penjualan"))
                        } catch (e: Exception) {
                            onShowMessage("Gagal membagikan laporan: ${e.message}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_report_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bagikan Laporan (WhatsApp / Email)")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            saveCsvLauncher.launch("Laporan_Tajir_$fileStamp.csv")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Simpan CSV")
                    }
                    OutlinedButton(
                        onClick = {
                            saveTxtLauncher.launch("Laporan_Tajir_$fileStamp.txt")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simpan TXT")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Laporan Tajir", textReport))
                        onShowMessage("Laporan disalin ke clipboard")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Salin Teks Laporan")
                }
            }
        }
    }
}

@Composable
private fun BestSellerRankingCard(
    periodLabel: String,
    transactions: List<CloudSaleWithItems>
) {
    var sortByRevenue by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }

    val bestSellers: List<BestSellerItemSummary> = remember(transactions, sortByRevenue) {
        ReportExporter.aggregateBestSellers(
            transactions = transactions,
            limit = 10,
            sortByRevenue = sortByRevenue
        )
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("best_seller_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarningAmberContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = "Barang Best Seller",
                            tint = GoldSecondary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (bestSellers.isEmpty()) {
                                "Barang Best Seller"
                            } else {
                                "Barang Best Seller (Top ${bestSellers.size})"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Produk terlaris pada $periodLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (bestSellers.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada barang terjual pada periode ini. Daftar produk terlaris akan muncul otomatis setelah transaksi tercatat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !sortByRevenue,
                        onClick = { sortByRevenue = false },
                        label = { Text("Terlaris (Qty)") },
                        modifier = Modifier.testTag("best_seller_sort_qty")
                    )
                    FilterChip(
                        selected = sortByRevenue,
                        onClick = { sortByRevenue = true },
                        label = { Text("Omzet Tertinggi") },
                        modifier = Modifier.testTag("best_seller_sort_revenue")
                    )
                }

                val maxMetric = if (sortByRevenue) {
                    bestSellers.maxOf { it.totalRevenue }.coerceAtLeast(1L).toFloat()
                } else {
                    bestSellers.maxOf { it.totalQuantity }.coerceAtLeast(1).toFloat()
                }
                val visibleList = if (showAll) bestSellers else bestSellers.take(5)

                visibleList.forEachIndexed { idx, item ->
                    val rank = idx + 1
                    val badgeBg = when (rank) {
                        1 -> WarningAmberContainer
                        2 -> MaterialTheme.colorScheme.primaryContainer
                        3 -> ProfitGreenContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val badgeFg = when (rank) {
                        1 -> GoldSecondary
                        2 -> MaterialTheme.colorScheme.primary
                        3 -> ProfitGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val metricRatio = if (sortByRevenue) {
                        item.totalRevenue.toFloat() / maxMetric
                    } else {
                        item.totalQuantity.toFloat() / maxMetric
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(
                            width = if (rank == 1) 1.5.dp else 1.dp,
                            color = if (rank == 1) GoldSecondary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = "#$rank",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.productName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.variantSummary.isNotBlank()) {
                                            Text(
                                                text = item.variantSummary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${item.categoryName} • ${item.transactionCount} transaksi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${item.totalQuantity} pcs",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = CurrencyFormatter.formatRupiah(item.totalRevenue),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Untung ${CurrencyFormatter.formatCompactRupiah(item.totalProfit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ProfitGreen
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { metricRatio.coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }

                if (bestSellers.size > 5) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(if (showAll) "Tampilkan Top 5" else "Lihat Semua (${bestSellers.size} Produk)")
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerStockMovementRecapCard(
    stockHistories: List<StockHistoryDoc>,
    periodMode: ReportPeriodMode,
    onOpenFullHistory: () -> Unit
) {
    val activePeriod = if (periodMode == ReportPeriodMode.LAST_7_DAYS) {
        StockHistoryPeriodFilter.LAST_7_DAYS
    } else {
        StockHistoryPeriodFilter.TODAY
    }
    val scopedLogs = remember(stockHistories, activePeriod) {
        StockHistorySummarizer.filterHistories(
            histories = stockHistories,
            typeFilter = StockHistoryFilterType.ALL,
            periodFilter = activePeriod
        )
    }
    val summary = remember(scopedLogs) {
        StockHistorySummarizer.summarize(scopedLogs)
    }
    val recentLogs = remember(stockHistories) {
        stockHistories
            .filter { it.changeTypeEnum != StockChangeType.PENJUALAN }
            .sortedByDescending { it.timestampMillis }
            .take(4)
            .ifEmpty { stockHistories.sortedByDescending { it.timestampMillis }.take(4) }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_stock_movement_recap_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Rekapan Tambah dan Kurang Stok",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Rekapan Tambah & Kurang Stok",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Ringkasan mutasi (${activePeriod.label}) • Total ${stockHistories.size} catatan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onOpenFullHistory,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("laporan_open_stock_history_button")
                ) {
                    Text("Riwayat", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = ProfitGreenContainer.copy(alpha = 0.65f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Ditambah (+)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+${summary.totalAddedUnits} pcs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = ProfitGreen
                        )
                        Text(
                            text = "${summary.totalAddedEvents}x catatan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = DangerCrimsonContainer.copy(alpha = 0.55f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Dikurangi (-)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "-${summary.totalManualReducedUnits} pcs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = DangerCrimson
                        )
                        Text(
                            text = "${summary.totalManualReducedEvents}x koreksi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Terjual Kasir",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "-${summary.totalSalesReducedUnits} pcs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${summary.totalSalesEvents}x item",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (recentLogs.isEmpty()) {
                Text(
                    text = "Belum ada riwayat penambahan atau pengurangan stok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Aktivitas Stok Terbaru (Owner & Pegawai)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recentLogs.forEach { log ->
                    val isPos = log.quantityDelta >= 0
                    val deltaColor = if (isPos) ProfitGreen else DangerCrimson
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.productName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${log.changeTypeEnum.displayName} • Oleh: ${log.actorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (log.note.isNotBlank()) {
                                    Text(
                                        text = "${DateUtils.formatDateTime(log.timestampMillis)} • ${log.note}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isPos) "+${log.quantityDelta}" else "${log.quantityDelta}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = deltaColor
                                )
                                Text(
                                    text = "Sisa ${log.stockAfter}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
