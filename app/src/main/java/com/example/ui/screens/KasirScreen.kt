package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.UserRole
import com.example.data.remote.CloudSaleWithItems
import com.example.data.remote.ProductDoc
import com.example.data.remote.SaleTransactionDoc
import com.example.data.remote.StaffPinProfileDoc
import com.example.data.remote.StoreDoc
import com.example.ui.components.CategoryBadge
import com.example.ui.components.NumericOutlinedField
import com.example.ui.components.SingleCodeQrScannerDialog
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.viewmodel.CartUiItem
import com.example.util.CurrencyFormatter
import com.example.util.DateUtils
import com.example.util.QrCodeGenerator
import com.example.util.QrisConfigManager
import com.example.util.ReportExporter

private enum class DiscountInputMode { NOMINAL, PERCENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirScreen(
    currentUser: StaffPinProfileDoc,
    cloudMemberRole: UserRole,
    activeStore: StoreDoc?,
    products: List<ProductDoc>,
    cartItems: List<CartUiItem>,
    lastReceipt: CloudSaleWithItems?,
    cashierTodayTransactions: List<SaleTransactionDoc>,
    onAddToCart: (ProductDoc) -> Unit,
    onUpdateCartQty: (productId: String, newQty: Int) -> Unit,
    onSetCartItemCustomPrice: (productId: String, customPrice: Long?) -> Unit,
    onClearCart: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onUpdateStoreQris: (String) -> Unit,
    onCheckout: (
        discount: Long,
        paymentMethod: PaymentMethod,
        amountPaid: Long,
        notes: String,
        qrisReference: String
    ) -> Unit,
    onDismissReceipt: () -> Unit,
    onSeedStarterCatalog: () -> Unit,
    onOpenAddProduct: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ProductCategory?>(null) }
    var showCheckoutSheet by remember { mutableStateOf(false) }
    var showCashierShiftSheet by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        products.filter { product ->
            val matchCat = selectedCategory == null || product.categoryEnum == selectedCategory
            val matchText = q.isEmpty() ||
                product.name.lowercase().contains(q) ||
                product.skuCode.lowercase().contains(q) ||
                product.variantInfo.lowercase().contains(q)
            matchCat && matchText
        }
    }

    val cartMap = remember(cartItems) { cartItems.associate { it.product.id to it.quantity } }
    val totalCartItems = cartItems.sumOf { it.quantity }
    val totalCartRevenue = cartItems.sumOf { it.subtotalRevenue }
    val totalCartCost = cartItems.sumOf { it.subtotalCost }
    val totalCartProfit = cartItems.sumOf { it.subtotalProfit }
    val isOwner = currentUser.roleEnum == UserRole.OWNER && cloudMemberRole == UserRole.OWNER

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_product_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Bersihkan pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kasir_search_input")
                )

                Button(
                    onClick = onOpenQrScanner,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("open_qr_scanner_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = stringResource(R.string.scan_qr_button)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("QR")
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isOwner) {
                    item {
                        FilledTonalButton(
                            onClick = { showCashierShiftSheet = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Shift Saya (${cashierTodayTransactions.size})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("Semua (${products.size})") }
                    )
                }
                items(ProductCategory.entries) { category ->
                    val count = products.count { it.categoryEnum == category }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        label = { Text("${category.displayName} ($count)") }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("seed_catalog_button")
                                ) {
                                    Text(stringResource(R.string.load_starter_catalog))
                                }
                                OutlinedButton(
                                    onClick = onOpenAddProduct,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Tambah Barang Manual")
                                }
                            } else {
                                Text(
                                    text = "Hubungi Owner untuk mengisi katalog barang toko.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Barang tidak ditemukan untuk kata kunci \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = if (totalCartItems > 0) 104.dp else 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val qtyInCart = cartMap[product.id] ?: 0
                        KasirProductCard(
                            product = product,
                            qtyInCart = qtyInCart,
                            showCostAndProfit = isOwner,
                            onAdd = { onAddToCart(product) },
                            onUpdateQty = { newQty -> onUpdateCartQty(product.id, newQty) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = totalCartItems > 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { showCheckoutSheet = true }
                    .testTag("cart_summary_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "$totalCartItems Barang • ${CurrencyFormatter.formatRupiah(totalCartRevenue)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isOwner) {
                                Text(
                                    text = "Modal ${CurrencyFormatter.formatCompactRupiah(totalCartCost)} • Est. Untung ${CurrencyFormatter.formatCompactRupiah(totalCartProfit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Ketuk untuk harga khusus, diskon & bayar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Bayar",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Icon(
                                imageVector = Icons.Rounded.ShoppingCartCheckout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCheckoutSheet && cartItems.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCheckoutSheet = false },
            sheetState = sheetState
        ) {
            CheckoutSheetContent(
                cartItems = cartItems,
                showCostAndProfit = isOwner,
                isOwner = isOwner,
                storeName = activeStore?.storeName ?: "UM TAZKIYAH",
                storeQrisPayload = activeStore?.qrisPayload?.takeIf { it.isNotBlank() }
                    ?: QrisConfigManager.DEFAULT_DEMO_QRIS,
                onUpdateQty = onUpdateCartQty,
                onSetCustomUnitPrice = onSetCartItemCustomPrice,
                onUpdateStoreQris = onUpdateStoreQris,
                onClearCart = {
                    onClearCart()
                    showCheckoutSheet = false
                },
                onConfirmCheckout = { discount, method, paid, notes, qrisRef ->
                    onCheckout(discount, method, paid, notes, qrisRef)
                    showCheckoutSheet = false
                }
            )
        }
    }

    if (lastReceipt != null) {
        ReceiptDialog(
            receipt = lastReceipt,
            storeName = activeStore?.storeName ?: "UM TAZKIYAH",
            showCostAndProfit = isOwner,
            onShowMessage = onShowMessage,
            onDismiss = onDismissReceipt
        )
    }

    if (showCashierShiftSheet) {
        CashierShiftSummaryDialog(
            cashierName = currentUser.fullName,
            transactions = cashierTodayTransactions,
            onDismiss = { showCashierShiftSheet = false }
        )
    }
}

@Composable
private fun KasirProductCard(
    product: ProductDoc,
    qtyInCart: Int,
    showCostAndProfit: Boolean,
    onAdd: () -> Unit,
    onUpdateQty: (Int) -> Unit
) {
    val isOutOfStock = product.stock <= 0
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (qtyInCart > 0) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (qtyInCart > 0) 1.5.dp else 1.dp,
            color = if (qtyInCart > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !isOutOfStock) { onAdd() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryBadge(category = product.categoryEnum)
                    StockStatusBadge(stock = product.stock, minAlert = product.minStockAlert)
                    if (product.discountPercent > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ProfitGreenContainer
                        ) {
                            Text(
                                text = "-${product.discountPercent}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ProfitGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = product.skuCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (product.variantInfo.isNotBlank()) {
                        Text(
                            text = "• ${product.variantInfo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    if (showCostAndProfit) {
                        Text(
                            text = "Laba ${CurrencyFormatter.formatCompactRupiah(product.profitPerUnit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ProfitGreen
                        )
                    }
                }
            }

            if (qtyInCart > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onUpdateQty(qtyInCart - 1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Kurangi jumlah")
                    }
                    Text(
                        text = qtyInCart.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { onUpdateQty(qtyInCart + 1) },
                        enabled = qtyInCart < product.stock,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Tambah jumlah")
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = !isOutOfStock,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isOutOfStock) "Habis" else "Pilih")
                }
            }
        }
    }
}

@Composable
private fun CheckoutSheetContent(
    cartItems: List<CartUiItem>,
    showCostAndProfit: Boolean,
    isOwner: Boolean,
    storeName: String,
    storeQrisPayload: String,
    onUpdateQty: (String, Int) -> Unit,
    onSetCustomUnitPrice: (String, Long?) -> Unit,
    onUpdateStoreQris: (String) -> Unit,
    onClearCart: () -> Unit,
    onConfirmCheckout: (
        discount: Long,
        method: PaymentMethod,
        paid: Long,
        notes: String,
        qrisReference: String
    ) -> Unit
) {
    var discountMode by remember { mutableStateOf(DiscountInputMode.NOMINAL) }
    var discountInput by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.TUNAI) }
    var cashPaidInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var scannedQrisRef by remember { mutableStateOf("") }

    var editingPriceItem by remember { mutableStateOf<CartUiItem?>(null) }
    var showQrisPaymentScanner by remember { mutableStateOf(false) }
    var showStoreQrisDialog by remember { mutableStateOf(false) }

    val grossTotal = cartItems.sumOf { it.subtotalRevenue }
    val totalCost = cartItems.sumOf { it.subtotalCost }

    val discount = remember(discountMode, discountInput, grossTotal) {
        val raw = discountInput.toLongOrNull() ?: 0L
        when (discountMode) {
            DiscountInputMode.NOMINAL -> raw.coerceIn(0L, grossTotal)
            DiscountInputMode.PERCENT -> {
                val pct = raw.coerceIn(0L, 100L)
                ((grossTotal * pct) / 100L).coerceIn(0L, grossTotal)
            }
        }
    }

    val finalTotal = grossTotal - discount
    val estimatedProfit = finalTotal - totalCost

    val cashPaid = cashPaidInput.toLongOrNull() ?: 0L
    val isCashValid = selectedMethod != PaymentMethod.TUNAI || cashPaid >= finalTotal
    val changeAmount = if (selectedMethod == PaymentMethod.TUNAI && cashPaid >= finalTotal) {
        cashPaid - finalTotal
    } else {
        0L
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.checkout_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            TextButton(onClick = onClearCart) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Kosongkan")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cartItems.forEach { item ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (item.hasCustomPrice) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = ProfitGreenContainer
                                    ) {
                                        Text(
                                            text = "Harga Khusus",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ProfitGreen,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${CurrencyFormatter.formatRupiah(item.appliedUnitPrice)} x ${item.quantity} = ${CurrencyFormatter.formatRupiah(item.subtotalRevenue)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { editingPriceItem = item },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Ubah harga satuan ${item.product.name}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onUpdateQty(item.product.id, item.quantity - 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Kurangi")
                            }
                            Text(
                                text = "${item.quantity}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(
                                onClick = { onUpdateQty(item.product.id, item.quantity + 1) },
                                enabled = item.quantity < item.product.stock,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Tambah")
                            }
                        }
                    }
                }
            }
        }

        // Discount Mode Toggle + Input + Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Diskon Transaksi",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = discountMode == DiscountInputMode.NOMINAL,
                    onClick = {
                        discountMode = DiscountInputMode.NOMINAL
                        discountInput = ""
                    },
                    label = { Text("Nominal (Rp)") }
                )
                FilterChip(
                    selected = discountMode == DiscountInputMode.PERCENT,
                    onClick = {
                        discountMode = DiscountInputMode.PERCENT
                        discountInput = ""
                    },
                    label = { Text("Persen (%)") }
                )
            }
        }

        NumericOutlinedField(
            value = discountInput,
            onValueChange = { raw ->
                discountInput = if (discountMode == DiscountInputMode.PERCENT) {
                    raw.filter { it.isDigit() }.take(3)
                } else {
                    raw.filter { it.isDigit() }
                }
            },
            label = if (discountMode == DiscountInputMode.PERCENT) {
                "Persentase Diskon (0-100%)"
            } else {
                "Potongan Harga (Opsional)"
            },
            prefixText = if (discountMode == DiscountInputMode.NOMINAL) "Rp " else null,
            suffixText = if (discountMode == DiscountInputMode.PERCENT) " %" else null,
            supportingText = if (discountMode == DiscountInputMode.PERCENT && discount > 0L) {
                "Potongan setara ${CurrencyFormatter.formatRupiah(discount)}"
            } else null,
            modifier = Modifier.testTag("checkout_discount_input")
        )

        if (discountMode == DiscountInputMode.PERCENT) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 25).forEach { pct ->
                    item {
                        FilterChip(
                            selected = discountInput == pct.toString(),
                            onClick = { discountInput = pct.toString() },
                            label = { Text("$pct%") }
                        )
                    }
                }
            }
        }

        Text(
            text = "Metode Pembayaran",
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = selectedMethod == method,
                    onClick = { selectedMethod = method },
                    label = { Text(method.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectedMethod == PaymentMethod.QRIS) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pembayaran QRIS Digital",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showQrisPaymentScanner = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_qris_payment_button")
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan QRIS", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { showStoreQrisDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("show_store_qris_button")
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("QRIS Toko", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (scannedQrisRef.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = ProfitGreenContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "QRIS Terpindai: ${scannedQrisRef.take(36)}${if (scannedQrisRef.length > 36) "…" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProfitGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { scannedQrisRef = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Hapus ref QRIS",
                                        tint = ProfitGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedMethod == PaymentMethod.TUNAI) {
            NumericOutlinedField(
                value = cashPaidInput,
                onValueChange = { cashPaidInput = it },
                label = "Uang Tunai Diterima",
                prefixText = "Rp ",
                isError = cashPaidInput.isNotEmpty() && !isCashValid,
                supportingText = if (cashPaidInput.isNotEmpty() && !isCashValid) {
                    "Uang diterima masih kurang ${CurrencyFormatter.formatRupiah(finalTotal - cashPaid)}"
                } else null
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilledTonalButton(onClick = { cashPaidInput = finalTotal.toString() }) {
                        Text("Uang Pas")
                    }
                }
                listOf(50_000L, 100_000L, 200_000L, 500_000L).forEach { nominal ->
                    item {
                        OutlinedButton(onClick = { cashPaidInput = nominal.toString() }) {
                            Text(CurrencyFormatter.formatCompactRupiah(nominal))
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = notesInput,
            onValueChange = { notesInput = it },
            label = { Text("Catatan Transaksi (Opsional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SummaryLine("Subtotal (${cartItems.sumOf { it.quantity }} pcs)", CurrencyFormatter.formatRupiah(grossTotal))
                if (discount > 0L) {
                    val discLabel = if (discountMode == DiscountInputMode.PERCENT) {
                        "Diskon (${discountInput}%)"
                    } else {
                        "Diskon"
                    }
                    SummaryLine(discLabel, "-${CurrencyFormatter.formatRupiah(discount)}", valueColor = ProfitGreen)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                SummaryLine(
                    label = "Total Tagihan",
                    value = CurrencyFormatter.formatRupiah(finalTotal),
                    bold = true
                )
                if (selectedMethod == PaymentMethod.TUNAI && cashPaid >= finalTotal) {
                    SummaryLine("Kembalian", CurrencyFormatter.formatRupiah(changeAmount), valueColor = MaterialTheme.colorScheme.primary)
                }
                if (showCostAndProfit) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    SummaryLine("Modal Barang (HPP)", CurrencyFormatter.formatRupiah(totalCost))
                    SummaryLine(
                        label = "Estimasi Keuntungan",
                        value = CurrencyFormatter.formatRupiah(estimatedProfit),
                        bold = true,
                        valueColor = ProfitGreen
                    )
                }
            }
        }

        Button(
            onClick = {
                val effectivePaid = if (selectedMethod == PaymentMethod.TUNAI) cashPaid else finalTotal
                onConfirmCheckout(discount, selectedMethod, effectivePaid, notesInput, scannedQrisRef)
            },
            enabled = isCashValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("confirm_checkout_button")
        ) {
            Icon(Icons.Rounded.Payments, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Selesaikan Transaksi",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }

    editingPriceItem?.let { target ->
        CustomUnitPriceDialog(
            item = target,
            onDismiss = { editingPriceItem = null },
            onSaveCustomPrice = { newUnitPrice ->
                onSetCustomUnitPrice(target.product.id, newUnitPrice)
                editingPriceItem = null
            }
        )
    }

    if (showQrisPaymentScanner) {
        SingleCodeQrScannerDialog(
            title = "Pindai Kode QRIS Pembayaran",
            subtitle = "Arahkan kamera ke kode QRIS pelanggan atau bukti QRIS",
            manualHint = "Kode / Referensi QRIS",
            onDismiss = { showQrisPaymentScanner = false },
            onCodeCaptured = { captured ->
                scannedQrisRef = captured
                showQrisPaymentScanner = false
            }
        )
    }

    if (showStoreQrisDialog) {
        StoreQrisDisplayDialog(
            storeName = storeName,
            qrisPayload = storeQrisPayload,
            amountToPay = finalTotal,
            canConfigureQris = isOwner,
            onUpdateStoreQris = onUpdateStoreQris,
            onDismiss = { showStoreQrisDialog = false }
        )
    }
}

@Composable
private fun CustomUnitPriceDialog(
    item: CartUiItem,
    onDismiss: () -> Unit,
    onSaveCustomPrice: (Long?) -> Unit
) {
    var priceText by remember { mutableStateOf(item.appliedUnitPrice.toString()) }
    val basePrice = item.product.effectiveSellingPrice

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur Harga Satuan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Harga Katalog: ${CurrencyFormatter.formatRupiah(basePrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NumericOutlinedField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = "Harga Jual Satuan (Rp)",
                    prefixText = "Rp "
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 20).forEach { pct ->
                        item {
                            OutlinedButton(
                                onClick = {
                                    val discounted = (basePrice * (100 - pct)) / 100L
                                    priceText = discounted.toString()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("-$pct%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = priceText.toLongOrNull() ?: basePrice
                    onSaveCustomPrice(if (parsed == basePrice) null else parsed)
                },
                enabled = (priceText.toLongOrNull() ?: -1L) >= 0L
            ) {
                Text("Terapkan")
            }
        },
        dismissButton = {
            Row {
                if (item.hasCustomPrice) {
                    TextButton(onClick = { onSaveCustomPrice(null) }) {
                        Text("Reset")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}

@Composable
private fun StoreQrisDisplayDialog(
    storeName: String,
    qrisPayload: String,
    amountToPay: Long,
    canConfigureQris: Boolean,
    onUpdateStoreQris: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showChangeQrisScanner by remember { mutableStateOf(false) }
    val bitmap: Bitmap? = remember(qrisPayload) {
        QrCodeGenerator.generateQrBitmap(qrisPayload, 560)
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
                    text = "QRIS Pembayaran $storeName",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Total Tagihan: ${CurrencyFormatter.formatRupiah(amountToPay)}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (bitmap != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Kode QRIS Toko $storeName",
                            modifier = Modifier
                                .size(220.dp)
                                .padding(12.dp)
                        )
                    }
                }
                Text(
                    text = "Minta pembeli scan kode QRIS di atas melalui aplikasi mobile banking / e-wallet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (canConfigureQris) {
                    OutlinedButton(
                        onClick = { showChangeQrisScanner = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan / Ubah QRIS Resmi Toko")
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup")
                }
            }
        }
    }

    if (showChangeQrisScanner) {
        SingleCodeQrScannerDialog(
            title = "Pindai QRIS Resmi Toko",
            subtitle = "Scan kode QRIS merchant milik toko untuk disimpan online",
            manualHint = "Payload String QRIS",
            onDismiss = { showChangeQrisScanner = false },
            onCodeCaptured = { captured ->
                onUpdateStoreQris(captured)
                showChangeQrisScanner = false
            }
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun ReceiptDialog(
    receipt: CloudSaleWithItems,
    storeName: String,
    showCostAndProfit: Boolean,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tx = receipt.transaction
    val context = LocalContext.current
    val receiptText = remember(receipt, storeName) {
        ReportExporter.buildCustomerDigitalReceipt(receipt, storeName)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ProfitGreenContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = ProfitGreen,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Transaksi Berhasil",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${tx.invoiceNumber} • ${DateUtils.formatDateTime(tx.timestampMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "$storeName • Kasir: ${tx.cashierName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    receipt.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "${item.quantity} x ${CurrencyFormatter.formatRupiah(item.unitSellingPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyFormatter.formatRupiah(item.subtotalRevenue),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                HorizontalDivider()

                if (tx.discountAmount > 0L) {
                    SummaryLine("Diskon", "-${CurrencyFormatter.formatRupiah(tx.discountAmount)}", valueColor = ProfitGreen)
                }
                SummaryLine("Total (${tx.paymentMethodEnum.displayName})", CurrencyFormatter.formatRupiah(tx.finalRevenue), bold = true)
                if (tx.qrisReference.isNotBlank()) {
                    SummaryLine("Ref QRIS", tx.qrisReference.take(24))
                }
                if (tx.paymentMethodEnum == PaymentMethod.TUNAI) {
                    SummaryLine("Dibayar", CurrencyFormatter.formatRupiah(tx.amountPaid))
                    SummaryLine("Kembalian", CurrencyFormatter.formatRupiah(tx.changeAmount), valueColor = MaterialTheme.colorScheme.primary)
                }
                if (showCostAndProfit) {
                    HorizontalDivider()
                    SummaryLine("Modal (HPP)", CurrencyFormatter.formatRupiah(tx.totalCost))
                    SummaryLine("Keuntungan Bersih", CurrencyFormatter.formatRupiah(tx.netProfit), bold = true, valueColor = ProfitGreen)
                }

                HorizontalDivider()

                Text(
                    text = "Bagikan Struk Digital ke Pelanggan",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { shareReceiptViaWhatsApp(context, tx.invoiceNumber, receiptText) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_receipt_whatsapp_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ProfitGreenContainer,
                            contentColor = ProfitGreen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Kirim lewat WhatsApp",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { shareReceiptChooser(context, tx.invoiceNumber, receiptText) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_receipt_other_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Bagikan ke aplikasi lain",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Lainnya", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedIconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Struk ${tx.invoiceNumber}", receiptText))
                            onShowMessage("Struk digital disalin ke clipboard")
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("copy_receipt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Salin struk digital"
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("receipt_done_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Transaksi Baru")
                }
            }
        }
    }
}

private fun shareReceiptViaWhatsApp(
    context: Context,
    invoiceNumber: String,
    receiptText: String
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Struk Digital $invoiceNumber")
        putExtra(Intent.EXTRA_TEXT, receiptText)
    }
    try {
        context.startActivity(Intent(sendIntent).setPackage("com.whatsapp"))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(sendIntent).setPackage("com.whatsapp.w4b"))
        } catch (_: ActivityNotFoundException) {
            shareReceiptChooser(context, invoiceNumber, receiptText)
        }
    }
}

private fun shareReceiptChooser(
    context: Context,
    invoiceNumber: String,
    receiptText: String
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Struk Digital $invoiceNumber")
        putExtra(Intent.EXTRA_TEXT, receiptText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Bagikan Struk Digital"))
}

@Composable
private fun CashierShiftSummaryDialog(
    cashierName: String,
    transactions: List<SaleTransactionDoc>,
    onDismiss: () -> Unit
) {
    val totalOmzet = transactions.sumOf { it.finalRevenue }
    val totalItems = transactions.sumOf { it.totalItems }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Rekap Shift Kasir Hari Ini",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Petugas: $cashierName • ${DateUtils.formatDisplayDate(DateUtils.todayKey())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SummaryLine("Jumlah Transaksi", "${transactions.size} trx")
                        SummaryLine("Total Barang Terjual", "$totalItems pcs")
                        SummaryLine("Total Omzet Shift", CurrencyFormatter.formatRupiah(totalOmzet), bold = true)
                        HorizontalDivider()
                        PaymentMethod.entries.forEach { method ->
                            val sum = transactions.filter { it.paymentMethodEnum == method }.sumOf { it.finalRevenue }
                            SummaryLine("Via ${method.displayName}", CurrencyFormatter.formatRupiah(sum))
                        }
                    }
                }
                Text(
                    text = "Catatan: Laporan modal, laba bersih, dan ekspor laporan hanya dapat diakses oleh Owner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}
