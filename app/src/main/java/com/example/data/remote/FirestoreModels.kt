package com.example.data.remote

import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.StockChangeType
import com.example.data.local.entity.StoreType
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
    val storeName: String = "Tajir",
    val storeType: String = StoreType.LAINNYA.name,
    val customStoreType: String = "",
    val qrisPayload: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val storeTypeEnum: StoreType
        get() = runCatching { StoreType.valueOf(storeType) }.getOrDefault(StoreType.LAINNYA)

    @get:Exclude
    val resolvedStoreTypeLabel: String
        get() {
            val typeEnum = storeTypeEnum
            val custom = customStoreType.trim()
            return if (typeEnum == StoreType.LAINNYA && custom.isNotBlank()) {
                custom
            } else {
                typeEnum.displayName
            }
        }
}

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
data class StoreCategoryDoc(
    @DocumentId val id: String = "",
    val code: String = "",
    val displayName: String = "",
    val codePrefix: String = "CT",
    val colorIndex: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun defaultPresetsForStoreType(storeType: StoreType): List<StoreCategoryDoc> = when (storeType) {
            StoreType.MAKANAN_MINUMAN -> listOf(
                StoreCategoryDoc(id = "cat_makanan", code = "MAKANAN", displayName = "Makanan", codePrefix = "MK", colorIndex = 0),
                StoreCategoryDoc(id = "cat_minuman", code = "MINUMAN", displayName = "Minuman", codePrefix = "MN", colorIndex = 1),
                StoreCategoryDoc(id = "cat_snack_kue", code = "SNACK_KUE", displayName = "Snack & Kue", codePrefix = "SK", colorIndex = 2),
                StoreCategoryDoc(id = "cat_paket_menu", code = "PAKET_MENU", displayName = "Paket Menu", codePrefix = "PM", colorIndex = 3)
            )
            StoreType.GROSIR -> listOf(
                StoreCategoryDoc(id = "cat_sembako", code = "SEMBAKO", displayName = "Sembako", codePrefix = "SB", colorIndex = 0),
                StoreCategoryDoc(id = "cat_minuman_kemasan", code = "MINUMAN_KEMASAN", displayName = "Minuman Kemasan", codePrefix = "MK", colorIndex = 1),
                StoreCategoryDoc(id = "cat_kebutuhan_rumah", code = "KEBUTUHAN_RUMAH", displayName = "Kebutuhan Rumah", codePrefix = "KR", colorIndex = 2),
                StoreCategoryDoc(id = "cat_snack_eceran", code = "SNACK_ECERAN", displayName = "Snack & Eceran", codePrefix = "SE", colorIndex = 3)
            )
            StoreType.BAJU -> listOf(
                StoreCategoryDoc(id = "cat_baju_atasan", code = "BAJU", displayName = "Baju & Atasan", codePrefix = "BJ", colorIndex = 0),
                StoreCategoryDoc(id = "cat_celana_rok", code = "CELANA_ROK", displayName = "Celana & Rok", codePrefix = "CR", colorIndex = 1),
                StoreCategoryDoc(id = "cat_hijab", code = "HIJAB_KERUDUNG", displayName = "Hijab & Kerudung", codePrefix = "HJ", colorIndex = 2),
                StoreCategoryDoc(id = "cat_aksesoris", code = "AKSESORIS", displayName = "Aksesoris", codePrefix = "AK", colorIndex = 3)
            )
            StoreType.LAINNYA -> listOf(
                StoreCategoryDoc(id = "cat_produk_utama", code = "PRODUK_UTAMA", displayName = "Produk Utama", codePrefix = "PU", colorIndex = 0),
                StoreCategoryDoc(id = "cat_kebutuhan_harian", code = "KEBUTUHAN_HARIAN", displayName = "Kebutuhan Harian", codePrefix = "KH", colorIndex = 1),
                StoreCategoryDoc(id = "cat_perlengkapan", code = "PERLENGKAPAN", displayName = "Perlengkapan", codePrefix = "PL", colorIndex = 2),
                StoreCategoryDoc(id = "cat_aksesoris", code = "AKSESORIS", displayName = "Aksesoris", codePrefix = "AK", colorIndex = 3)
            )
        }

        fun defaultPresets(): List<StoreCategoryDoc> = defaultPresetsForStoreType(StoreType.LAINNYA)

        fun normalizeCode(displayName: String): String {
            val ascii = displayName.trim().uppercase()
                .replace(Regex("[^A-Z0-9]+"), "_")
                .trim('_')
            return ascii.take(48).ifBlank { "KATEGORI" }
        }

        fun suggestPrefix(displayName: String): String {
            val words = displayName.trim().uppercase()
                .split(Regex("[^A-Z0-9]+"))
                .filter { it.isNotBlank() }
            val raw = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}"
                words.size == 1 && words[0].length >= 2 -> words[0].take(2)
                words.size == 1 -> words[0]
                else -> "CT"
            }
            return raw.take(4).ifBlank { "CT" }
        }

        fun resolve(categoryCode: String, categories: List<StoreCategoryDoc>): StoreCategoryDoc {
            val matched = categories.firstOrNull { it.code.equals(categoryCode, ignoreCase = true) }
            if (matched != null) return matched
            val enumMatch = ProductCategory.entries.firstOrNull { it.name.equals(categoryCode, ignoreCase = true) }
            if (enumMatch != null) {
                return StoreCategoryDoc(
                    id = "cat_${enumMatch.name.lowercase()}",
                    code = enumMatch.name,
                    displayName = enumMatch.displayName,
                    codePrefix = enumMatch.codePrefix,
                    colorIndex = enumMatch.ordinal
                )
            }
            val cleanCode = categoryCode.trim().ifBlank { "UMUM" }
            val pretty = cleanCode.replace('_', ' ').lowercase()
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { w -> w.replaceFirstChar { ch -> ch.uppercase() } }
                .ifBlank { "Umum" }
            return StoreCategoryDoc(
                id = "cat_${cleanCode.lowercase().replace(Regex("[^a-z0-9_]+"), "_").take(40)}",
                code = cleanCode.uppercase(),
                displayName = pretty,
                codePrefix = suggestPrefix(pretty),
                colorIndex = kotlin.math.abs(cleanCode.uppercase().hashCode()) % 6
            )
        }
    }
}

