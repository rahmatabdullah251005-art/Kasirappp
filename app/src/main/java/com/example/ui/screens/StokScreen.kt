package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.UserRole
import com.example.data.remote.ProductDoc
import com.example.data.remote.StockHistoryDoc
import com.example.data.remote.StoreCategoryDoc
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryManagementDialog
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.NumericOutlinedField
import com.example.ui.components.ProductAttributeChipsRow
import com.example.ui.components.RestockDialog
import com.example.ui.components.SingleCodeQrScannerDialog
import com.example.ui.components.StaffStockAdjustmentDialog
import com.example.ui.components.StockHistoryDialog
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.util.CurrencyFormatter
import com.example.util.QrCodeGenerator
import com.example.util.StockHistoryFilterType
import com.example.util.StockHistoryPeriodFilter
import com.example.util.StockHistorySummarizer
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StokScreen(
    storeName: String = "UM TAZKIYAH",
    userRole: UserRole,
    products: List<ProductDoc>,
    categories: List<StoreCategoryDoc>,
    stockHistories: List<StockHistoryDoc>,
    prefilledSkuRequest: String?,
    onClearPrefilledSkuRequest: () -> Unit,
    openLowStockFilterTrigger: Int = 0,
    generateNextSku: (String) -> String,
    onSaveProduct: (
        existingProduct: ProductDoc?,
        skuCode: String,
        name: String,
        categoryCode: String,
        variantName: String,
        color: String,
        size: String,
        variantInfo: String,
        costPrice: Long,
        sellingPrice: Long,
        discountPercent: Int,
        stock: Int,
        minStockAlert: Int,
        onSuccess: () -> Unit
    ) -> Unit,
    onUpdateProductPricing: (
        productId: String,
        costPrice: Long,
        sellingPrice: Long,
        discountPercent: Int,
        onSuccess: () -> Unit
    ) -> Unit,
    onRestockProduct: (
        product: ProductDoc,
        addQty: Int,
        newCostPrice: Long,
        note: String,
        onSuccess: () -> Unit
    ) -> Unit,
    onAdjustStock: (
        product: ProductDoc,
        isAddition: Boolean,
        quantity: Int,
        note: String,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onDeleteProduct: (ProductDoc) -> Unit,
    onCreateCategory: (
        displayName: String,
        customPrefix: String,
        colorIndex: Int,
        onSuccess: (StoreCategoryDoc) -> Unit
    ) -> Unit,
    onDeleteCategory: (StoreCategoryDoc) -> Unit,
    onRestoreDefaultCategories: () -> Unit,
    onSeedStarterCatalog: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val isOwner = userRole == UserRole.OWNER
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, LOW, PROMO, or category.code
    var showFormSheet by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var prefilledSkuForSheet by remember { mutableStateOf<String?>(null) }

    var qrPreviewProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var pricingTargetProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var restockTargetProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var stockAdjustTarget by remember { mutableStateOf<Pair<ProductDoc, Boolean>?>(null) }
    var deleteConfirmProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var lastAddedCategoryCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefilledSkuRequest) {
        if (!prefilledSkuRequest.isNullOrBlank() && isOwner) {
            editingProduct = null
            prefilledSkuForSheet = prefilledSkuRequest
            showFormSheet = true
            onClearPrefilledSkuRequest()
        }
    }

    LaunchedEffect(openLowStockFilterTrigger) {
        if (openLowStockFilterTrigger > 0) {
            filterMode = "LOW"
        }
    }

    LaunchedEffect(categories) {
        if (filterMode !in listOf("ALL", "LOW", "PROMO") &&
            categories.none { it.code.equals(filterMode, ignoreCase = true) }
        ) {
            filterMode = "ALL"
        }
    }

    val lowStockCount = remember(products) { products.count { it.isLowStock } }
    val promoCount = remember(products) { products.count { it.discountPercent > 0 } }
    val totalUnits = remember(products) { products.sumOf { it.stock } }
    val totalInventoryCapital = remember(products) { products.sumOf { it.costPrice * it.stock } }

    val filteredProducts = remember(products, searchQuery, filterMode) {
        val q = searchQuery.trim().lowercase()
        products.filter { item ->
            val matchFilter = when (filterMode) {
                "ALL" -> true
                "LOW" -> item.isLowStock
                "PROMO" -> item.discountPercent > 0
                else -> item.category.equals(filterMode, ignoreCase = true)
            }
            val matchQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.skuCode.lowercase().contains(q) ||
                item.variantName.lowercase().contains(q) ||
                item.color.lowercase().contains(q) ||
                item.size.lowercase().contains(q) ||
                item.variantInfo.lowercase().contains(q)
            matchFilter && matchQuery
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isOwner) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiMetricCard(
                            title = "Nilai Aset Modal",
                            value = CurrencyFormatter.formatCompactRupiah(totalInventoryCapital),
                            subtitle = "${products.size} produk • $totalUnits unit",
                            icon = Icons.Rounded.Wallet,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Stok Menipis",
                            value = "$lowStockCount Item",
                            subtitle = if (lowStockCount > 0) "Segera restok barang" else "Semua stok aman",
                            icon = Icons.Rounded.WarningAmber,
                            accentColor = if (lowStockCount > 0) WarningAmber else ProfitGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    val todayStockSummary = remember(stockHistories) {
                        val todayLogs = StockHistorySummarizer.filterHistories(
                            histories = stockHistories,
                            typeFilter = StockHistoryFilterType.ALL,
                            periodFilter = StockHistoryPeriodFilter.TODAY
                        )
                        StockHistorySummarizer.summarize(todayLogs)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_stok_recap_strip")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rekapan Mutasi Stok Hari Ini",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "+${todayStockSummary.totalAddedUnits} masuk • -${todayStockSummary.totalManualReducedUnits} kurang manual • -${todayStockSummary.totalSalesReducedUnits} terjual",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { showHistoryDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("stok_open_recap_button")
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("History", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Mode Stok Pegawai Kasir: Anda dapat memeriksa stok, menambah (+) atau mengurangi (-) stok barang. Data modal, harga & katalog produk khusus Owner.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari stok atau kode QR…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Bersihkan")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOwner) {
                        FilledTonalButton(
                            onClick = { showHistoryDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "Riwayat stok")
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filterMode == "ALL",
                            onClick = { filterMode = "ALL" },
                            label = { Text("Semua (${products.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "PROMO",
                            onClick = { filterMode = "PROMO" },
                            label = { Text("Promo ($promoCount)") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "LOW",
                            onClick = { filterMode = "LOW" },
                            label = { Text("Menipis ($lowStockCount)") }
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        val catCount = products.count { it.category.equals(cat.code, ignoreCase = true) }
                        FilterChip(
                            selected = filterMode.equals(cat.code, ignoreCase = true),
                            onClick = { filterMode = cat.code },
                            label = { Text("${cat.displayName} ($catCount)") }
                        )
                    }
                    if (isOwner) {
                        item {
                            FilledTonalButton(
                                onClick = { showCategoryManagerDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("manage_categories_chip")
                            ) {
                                Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("+/- Kategori", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (products.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_catalog_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = stringResource(R.string.empty_catalog_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isOwner) {
                                Button(
                                    onClick = onSeedStarterCatalog,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.load_starter_catalog))
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    StokProductCard(
                        product = product,
                        categories = categories,
                        isOwner = isOwner,
                        onShowQr = { qrPreviewProduct = product },
                        onEditPricing = { pricingTargetProduct = product },
                        onRestock = { restockTargetProduct = product },
                        onStaffAdjustStock = { isAddition ->
                            stockAdjustTarget = product to isAddition
                        },
                        onEdit = {
                            editingProduct = product
                            prefilledSkuForSheet = null
                            showFormSheet = true
                        },
                        onDelete = { deleteConfirmProduct = product }
                    )
                }
            }
        }

        if (isOwner) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingProduct = null
                    prefilledSkuForSheet = null
                    showFormSheet = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_product)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_product_fab")
            )
        }
    }

    if (showFormSheet && isOwner) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFormSheet = false },
            sheetState = sheetState
        ) {
            ProductFormSheetContent(
                existingProduct = editingProduct,
                initialSku = prefilledSkuForSheet,
                categories = categories,
                lastAddedCategoryCode = lastAddedCategoryCode,
                onClearLastAddedCategory = { lastAddedCategoryCode = null },
                generateNextSku = generateNextSku,
                onOpenCategoryManager = { showCategoryManagerDialog = true },
                onCancel = { showFormSheet = false },
                onSave = { existing, sku, name, categoryCode, variantName, color, size, variantInfo, cost, sell, discPct, stock, minAlert ->
                    onSaveProduct(existing, sku, name, categoryCode, variantName, color, size, variantInfo, cost, sell, discPct, stock, minAlert) {
                        showFormSheet = false
                    }
                }
            )
        }
    }

    qrPreviewProduct?.let { product ->
        ProductQrPreviewDialog(
            product = product,
            onDismiss = { qrPreviewProduct = null }
        )
    }

    pricingTargetProduct?.let { product ->
        QuickPriceDiscountDialog(
            product = product,
            onDismiss = { pricingTargetProduct = null },
            onConfirm = { newCost, newSell, newDiscountPct ->
                onUpdateProductPricing(product.id, newCost, newSell, newDiscountPct) {
                    pricingTargetProduct = null
                }
            }
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

    stockAdjustTarget?.let { (product, initialIsAddition) ->
        StaffStockAdjustmentDialog(
            product = product,
            initialIsAddition = initialIsAddition,
            onDismiss = { stockAdjustTarget = null },
            onConfirm = { isAddition, qty, note ->
                onAdjustStock(product, isAddition, qty, note) {
                    stockAdjustTarget = null
                }
            }
        )
    }

    deleteConfirmProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProduct = null },
            title = { Text("Hapus Barang?") },
            text = { Text("Yakin ingin menghapus '${product.name}' (${product.skuCode}) dari katalog stok?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(product)
                        deleteConfirmProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmProduct = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showHistoryDialog && isOwner) {
        StockHistoryDialog(
            storeName = storeName,
            histories = stockHistories,
            onShowMessage = onShowMessage,
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showCategoryManagerDialog && isOwner) {
        CategoryManagementDialog(
            categories = categories,
            products = products,
            onCreateCategory = { name, prefix, colorIdx, onDone ->
                onCreateCategory(name, prefix, colorIdx) { created ->
                    lastAddedCategoryCode = created.code
                    onDone(created)
                }
            },
            onDeleteCategory = onDeleteCategory,
            onRestoreDefaults = onRestoreDefaultCategories,
            onDismiss = { showCategoryManagerDialog = false }
        )
    }
}

@Composable
private fun StokProductCard(
    product: ProductDoc,
    categories: List<StoreCategoryDoc>,
    isOwner: Boolean,
    onShowQr: () -> Unit,
    onEditPricing: () -> Unit,
    onRestock: () -> Unit,
    onStaffAdjustStock: (isAddition: Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryBadge(category = product.resolvedCategory(categories))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = product.skuCode,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (product.discountPercent > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ProfitGreenContainer
                        ) {
                            Text(
                                text = "-${product.discountPercent}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ProfitGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                StockStatusBadge(stock = product.stock, minAlert = product.minStockAlert)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                ProductAttributeChipsRow(product = product)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (product.discountPercent > 0) "Harga Promo" else "Harga Jual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (product.discountPercent > 0) {
                        Text(
                            text = CurrencyFormatter.formatRupiah(product.sellingPrice),
                            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatRupiah(product.effectiveSellingPrice),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isOwner) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Modal (HPP)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatRupiah(product.costPrice),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Untung / pcs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${CurrencyFormatter.formatRupiah(product.profitPerUnit)} (${String.format("%.0f", product.marginPercent)}%)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (product.profitPerUnit >= 0L) ProfitGreen else DangerCrimson
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onShowQr,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("QR", style = MaterialTheme.typography.labelMedium)
                }
                if (isOwner) {
                    OutlinedButton(
                        onClick = onEditPricing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Rounded.LocalOffer, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Harga", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = onRestock,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Rounded.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restok", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit produk")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus produk", tint = DangerCrimson)
                    }
                } else {
                    FilledTonalButton(
                        onClick = { onStaffAdjustStock(true) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("staff_add_stock_button_${product.id}")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onStaffAdjustStock(false) },
                        enabled = product.stock > 0,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("staff_reduce_stock_button_${product.id}")
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kurangi", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductFormSheetContent(
    existingProduct: ProductDoc?,
    initialSku: String?,
    categories: List<StoreCategoryDoc>,
    lastAddedCategoryCode: String?,
    onClearLastAddedCategory: () -> Unit,
    generateNextSku: (String) -> String,
    onOpenCategoryManager: () -> Unit,
    onCancel: () -> Unit,
    onSave: (
        existingProduct: ProductDoc?,
        skuCode: String,
        name: String,
        categoryCode: String,
        variantName: String,
        color: String,
        size: String,
        variantInfo: String,
        costPrice: Long,
        sellingPrice: Long,
        discountPercent: Int,
        stock: Int,
        minStockAlert: Int
    ) -> Unit
) {
    val defaultCatCode = existingProduct?.category
        ?: categories.firstOrNull()?.code
        ?: ProductCategory.BAJU.name
    var categoryCode by remember { mutableStateOf(defaultCatCode) }
    var skuCode by remember {
        mutableStateOf(
            existingProduct?.skuCode ?: initialSku ?: generateNextSku(defaultCatCode)
        )
    }
    LaunchedEffect(lastAddedCategoryCode) {
        if (!lastAddedCategoryCode.isNullOrBlank()) {
            categoryCode = lastAddedCategoryCode
            if (existingProduct == null && initialSku == null) {
                skuCode = generateNextSku(lastAddedCategoryCode)
            }
            onClearLastAddedCategory()
        }
    }
    var name by remember { mutableStateOf(existingProduct?.name ?: "") }
    var includeAttributes by remember {
        mutableStateOf(existingProduct?.hasAttributes == true)
    }
    var variantName by remember {
        mutableStateOf(
            existingProduct?.let { p ->
                if (p.variantName.isNotBlank() || p.color.isNotBlank() || p.size.isNotBlank()) {
                    p.variantName
                } else {
                    p.variantInfo
                }
            } ?: ""
        )
    }
    var productColor by remember { mutableStateOf(existingProduct?.color ?: "") }
    var productSize by remember { mutableStateOf(existingProduct?.size ?: "") }
    var costPriceText by remember { mutableStateOf(existingProduct?.costPrice?.toString() ?: "") }
    var sellingPriceText by remember { mutableStateOf(existingProduct?.sellingPrice?.toString() ?: "") }
    var discountPercentText by remember {
        mutableStateOf(
            if ((existingProduct?.discountPercent ?: 0) > 0) existingProduct!!.discountPercent.toString() else ""
        )
    }
    var stockText by remember { mutableStateOf(existingProduct?.stock?.toString() ?: "10") }
    var minAlertText by remember { mutableStateOf(existingProduct?.minStockAlert?.toString() ?: "5") }
    var showSkuScanner by remember { mutableStateOf(false) }

    val selectableCategories = remember(categories, categoryCode) {
        if (categories.any { it.code.equals(categoryCode, ignoreCase = true) }) {
            categories
        } else if (categories.isEmpty()) {
            listOf(StoreCategoryDoc.resolve(categoryCode, emptyList()))
        } else {
            categories + StoreCategoryDoc.resolve(categoryCode, emptyList())
        }
    }

    val costPrice = costPriceText.toLongOrNull() ?: 0L
    val sellingPrice = sellingPriceText.toLongOrNull() ?: 0L
    val discountPct = (discountPercentText.toIntOrNull() ?: 0).coerceIn(0, 99)
    val effectivePrice = if (discountPct in 1..99) {
        (sellingPrice * (100 - discountPct)) / 100L
    } else {
        sellingPrice
    }
    val profit = effectivePrice - costPrice
    val margin = if (effectivePrice > 0L) (profit.toDouble() / effectivePrice.toDouble()) * 100.0 else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (existingProduct == null) "Tambah Barang Baru" else "Edit Barang, Harga & Diskon",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Kategori Toko", style = MaterialTheme.typography.labelLarge)
            TextButton(
                onClick = onOpenCategoryManager,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("open_category_manager_in_form")
            ) {
                Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tambah / Kurangi", style = MaterialTheme.typography.labelMedium)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selectableCategories, key = { it.code }) { item ->
                FilterChip(
                    selected = categoryCode.equals(item.code, ignoreCase = true),
                    onClick = {
                        categoryCode = item.code
                        if (existingProduct == null && initialSku == null) {
                            skuCode = generateNextSku(item.code)
                        }
                    },
                    label = { Text(item.displayName) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = skuCode,
                onValueChange = { skuCode = it.uppercase() },
                label = { Text("Kode QR / Barcode / SKU") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("product_sku_input")
            )
            IconButton(onClick = { showSkuScanner = true }) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan barcode/QR")
            }
            IconButton(onClick = { skuCode = generateNextSku(categoryCode) }) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generate kode otomatis")
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nama Barang") },
            placeholder = { Text("Misal: Gamis Katun / Buku Fiqih / Sajadah") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("product_name_input")
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (includeAttributes) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Opsi Varian, Warna & Ukuran",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (includeAttributes) {
                                    "Aktif • Isi keterangan sesuai barang"
                                } else {
                                    "Opsional • Aktifkan untuk tambah varian/warna/ukuran"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = includeAttributes,
                        onCheckedChange = { includeAttributes = it },
                        modifier = Modifier.testTag("toggle_product_attributes_switch")
                    )
                }

                AnimatedVisibility(visible = includeAttributes) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider()

                        OutlinedTextField(
                            value = variantName,
                            onValueChange = { variantName = it },
                            label = { Text("Keterangan Varian / Motif (Opsional)") },
                            placeholder = { Text("Misal: Polos, Motif Bunga, Hardcover") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_variant_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Polos", "Motif", "Bordir", "Hardcover", "Travel").forEach { preset ->
                                item {
                                    FilterChip(
                                        selected = variantName.equals(preset, ignoreCase = true),
                                        onClick = {
                                            variantName = if (variantName.equals(preset, ignoreCase = true)) "" else preset
                                        },
                                        label = { Text(preset) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = productColor,
                            onValueChange = { productColor = it },
                            label = { Text("Warna (Opsional)") },
                            placeholder = { Text("Misal: Sage Green, Hitam, Putih") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_color_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Hitam", "Putih", "Navy", "Sage Green", "Mocca", "Maroon").forEach { preset ->
                                item {
                                    FilterChip(
                                        selected = productColor.equals(preset, ignoreCase = true),
                                        onClick = {
                                            productColor = if (productColor.equals(preset, ignoreCase = true)) "" else preset
                                        },
                                        label = { Text(preset) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = productSize,
                            onValueChange = { productSize = it },
                            label = { Text("Ukuran (Opsional)") },
                            placeholder = { Text("Misal: S, M, L, XL, All Size, A5") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_size_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("S", "M", "L", "XL", "XXL", "All Size", "Dewasa", "Anak").forEach { preset ->
                                item {
                                    FilterChip(
                                        selected = productSize.equals(preset, ignoreCase = true),
                                        onClick = {
                                            productSize = if (productSize.equals(preset, ignoreCase = true)) "" else preset
                                        },
                                        label = { Text(preset) }
                                    )
                                }
                            }
                        }

                        val previewSummary = ProductDoc.composeVariantSummary(
                            variantName = variantName,
                            color = productColor,
                            size = productSize
                        )
                        if (previewSummary.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Preview: $previewSummary",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NumericOutlinedField(
                value = costPriceText,
                onValueChange = { costPriceText = it },
                label = "Harga Modal (HPP)",
                prefixText = "Rp ",
                modifier = Modifier
                    .weight(1f)
                    .testTag("product_cost_input")
            )
            NumericOutlinedField(
                value = sellingPriceText,
                onValueChange = { sellingPriceText = it },
                label = "Harga Jual Normal",
                prefixText = "Rp ",
                modifier = Modifier
                    .weight(1f)
                    .testTag("product_sell_input")
            )
        }

        if (costPrice > 0L) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set Markup:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(20, 30, 50).forEach { pct ->
                    OutlinedButton(
                        onClick = {
                            val suggested = ((costPrice * (100.0 + pct) / 100.0) / 500.0).roundToLong() * 500L
                            sellingPriceText = suggested.coerceAtLeast(costPrice).toString()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("+$pct%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        NumericOutlinedField(
            value = discountPercentText,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }.take(2)
                discountPercentText = filtered
            },
            label = "Diskon Promo Produk (0-99%)",
            suffixText = " %",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("product_discount_input")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 5, 10, 15, 20, 30).forEach { preset ->
                item {
                    FilterChip(
                        selected = discountPct == preset,
                        onClick = {
                            discountPercentText = if (preset == 0) "" else preset.toString()
                        },
                        label = { Text(if (preset == 0) "Tanpa Diskon" else "$preset%") }
                    )
                }
            }
        }

        if (sellingPrice > 0L || costPrice > 0L) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (profit >= 0L) ProfitGreenContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (discountPct > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Harga Setelah Diskon $discountPct%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (profit >= 0L) ProfitGreen else DangerCrimson
                            )
                            Text(
                                text = CurrencyFormatter.formatRupiah(effectivePrice),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (profit >= 0L) ProfitGreen else DangerCrimson
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimasi Keuntungan / pcs",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (profit >= 0L) ProfitGreen else DangerCrimson
                        )
                        Text(
                            text = "${CurrencyFormatter.formatRupiah(profit)} (${String.format("%.1f", margin)}%)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (profit >= 0L) ProfitGreen else DangerCrimson
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NumericOutlinedField(
                value = stockText,
                onValueChange = { stockText = it },
                label = "Jumlah Stok",
                suffixText = " pcs",
                modifier = Modifier
                    .weight(1f)
                    .testTag("product_stock_input")
            )
            NumericOutlinedField(
                value = minAlertText,
                onValueChange = { minAlertText = it },
                label = "Batas Stok Menipis",
                suffixText = " pcs",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Batal")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val effectiveVariant = if (includeAttributes) variantName.trim() else ""
                    val effectiveColor = if (includeAttributes) productColor.trim() else ""
                    val effectiveSize = if (includeAttributes) productSize.trim() else ""
                    val effectiveSummary = ProductDoc.composeVariantSummary(
                        variantName = effectiveVariant,
                        color = effectiveColor,
                        size = effectiveSize
                    )
                    onSave(
                        existingProduct,
                        skuCode,
                        name,
                        categoryCode,
                        effectiveVariant,
                        effectiveColor,
                        effectiveSize,
                        effectiveSummary,
                        costPrice,
                        sellingPrice,
                        discountPct,
                        stockText.toIntOrNull() ?: 0,
                        minAlertText.toIntOrNull() ?: 5
                    )
                },
                enabled = skuCode.isNotBlank() && name.isNotBlank() && sellingPriceText.isNotBlank(),
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text("Simpan Barang")
            }
        }
    }

    if (showSkuScanner) {
        SingleCodeQrScannerDialog(
            onDismiss = { showSkuScanner = false },
            onCodeCaptured = { captured ->
                skuCode = captured.uppercase()
                showSkuScanner = false
            }
        )
    }
}

@Composable
private fun QuickPriceDiscountDialog(
    product: ProductDoc,
    onDismiss: () -> Unit,
    onConfirm: (costPrice: Long, sellingPrice: Long, discountPercent: Int) -> Unit
) {
    var costText by remember { mutableStateOf(product.costPrice.toString()) }
    var sellText by remember { mutableStateOf(product.sellingPrice.toString()) }
    var discText by remember {
        mutableStateOf(if (product.discountPercent > 0) product.discountPercent.toString() else "")
    }

    val cost = costText.toLongOrNull() ?: 0L
    val sell = sellText.toLongOrNull() ?: 0L
    val discPct = (discText.toIntOrNull() ?: 0).coerceIn(0, 99)
    val effective = if (discPct in 1..99) (sell * (100 - discPct)) / 100L else sell
    val profit = effective - cost

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur Harga & Diskon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                NumericOutlinedField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = "Harga Modal (HPP)",
                    prefixText = "Rp "
                )
                NumericOutlinedField(
                    value = sellText,
                    onValueChange = { sellText = it },
                    label = "Harga Jual Normal",
                    prefixText = "Rp "
                )
                NumericOutlinedField(
                    value = discText,
                    onValueChange = { discText = it.filter { c -> c.isDigit() }.take(2) },
                    label = "Diskon Produk (%)",
                    suffixText = " %"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 10, 15, 20, 25).forEach { pct ->
                        item {
                            FilterChip(
                                selected = discPct == pct,
                                onClick = { discText = if (pct == 0) "" else pct.toString() },
                                label = { Text(if (pct == 0) "0%" else "$pct%") }
                            )
                        }
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (profit >= 0L) ProfitGreenContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Harga Jual Akhir: ${CurrencyFormatter.formatRupiah(effective)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (profit >= 0L) ProfitGreen else DangerCrimson
                        )
                        Text(
                            text = "Est. Laba / pcs: ${CurrencyFormatter.formatRupiah(profit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (profit >= 0L) ProfitGreen else DangerCrimson
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(cost, sell, discPct) },
                enabled = sell > 0L
            ) {
                Text("Simpan Harga")
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
private fun ProductQrPreviewDialog(
    product: ProductDoc,
    onDismiss: () -> Unit
) {
    val bitmap: Bitmap? = remember(product.skuCode) {
        QrCodeGenerator.generateQrBitmap(product.skuCode, 520)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Label QR Code Produk",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (bitmap != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code ${product.skuCode}",
                            modifier = Modifier
                                .size(210.dp)
                                .padding(12.dp)
                        )
                    }
                }
                Text(
                    text = product.skuCode,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (product.discountPercent > 0) {
                    Text(
                        text = "${CurrencyFormatter.formatRupiah(product.sellingPrice)} (-${product.discountPercent}%)",
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = CurrencyFormatter.formatRupiah(product.effectiveSellingPrice),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}
