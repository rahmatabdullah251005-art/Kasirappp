package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ProductCategory
import com.example.data.remote.ProductDoc
import com.example.data.remote.StoreCategoryDoc
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.DangerCrimsonContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldOnContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.TerracottaContainer
import com.example.ui.theme.TerracottaOnContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.util.StockAlertSeverity
import com.example.util.StockAlertUiState

fun StoreCategoryDoc.icon(): ImageVector = when (code.uppercase()) {
    ProductCategory.BAJU.name -> Icons.Rounded.Checkroom
    ProductCategory.BUKU.name -> Icons.Rounded.AutoStories
    ProductCategory.KEBUTUHAN_MUSLIM.name -> Icons.Rounded.Mosque
    ProductCategory.AKSESORIS.name -> Icons.Rounded.Diamond
    else -> when (kotlin.math.abs(colorIndex) % 6) {
        0 -> Icons.Rounded.Checkroom
        1 -> Icons.Rounded.AutoStories
        2 -> Icons.Rounded.Mosque
        3 -> Icons.Rounded.Diamond
        4 -> Icons.Rounded.Category
        else -> Icons.Rounded.LocalOffer
    }
}

@Composable
fun StoreCategoryDoc.containerColor(): Color = when (kotlin.math.abs(colorIndex) % 6) {
    0 -> EmeraldContainer
    1 -> GoldContainer
    2 -> ProfitGreenContainer
    3 -> TerracottaContainer
    4 -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun StoreCategoryDoc.contentColor(): Color = when (kotlin.math.abs(colorIndex) % 6) {
    0 -> EmeraldOnContainer
    1 -> GoldOnContainer
    2 -> ProfitGreen
    3 -> TerracottaOnContainer
    4 -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
fun CategoryBadge(
    category: StoreCategoryDoc,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = category.containerColor()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = category.icon(),
                contentDescription = category.displayName,
                tint = category.contentColor(),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = category.contentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryBadge(
    category: ProductCategory,
    modifier: Modifier = Modifier
) {
    CategoryBadge(
        category = StoreCategoryDoc(
            id = "cat_${category.name.lowercase()}",
            code = category.name,
            displayName = category.displayName,
            codePrefix = category.codePrefix,
            colorIndex = category.ordinal
        ),
        modifier = modifier
    )
}

@Composable
fun StockStatusBadge(
    stock: Int,
    minAlert: Int,
    modifier: Modifier = Modifier
) {
    val (bg, fg, label) = when {
        stock <= 0 -> Triple(DangerCrimsonContainer, DangerCrimson, "Habis")
        stock <= minAlert -> Triple(WarningAmberContainer, WarningAmber, "Sisa $stock")
        else -> Triple(ProfitGreenContainer, ProfitGreen, "Stok $stock")
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductAttributeChipsRow(
    product: ProductDoc,
    modifier: Modifier = Modifier
) {
    val badges = product.attributeBadges
    val legacyNote = if (badges.isEmpty()) product.variantInfo.trim() else ""
    if (badges.isEmpty() && legacyNote.isBlank()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (badges.isNotEmpty()) {
            badges.forEach { (label, value) ->
                val bgColor = when (label) {
                    "Warna" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                    "Ukuran" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val fgColor = when (label) {
                    "Warna" -> MaterialTheme.colorScheme.onSecondaryContainer
                    "Ukuran" -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = bgColor
                ) {
                    Text(
                        text = "$label: $value",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = fgColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = legacyNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NumericOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefixText: String? = null,
    suffixText: String? = null,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() })
        },
        label = { Text(label) },
        prefix = prefixText?.let { { Text(it) } },
        suffix = suffixText?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun RestockDialog(
    product: ProductDoc,
    onDismiss: () -> Unit,
    onConfirm: (addQty: Int, newCostPrice: Long, note: String) -> Unit
) {
    var addQtyText by remember { mutableStateOf("10") }
    var costText by remember { mutableStateOf(product.costPrice.toString()) }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restok: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Stok saat ini: ${product.stock} pcs (Batas minimum: ${product.minStockAlert} pcs)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NumericOutlinedField(
                    value = addQtyText,
                    onValueChange = { addQtyText = it },
                    label = "Jumlah Tambahan Masuk",
                    suffixText = " pcs"
                )
                NumericOutlinedField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = "Harga Modal Terbaru (HPP)",
                    prefixText = "Rp "
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Catatan Supplier (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = addQtyText.toIntOrNull() ?: 0
                    val cost = costText.toLongOrNull() ?: product.costPrice
                    onConfirm(qty, cost, noteText)
                },
                enabled = (addQtyText.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.testTag("confirm_restock_button")
            ) {
                Text("Simpan Restok")
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
fun LowStockAlertCenterDialog(
    alertState: StockAlertUiState,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onQuickRestock: (ProductDoc) -> Unit,
    onOpenLowStockTab: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (alertState.outOfStockCount > 0) DangerCrimsonContainer else if (alertState.hasAlerts) WarningAmberContainer else ProfitGreenContainer
                        ) {
                            Icon(
                                imageVector = if (alertState.hasAlerts) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (alertState.outOfStockCount > 0) DangerCrimson else if (alertState.hasAlerts) WarningAmber else ProfitGreen,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Notifikasi Stok Owner",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (alertState.hasAlerts) {
                                    "${alertState.outOfStockCount} habis • ${alertState.lowStockOnlyCount} mencapai batas minimum"
                                } else {
                                    "Semua produk berada di atas batas minimum"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup notifikasi")
                    }
                }

                if (!hasNotificationPermission) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notifikasi Sistem Nonaktif",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Aktifkan agar peringatan stok menipis muncul di bar notifikasi HP.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = onRequestNotificationPermission,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Aktifkan")
                            }
                        }
                    }
                }

                HorizontalDivider()

                if (!alertState.hasAlerts) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = ProfitGreen,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Semua Stok Barang Aman",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Anda akan menerima peringatan otomatis begitu ada barang yang stoknya mencapai batas minimum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alertState.alerts, key = { it.product.id }) { item ->
                            val product = item.product
                            val isOut = item.severity == StockAlertSeverity.OUT_OF_STOCK
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (isOut) DangerCrimsonContainer.copy(alpha = 0.45f) else WarningAmberContainer.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    (if (isOut) DangerCrimson else WarningAmber).copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (product.resolvedVariantSummary.isNotBlank()) {
                                            Text(
                                                text = product.resolvedVariantSummary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${product.categoryEnum.displayName} • ${product.skuCode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (isOut) {
                                                "STOK HABIS (0 pcs) • Batas min ${product.minStockAlert} pcs"
                                            } else {
                                                "Sisa ${product.stock} pcs • Batas min ${product.minStockAlert} pcs"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isOut) DangerCrimson else WarningAmber
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { onQuickRestock(product) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("alert_restock_button_${product.id}")
                                    ) {
                                        Icon(Icons.Rounded.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restok")
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
                    if (alertState.hasAlerts) {
                        OutlinedButton(
                            onClick = onOpenLowStockTab,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("open_low_stock_tab_button")
                        ) {
                            Text("Buka Tab Stok")
                        }
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
