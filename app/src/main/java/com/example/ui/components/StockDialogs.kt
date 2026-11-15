package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.StockChangeType
import com.example.data.remote.ProductDoc
import com.example.data.remote.StockHistoryDoc
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.DangerCrimsonContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.util.DateUtils
import com.example.util.StockHistoryFilterType
import com.example.util.StockHistoryPeriodFilter
import com.example.util.StockHistorySummarizer

@Composable
fun StaffStockAdjustmentDialog(
    product: ProductDoc,
    initialIsAddition: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (isAddition: Boolean, quantity: Int, note: String) -> Unit
) {
    var isAddition by remember { mutableStateOf(initialIsAddition) }
    var qtyText by remember { mutableStateOf("1") }
    var noteText by remember { mutableStateOf("") }

    val qty = qtyText.toIntOrNull() ?: 0
    val resultingStock = if (isAddition) product.stock + qty else product.stock - qty
    val isInvalidReduction = !isAddition && qty > product.stock
    val isValid = qty > 0 && !isInvalidReduction

    val presetReasons = remember(isAddition) {
        if (isAddition) {
            listOf("Barang masuk supplier", "Retur pelanggan", "Koreksi opname (+)")
        } else {
            listOf("Barang rusak / cacat", "Stok hilang / selisih", "Koreksi opname (-)")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isAddition) "Tambah Stok Barang" else "Kurangi Stok Barang",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${product.skuCode} • Stok saat ini: ${product.stock} pcs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Mode switcher (+ Tambah vs - Kurangi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Tambah (+)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adjust_mode_add_chip")
                    )
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false },
                        leadingIcon = {
                            Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Kurangi (-)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adjust_mode_reduce_chip")
                    )
                }

                NumericOutlinedField(
                    value = qtyText,
                    onValueChange = { qtyText = it.take(5) },
                    label = if (isAddition) "Jumlah Penambahan" else "Jumlah Pengurangan",
                    suffixText = " pcs",
                    isError = isInvalidReduction,
                    supportingText = if (isInvalidReduction) {
                        "Stok saat ini hanya ${product.stock} pcs"
                    } else null,
                    modifier = Modifier.testTag("staff_adjust_qty_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 5, 10, 20).forEach { preset ->
                        item {
                            FilterChip(
                                selected = qty == preset,
                                onClick = { qtyText = preset.toString() },
                                label = { Text(if (isAddition) "+$preset" else "-$preset") }
                            )
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        isInvalidReduction -> DangerCrimsonContainer
                        isAddition -> ProfitGreenContainer.copy(alpha = 0.7f)
                        else -> WarningAmberContainer.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stok setelah disesuaikan",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isInvalidReduction) DangerCrimson else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isInvalidReduction) "Melebihi stok" else "${product.stock} -> $resultingStock pcs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isInvalidReduction -> DangerCrimson
                                isAddition -> ProfitGreen
                                else -> WarningAmber
                            }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetReasons) { reason ->
                        FilterChip(
                            selected = noteText.equals(reason, ignoreCase = true),
                            onClick = { noteText = reason },
                            label = { Text(reason, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(150) },
                    label = { Text("Keterangan / Alasan (Opsional)") },
                    placeholder = {
                        Text(if (isAddition) "Misal: Barang datang dari supplier" else "Misal: Kemasan rusak / opname")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_adjust_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(isAddition, qty, noteText) },
                enabled = isValid,
                colors = if (isAddition) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(containerColor = DangerCrimson)
                },
                modifier = Modifier.testTag("confirm_staff_adjust_stock_button")
            ) {
                Text(if (isAddition) "Tambah +$qty" else "Kurangi -$qty")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun StockHistoryDialog(
    storeName: String,
    histories: List<StockHistoryDoc>,
    onShowMessage: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var typeFilter by remember { mutableStateOf(StockHistoryFilterType.ALL) }
    var periodFilter by remember { mutableStateOf(StockHistoryPeriodFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val periodScopedHistories = remember(histories, periodFilter) {
        StockHistorySummarizer.filterHistories(
            histories = histories,
            typeFilter = StockHistoryFilterType.ALL,
            periodFilter = periodFilter,
            query = ""
        )
    }

    val summary = remember(periodScopedHistories) {
        StockHistorySummarizer.summarize(periodScopedHistories)
    }

    val filteredHistories = remember(histories, typeFilter, periodFilter, searchQuery) {
        StockHistorySummarizer.filterHistories(
            histories = histories,
            typeFilter = typeFilter,
            periodFilter = periodFilter,
            query = searchQuery
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Rekapan & Riwayat Stok",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${filteredHistories.size} catatan ditampilkan (${periodFilter.label})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val text = StockHistorySummarizer.buildRecapText(
                                storeName = storeName,
                                periodLabel = "${periodFilter.label} (${typeFilter.label})",
                                histories = filteredHistories
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Rekap Stok $storeName", text))
                            onShowMessage("Rekapan stok disalin ke clipboard")
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Salin rekapan stok")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup riwayat stok")
                    }
                }

                // Ringkasan Penambahan vs Pengurangan Stok
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StockRecapMiniCard(
                        label = "Penambahan (+)",
                        value = "+${summary.totalAddedUnits} pcs",
                        subtitle = "${summary.totalAddedEvents}x catatan",
                        containerColor = ProfitGreenContainer.copy(alpha = 0.65f),
                        accentColor = ProfitGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StockRecapMiniCard(
                        label = "Kurang Manual (-)",
                        value = "-${summary.totalManualReducedUnits} pcs",
                        subtitle = "${summary.totalManualReducedEvents}x koreksi",
                        containerColor = DangerCrimsonContainer.copy(alpha = 0.6f),
                        accentColor = DangerCrimson,
                        modifier = Modifier.weight(1f)
                    )
                    StockRecapMiniCard(
                        label = "Terjual Kasir",
                        value = "-${summary.totalSalesReducedUnits} pcs",
                        subtitle = "${summary.totalSalesEvents}x jual",
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Period Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(StockHistoryPeriodFilter.entries) { period ->
                        FilterChip(
                            selected = periodFilter == period,
                            onClick = { periodFilter = period },
                            label = { Text(period.label) },
                            modifier = Modifier.testTag("stock_history_period_${period.name}")
                        )
                    }
                }

                // Type Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(StockHistoryFilterType.entries) { type ->
                        FilterChip(
                            selected = typeFilter == type,
                            onClick = { typeFilter = type },
                            label = { Text(type.label) },
                            modifier = Modifier.testTag("stock_history_type_${type.name}")
                        )
                    }
                }

                // Search bar (Produk / Nama Petugas / Catatan)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari produk, petugas, atau catatan…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_history_search_input")
                )

                HorizontalDivider()

                if (filteredHistories.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Tidak ada catatan pergerakan stok",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Penambahan atau pengurangan stok oleh Owner maupun Pegawai akan tercatat otomatis di sini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 310.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHistories, key = { it.id.ifBlank { "${it.productId}_${it.timestampMillis}_${it.quantityDelta}" } }) { log ->
                            val isPositive = log.quantityDelta >= 0
                            val isSale = log.changeTypeEnum == StockChangeType.PENJUALAN
                            val accentColor = when {
                                isPositive -> ProfitGreen
                                isSale -> MaterialTheme.colorScheme.primary
                                else -> DangerCrimson
                            }
                            val badgeContainer = when {
                                isPositive -> ProfitGreenContainer
                                isSale -> MaterialTheme.colorScheme.primaryContainer
                                else -> DangerCrimsonContainer
                            }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = badgeContainer
                                            ) {
                                                Text(
                                                    text = log.changeTypeEnum.displayName,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = accentColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = DateUtils.formatDateTime(log.timestampMillis),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = log.productName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Oleh: ${log.actorName}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (log.note.isNotBlank()) {
                                            Text(
                                                text = "Catatan: ${log.note}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isPositive) Icons.Rounded.AddCircleOutline else Icons.Rounded.RemoveCircleOutline,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = if (isPositive) "+${log.quantityDelta}" else "${log.quantityDelta}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = accentColor
                                            )
                                        }
                                        Text(
                                            text = "Stok: ${log.stockAfter} pcs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val text = StockHistorySummarizer.buildRecapText(
                                    storeName = storeName,
                                    periodLabel = "${periodFilter.label} (${typeFilter.label})",
                                    histories = filteredHistories
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Rekapan Stok $storeName - ${periodFilter.label}")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekapan Stok"))
                            } catch (e: Exception) {
                                onShowMessage("Gagal membagikan rekapan stok: ${e.message}")
                            }
                        },
                        enabled = filteredHistories.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_stock_history_recap_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bagikan Rekap")
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
private fun StockRecapMiniCard(
    label: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
