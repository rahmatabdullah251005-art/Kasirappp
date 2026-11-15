package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
                category = ProductCategory.BAJU,
                variantInfo = "Ukuran M",
                costPrice = 100_000L,
                sellingPrice = 200_000L,
                discountPercent = 10,
                stock = 12,
                minStockAlert = 3,
                actorName = "Owner Uji"
            ).getOrThrow()
        }
        assertEquals(180_000L, product.effectiveSellingPrice)

        val observedProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProducts(store.id).first { list -> list.any { it.id == product.id } }
        }
        assertTrue(observedProducts.any { it.skuCode == dynamicSku })

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
                category = ProductCategory.BUKU,
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

    private companion object {
        const val OWNER_EMAIL = "owner-umtazkiyah@test.com"
        const val STRANGER_EMAIL = "stranger-umtazkiyah@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