@IgnoreExtraProperties
data class ProductDoc(
    @DocumentId val id: String = "",
    val skuCode: String = "",
    val name: String = "",
    val category: String = ProductCategory.BAJU.name,
    val variantInfo: String = "",
    val variantName: String = "",
    val color: String = "",
    val size: String = "",
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

    @get:Exclude
    val attributeBadges: List<Pair<String, String>>
        get() {
            val cleanVariant = variantName.trim()
            val cleanColor = color.trim()
            val cleanSize = this@ProductDoc.size.trim()
            return buildList {
                if (cleanVariant.isNotBlank()) add("Varian" to cleanVariant)
                if (cleanColor.isNotBlank()) add("Warna" to cleanColor)
                if (cleanSize.isNotBlank()) add("Ukuran" to cleanSize)
            }
        }

    @get:Exclude
    val resolvedVariantSummary: String
        get() = composeVariantSummary(variantName, color, size, variantInfo)

    @get:Exclude
    val hasAttributes: Boolean
        get() = variantName.isNotBlank() || color.isNotBlank() || size.isNotBlank() || variantInfo.isNotBlank()

    fun resolvedCategory(categories: List<StoreCategoryDoc>): StoreCategoryDoc =
        StoreCategoryDoc.resolve(category, categories)

    companion object {
        fun composeVariantSummary(
            variantName: String = "",
            color: String = "",
            size: String = "",
            legacyVariantInfo: String = ""
        ): String {
            val structuredParts = buildList {
                if (variantName.trim().isNotBlank()) add("Varian: ${variantName.trim()}")
                if (color.trim().isNotBlank()) add("Warna: ${color.trim()}")
                if (size.trim().isNotBlank()) add("Ukuran: ${size.trim()}")
            }
            return if (structuredParts.isNotEmpty()) {
                structuredParts.joinToString(" • ").take(160)
            } else {
                legacyVariantInfo.trim().take(160)
            }
        }
    }
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
    val customerName: String = "",
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
    val variantSummary: String = "",
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
