package com.example.data.remote

import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.StockChangeType
import com.example.data.local.entity.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class StoreDoc(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val storeName: String = "UM TAZKIYAH",
    val qrisPayload: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StoreMemberDoc(
    @DocumentId val id: String = "",
    val uid: String = "",
    val storeId: String = "",
    val ownerUid: String = "",
    val storeName: String = "",
    val fullName: String = "",
    val email: String = "",
    val inviteId: String = "",
    val role: String = UserRole.KASIR.name,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val roleEnum: UserRole
        get() = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.KASIR)
}

@IgnoreExtraProperties
data class StoreInviteDoc(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val ownerUid: String = "",
    val cashierEmail: String = "",
    val cashierName: String = "",
    val status: String = "pending",
    val acceptedUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StaffPinProfileDoc(
    @DocumentId val id: String = "",
    val username: String = "",
    val fullName: String = "",
    val role: String = UserRole.KASIR.name,
    val pinHash: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val roleEnum: UserRole
        get() = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.KASIR)
}

@IgnoreExtraProperties
data class ProductDoc(
    @DocumentId val id: String = "",
    val skuCode: String = "",
    val name: String = "",
    val category: String = ProductCategory.BAJU.name,
    val variantInfo: String = "",
    val costPrice: Long = 0L,
    val sellingPrice: Long = 0L,
    val discountPercent: Int = 0,
    val stock: Int = 0,
    val minStockAlert: Int = 5,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val categoryEnum: ProductCategory
        get() = runCatching { ProductCategory.valueOf(category) }.getOrDefault(ProductCategory.BAJU)

    @get:Exclude
    val effectiveSellingPrice: Long
        get() = if (discountPercent in 1..99) {
            (sellingPrice * (100 - discountPercent)) / 100L
        } else {
            sellingPrice
        }

    @get:Exclude
    val profitPerUnit: Long
        get() = effectiveSellingPrice - costPrice

    @get:Exclude
    val marginPercent: Double
        get() = if (effectiveSellingPrice > 0L) ((effectiveSellingPrice - costPrice).toDouble() / effectiveSellingPrice.toDouble()) * 100.0 else 0.0

    @get:Exclude
    val isLowStock: Boolean
        get() = stock <= minStockAlert
}

@IgnoreExtraProperties
data class SaleTransactionDoc(
    @DocumentId val id: String = "",
    val invoiceNumber: String = "",
    val timestampMillis: Long = 0L,
    val dateKey: String = "",
    val totalItems: Int = 0,
    val totalRevenue: Long = 0L,
    val discountAmount: Long = 0L,
    val finalRevenue: Long = 0L,
    val totalCost: Long = 0L,
    val netProfit: Long = 0L,
    val paymentMethod: String = PaymentMethod.TUNAI.name,
    val amountPaid: Long = 0L,
    val changeAmount: Long = 0L,
    val cashierUid: String = "",
    val cashierName: String = "",
    val cashierRole: String = UserRole.KASIR.name,
    val notes: String = "",
    val qrisReference: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @get:Exclude
    val paymentMethodEnum: PaymentMethod
        get() = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.TUNAI)

    @get:Exclude
    val cashierRoleEnum: UserRole
        get() = runCatching { UserRole.valueOf(cashierRole) }.getOrDefault(UserRole.KASIR)
}

@IgnoreExtraProperties
data class SaleItemDoc(
    @DocumentId val id: String = "",
    val transactionId: String = "",
    val dateKey: String = "",
    val timestampMillis: Long = 0L,
    val productId: String = "",
    val skuCode: String = "",
    val productName: String = "",
    val categoryName: String = "",
    val quantity: Int = 0,
    val unitCostPrice: Long = 0L,
    val unitSellingPrice: Long = 0L,
    val subtotalRevenue: Long = 0L,
    val subtotalCost: Long = 0L,
    val subtotalProfit: Long = 0L,
    val cashierUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StockHistoryDoc(
    @DocumentId val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val changeType: String = StockChangeType.RESTOK.name,
    val quantityDelta: Int = 0,
    val stockAfter: Int = 0,
    val unitCostAtChange: Long = 0L,
    val actorName: String = "",
    val actorUid: String = "",
    val note: String = "",
    val timestampMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @get:Exclude
    val changeTypeEnum: StockChangeType
        get() = runCatching { StockChangeType.valueOf(changeType) }.getOrDefault(StockChangeType.RESTOK)
}

data class CloudSaleWithItems(
    val transaction: SaleTransactionDoc,
    val items: List<SaleItemDoc>
)
