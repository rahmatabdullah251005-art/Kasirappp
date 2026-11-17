package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.StockChangeType
import com.example.data.local.entity.StoreType
import com.example.data.local.entity.UserRole
import com.example.util.DateUtils
import com.example.util.PinHasher
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class CloudCartLineRequest(
    val productId: String,
    val quantity: Int,
    val customUnitPrice: Long? = null
)

class FirestorePosRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus login dengan Google terlebih dahulu.")
    }

    private fun <T> Flow<T>.handleStreamErrors(operation: OperationType, path: String, expectedUid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(exceptionToLog, operation, path)
            }
            throw error
        }

    // --- STORE DISCOVERY & MULTI-DEVICE LINKING ---

    fun observeOwnedStores(): Flow<List<StoreDoc>> = flow {
        val uid = requireUserId()
        val path = "stores"
        emitAll(
            db.collection(path)
                .whereEqualTo("ownerUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StoreDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAcceptedStoreInvites(): Flow<List<StoreInviteDoc>> = flow {
        val uid = requireUserId()
        val path = "store_invites"
        emitAll(
            db.collection(path)
                .whereEqualTo("acceptedUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StoreInviteDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observePendingInvitesForEmail(email: String): Flow<List<StoreInviteDoc>> = flow {
        val uid = requireUserId()
        val cleanEmail = email.trim().lowercase()
        val path = "store_invites"
        emitAll(
            db.collection(path)
                .whereEqualTo("cashierEmail", cleanEmail)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StoreInviteDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "pending" }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeStoreDoc(storeId: String): Flow<StoreDoc?> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId"
        emitAll(
            db.document(path)
                .snapshots()
                .map { snap ->
                    snap.toObject(StoreDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamErrors(OperationType.GET, path, uid)
        )
    }

    fun observeStoreInvitesAsOwner(storeId: String): Flow<List<StoreInviteDoc>> = flow {
        val uid = requireUserId()
        val path = "store_invites"
        emitAll(
            db.collection(path)
                .whereEqualTo("ownerUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StoreInviteDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.storeId == storeId }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createOwnerStore(
        storeName: String,
        ownerFullName: String,
        ownerEmail: String,
        defaultQrisPayload: String,
        storeType: StoreType = StoreType.LAINNYA,
        customStoreType: String = ""
    ): Result<StoreDoc> = runCatching {
        val uid = requireUserId()
        val storeRef = db.collection("stores").document()
        val storeId = storeRef.id
        val cleanStoreName = storeName.trim().ifBlank { "Toko Tajir" }
        val cleanOwnerName = ownerFullName.trim().ifBlank { "Owner Tajir" }
        val cleanCustomType = if (storeType == StoreType.LAINNYA) {
            customStoreType.trim().take(60)
        } else {
            ""
        }

        val storePayload = buildMap<String, Any> {
            put("ownerUid", uid)
            put("storeName", cleanStoreName)
            put("storeType", storeType.name)
            if (cleanCustomType.isNotBlank()) put("customStoreType", cleanCustomType)
            put("qrisPayload", defaultQrisPayload)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val memberRef = storeRef.collection("members").document(uid)
        val memberPayload = buildMap<String, Any> {
            put("uid", uid)
            put("storeId", storeId)
            put("ownerUid", uid)
            put("storeName", cleanStoreName)
            put("fullName", cleanOwnerName)
            if (ownerEmail.isNotBlank()) put("email", ownerEmail.trim().lowercase())
            put("role", UserRole.OWNER.name)
            put("isActive", true)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val defaultPins = listOf(
            Triple("owner", cleanOwnerName, UserRole.OWNER to "0000"),
            Triple("aisyah", "Aisyah (Kasir)", UserRole.KASIR to "1234"),
            Triple("umar", "Umar (Kasir)", UserRole.KASIR to "2345")
        )

        try {
            // Commit parent store document first so subcollection security rules
            // checking exists()/get() on stores/{storeId} evaluate cleanly.
            storeRef.set(storePayload).await()

            val batch = db.batch()
            batch.set(memberRef, memberPayload)
            defaultPins.forEach { (uname, fName, rolePair) ->
                val pinRef = storeRef.collection("staff_pins").document("pin_$uname")
                batch.set(
                    pinRef,
                    mapOf(
                        "username" to uname,
                        "fullName" to fName,
                        "role" to rolePair.first.name,
                        "pinHash" to PinHasher.hashPin(rolePair.second),
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            StoreCategoryDoc.defaultPresetsForStoreType(storeType).forEach { cat ->
                val catRef = storeRef.collection("categories").document(cat.id)
                batch.set(
                    catRef,
                    mapOf(
                        "code" to cat.code,
                        "displayName" to cat.displayName,
                        "codePrefix" to cat.codePrefix,
                        "colorIndex" to cat.colorIndex,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            StoreDoc(
                id = storeId,
                ownerUid = uid,
                storeName = cleanStoreName,
                storeType = storeType.name,
                customStoreType = cleanCustomType,
                qrisPayload = defaultQrisPayload
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, storeRef.path)
            throw e
        }
    }

    suspend fun inviteCashierByEmail(
        store: StoreDoc,
        cashierEmail: String,
        cashierName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanEmail = cashierEmail.trim().lowercase()
        val cleanName = cashierName.trim()
        require(cleanEmail.contains("@")) { "Email Google pegawai tidak valid" }
        require(cleanName.isNotBlank()) { "Nama pegawai wajib diisi" }

        val inviteRef = db.collection("store_invites").document()
        val payload = mapOf(
            "storeId" to store.id,
            "storeName" to store.storeName,
            "ownerUid" to uid,
            "cashierEmail" to cleanEmail,
            "cashierName" to cleanName,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            inviteRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, inviteRef.path)
            throw e
        }
    }

    suspend fun acceptStoreInvite(
        invite: StoreInviteDoc,
        userFullName: String,
        userEmail: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val inviteRef = db.collection("store_invites").document(invite.id)
        val memberRef = db.collection("stores").document(invite.storeId)
            .collection("members").document(uid)

        val batch = db.batch()
        batch.update(
            inviteRef,
            mapOf(
                "status" to "accepted",
                "acceptedUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            memberRef,
            buildMap<String, Any> {
                put("uid", uid)
                put("storeId", invite.storeId)
                put("ownerUid", invite.ownerUid)
                put("storeName", invite.storeName)
                put("fullName", userFullName.ifBlank { invite.cashierName })
                if (userEmail.isNotBlank()) put("email", userEmail.trim().lowercase())
                put("inviteId", invite.id)
                put("role", UserRole.KASIR.name)
                put("isActive", true)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        try {
            batch.commit().await()
            invite.storeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, memberRef.path)
            throw e
        }
    }

    suspend fun updateStoreQrisPayload(storeId: String, qrisPayload: String): Result<Unit> = runCatching {
        requireUserId()
        require(qrisPayload.trim().isNotBlank()) { "Payload QRIS tidak boleh kosong" }
        val docRef = db.collection("stores").document(storeId)
        try {
            docRef.update(
                mapOf(
                    "qrisPayload" to qrisPayload.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- STAFF PIN PROFILES ---

    fun observeStaffPins(storeId: String): Flow<List<StaffPinProfileDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/staff_pins"
        emitAll(
            db.collection("stores").document(storeId).collection("staff_pins")
                .snapshots()
                .map { snap ->
                    snap.toObjects(StaffPinProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<StaffPinProfileDoc> { it.roleEnum != UserRole.OWNER }.thenBy { it.fullName })
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createStaffPinProfile(
        storeId: String,
        username: String,
        fullName: String,
        rawPin: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val cleanUsername = username.trim().lowercase()
        val cleanName = fullName.trim()
        require(cleanUsername.isNotBlank()) { "Username wajib diisi" }
        require(cleanName.isNotBlank()) { "Nama kasir wajib diisi" }
        require(rawPin.trim().length in 4..6) { "PIN harus 4 hingga 6 digit" }

        val docRef = db.collection("stores").document(storeId)
            .collection("staff_pins").document("pin_$cleanUsername")
        val existing = docRef.get().await()
        require(!existing.exists()) { "Username '$cleanUsername' sudah ada" }

        try {
            docRef.set(
                mapOf(
                    "username" to cleanUsername,
                    "fullName" to cleanName,
                    "role" to UserRole.KASIR.name,
                    "pinHash" to PinHasher.hashPin(rawPin),
                    "isActive" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateStaffPinProfile(
        storeId: String,
        profile: StaffPinProfileDoc,
        newFullName: String,
        newRawPin: String?
    ): Result<Unit> = runCatching {
        requireUserId()
        val cleanName = newFullName.trim()
        require(cleanName.isNotBlank()) { "Nama wajib diisi" }
        val updatedHash = if (!newRawPin.isNullOrBlank()) {
            require(newRawPin.trim().length in 4..6) { "PIN baru harus 4 hingga 6 digit" }
            PinHasher.hashPin(newRawPin)
        } else {
            profile.pinHash
        }
        val docRef = db.collection("stores").document(storeId)
            .collection("staff_pins").document(profile.id)
        try {
            docRef.update(
                mapOf(
                    "fullName" to cleanName,
                    "pinHash" to updatedHash,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleStaffPinActive(storeId: String, profile: StaffPinProfileDoc): Result<Unit> = runCatching {
        requireUserId()
        require(profile.roleEnum != UserRole.OWNER) { "Akun Owner tidak dapat dinonaktifkan" }
        val docRef = db.collection("stores").document(storeId)
            .collection("staff_pins").document(profile.id)
        try {
            docRef.update(
                mapOf(
                    "isActive" to !profile.isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- CATEGORIES ---

    fun observeCategories(storeId: String): Flow<List<StoreCategoryDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/categories"
        emitAll(
            db.collection("stores").document(storeId).collection("categories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(StoreCategoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createCategory(
        storeId: String,
        displayName: String,
        customPrefix: String = "",
        colorIndex: Int? = null
    ): Result<StoreCategoryDoc> = runCatching {
        requireUserId()
        val cleanName = displayName.trim()
        require(cleanName.isNotBlank()) { "Nama kategori tidak boleh kosong" }
        require(cleanName.length <= 60) { "Nama kategori maksimal 60 karakter" }

        val col = db.collection("stores").document(storeId).collection("categories")
        val existingSnap = col.get().await()
        val existingDocs = existingSnap.toObjects(StoreCategoryDoc::class.java)
        require(existingDocs.none { it.displayName.equals(cleanName, ignoreCase = true) }) {
            "Kategori '$cleanName' sudah ada"
        }

        val baseCode = StoreCategoryDoc.normalizeCode(cleanName)
        var candidateCode = baseCode
        var suffix = 2
        while (existingDocs.any { it.code.equals(candidateCode, ignoreCase = true) }) {
            candidateCode = "${baseCode.take(42)}_$suffix"
            suffix++
        }

        val cleanPrefix = customPrefix.trim().uppercase()
            .replace(Regex("[^A-Z0-9]"), "")
            .take(6)
            .ifBlank { StoreCategoryDoc.suggestPrefix(cleanName) }
        val resolvedColor = (colorIndex ?: existingDocs.size) % 6
        val docId = "cat_${candidateCode.lowercase()}"
        val docRef = col.document(docId)

        try {
            docRef.set(
                mapOf(
                    "code" to candidateCode,
                    "displayName" to cleanName,
                    "codePrefix" to cleanPrefix,
                    "colorIndex" to resolvedColor,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            StoreCategoryDoc(
                id = docId,
                code = candidateCode,
                displayName = cleanName,
                codePrefix = cleanPrefix,
                colorIndex = resolvedColor
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCategory(storeId: String, categoryId: String): Result<Unit> = runCatching {
        requireUserId()
        require(categoryId.isNotBlank()) { "ID kategori tidak valid" }
        val docRef = db.collection("stores").document(storeId).collection("categories").document(categoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun seedDefaultCategoriesIfEmpty(
        storeId: String,
        storeType: StoreType = StoreType.LAINNYA
    ): Result<Int> = runCatching {
        requireUserId()
        val col = db.collection("stores").document(storeId).collection("categories")
        val existing = col.limit(1).get().await()
        if (!existing.isEmpty) return@runCatching 0

        val presets = StoreCategoryDoc.defaultPresetsForStoreType(storeType)
        val batch = db.batch()
        presets.forEach { cat ->
            batch.set(
                col.document(cat.id),
                mapOf(
                    "code" to cat.code,
                    "displayName" to cat.displayName,
                    "codePrefix" to cat.codePrefix,
                    "colorIndex" to cat.colorIndex,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
            presets.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path)
            throw e
        }
    }

    // --- PRODUCTS & STOCK ---

    fun observeProducts(storeId: String): Flow<List<ProductDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/products"
        emitAll(
            db.collection("stores").document(storeId).collection("products")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ProductDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeStockHistories(storeId: String): Flow<List<StockHistoryDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/stock_histories"
        emitAll(
            db.collection("stores").document(storeId).collection("stock_histories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(StockHistoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.timestampMillis }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun saveProduct(
        storeId: String,
        existingProduct: ProductDoc?,
        skuCode: String,
        name: String,
        categoryCode: String,
        variantInfo: String = "",
        variantName: String = "",
        color: String = "",
        size: String = "",
        costPrice: Long,
        sellingPrice: Long,
        discountPercent: Int = 0,
        stock: Int,
        minStockAlert: Int,
        actorName: String
    ): Result<ProductDoc> = runCatching {
        val uid = requireUserId()
        val cleanSku = skuCode.trim().uppercase()
        val cleanName = name.trim()
        val cleanCategory = categoryCode.trim().ifBlank { ProductCategory.BAJU.name }
        val cleanVariantName = variantName.trim().take(80)
        val cleanColor = color.trim().take(60)
        val cleanSize = size.trim().take(60)
        val cleanVariantInfo = ProductDoc.composeVariantSummary(
            variantName = cleanVariantName,
            color = cleanColor,
            size = cleanSize,
            legacyVariantInfo = variantInfo
        )
        val clampedDiscount = discountPercent.coerceIn(0, 99)
        require(cleanSku.isNotBlank()) { "Kode QR / SKU tidak boleh kosong" }
        require(cleanName.isNotBlank()) { "Nama barang tidak boleh kosong" }
        require(costPrice >= 0L) { "Harga modal tidak valid" }
        require(sellingPrice >= 0L) { "Harga jual tidak valid" }
        require(stock >= 0) { "Jumlah stok tidak valid" }

        val productsCol = db.collection("stores").document(storeId).collection("products")
        val duplicateCheck = productsCol.whereEqualTo("skuCode", cleanSku).get().await()
        val conflicting = duplicateCheck.documents.firstOrNull { it.id != existingProduct?.id }
        if (conflicting != null) {
            throw IllegalArgumentException("Kode QR '$cleanSku' sudah digunakan oleh produk lain")
        }

        val now = System.currentTimeMillis()
        val batch = db.batch()
        val targetDocRef = if (existingProduct == null) {
            productsCol.document()
        } else {
            productsCol.document(existingProduct.id)
        }

        if (existingProduct == null) {
            val payload = buildMap<String, Any> {
                put("skuCode", cleanSku)
                put("name", cleanName)
                put("category", cleanCategory)
                if (cleanVariantInfo.isNotBlank()) put("variantInfo", cleanVariantInfo)
                if (cleanVariantName.isNotBlank()) put("variantName", cleanVariantName)
                if (cleanColor.isNotBlank()) put("color", cleanColor)
                if (cleanSize.isNotBlank()) put("size", cleanSize)
                put("costPrice", costPrice)
                put("sellingPrice", sellingPrice)
                put("discountPercent", clampedDiscount)
                put("stock", stock)
                put("minStockAlert", minStockAlert.coerceAtLeast(0))
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.set(targetDocRef, payload)

            val histRef = db.collection("stores").document(storeId).collection("stock_histories").document()
            batch.set(
                histRef,
                mapOf(
                    "productId" to targetDocRef.id,
                    "productName" to cleanName,
                    "changeType" to StockChangeType.PRODUK_BARU.name,
                    "quantityDelta" to stock,
                    "stockAfter" to stock,
                    "unitCostAtChange" to costPrice,
                    "actorName" to actorName,
                    "actorUid" to uid,
                    "note" to "Input produk baru",
                    "timestampMillis" to now,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            val payload = buildMap<String, Any> {
                put("skuCode", cleanSku)
                put("name", cleanName)
                put("category", cleanCategory)
                put("variantInfo", cleanVariantInfo)
                put("variantName", cleanVariantName)
                put("color", cleanColor)
                put("size", cleanSize)
                put("costPrice", costPrice)
                put("sellingPrice", sellingPrice)
                put("discountPercent", clampedDiscount)
                put("stock", stock)
                put("minStockAlert", minStockAlert.coerceAtLeast(0))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.update(targetDocRef, payload)

            if (stock != existingProduct.stock) {
                val histRef = db.collection("stores").document(storeId).collection("stock_histories").document()
                batch.set(
                    histRef,
                    mapOf(
                        "productId" to targetDocRef.id,
                        "productName" to cleanName,
                        "changeType" to StockChangeType.KOREKSI.name,
                        "quantityDelta" to (stock - existingProduct.stock),
                        "stockAfter" to stock,
                        "unitCostAtChange" to costPrice,
                        "actorName" to actorName,
                        "actorUid" to uid,
                        "note" to "Penyesuaian stok manual",
                        "timestampMillis" to now,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        try {
            batch.commit().await()
            ProductDoc(
                id = targetDocRef.id,
                skuCode = cleanSku,
                name = cleanName,
                category = cleanCategory,
                variantInfo = cleanVariantInfo,
                variantName = cleanVariantName,
                color = cleanColor,
                size = cleanSize,
                costPrice = costPrice,
                sellingPrice = sellingPrice,
                discountPercent = clampedDiscount,
                stock = stock,
                minStockAlert = minStockAlert.coerceAtLeast(0)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, targetDocRef.path)
            throw e
        }
    }

    suspend fun updateProductPricing(
        storeId: String,
        productId: String,
        costPrice: Long,
        sellingPrice: Long,
        discountPercent: Int
    ): Result<Unit> = runCatching {
        requireUserId()
        require(costPrice >= 0L) { "Harga modal tidak valid" }
        require(sellingPrice >= 0L) { "Harga jual tidak valid" }
        val clampedDiscount = discountPercent.coerceIn(0, 99)
        val docRef = db.collection("stores").document(storeId).collection("products").document(productId)
        try {
            docRef.update(
                mapOf(
                    "costPrice" to costPrice,
                    "sellingPrice" to sellingPrice,
                    "discountPercent" to clampedDiscount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun restockProduct(
        storeId: String,
        product: ProductDoc,
        addQuantity: Int,
        newCostPrice: Long,
        actorName: String,
        note: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(addQuantity > 0) { "Jumlah restok harus lebih dari 0" }
        require(newCostPrice >= 0L) { "Harga modal tidak valid" }

        val now = System.currentTimeMillis()
        val prodRef = db.collection("stores").document(storeId).collection("products").document(product.id)
        val histRef = db.collection("stores").document(storeId).collection("stock_histories").document()

        try {
            db.runTransaction { tx ->
                val snap = tx.get(prodRef)
                val currentStock = (snap.getLong("stock") ?: 0L).toInt()
                val newStock = currentStock + addQuantity
                tx.update(
                    prodRef,
                    mapOf(
                        "stock" to newStock,
                        "costPrice" to newCostPrice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    histRef,
                    mapOf(
                        "productId" to product.id,
                        "productName" to product.name,
                        "changeType" to StockChangeType.RESTOK.name,
                        "quantityDelta" to addQuantity,
                        "stockAfter" to newStock,
                        "unitCostAtChange" to newCostPrice,
                        "actorName" to actorName,
                        "actorUid" to uid,
                        "note" to note.ifBlank { "Restok barang masuk" },
                        "timestampMillis" to now,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, prodRef.path)
            throw e
        }
    }

    /**
     * Penambahan atau pengurangan stok oleh Pegawai Kasir maupun Owner.
     * Hanya mengubah field `stock` & `updatedAt` pada dokumen produk sehingga harga modal/jual tetap terkunci oleh Security Rules.
     */
    suspend fun adjustProductStock(
        storeId: String,
        product: ProductDoc,
        isAddition: Boolean,
        quantity: Int,
        actorName: String,
        note: String,
        includeUnitCost: Boolean = false
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        require(quantity > 0) { "Jumlah perubahan stok harus lebih dari 0" }

        val now = System.currentTimeMillis()
        val prodRef = db.collection("stores").document(storeId).collection("products").document(product.id)
        val histRef = db.collection("stores").document(storeId).collection("stock_histories").document()
        val delta = if (isAddition) quantity else -quantity
        val changeType = if (isAddition) StockChangeType.RESTOK else StockChangeType.KOREKSI
        val resolvedNote = note.trim().ifBlank {
            if (isAddition) "Penambahan stok barang" else "Pengurangan / koreksi stok"
        }

        try {
            db.runTransaction { tx ->
                val snap = tx.get(prodRef)
                val currentStock = (snap.getLong("stock") ?: 0L).toInt()
                if (!isAddition && currentStock < quantity) {
                    throw IllegalArgumentException(
                        "Stok ${product.name} hanya tersisa $currentStock pcs, tidak dapat dikurangi $quantity pcs"
                    )
                }
                val newStock = currentStock + delta
                tx.update(
                    prodRef,
                    mapOf(
                        "stock" to newStock,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    histRef,
                    mapOf(
                        "productId" to product.id,
                        "productName" to product.name,
                        "changeType" to changeType.name,
                        "quantityDelta" to delta,
                        "stockAfter" to newStock,
                        "unitCostAtChange" to if (includeUnitCost) product.costPrice else 0L,
                        "actorName" to actorName,
                        "actorUid" to uid,
                        "note" to resolvedNote,
                        "timestampMillis" to now,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                newStock
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, prodRef.path)
            throw e
        }
    }

    suspend fun deleteProduct(storeId: String, productId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("products").document(productId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- TRANSACTIONS & REAL-TIME DAILY REPORTING ---

    fun observeAllTransactionsForOwner(storeId: String): Flow<List<SaleTransactionDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/transactions"
        emitAll(
            db.collection("stores").document(storeId).collection("transactions")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SaleTransactionDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.timestampMillis }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllSaleItemsForOwner(storeId: String): Flow<List<SaleItemDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/sale_items"
        emitAll(
            db.collection("stores").document(storeId).collection("sale_items")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SaleItemDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeCashierOwnTransactions(storeId: String, dateKey: String): Flow<List<SaleTransactionDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/transactions"
        emitAll(
            db.collection("stores").document(storeId).collection("transactions")
                .whereEqualTo("cashierUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SaleTransactionDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.dateKey == dateKey }
                        .sortedByDescending { it.timestampMillis }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllCashierOwnTransactions(storeId: String): Flow<List<SaleTransactionDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/transactions"
        emitAll(
            db.collection("stores").document(storeId).collection("transactions")
                .whereEqualTo("cashierUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SaleTransactionDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.timestampMillis }
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeCashierOwnSaleItems(storeId: String): Flow<List<SaleItemDoc>> = flow {
        val uid = requireUserId()
        val path = "stores/$storeId/sale_items"
        emitAll(
            db.collection("stores").document(storeId).collection("sale_items")
                .whereEqualTo("cashierUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SaleItemDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun checkoutSale(
        storeId: String,
        cartLines: List<CloudCartLineRequest>,
        discountAmount: Long,
        paymentMethod: PaymentMethod,
        amountPaid: Long,
        cashierName: String,
        cashierRole: UserRole,
        customerName: String = "",
        notes: String,
        qrisReference: String
    ): Result<CloudSaleWithItems> = runCatching {
        val uid = requireUserId()
        require(cartLines.isNotEmpty()) { "Keranjang belanja masih kosong" }
        require(discountAmount >= 0L) { "Diskon tidak boleh negatif" }

        val now = System.currentTimeMillis()
        val dateKey = DateUtils.dateKeyFromMillis(now)
        val storeRef = db.collection("stores").document(storeId)
        val txRef = storeRef.collection("transactions").document()
        val shortSuffix = UUID.randomUUID().toString().take(4).uppercase()
        val invoiceNumber = "INV-${DateUtils.invoiceDatePrefix(now)}-$shortSuffix"
        val storeCategories = runCatching {
            storeRef.collection("categories").get().await()
                .toObjects(StoreCategoryDoc::class.java)
        }.getOrDefault(emptyList())

        try {
            db.runTransaction { transaction ->
                // 1. ALL READS FIRST
                val productSnaps = cartLines.map { line ->
                    require(line.quantity > 0) { "Jumlah barang harus lebih dari 0" }
                    if (line.customUnitPrice != null) {
                        require(line.customUnitPrice >= 0L) { "Harga satuan tidak valid" }
                    }
                    val pRef = storeRef.collection("products").document(line.productId)
                    val snap = transaction.get(pRef)
                    val product = snap.toObject(ProductDoc::class.java)
                        ?: throw IllegalStateException("Salah satu barang sudah tidak tersedia")
                    Triple(pRef, product, line)
                }

                // 2. VALIDATE STOCK & COMPUTE TOTALS
                var grossRevenue = 0L
                var totalCost = 0L
                var totalItemQty = 0
                for ((_, product, line) in productSnaps) {
                    val qty = line.quantity
                    if (product.stock < qty) {
                        throw IllegalStateException("Stok ${product.name} tersisa ${product.stock}, tidak cukup untuk $qty pcs")
                    }
                    val appliedUnitPrice = line.customUnitPrice ?: product.effectiveSellingPrice
                    grossRevenue += appliedUnitPrice * qty
                    totalCost += product.costPrice * qty
                    totalItemQty += qty
                }

                val appliedDiscount = discountAmount.coerceAtMost(grossRevenue)
                val finalRevenue = grossRevenue - appliedDiscount
                val netProfit = finalRevenue - totalCost
                val effectivePaid = if (paymentMethod == PaymentMethod.TUNAI) {
                    if (amountPaid < finalRevenue) {
                        throw IllegalArgumentException("Uang tunai kurang dari total tagihan")
                    }
                    amountPaid
                } else {
                    finalRevenue
                }
                val change = (effectivePaid - finalRevenue).coerceAtLeast(0L)

                // 3. ALL WRITES AFTER READS
                for ((pRef, product, line) in productSnaps) {
                    transaction.update(
                        pRef,
                        mapOf(
                            "stock" to (product.stock - line.quantity),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val txPayload = buildMap<String, Any> {
                    put("invoiceNumber", invoiceNumber)
                    put("timestampMillis", now)
                    put("dateKey", dateKey)
                    put("totalItems", totalItemQty)
                    put("totalRevenue", grossRevenue)
                    put("discountAmount", appliedDiscount)
                    put("finalRevenue", finalRevenue)
                    put("totalCost", totalCost)
                    put("netProfit", netProfit)
                    put("paymentMethod", paymentMethod.name)
                    put("amountPaid", effectivePaid)
                    put("changeAmount", change)
                    put("cashierUid", uid)
                    put("cashierName", cashierName)
                    put("cashierRole", cashierRole.name)
                    if (customerName.trim().isNotBlank()) put("customerName", customerName.trim())
                    if (notes.trim().isNotBlank()) put("notes", notes.trim())
                    if (qrisReference.trim().isNotBlank()) put("qrisReference", qrisReference.trim())
                    put("createdAt", FieldValue.serverTimestamp())
                }
                transaction.set(txRef, txPayload)

                val createdSaleItems = productSnaps.map { (_, product, line) ->
                    val qty = line.quantity
                    val appliedUnitPrice = line.customUnitPrice ?: product.effectiveSellingPrice
                    val itemRef = storeRef.collection("sale_items").document()
                    val lineRev = appliedUnitPrice * qty
                    val lineCost = product.costPrice * qty
                    val resolvedCatName = StoreCategoryDoc.resolve(product.category, storeCategories).displayName
                    val variantSummary = product.resolvedVariantSummary
                    val itemPayload = buildMap<String, Any> {
                        put("transactionId", txRef.id)
                        put("dateKey", dateKey)
                        put("timestampMillis", now)
                        put("productId", product.id)
                        put("skuCode", product.skuCode)
                        put("productName", product.name)
                        if (variantSummary.isNotBlank()) put("variantSummary", variantSummary)
                        put("categoryName", resolvedCatName)
                        put("quantity", qty)
                        put("unitCostPrice", product.costPrice)
                        put("unitSellingPrice", appliedUnitPrice)
                        put("subtotalRevenue", lineRev)
                        put("subtotalCost", lineCost)
                        put("subtotalProfit", (lineRev - lineCost))
                        put("cashierUid", uid)
                        put("createdAt", FieldValue.serverTimestamp())
                    }
                    transaction.set(itemRef, itemPayload)

                    val histRef = storeRef.collection("stock_histories").document()
                    transaction.set(
                        histRef,
                        mapOf(
                            "productId" to product.id,
                            "productName" to product.name,
                            "changeType" to StockChangeType.PENJUALAN.name,
                            "quantityDelta" to -qty,
                            "stockAfter" to (product.stock - qty),
                            "unitCostAtChange" to product.costPrice,
                            "actorName" to cashierName,
                            "actorUid" to uid,
                            "note" to "Penjualan $invoiceNumber",
                            "timestampMillis" to now,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )

                    SaleItemDoc(
                        id = itemRef.id,
                        transactionId = txRef.id,
                        dateKey = dateKey,
                        timestampMillis = now,
                        productId = product.id,
                        skuCode = product.skuCode,
                        productName = product.name,
                        variantSummary = variantSummary,
                        categoryName = resolvedCatName,
                        quantity = qty,
                        unitCostPrice = product.costPrice,
                        unitSellingPrice = appliedUnitPrice,
                        subtotalRevenue = lineRev,
                        subtotalCost = lineCost,
                        subtotalProfit = lineRev - lineCost,
                        cashierUid = uid
                    )
                }

                CloudSaleWithItems(
                    transaction = SaleTransactionDoc(
                        id = txRef.id,
                        invoiceNumber = invoiceNumber,
                        timestampMillis = now,
                        dateKey = dateKey,
                        totalItems = totalItemQty,
                        totalRevenue = grossRevenue,
                        discountAmount = appliedDiscount,
                        finalRevenue = finalRevenue,
                        totalCost = totalCost,
                        netProfit = netProfit,
                        paymentMethod = paymentMethod.name,
                        amountPaid = effectivePaid,
                        changeAmount = change,
                        cashierUid = uid,
                        cashierName = cashierName,
                        cashierRole = cashierRole.name,
                        customerName = customerName.trim(),
                        notes = notes.trim(),
                        qrisReference = qrisReference.trim()
                    ),
                    items = createdSaleItems
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            throw e
        }
    }

    suspend fun seedStarterCatalog(
        storeId: String,
        actorName: String,
        storeType: StoreType = StoreType.LAINNYA
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        val now = System.currentTimeMillis()
        val existingSkus = db.collection("stores").document(storeId).collection("products")
            .get().await().documents.mapNotNull { it.getString("skuCode")?.uppercase() }.toSet()

        val starter = starterCatalogForStoreType(storeType)

        val toInsert = starter.filter { it.skuCode !in existingSkus }
        if (toInsert.isEmpty()) return@runCatching 0

        val batch = db.batch()
        val storeRef = db.collection("stores").document(storeId)
        toInsert.forEach { item ->
            val pRef = storeRef.collection("products").document()
            batch.set(
                pRef,
                buildMap<String, Any> {
                    put("skuCode", item.skuCode)
                    put("name", item.name)
                    put("category", item.category)
                    if (item.variantInfo.isNotBlank()) put("variantInfo", item.variantInfo)
                    if (item.variantName.isNotBlank()) put("variantName", item.variantName)
                    if (item.color.isNotBlank()) put("color", item.color)
                    if (item.size.isNotBlank()) put("size", item.size)
                    put("costPrice", item.costPrice)
                    put("sellingPrice", item.sellingPrice)
                    put("stock", item.stock)
                    put("minStockAlert", item.minStockAlert)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
            val hRef = storeRef.collection("stock_histories").document()
            batch.set(
                hRef,
                mapOf(
                    "productId" to pRef.id,
                    "productName" to item.name,
                    "changeType" to StockChangeType.PRODUK_BARU.name,
                    "quantityDelta" to item.stock,
                    "stockAfter" to item.stock,
                    "unitCostAtChange" to item.costPrice,
                    "actorName" to actorName,
                    "actorUid" to uid,
                    "note" to "Katalog awal toko",
                    "timestampMillis" to now,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
            toInsert.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, storeRef.collection("products").path)
            throw e
        }
    }

    private fun starterCatalogForStoreType(storeType: StoreType): List<ProductDoc> = when (storeType) {
        StoreType.MAKANAN_MINUMAN -> listOf(
            ProductDoc(skuCode = "TJR-MK-001", name = "Nasi Ayam Bakar Madu", category = "MAKANAN", variantName = "Pedas Manis", size = "Porsi Reguler", variantInfo = "Varian: Pedas Manis • Ukuran: Porsi Reguler", costPrice = 15000L, sellingPrice = 26000L, stock = 25, minStockAlert = 5),
            ProductDoc(skuCode = "TJR-MK-002", name = "Mie Goreng Spesial", category = "MAKANAN", variantName = "Telur Mata Sapi", size = "Jumbo", variantInfo = "Varian: Telur Mata Sapi • Ukuran: Jumbo", costPrice = 12000L, sellingPrice = 22000L, stock = 30, minStockAlert = 6),
            ProductDoc(skuCode = "TJR-MN-001", name = "Es Kopi Susu Gula Aren", category = "MINUMAN", variantName = "Less Sugar", size = "Large 500ml", variantInfo = "Varian: Less Sugar • Ukuran: Large 500ml", costPrice = 9000L, sellingPrice = 18000L, stock = 40, minStockAlert = 8),
            ProductDoc(skuCode = "TJR-MN-002", name = "Matcha Latte Dingin", category = "MINUMAN", variantName = "Creamy Oat", size = "Regular 350ml", variantInfo = "Varian: Creamy Oat • Ukuran: Regular 350ml", costPrice = 11000L, sellingPrice = 22000L, stock = 28, minStockAlert = 6),
            ProductDoc(skuCode = "TJR-SK-001", name = "Dimsum Ayam Udang (Isi 4)", category = "SNACK_KUE", variantName = "Saos Mentai", size = "Isi 4", variantInfo = "Varian: Saos Mentai • Ukuran: Isi 4", costPrice = 10000L, sellingPrice = 18000L, stock = 24, minStockAlert = 5),
            ProductDoc(skuCode = "TJR-PM-001", name = "Paket Hemat Makan + Es Teh", category = "PAKET_MENU", variantName = "Komplit", size = "1 Paket", variantInfo = "Varian: Komplit • Ukuran: 1 Paket", costPrice = 18000L, sellingPrice = 30000L, stock = 20, minStockAlert = 4)
        )
        StoreType.GROSIR -> listOf(
            ProductDoc(skuCode = "TJR-SB-001", name = "Beras Premium Pandan Wangi 5kg", category = "SEMBAKO", variantName = "Pulén Super", size = "5 kg", variantInfo = "Varian: Pulen Super • Ukuran: 5 kg", costPrice = 68000L, sellingPrice = 76000L, stock = 30, minStockAlert = 6),
            ProductDoc(skuCode = "TJR-SB-002", name = "Minyak Goreng Pouch 2 Liter", category = "SEMBAKO", variantName = "2x Penyaringan", size = "2 Liter", variantInfo = "Varian: 2x Penyaringan • Ukuran: 2 Liter", costPrice = 31000L, sellingPrice = 36000L, stock = 45, minStockAlert = 10),
            ProductDoc(skuCode = "TJR-SB-003", name = "Gula Pasir Kristal Putih 1kg", category = "SEMBAKO", variantName = "Kemasan Pabrik", size = "1 kg", variantInfo = "Varian: Kemasan Pabrik • Ukuran: 1 kg", costPrice = 15500L, sellingPrice = 18000L, stock = 50, minStockAlert = 10),
            ProductDoc(skuCode = "TJR-MK-001", name = "Air Mineral Gelas 1 Dus (48 Cup)", category = "MINUMAN_KEMASAN", variantName = "1 Dus", size = "220ml x 48", variantInfo = "Varian: 1 Dus • Ukuran: 220ml x 48", costPrice = 18000L, sellingPrice = 24000L, stock = 24, minStockAlert = 5),
            ProductDoc(skuCode = "TJR-KR-001", name = "Deterjen Bubuk Ekonomis 770g", category = "KEBUTUHAN_RUMAH", variantName = "Wangi Bunga", size = "770g", variantInfo = "Varian: Wangi Bunga • Ukuran: 770g", costPrice = 16000L, sellingPrice = 20500L, stock = 32, minStockAlert = 6),
            ProductDoc(skuCode = "TJR-SE-001", name = "Mie Instan Goreng 1 Dus (40 Bks)", category = "SNACK_ECERAN", variantName = "Original", size = "1 Dus (40 Pcs)", variantInfo = "Varian: Original • Ukuran: 1 Dus (40 Pcs)", costPrice = 106000L, sellingPrice = 118000L, stock = 18, minStockAlert = 4)
        )
        StoreType.BAJU -> listOf(
            ProductDoc(skuCode = "TJR-BJ-001", name = "Kemeja Linen Oversize", category = "BAJU", variantName = "Polos", color = "Sage Green", size = "L", variantInfo = "Varian: Polos • Warna: Sage Green • Ukuran: L", costPrice = 95000L, sellingPrice = 149000L, stock = 18, minStockAlert = 4),
            ProductDoc(skuCode = "TJR-BJ-002", name = "Gamis Katun Toyobo Premium", category = "BAJU", variantName = "Busui Friendly", color = "Mocca", size = "M", variantInfo = "Varian: Busui Friendly • Warna: Mocca • Ukuran: M", costPrice = 135000L, sellingPrice = 195000L, stock = 14, minStockAlert = 4),
            ProductDoc(skuCode = "TJR-CR-001", name = "Celana Chino Slim Fit", category = "CELANA_ROK", variantName = "Stretch", color = "Khaki", size = "32", variantInfo = "Varian: Stretch • Warna: Khaki • Ukuran: 32", costPrice = 110000L, sellingPrice = 165000L, stock = 16, minStockAlert = 4),
            ProductDoc(skuCode = "TJR-HJ-001", name = "Pashmina Ceruty Babydoll", category = "HIJAB_KERUDUNG", variantName = "Jahit Tepi", color = "Dusty Pink", size = "180x75 cm", variantInfo = "Varian: Jahit Tepi • Warna: Dusty Pink • Ukuran: 180x75 cm", costPrice = 24000L, sellingPrice = 45000L, stock = 30, minStockAlert = 6),
            ProductDoc(skuCode = "TJR-AK-001", name = "Ikat Pinggang Kulit Sintetis", category = "AKSESORIS", variantName = "Gesper Alloy", color = "Hitam", size = "115 cm", variantInfo = "Varian: Gesper Alloy • Warna: Hitam • Ukuran: 115 cm", costPrice = 32000L, sellingPrice = 59000L, stock = 20, minStockAlert = 5)
        )
        StoreType.LAINNYA -> listOf(
            ProductDoc(skuCode = "TJR-PU-001", name = "Produk Unggulan Harian", category = "PRODUK_UTAMA", variantName = "Reguler", color = "Netral", size = "Standar", variantInfo = "Varian: Reguler • Warna: Netral • Ukuran: Standar", costPrice = 35000L, sellingPrice = 55000L, stock = 20, minStockAlert = 5),
            ProductDoc(skuCode = "TJR-KH-001", name = "Paket Kebutuhan Harian", category = "KEBUTUHAN_HARIAN", variantName = "Hemat", size = "Pack", variantInfo = "Varian: Hemat • Ukuran: Pack", costPrice = 22000L, sellingPrice = 35000L, stock = 25, minStockAlert = 5),
            ProductDoc(skuCode = "TJR-PL-001", name = "Perlengkapan Serbaguna", category = "PERLENGKAPAN", variantName = "Premium", color = "Biru", size = "Medium", variantInfo = "Varian: Premium • Warna: Biru • Ukuran: Medium", costPrice = 45000L, sellingPrice = 75000L, stock = 15, minStockAlert = 3),
            ProductDoc(skuCode = "TJR-AK-001", name = "Aksesoris Pelengkap", category = "AKSESORIS", variantName = "Klasik", color = "Hitam", size = "All Size", variantInfo = "Varian: Klasik • Warna: Hitam • Ukuran: All Size", costPrice = 18000L, sellingPrice = 32000L, stock = 30, minStockAlert = 5)
        )
    }
}
