package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.UserRole
import com.example.data.remote.CloudSaleWithItems
import com.example.data.remote.ProductDoc
import com.example.data.remote.SaleItemDoc
import com.example.data.remote.SaleTransactionDoc
import com.example.util.DateUtils
import com.example.util.PinHasher
import com.example.util.QrisConfigManager
import com.example.util.ReportExporter
import com.example.util.StockAlertEvaluator
import com.example.util.StockAlertSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readAppNameMatchesUmTazkiyah() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UM TAZKIYAH", appName)
    }

    @Test
    fun productPricingAndDiscountPercentComputesEffectivePriceAndProfit() {
        val gamisPromo = ProductDoc(
            id = "p1",
            skuCode = "UMT-BJ-001",
            name = "Gamis Madinah",
            category = ProductCategory.BAJU.name,
            costPrice = 100_000L,
            sellingPrice = 200_000L,
            discountPercent = 15,
            stock = 12,
            minStockAlert = 3
        )

        assertEquals(170_000L, gamisPromo.effectiveSellingPrice)
        assertEquals(70_000L, gamisPromo.profitPerUnit)
        assertFalse(gamisPromo.isLowStock)

        val noDiscount = gamisPromo.copy(discountPercent = 0)
        assertEquals(200_000L, noDiscount.effectiveSellingPrice)
        assertEquals(100_000L, noDiscount.profitPerUnit)
    }

    @Test
    fun pinHasherAndQrisConfigWorkCorrectly() {
        val hash = PinHasher.hashPin("1234")
        assertTrue(PinHasher.verifyPin("1234", hash))
        assertFalse(PinHasher.verifyPin("9999", hash))
        assertTrue(QrisConfigManager.DEFAULT_DEMO_QRIS.contains("UM TAZKIYAH"))
    }

    @Test
    fun reportExporterGeneratesReadableTextAndCsvForCloudSales() {
        val tx = SaleTransactionDoc(
            id = "tx-1",
            invoiceNumber = "INV-TEST-001",
            timestampMillis = 1_700_000_000_000L,
            dateKey = DateUtils.todayKey(),
            totalItems = 2,
            totalRevenue = 340_000L,
            discountAmount = 10_000L,
            finalRevenue = 330_000L,
            totalCost = 200_000L,
            netProfit = 130_000L,
            paymentMethod = PaymentMethod.QRIS.name,
            amountPaid = 330_000L,
            changeAmount = 0L,
            cashierUid = "kasir-uid",
            cashierName = "Aisyah",
            cashierRole = UserRole.KASIR.name,
            qrisReference = "QRIS-REF-8899"
        )
        val item = SaleItemDoc(
            id = "item-1",
            transactionId = "tx-1",
            dateKey = tx.dateKey,
            timestampMillis = tx.timestampMillis,
            productId = "p1",
            skuCode = "UMT-BJ-001",
            productName = "Gamis Madinah",
            categoryName = ProductCategory.BAJU.displayName,
            quantity = 2,
            unitCostPrice = 100_000L,
            unitSellingPrice = 170_000L,
            subtotalRevenue = 340_000L,
            subtotalCost = 200_000L,
            subtotalProfit = 140_000L,
            cashierUid = "kasir-uid"
        )
        val sales = listOf(CloudSaleWithItems(tx, listOf(item)))

        val textReport = ReportExporter.buildReadableTextReport("Hari Ini", sales)
        assertTrue(textReport.contains("UM TAZKIYAH"))
        assertTrue(textReport.contains("Gamis Madinah"))
        assertTrue(textReport.contains("130.000"))

        val csvReport = ReportExporter.buildCsvReport("Hari Ini", sales)
        assertTrue(csvReport.contains("INV-TEST-001"))
        assertTrue(csvReport.contains("QRIS"))

        val customerReceipt = ReportExporter.buildCustomerDigitalReceipt(sales.first(), "UM TAZKIYAH")
        assertTrue(customerReceipt.contains("STRUK DIGITAL - UM TAZKIYAH"))
        assertTrue(customerReceipt.contains("INV-TEST-001"))
        assertTrue(customerReceipt.contains("Gamis Madinah"))
        assertTrue(customerReceipt.contains("330.000"))
        assertTrue(customerReceipt.contains("QRIS-REF-8899"))
        assertFalse(customerReceipt.contains("200.000"))
        assertFalse(customerReceipt.contains("130.000"))
    }

    @Test
    fun stockAlertEvaluatorDetectsThresholdCrossingAndEscalation() {
        val outOfStock = ProductDoc(id = "p-out", skuCode = "SKU-1", name = "Koko Putih", stock = 0, minStockAlert = 5)
        val lowStock = ProductDoc(id = "p-low", skuCode = "SKU-2", name = "Sajadah Turki", stock = 3, minStockAlert = 3)
        val safeStock = ProductDoc(id = "p-safe", skuCode = "SKU-3", name = "Peci Hitam", stock = 10, minStockAlert = 4)

        val initialAlerts = StockAlertEvaluator.evaluateAlerts(listOf(safeStock, lowStock, outOfStock))
        assertEquals(2, initialAlerts.size)
        assertEquals("p-out", initialAlerts[0].product.id)
        assertEquals(StockAlertSeverity.OUT_OF_STOCK, initialAlerts[0].severity)
        assertEquals("p-low", initialAlerts[1].product.id)
        assertEquals(StockAlertSeverity.LOW_STOCK, initialAlerts[1].severity)

        // Subsequent update where p-safe drops from 10 to 4 (<= minStockAlert=4), while p-low stays 3
        val prevMap = mapOf("p-out" to 0, "p-low" to 3, "p-safe" to 10)
        val droppedSafe = safeStock.copy(stock = 4)
        val triggered = StockAlertEvaluator.detectNewlyTriggeredAlerts(prevMap, listOf(outOfStock, lowStock, droppedSafe))
        assertEquals(1, triggered.size)
        assertEquals("p-safe", triggered.first().product.id)
        assertTrue(StockAlertEvaluator.buildNotificationMessage(triggered).contains("Peci Hitam"))

        // Escalation: p-low drops from 3 to 0 (out of stock) -> triggers critical notification again
        val prevAfterDrop = mapOf("p-out" to 0, "p-low" to 3, "p-safe" to 4)
        val escalatedLow = lowStock.copy(stock = 0)
        val escalated = StockAlertEvaluator.detectNewlyTriggeredAlerts(prevAfterDrop, listOf(outOfStock, escalatedLow, droppedSafe))
        assertEquals(1, escalated.size)
        assertEquals("p-low", escalated.first().product.id)
        assertEquals(StockAlertSeverity.OUT_OF_STOCK, escalated.first().severity)
        assertTrue(StockAlertEvaluator.buildNotificationTitle(escalated).contains("Stok Habis"))
    }
}
