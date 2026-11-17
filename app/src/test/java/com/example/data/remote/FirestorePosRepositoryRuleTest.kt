package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.StockChangeType
import com.example.data.local.entity.StoreType
import com.example.data.local.entity.UserRole
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FirestorePosRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createOwnerStore_saveProduct_andCheckout_calculatesProfitAndDecrementsStock() = runBlocking {
        signInTestUser(OWNER_EMAIL)
        val repository = FirestorePosRepository(firestore, auth)

        val store = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createOwnerStore(
                storeName = "UM TAZKIYAH",
                ownerFullName = "Owner Uji",
                ownerEmail = OWNER_EMAIL,
                defaultQrisPayload = "000201010211UMTAZKIYAH"
            ).getOrThrow()
        }
        assertTrue(store.id.isNotBlank())

        val dynamicSku = "UMT-TEST-${UUID.randomUUID().toString().take(6).uppercase()}"
        val product = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveProduct(
                storeId = store.id,
                existingProduct = null,
                skuCode = dynamicSku,
                name = "Gamis Madinah Uji",
                categoryCode = ProductCategory.BAJU.name,
                variantName = "Bordir",
                color = "Sage Green",
                size = "M",
                costPrice = 100_000L,
                sellingPrice = 200_000L,
                discountPercent = 10,
                stock = 12,
                minStockAlert = 3,
                actorName = "Owner Uji"
            ).getOrThrow()
        }
        assertEquals(180_000L, product.effectiveSellingPrice)
        assertEquals("Bordir", product.variantName)
        assertEquals("Sage Green", product.color)
        assertEquals("M", product.size)
        assertTrue(product.resolvedVariantSummary.contains("Sage Green"))

        val observedProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProducts(store.id).first { list -> list.any { it.id == product.id } }
        }
        assertTrue(observedProducts.any { it.skuCode == dynamicSku && it.color == "Sage Green" && it.size == "M" })

        // Checkout 2 pcs with custom unit price 175.000 and transaction discount 10.000 via QRIS
        val receipt = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.checkoutSale(
                storeId = store.id,
                cartLines = listOf(CloudCartLineRequest(product.id, 2, customUnitPrice = 175_000L)),
                discountAmount = 10_000L,
                paymentMethod = PaymentMethod.QRIS,
                amountPaid = 340_000L,
                cashierName = "Owner Uji",
                cashierRole = UserRole.OWNER,
                notes = "Harga khusus pelanggan tetap",
                qrisReference = "QRIS-REF-8899"
            ).getOrThrow()
        }

        assertEquals(350_000L, receipt.transaction.totalRevenue)
        assertEquals(10_000L, receipt.transaction.discountAmount)
        assertEquals(340_000L, receipt.transaction.finalRevenue)
        assertEquals(200_000L, receipt.transaction.totalCost)
        assertEquals(140_000L, receipt.transaction.netProfit)
        assertEquals("QRIS-REF-8899", receipt.transaction.qrisReference)
        assertTrue(receipt.items.first().variantSummary.contains("Varian: Bordir"))
        assertTrue(receipt.items.first().variantSummary.contains("Warna: Sage Green"))
        assertTrue(receipt.items.first().variantSummary.contains("Ukuran: M"))

        val updatedList = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProducts(store.id).first { list ->
                list.firstOrNull { it.id == product.id }?.stock == 10
            }
        }
        assertEquals(10, updatedList.first { it.id == product.id }.stock)
    }

    @Test
    fun strangerUser_cannotReadOrWriteAnotherOwnersStoreProducts() = runBlocking {
        signInTestUser(OWNER_EMAIL)
        val ownerRepo = FirestorePosRepository(firestore, auth)
        val store = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOwnerStore(
                storeName = "UM TAZKIYAH",
                ownerFullName = "Owner Uji",
                ownerEmail = OWNER_EMAIL,
                defaultQrisPayload = "000201010211UMTAZKIYAH"
            ).getOrThrow()
        }

        signInTestUser(STRANGER_EMAIL)
        val strangerRepo = FirestorePosRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                strangerRepo.observeProducts(store.id).first()
            }
            fail("Expected PERMISSION_DENIED for stranger observing products")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }

        val writeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            strangerRepo.saveProduct(
                storeId = store.id,
                existingProduct = null,
                skuCode = "HACK-${UUID.randomUUID().toString().take(5)}",
                name = "Unauthorized Product",
                categoryCode = ProductCategory.BUKU.name,
                variantInfo = "",
                costPrice = 10_000L,
                sellingPrice = 20_000L,
                discountPercent = 0,
                stock = 5,
                minStockAlert = 1,
                actorName = "Stranger"
            )
        }
        assertTrue(writeResult.isFailure)
        val fsWriteErr = writeResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsWriteErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsWriteErr!!.code)
    }

    @Test
    fun unauthenticatedUser_observeOwnedStores_rejectedOrFailsAuth() = runBlocking {
        signInTestUser(OWNER_EMAIL)
        val repo = FirestorePosRepository(firestore, auth)
        val store = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createOwnerStore(
                storeName = "UM TAZKIYAH",
                ownerFullName = "Owner Uji",
                ownerEmail = OWNER_EMAIL,
                defaultQrisPayload = "000201010211UMTAZKIYAH"
            ).getOrThrow()
        }

        auth.signOut()
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeProducts(store.id).first()
            }
            fail("Expected unauthenticated access to fail")
        } catch (e: Exception) {
            assertTrue(
                e is IllegalStateException ||
                    e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            )
        }
    }

    @Test
    fun storeCategories_ownerCanAddAndDelete_andSaveProductWithCustomCategory() = runBlocking {
        signInTestUser(OWNER_EMAIL)
        val ownerRepo = FirestorePosRepository(firestore, auth)
        val store = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOwnerStore(
                storeName = "UM TAZKIYAH",
                ownerFullName = "Owner Uji",
                ownerEmail = OWNER_EMAIL,
                defaultQrisPayload = "000201010211UMTAZKIYAH"
            ).getOrThrow()
        }

        val initialCats = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeCategories(store.id).first { it.size >= 4 }
        }
        assertEquals(4, initialCats.size)

        val createdCat = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createCategory(
                storeId = store.id,
                displayName = "Parfum Arab",
                customPrefix = "PF"
            ).getOrThrow()
        }
        assertEquals("PARFUM_ARAB", createdCat.code)
        assertEquals("PF", createdCat.codePrefix)

        val customProduct = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.saveProduct(
                storeId = store.id,
                existingProduct = null,
                skuCode = "UMT-PF-${UUID.randomUUID().toString().take(4).uppercase()}",
                name = "Misk Thaharah 12ml",
                categoryCode = createdCat.code,
                variantInfo = "Original",
                costPrice = 25_000L,
                sellingPrice = 50_000L,
                discountPercent = 0,
                stock = 10,
                minStockAlert = 2,
                actorName = "Owner Uji"
            ).getOrThrow()
        }
        assertEquals("PARFUM_ARAB", customProduct.category)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.deleteCategory(store.id, createdCat.id).getOrThrow()
        }
        val afterDelete = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeCategories(store.id).first { list -> list.none { it.id == createdCat.id } }
        }
        assertEquals(4, afterDelete.size)

        signInTestUser(STRANGER_EMAIL)
        val strangerRepo = FirestorePosRepository(firestore, auth)
        val strangerResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            strangerRepo.createCategory(store.id, "Kategori Ilegal")
        }
        assertTrue(strangerResult.isFailure)
        val fsErr = strangerResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr!!.code)
    }

    @Test
    fun cashier_canAddAndReduceStock_andOwnerSeesStockHistoryRecap() = runBlocking {
        val kasirUid = signInTestUser(KASIR_EMAIL)
        val ownerUid = signInTestUser(OWNER_EMAIL)
        val ownerRepo = FirestorePosRepository(firestore, auth)

        val store = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOwnerStore(
                storeName = "UM TAZKIYAH",
                ownerFullName = "Owner Uji",
                ownerEmail = OWNER_EMAIL,
                defaultQrisPayload = "000201010211UMTAZKIYAH"
            ).getOrThrow()
        }

        // Register kasir as active store member
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("stores").document(store.id)
                .collection("members").document(kasirUid)
                .set(
                    mapOf(
                        "uid" to kasirUid,
                        "storeId" to store.id,
                        "ownerUid" to ownerUid,
                        "storeName" to store.storeName,
                        "fullName" to "Aisyah (Kasir)",
                        "email" to KASIR_EMAIL,
                        "role" to UserRole.KASIR.name,
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }

        val product = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.saveProduct(
                storeId = store.id,
                existingProduct = null,
                skuCode = "UMT-ST-${UUID.randomUUID().toString().take(5).uppercase()}",
                name = "Koko Kurta Katun",
                categoryCode = ProductCategory.BAJU.name,
                costPrice = 90_000L,
                sellingPrice = 145_000L,
                discountPercent = 0,
                stock = 10,
                minStockAlert = 3,
                actorName = "Owner Uji"
            ).getOrThrow()
        }

        // Switch to Kasir account and add (+6 => 16), then reduce (-4 => 12)
        signInTestUser(KASIR_EMAIL)
        val kasirRepo = FirestorePosRepository(firestore, auth)

        val stockAfterAdd = withTimeout(DEFAULT_TIMEOUT_MS) {
            kasirRepo.adjustProductStock(
                storeId = store.id,
                product = product,
                isAddition = true,
                quantity = 6,
                actorName = "Aisyah (Kasir)",
                note = "Barang masuk supplier",
                includeUnitCost = false
            ).getOrThrow()
        }
        assertEquals(16, stockAfterAdd)

        val stockAfterReduce = withTimeout(DEFAULT_TIMEOUT_MS) {
            kasirRepo.adjustProductStock(
                storeId = store.id,
                product = product.copy(stock = stockAfterAdd),
                isAddition = false,
                quantity = 4,
                actorName = "Aisyah (Kasir)",
                note = "Stok rusak",
                includeUnitCost = false
            ).getOrThrow()
        }
        assertEquals(12, stockAfterReduce)

        // Kasir CANNOT read stock_histories
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                kasirRepo.observeStockHistories(store.id).first()
            }
            fail("Expected PERMISSION_DENIED for kasir reading stock_histories")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull(fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr!!.code)
        }

        // Switch back to Owner and confirm stock history recap shows both adjustments
        signInTestUser(OWNER_EMAIL)
        val ownerLogs = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeStockHistories(store.id).first { logs ->
                logs.any { it.changeType == StockChangeType.RESTOK.name && it.quantityDelta == 6 } &&
                    logs.any { it.changeType == StockChangeType.KOREKSI.name && it.quantityDelta == -4 }
            }
        }
        val addLog = ownerLogs.first { it.changeType == StockChangeType.RESTOK.name && it.quantityDelta == 6 }
        val reduceLog = ownerLogs.first { it.changeType == StockChangeType.KOREKSI.name && it.quantityDelta == -4 }
        assertEquals("Aisyah (Kasir)", addLog.actorName)
        assertEquals("Barang masuk supplier", addLog.note)
        assertEquals(16, addLog.stockAfter)
        assertEquals("Aisyah (Kasir)", reduceLog.actorName)
        assertEquals("Stok rusak", reduceLog.note)
        assertEquals(12, reduceLog.stockAfter)
    }

    @Test
    fun createOwnerStore_withStoreTypeGrosir_savesStoreTypeAndSeedsGrosirCategories() = runBlocking {
        signInTestUser(OWNER_EMAIL)
        val ownerRepo = FirestorePosRepository(firestore, auth)

        val store = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOwnerStore(
                storeName = "Tajir Grosir Makmur",
                ownerFullName = "Owner Uji",
                ownerEmail = OWNER_EMAIL,
                defaultQrisPayload = "000201010211TAJIR",
                storeType = StoreType.GROSIR
            ).getOrThrow()
        }
        assertEquals(StoreType.GROSIR, store.storeTypeEnum)
        assertEquals("Toko Grosir", store.resolvedStoreTypeLabel)

        val cats = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeCategories(store.id).first { it.size >= 4 }
        }
        assertTrue(cats.any { it.code == "SEMBAKO" })
        assertTrue(cats.any { it.code == "MINUMAN_KEMASAN" })
        assertTrue(cats.any { it.code == "KEBUTUHAN_RUMAH" })
        assertTrue(cats.any { it.code == "SNACK_ECERAN" })
    }

    private companion object {
        const val OWNER_EMAIL = "owner-umtazkiyah@test.com"
        const val KASIR_EMAIL = "kasir-umtazkiyah@test.com"
        const val STRANGER_EMAIL = "stranger-umtazkiyah@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
