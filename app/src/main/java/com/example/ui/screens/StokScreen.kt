package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
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
import com.example.ui.components.CategoryBadge
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.NumericOutlinedField
import com.example.ui.components.RestockDialog
import com.example.ui.components.SingleCodeQrScannerDialog
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.util.CurrencyFormatter
import com.example.util.DateUtils
import com.example.util.QrCodeGenerator
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StokScreen(
    userRole: UserRole,
    products: List<ProductDoc>,
    stockHistories: List<StockHistoryDoc>,
    prefilledSkuRequest: String?,
    onClearPrefilledSkuRequest: () -> Unit,
    openLowStockFilterTrigger: Int = 0,
    generateNextSku: (ProductCategory) -> String,
    onSaveProduct: (
        existingProduct: ProductDoc?,
        skuCode: String,
        name: String,
        category: ProductCategory,
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
    onDeleteProduct: (ProductDoc) -> Unit,
    onSeedStarterCatalog: () -> Unit
) {
    val isOwner = userRole == UserRole.OWNER
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, LOW, PROMO, or category.name
    var showFormSheet by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var prefilledSkuForSheet by remember { mutableStateOf<String?>(null) }

    var qrPreviewProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var pricingTargetProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var restockTargetProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var deleteConfirmProduct by remember { mutableStateOf<ProductDoc?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

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
                else -> item.categoryEnum.name == filterMode
            }
            val matchQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.skuCode.lowercase().contains(q) ||
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
                                text = "Mode Cek Stok Pegawai Kasir: Anda dapat memeriksa jumlah stok, harga jual, diskon promo & QR barang. Data modal & ubah stok khusus Owner.",
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
                    items(ProductCategory.entries) { cat ->
                        FilterChip(
                            selected = filterMode == cat.name,
                            onClick = { filterMode = cat.name },
                            label = { Text(cat.displayName) }
                        )
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
                        isOwner = isOwner,
                        onShowQr = { qrPreviewProduct = product },
                        onEditPricing = { pricingTargetProduct = product },
                        onRestock = { restockTargetProduct = product },
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
                generateNextSku = generateNextSku,
                onCancel = { showFormSheet = false },
                onSave = { existing, sku, name, category, variant, cost, sell, discPct, stock, minAlert ->
                    onSaveProduct(existing, sku, name, category, variant, cost, sell, discPct, stock, minAlert) {
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
            histories = stockHistories,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@Composable
private fun StokProductCard(
    product: ProductDoc,
    isOwner: Boolean,
    onShowQr: () -> Unit,
    onEditPricing: () -> Unit,
    onRestock: () -> Unit,
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
                    CategoryBadge(category = product.categoryEnum)
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

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (product.variantInfo.isNotBlank()) {
                    Text(
                        text = product.variantInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                }
            }
        }
    }
}

@Composable
private fun ProductFormSheetContent(
    existingProduct: ProductDoc?,
    initialSku: String?,
    generateNextSku: (ProductCategory) -> String,
    onCancel: () -> Unit,
    onSave: (
        existingProduct: ProductDoc?,
        skuCode: String,
        name: String,
        category: ProductCategory,
        variantInfo: String,
        costPrice: Long,
        sellingPrice: Long,
        discountPercent: Int,
        stock: Int,
        minStockAlert: Int
    ) -> Unit
) {
    var category by remember { mutableStateOf(existingProduct?.categoryEnum ?: ProductCategory.BAJU) }
    var skuCode by remember {
        mutableStateOf(
            existingProduct?.skuCode ?: initialSku ?: generateNextSku(ProductCategory.BAJU)
        )
    }
    var name by remember { mutableStateOf(existingProduct?.name ?: "") }
    var variantInfo by remember { mutableStateOf(existingProduct?.variantInfo ?: "") }
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

        Text(text = "Kategori Toko", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ProductCategory.entries) { item ->
                FilterChip(
                    selected = category == item,
                    onClick = {
                        category = item
                        if (existingProduct == null && initialSku == null) {
                            skuCode = generateNextSku(item)
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
            IconButton(onClick = { skuCode = generateNextSku(category) }) {
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

        OutlinedTextField(
            value = variantInfo,
            onValueChange = { variantInfo = it },
            label = { Text("Varian / Ukuran / Penulis (Opsional)") },
            placeholder = { Text("Misal: Ukuran L • Sage Green") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

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
                    onSave(
                        existingProduct,
                        skuCode,
                        name,
                        category,
                        variantInfo,
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

@Composable
private fun StockHistoryDialog(
    histories: List<StockHistoryDoc>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Riwayat Pergerakan Stok",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                HorizontalDivider()
                if (histories.isEmpty()) {
                    Text(
                        text = "Belum ada catatan pergerakan stok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(histories, key = { it.id }) { log ->
                            val isPositive = log.quantityDelta >= 0
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.productName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${log.changeTypeEnum.displayName} • ${log.actorName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${DateUtils.formatDateTime(log.timestampMillis)}${if (log.note.isNotBlank()) " • ${log.note}" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (isPositive) "+${log.quantityDelta}" else "${log.quantityDelta}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isPositive) ProfitGreen else DangerCrimson
                                        )
                                        Text(
                                            text = "Sisa: ${log.stockAfter}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}
