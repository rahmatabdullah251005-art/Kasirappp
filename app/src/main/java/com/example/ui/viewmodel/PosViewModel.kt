package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.UserRole
import com.example.data.remote.CloudCartLineRequest
import com.example.data.remote.CloudSaleWithItems
import com.example.data.remote.FirestorePosRepository
import com.example.data.remote.ProductDoc
import com.example.data.remote.SaleItemDoc
import com.example.data.remote.SaleTransactionDoc
import com.example.data.remote.StaffPinProfileDoc
import com.example.data.remote.StockHistoryDoc
import com.example.data.remote.StoreDoc
import com.example.data.remote.StoreInviteDoc
import com.example.util.DateUtils
import com.example.util.PinHasher
import com.example.util.QrisConfigManager
import com.example.util.ReportExporter
import com.example.util.SoldItemSummary
import com.example.util.StockAlertEvaluator
import com.example.util.StockAlertItem
import com.example.util.StockAlertUiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartUiItem(
    val product: ProductDoc,
    val quantity: Int,
    val customUnitPrice: Long? = null
) {
    val appliedUnitPrice: Long get() = customUnitPrice ?: product.effectiveSellingPrice
    val subtotalRevenue: Long get() = appliedUnitPrice * quantity
    val subtotalCost: Long get() = product.costPrice * quantity
    val subtotalProfit: Long get() = subtotalRevenue - subtotalCost
    val hasCustomPrice: Boolean get() = customUnitPrice != null && customUnitPrice != product.effectiveSellingPrice
}

enum class ReportPeriodMode(val label: String) {
    SELECTED_DAY("Harian"),
    LAST_7_DAYS("7 Hari Terakhir")
}

data class CategoryBreakdown(
    val categoryName: String,
    val quantity: Int,
    val revenue: Long,
    val cost: Long,
    val profit: Long
)

data class DailyTrendPoint(
    val dateKey: String,
    val label: String,
    val revenue: Long,
    val profit: Long
)

data class DailyReportUiState(
    val periodLabel: String = DateUtils.formatDisplayDate(DateUtils.todayKey()),
    val transactionCount: Int = 0,
    val totalItemsSold: Int = 0,
    val grossRevenue: Long = 0L,
    val totalDiscount: Long = 0L,
    val totalRevenue: Long = 0L,
    val totalCost: Long = 0L,
    val totalProfit: Long = 0L,
    val marginPercent: Double = 0.0,
    val paymentTotals: Map<PaymentMethod, Pair<Int, Long>> = emptyMap(),
    val categoryBreakdowns: List<CategoryBreakdown> = emptyList(),
    val soldItems: List<SoldItemSummary> = emptyList(),
    val transactions: List<CloudSaleWithItems> = emptyList()
)

sealed interface QrScanOutcome {
    data class AddedToCart(val product: ProductDoc, val newCartQty: Int) : QrScanOutcome
    data class OutOfStock(val product: ProductDoc) : QrScanOutcome
    data class NotRegistered(val scannedCode: String) : QrScanOutcome
}

data class ActiveStoreContext(
    val storeId: String,
    val storeName: String,
    val cloudMemberRole: UserRole
)

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModel(
    private val repository: FirestorePosRepository,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserDisplayName: String
) : ViewModel() {

    private val _selectedStoreId = MutableStateFlow<String?>(null)
    private val _currentPinProfile = MutableStateFlow<StaffPinProfileDoc?>(null)
    val currentPinProfile: StateFlow<StaffPinProfileDoc?> = _currentPinProfile.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _onboardingBusy = MutableStateFlow(false)
    val onboardingBusy: StateFlow<Boolean> = _onboardingBusy.asStateFlow()

    private val _onboardingError = MutableStateFlow<String?>(null)
    val onboardingError: StateFlow<String?> = _onboardingError.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _cartQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _customUnitPrices = MutableStateFlow<Map<String, Long>>(emptyMap())

    private val _lastReceipt = MutableStateFlow<CloudSaleWithItems?>(null)
    val lastReceipt: StateFlow<CloudSaleWithItems?> = _lastReceipt.asStateFlow()

    private val _selectedReportDateKey = MutableStateFlow(DateUtils.todayKey())
    val selectedReportDateKey: StateFlow<String> = _selectedReportDateKey.asStateFlow()

    private val _reportPeriodMode = MutableStateFlow(ReportPeriodMode.SELECTED_DAY)
    val reportPeriodMode: StateFlow<ReportPeriodMode> = _reportPeriodMode.asStateFlow()

    private val _acknowledgedLowStockSignatures = MutableStateFlow<Set<String>>(emptySet())
    private val _lowStockNotificationEvent = MutableSharedFlow<List<StockAlertItem>>(extraBufferCapacity = 4)
    val lowStockNotificationEvent: SharedFlow<List<StockAlertItem>> = _lowStockNotificationEvent.asSharedFlow()

    private fun <T> Flow<T>.unwrapFirestoreCatch(tag: String, fallback: T): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "$tag: ${root.message}", root)
            emit(fallback)
        }

    // --- STORE DISCOVERY ---
    val ownedStores: StateFlow<List<StoreDoc>> =
        repository.observeOwnedStores()
            .unwrapFirestoreCatch("observeOwnedStores", emptyList())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val acceptedInvites: StateFlow<List<StoreInviteDoc>> =
        repository.observeAcceptedStoreInvites()
            .unwrapFirestoreCatch("observeAcceptedInvites", emptyList())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val pendingInvitesForUser: StateFlow<List<StoreInviteDoc>> =
        if (currentUserEmail.isBlank()) {
            MutableStateFlow(emptyList())
        } else {
            repository.observePendingInvitesForEmail(currentUserEmail)
                .unwrapFirestoreCatch("observePendingInvites", emptyList())
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
        }

    val isDiscoveryLoading: StateFlow<Boolean> = combine(
        repository.observeOwnedStores().map { false }.unwrapFirestoreCatch("ownedLoad", false),
        repository.observeAcceptedStoreInvites().map { false }.unwrapFirestoreCatch("acceptedLoad", false)
    ) { a, b -> a || b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val activeStoreContext: StateFlow<ActiveStoreContext?> = combine(
        ownedStores,
        acceptedInvites,
        _selectedStoreId
    ) { owned, accepted, explicitId ->
        val selectedOwned = if (explicitId != null) owned.find { it.id == explicitId } else owned.firstOrNull()
        if (selectedOwned != null) {
            ActiveStoreContext(
                storeId = selectedOwned.id,
                storeName = selectedOwned.storeName,
                cloudMemberRole = UserRole.OWNER
            )
        } else {
            val selectedInvite = if (explicitId != null) {
                accepted.find { it.storeId == explicitId }
            } else {
                accepted.firstOrNull()
            }
            selectedInvite?.let {
                ActiveStoreContext(
                    storeId = it.storeId,
                    storeName = it.storeName,
                    cloudMemberRole = UserRole.KASIR
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeStoreDoc: StateFlow<StoreDoc?> = activeStoreContext.flatMapLatest { ctx ->
        if (ctx == null) flowOf(null)
        else repository.observeStoreDoc(ctx.storeId).unwrapFirestoreCatch("observeStoreDoc", null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // --- STAFF & INVITES ---
    val allStaffPins: StateFlow<List<StaffPinProfileDoc>> = activeStoreContext.flatMapLatest { ctx ->
        if (ctx == null) flowOf(emptyList())
        else repository.observeStaffPins(ctx.storeId).unwrapFirestoreCatch("observeStaffPins", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val activeCashierPins: StateFlow<List<StaffPinProfileDoc>> = allStaffPins
        .map { list -> list.filter { it.roleEnum == UserRole.KASIR && it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val storeInvites: StateFlow<List<StoreInviteDoc>> = activeStoreContext.flatMapLatest { ctx ->
        if (ctx == null || ctx.cloudMemberRole != UserRole.OWNER) flowOf(emptyList())
        else repository.observeStoreInvitesAsOwner(ctx.storeId).unwrapFirestoreCatch("observeStoreInvites", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // --- PRODUCTS & STOCK HISTORIES ---
    val allProducts: StateFlow<List<ProductDoc>> = activeStoreContext.flatMapLatest { ctx ->
        if (ctx == null) flowOf(emptyList())
        else repository.observeProducts(ctx.storeId).unwrapFirestoreCatch("observeProducts", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val stockHistories: StateFlow<List<StockHistoryDoc>> = combine(
        activeStoreContext,
        _currentPinProfile
    ) { ctx, pinProfile -> ctx to pinProfile }.flatMapLatest { (ctx, pinProfile) ->
        if (ctx == null || ctx.cloudMemberRole != UserRole.OWNER || pinProfile?.roleEnum != UserRole.OWNER) {
            flowOf(emptyList())
        } else {
            repository.observeStockHistories(ctx.storeId).unwrapFirestoreCatch("observeStockHistories", emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val stockAlertState: StateFlow<StockAlertUiState> = combine(
        allProducts,
        _currentPinProfile,
        activeStoreContext,
        _acknowledgedLowStockSignatures
    ) { products, pinProfile, storeCtx, ackSet ->
        val isOwnerActive = pinProfile?.roleEnum == UserRole.OWNER && storeCtx?.cloudMemberRole == UserRole.OWNER
        if (!isOwnerActive) {
            StockAlertUiState()
        } else {
            val alerts = StockAlertEvaluator.evaluateAlerts(products)
            val unread = alerts.count { it.signature !in ackSet }
            StockAlertUiState(alerts = alerts, unreadCount = unread)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StockAlertUiState())

    init {
        viewModelScope.launch {
            var observedSessionKey: String? = null
            var previousStocksById: Map<String, Int>? = null
            combine(
                allProducts,
                _currentPinProfile,
                activeStoreContext
            ) { products, pinProfile, storeCtx ->
                Triple(products, pinProfile, storeCtx)
            }.collect { (products, pinProfile, storeCtx) ->
                val isOwnerActive = pinProfile?.roleEnum == UserRole.OWNER && storeCtx?.cloudMemberRole == UserRole.OWNER
                if (!isOwnerActive) {
                    observedSessionKey = null
                    previousStocksById = null
                    return@collect
                }
                val sessionKey = "${storeCtx!!.storeId}:${pinProfile!!.id}"
                if (observedSessionKey != sessionKey) {
                    observedSessionKey = sessionKey
                    previousStocksById = null
                }
                if (products.isEmpty()) {
                    return@collect
                }
                val triggered = StockAlertEvaluator.detectNewlyTriggeredAlerts(previousStocksById, products)
                previousStocksById = products.associate { it.id to it.stock }
                if (triggered.isNotEmpty()) {
                    _lowStockNotificationEvent.tryEmit(triggered)
                    emitToast(StockAlertEvaluator.buildNotificationMessage(triggered))
                }
            }
        }
    }

    // --- CART ---
    val cartItems: StateFlow<List<CartUiItem>> =
        combine(allProducts, _cartQuantities, _customUnitPrices) { products, qtyMap, customPriceMap ->
            val productMap = products.associateBy { it.id }
            qtyMap.mapNotNull { (productId, qty) ->
                val product = productMap[productId] ?: return@mapNotNull null
                val clampedQty = qty.coerceAtMost(product.stock)
                if (clampedQty <= 0) null else CartUiItem(
                    product = product,
                    quantity = clampedQty,
                    customUnitPrice = customPriceMap[productId]
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // --- TRANSACTIONS & REPORTING ---
    private val ownerAllTransactionsFlow: Flow<List<SaleTransactionDoc>> =
        activeStoreContext.flatMapLatest { ctx ->
            if (ctx == null || ctx.cloudMemberRole != UserRole.OWNER) flowOf(emptyList())
            else repository.observeAllTransactionsForOwner(ctx.storeId).unwrapFirestoreCatch("observeOwnerTxs", emptyList())
        }

    private val ownerAllSaleItemsFlow: Flow<List<SaleItemDoc>> =
        activeStoreContext.flatMapLatest { ctx ->
            if (ctx == null || ctx.cloudMemberRole != UserRole.OWNER) flowOf(emptyList())
            else repository.observeAllSaleItemsForOwner(ctx.storeId).unwrapFirestoreCatch("observeOwnerItems", emptyList())
        }

    private val ownerCombinedSalesFlow: Flow<List<CloudSaleWithItems>> =
        combine(ownerAllTransactionsFlow, ownerAllSaleItemsFlow) { txs, items ->
            val itemsByTx = items.groupBy { it.transactionId }
            txs.map { tx -> CloudSaleWithItems(transaction = tx, items = itemsByTx[tx.id].orEmpty()) }
        }

    val dailyReportState: StateFlow<DailyReportUiState> =
        combine(
            ownerCombinedSalesFlow,
            _selectedReportDateKey,
            _reportPeriodMode
        ) { allSales, dateKey, mode ->
            val filtered = when (mode) {
                ReportPeriodMode.SELECTED_DAY -> allSales.filter { it.transaction.dateKey == dateKey }
                ReportPeriodMode.LAST_7_DAYS -> {
                    val startKey = DateUtils.dateKeyForOffset(6)
                    val endKey = DateUtils.todayKey()
                    allSales.filter { it.transaction.dateKey in startKey..endKey }
                }
            }
            val periodLabel = when (mode) {
                ReportPeriodMode.SELECTED_DAY -> DateUtils.formatDisplayDate(dateKey)
                ReportPeriodMode.LAST_7_DAYS -> "${DateUtils.formatShortDate(DateUtils.dateKeyForOffset(6))} - ${DateUtils.formatShortDate(DateUtils.todayKey())}"
            }
            val gross = filtered.sumOf { it.transaction.totalRevenue }
            val discount = filtered.sumOf { it.transaction.discountAmount }
            val finalRev = filtered.sumOf { it.transaction.finalRevenue }
            val cost = filtered.sumOf { it.transaction.totalCost }
            val profit = filtered.sumOf { it.transaction.netProfit }
            val itemsSold = filtered.sumOf { it.transaction.totalItems }
            val margin = if (finalRev > 0L) (profit.toDouble() / finalRev.toDouble()) * 100.0 else 0.0

            val paymentTotals = PaymentMethod.entries.associateWith { method ->
                val matching = filtered.filter { it.transaction.paymentMethodEnum == method }
                matching.size to matching.sumOf { it.transaction.finalRevenue }
            }

            val allSaleItems = filtered.flatMap { it.items }
            val categoryBreakdowns = allSaleItems
                .groupBy { it.categoryName }
                .map { (catName, items) ->
                    CategoryBreakdown(
                        categoryName = catName,
                        quantity = items.sumOf { it.quantity },
                        revenue = items.sumOf { it.subtotalRevenue },
                        cost = items.sumOf { it.subtotalCost },
                        profit = items.sumOf { it.subtotalProfit }
                    )
                }
                .sortedByDescending { it.revenue }

            val soldItems = ReportExporter.aggregateSoldItems(filtered)

            DailyReportUiState(
                periodLabel = periodLabel,
                transactionCount = filtered.size,
                totalItemsSold = itemsSold,
                grossRevenue = gross,
                totalDiscount = discount,
                totalRevenue = finalRev,
                totalCost = cost,
                totalProfit = profit,
                marginPercent = margin,
                paymentTotals = paymentTotals,
                categoryBreakdowns = categoryBreakdowns,
                soldItems = soldItems,
                transactions = filtered
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DailyReportUiState())

    val sevenDayTrend: StateFlow<List<DailyTrendPoint>> =
        ownerCombinedSalesFlow.map { allSales ->
            val byDate = allSales.groupBy { it.transaction.dateKey }
            (6 downTo 0).map { offset ->
                val key = DateUtils.dateKeyForOffset(offset)
                val dayTxs = byDate[key].orEmpty()
                DailyTrendPoint(
                    dateKey = key,
                    label = DateUtils.formatShortDay(key),
                    revenue = dayTxs.sumOf { it.transaction.finalRevenue },
                    profit = dayTxs.sumOf { it.transaction.netProfit }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val cashierTodayTransactions: StateFlow<List<SaleTransactionDoc>> =
        activeStoreContext.flatMapLatest { ctx ->
            if (ctx == null) flowOf(emptyList())
            else repository.observeCashierOwnTransactions(ctx.storeId, DateUtils.todayKey())
                .unwrapFirestoreCatch("observeCashierOwnTxs", emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // --- STORE ONBOARDING ACTIONS ---
    fun createOwnerStore(storeName: String, ownerName: String) {
        viewModelScope.launch {
            _onboardingBusy.value = true
            _onboardingError.value = null
            repository.createOwnerStore(
                storeName = storeName,
                ownerFullName = ownerName,
                ownerEmail = currentUserEmail,
                defaultQrisPayload = QrisConfigManager.DEFAULT_DEMO_QRIS
            ).onSuccess { createdStore ->
                _selectedStoreId.value = createdStore.id
                emitToast("Toko online '${createdStore.storeName}' aktif! PIN Owner default: 0000")
            }.onFailure { err ->
                _onboardingError.value = err.message ?: "Gagal membuat toko online"
            }
            _onboardingBusy.value = false
        }
    }

    fun acceptStoreInvite(invite: StoreInviteDoc) {
        viewModelScope.launch {
            _onboardingBusy.value = true
            _onboardingError.value = null
            repository.acceptStoreInvite(
                invite = invite,
                userFullName = currentUserDisplayName,
                userEmail = currentUserEmail
            ).onSuccess { storeId ->
                _selectedStoreId.value = storeId
                emitToast("Terhubung ke toko ${invite.storeName} sebagai Pegawai Kasir")
            }.onFailure { err ->
                _onboardingError.value = err.message ?: "Gagal menerima undangan toko"
            }
            _onboardingBusy.value = false
        }
    }

    // --- PIN UNLOCK & ROLE SEPARATION ---
    fun clearLoginError() {
        _loginError.value = null
    }

    fun loginWithPin(username: String, rawPin: String, requestedRole: UserRole) {
        val storeCtx = activeStoreContext.value
        if (storeCtx == null) {
            _loginError.value = "Toko belum terhubung"
            return
        }
        if (requestedRole == UserRole.OWNER && storeCtx.cloudMemberRole != UserRole.OWNER) {
            _loginError.value = "Akun Google Anda diundang sebagai Pegawai Kasir. Hanya pemilik toko yang dapat masuk sebagai Owner."
            return
        }

        val cleanUser = username.trim().lowercase()
        val profile = allStaffPins.value.firstOrNull { it.username.equals(cleanUser, ignoreCase = true) }
        if (profile == null) {
            _loginError.value = "Username '$cleanUser' tidak ditemukan"
            return
        }
        if (!profile.isActive) {
            _loginError.value = "Akun '$cleanUser' sedang dinonaktifkan oleh Owner"
            return
        }
        if (profile.roleEnum != requestedRole) {
            _loginError.value = "Akun ini terdaftar sebagai ${profile.roleEnum.displayName}, bukan ${requestedRole.displayName}"
            return
        }
        if (!PinHasher.verifyPin(rawPin, profile.pinHash)) {
            _loginError.value = "PIN salah. Silakan coba lagi"
            return
        }

        _loginError.value = null
        _currentPinProfile.value = profile
        _cartQuantities.value = emptyMap()
        _customUnitPrices.value = emptyMap()
    }

    fun lockPinSession() {
        _currentPinProfile.value = null
        _cartQuantities.value = emptyMap()
        _customUnitPrices.value = emptyMap()
        _lastReceipt.value = null
        _loginError.value = null
    }

    // --- CART, PRICING & DISCOUNTS ---
    fun addToCart(product: ProductDoc) {
        if (product.stock <= 0) {
            emitToast("Stok ${product.name} sedang habis")
            return
        }
        val currentMap = _cartQuantities.value.toMutableMap()
        val currentQty = currentMap[product.id] ?: 0
        if (currentQty >= product.stock) {
            emitToast("Stok maksimal ${product.name} hanya ${product.stock} pcs")
            return
        }
        currentMap[product.id] = currentQty + 1
        _cartQuantities.value = currentMap
        emitToast("Ditambahkan: ${product.name} (${currentQty + 1}x)")
    }

    fun updateCartQuantity(productId: String, newQuantity: Int) {
        val currentMap = _cartQuantities.value.toMutableMap()
        if (newQuantity <= 0) {
            currentMap.remove(productId)
            _cartQuantities.value = currentMap
            val priceMap = _customUnitPrices.value.toMutableMap()
            priceMap.remove(productId)
            _customUnitPrices.value = priceMap
            return
        }
        val product = allProducts.value.find { it.id == productId } ?: return
        if (newQuantity > product.stock) {
            emitToast("Stok ${product.name} tersisa ${product.stock} pcs")
            currentMap[productId] = product.stock
        } else {
            currentMap[productId] = newQuantity
        }
        _cartQuantities.value = currentMap
    }

    fun setCartItemCustomPrice(productId: String, customPrice: Long?) {
        val priceMap = _customUnitPrices.value.toMutableMap()
        if (customPrice == null || customPrice < 0L) {
            priceMap.remove(productId)
        } else {
            priceMap[productId] = customPrice
        }
        _customUnitPrices.value = priceMap
    }

    fun clearCart() {
        _cartQuantities.value = emptyMap()
        _customUnitPrices.value = emptyMap()
    }

    fun handleQrCodeScanned(rawCode: String): QrScanOutcome {
        val cleanCode = rawCode.trim()
        val product = allProducts.value.firstOrNull {
            it.skuCode.equals(cleanCode, ignoreCase = true)
        } ?: return QrScanOutcome.NotRegistered(cleanCode)

        if (product.stock <= 0) {
            return QrScanOutcome.OutOfStock(product)
        }
        val currentQty = _cartQuantities.value[product.id] ?: 0
        if (currentQty >= product.stock) {
            return QrScanOutcome.OutOfStock(product)
        }

        val updatedMap = _cartQuantities.value.toMutableMap()
        val newQty = currentQty + 1
        updatedMap[product.id] = newQty
        _cartQuantities.value = updatedMap
        return QrScanOutcome.AddedToCart(product, newQty)
    }

    fun checkout(
        discountAmount: Long,
        paymentMethod: PaymentMethod,
        amountPaid: Long,
        notes: String,
        qrisReference: String
    ) {
        val storeCtx = activeStoreContext.value ?: return
        val cashier = _currentPinProfile.value ?: return
        val lines = cartItems.value.map {
            CloudCartLineRequest(
                productId = it.product.id,
                quantity = it.quantity,
                customUnitPrice = it.customUnitPrice
            )
        }
        viewModelScope.launch {
            repository.checkoutSale(
                storeId = storeCtx.storeId,
                cartLines = lines,
                discountAmount = discountAmount,
                paymentMethod = paymentMethod,
                amountPaid = amountPaid,
                cashierName = cashier.fullName,
                cashierRole = cashier.roleEnum,
                notes = notes,
                qrisReference = qrisReference
            ).onSuccess { receipt ->
                _cartQuantities.value = emptyMap()
                _customUnitPrices.value = emptyMap()
                _lastReceipt.value = receipt
                emitToast("Transaksi ${receipt.transaction.invoiceNumber} tersimpan ke cloud")
            }.onFailure { err ->
                emitToast(err.message ?: "Gagal memproses transaksi")
            }
        }
    }

    fun dismissReceipt() {
        _lastReceipt.value = null
    }

    // --- STORE QRIS CONFIG ---
    fun updateStoreQrisPayload(payload: String) {
        val storeCtx = activeStoreContext.value ?: return
        if (storeCtx.cloudMemberRole != UserRole.OWNER) {
            emitToast("Hanya Owner yang dapat mengubah QRIS toko")
            return
        }
        viewModelScope.launch {
            repository.updateStoreQrisPayload(storeCtx.storeId, payload)
                .onSuccess { emitToast("QRIS Toko berhasil diperbarui & tersinkronisasi online") }
                .onFailure { emitToast(it.message ?: "Gagal memperbarui QRIS toko") }
        }
    }

    // --- PRODUCT & STOCK MANAGEMENT (OWNER) ---
    fun saveProduct(
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
    ) {
        val storeCtx = activeStoreContext.value ?: return
        val user = _currentPinProfile.value ?: return
        if (user.roleEnum != UserRole.OWNER || storeCtx.cloudMemberRole != UserRole.OWNER) {
            emitToast("Hanya Owner yang dapat mengelola data barang & harga")
            return
        }
        viewModelScope.launch {
            repository.saveProduct(
                storeId = storeCtx.storeId,
                existingProduct = existingProduct,
                skuCode = skuCode,
                name = name,
                category = category,
                variantInfo = variantInfo,
                costPrice = costPrice,
                sellingPrice = sellingPrice,
                discountPercent = discountPercent,
                stock = stock,
                minStockAlert = minStockAlert,
                actorName = user.fullName
            ).onSuccess { saved ->
                emitToast(if (existingProduct == null) "Barang '${saved.name}' ditambahkan" else "Data '${saved.name}' diperbarui")
                onSuccess()
            }.onFailure { err ->
                emitToast(err.message ?: "Gagal menyimpan barang")
            }
        }
    }

    fun updateProductPricing(
        productId: String,
        costPrice: Long,
        sellingPrice: Long,
        discountPercent: Int,
        onSuccess: () -> Unit
    ) {
        val storeCtx = activeStoreContext.value ?: return
        val user = _currentPinProfile.value ?: return
        if (user.roleEnum != UserRole.OWNER || storeCtx.cloudMemberRole != UserRole.OWNER) {
            emitToast("Hanya Owner yang dapat mengatur harga & diskon barang")
            return
        }
        viewModelScope.launch {
            repository.updateProductPricing(
                storeId = storeCtx.storeId,
                productId = productId,
                costPrice = costPrice,
                sellingPrice = sellingPrice,
                discountPercent = discountPercent
            ).onSuccess {
                emitToast("Harga & diskon barang diperbarui")
                onSuccess()
            }.onFailure { err ->
                emitToast(err.message ?: "Gagal memperbarui harga")
            }
        }
    }

    fun restockProduct(
        product: ProductDoc,
        addQuantity: Int,
        newCostPrice: Long,
        note: String,
        onSuccess: () -> Unit
    ) {
        val storeCtx = activeStoreContext.value ?: return
        val user = _currentPinProfile.value ?: return
        if (user.roleEnum != UserRole.OWNER || storeCtx.cloudMemberRole != UserRole.OWNER) {
            emitToast("Hanya Owner yang dapat melakukan restok")
            return
        }
        viewModelScope.launch {
            repository.restockProduct(
                storeId = storeCtx.storeId,
                product = product,
                addQuantity = addQuantity,
                newCostPrice = newCostPrice,
                actorName = user.fullName,
                note = note
            ).onSuccess {
                emitToast("Restok +$addQuantity pcs berhasil dicatat")
                onSuccess()
            }.onFailure { err ->
                emitToast(err.message ?: "Gagal restok barang")
            }
        }
    }

    fun deleteProduct(product: ProductDoc) {
        val storeCtx = activeStoreContext.value ?: return
        val user = _currentPinProfile.value ?: return
        if (user.roleEnum != UserRole.OWNER || storeCtx.cloudMemberRole != UserRole.OWNER) {
            emitToast("Hanya Owner yang dapat menghapus barang")
            return
        }
        viewModelScope.launch {
            repository.deleteProduct(storeCtx.storeId, product.id)
                .onSuccess {
                    val map = _cartQuantities.value.toMutableMap()
                    map.remove(product.id)
                    _cartQuantities.value = map
                    val priceMap = _customUnitPrices.value.toMutableMap()
                    priceMap.remove(product.id)
                    _customUnitPrices.value = priceMap
                    emitToast("Produk '${product.name}' dihapus")
                }
                .onFailure {
                    emitToast("Gagal menghapus produk")
                }
        }
    }

    fun seedStarterCatalog() {
        val storeCtx = activeStoreContext.value ?: return
        if (storeCtx.cloudMemberRole != UserRole.OWNER) {
            emitToast("Minta Owner untuk mengisi katalog awal toko")
            return
        }
        val actor = _currentPinProfile.value?.fullName ?: currentUserDisplayName.ifBlank { "Owner" }
        viewModelScope.launch {
            repository.seedStarterCatalog(storeCtx.storeId, actor)
                .onSuccess { count ->
                    if (count > 0) {
                        emitToast("$count produk contoh berhasil disimpan ke cloud")
                    } else {
                        emitToast("Katalog contoh sudah tersedia")
                    }
                }
                .onFailure {
                    emitToast("Gagal memuat katalog contoh")
                }
        }
    }

    // --- STAFF & EMAIL INVITE MANAGEMENT (OWNER) ---
    fun inviteCashierByEmail(cashierEmail: String, cashierName: String, onSuccess: () -> Unit) {
        val store = activeStoreDoc.value ?: return
        if (activeStoreContext.value?.cloudMemberRole != UserRole.OWNER) return
        viewModelScope.launch {
            repository.inviteCashierByEmail(store, cashierEmail, cashierName)
                .onSuccess {
                    emitToast("Undangan untuk $cashierEmail dikirim! Minta kasir login dengan akun Google tersebut.")
                    onSuccess()
                }
                .onFailure { err ->
                    emitToast(err.message ?: "Gagal mengirim undangan kasir")
                }
        }
    }

    fun createStaffPinAccount(
        username: String,
        fullName: String,
        rawPin: String,
        onSuccess: () -> Unit
    ) {
        val storeCtx = activeStoreContext.value ?: return
        if (_currentPinProfile.value?.roleEnum != UserRole.OWNER) return
        viewModelScope.launch {
            repository.createStaffPinProfile(storeCtx.storeId, username, fullName, rawPin)
                .onSuccess {
                    emitToast("Profil PIN kasir '$fullName' berhasil dibuat")
                    onSuccess()
                }
                .onFailure { err ->
                    emitToast(err.message ?: "Gagal membuat profil PIN")
                }
        }
    }

    fun updateStaffPinAccount(
        profile: StaffPinProfileDoc,
        newFullName: String,
        newPin: String?,
        onSuccess: () -> Unit
    ) {
        val storeCtx = activeStoreContext.value ?: return
        if (_currentPinProfile.value?.roleEnum != UserRole.OWNER) return
        viewModelScope.launch {
            repository.updateStaffPinProfile(storeCtx.storeId, profile, newFullName, newPin)
                .onSuccess {
                    if (_currentPinProfile.value?.id == profile.id) {
                        _currentPinProfile.value = profile.copy(fullName = newFullName.trim())
                    }
                    emitToast("Profil '${newFullName.trim()}' diperbarui")
                    onSuccess()
                }
                .onFailure { err ->
                    emitToast(err.message ?: "Gagal memperbarui profil")
                }
        }
    }

    fun toggleStaffPinActive(profile: StaffPinProfileDoc) {
        val storeCtx = activeStoreContext.value ?: return
        if (_currentPinProfile.value?.roleEnum != UserRole.OWNER) return
        viewModelScope.launch {
            repository.toggleStaffPinActive(storeCtx.storeId, profile)
                .onSuccess {
                    val statusText = if (!profile.isActive) "diaktifkan" else "dinonaktifkan"
                    emitToast("Profil '${profile.fullName}' $statusText")
                }
                .onFailure { err ->
                    emitToast(err.message ?: "Gagal mengubah status profil")
                }
        }
    }

    // --- REPORT FILTERS ---
    fun selectReportDate(dateKey: String) {
        _reportPeriodMode.value = ReportPeriodMode.SELECTED_DAY
        _selectedReportDateKey.value = dateKey
    }

    fun setReportPeriodMode(mode: ReportPeriodMode) {
        _reportPeriodMode.value = mode
    }

    fun generateNextSkuSuggestion(category: ProductCategory): String {
        val prefix = "UMT-${category.codePrefix}-"
        val existingCount = allProducts.value.count { it.categoryEnum == category } + 1
        var candidate = prefix + existingCount.toString().padStart(3, '0')
        val existingSkus = allProducts.value.map { it.skuCode.uppercase() }.toSet()
        var counter = existingCount
        while (candidate.uppercase() in existingSkus) {
            counter++
            candidate = prefix + counter.toString().padStart(3, '0')
        }
        return candidate
    }

    fun markStockAlertsRead() {
        val currentSigs = stockAlertState.value.alerts.map { it.signature }.toSet()
        if (currentSigs.isNotEmpty()) {
            _acknowledgedLowStockSignatures.value = _acknowledgedLowStockSignatures.value + currentSigs
        }
    }

    fun emitToast(message: String) {
        _toastMessage.tryEmit(message)
    }

    private companion object {
        const val TAG = "PosViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
