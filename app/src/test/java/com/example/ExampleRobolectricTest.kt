package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.PaymentMethod
import com.example.data.local.entity.ProductCategory
import com.example.data.local.entity.StockChangeType
import com.example.data.local.entity.StoreType
import com.example.data.local.entity.UserRole
import com.example.data.remote.CloudSaleWithItems
import com.example.data.remote.ProductDoc
import com.example.data.remote.SaleItemDoc
import com.example.data.remote.SaleTransactionDoc
import com.example.data.remote.StockHistoryDoc
import com.example.data.remote.StoreCategoryDoc
import com.example.data.remote.StoreDoc
import com.example.util.DateUtils
import com.example.util.EscPosReceiptFormatter
import com.example.util.PinHasher
import com.example.util.PrinterPaperWidth
import com.example.util.QrisConfigManager
import com.example.util.ReportExporter
import com.example.util.StockAlertEvaluator
import com.example.util.StockAlertSeverity
import com.example.util.StockHistoryFilterType
import com.example.util.StockHistoryPeriodFilter
import com.example.util.StockHistorySummarizer
import com.example.util.TransactionSearchHelper
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
    fun readAppNameMatchesTajir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tajir", appName)
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
        assertTrue(QrisConfigManager.DEFAULT_DEMO_QRIS.contains("TAJIR"))
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
        assertTrue(textReport.contains("TAJIR"))
        assertTrue(textReport.contains("Gamis Madinah"))
        assertTrue(textReport.contains("130.000"))

        val csvReport = ReportExporter.buildCsvReport("Hari Ini", sales)
        assertTrue(csvReport.contains("INV-TEST-001"))
        assertTrue(csvReport.contains("QRIS"))

        val customerReceipt = ReportExporter.buildCustomerDigitalReceipt(sales.first(), "Tajir")
        assertTrue(customerReceipt.contains("STRUK DIGITAL - Tajir"))
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

    @Test
    fun transactionSearchHelperFiltersByDateCustomerNameAndSoldItems() {
        val tx1 = SaleTransactionDoc(
            id = "tx-search-1",
            invoiceNumber = "INV-20261115-0001",
            timestampMillis = 1_794_750_000_000L,
            dateKey = "2026-11-15",
            totalItems = 2,
            totalRevenue = 360_000L,
            discountAmount = 0L,
            finalRevenue = 360_000L,
            totalCost = 220_000L,
            netProfit = 140_000L,
            paymentMethod = PaymentMethod.TUNAI.name,
            amountPaid = 400_000L,
            changeAmount = 40_000L,
            cashierUid = "kasir-1",
            cashierName = "Aisyah",
            cashierRole = UserRole.KASIR.name,
            customerName = "Ahmad Fauzi"
        )
        val item1 = SaleItemDoc(
            id = "i-1",
            transactionId = "tx-search-1",
            dateKey = "2026-11-15",
            timestampMillis = tx1.timestampMillis,
            productId = "p-gamis",
            skuCode = "UMT-BJ-101",
            productName = "Gamis Madinah Premium",
            categoryName = ProductCategory.BAJU.displayName,
            quantity = 2,
            unitCostPrice = 110_000L,
            unitSellingPrice = 180_000L,
            subtotalRevenue = 360_000L,
            subtotalCost = 220_000L,
            subtotalProfit = 140_000L,
            cashierUid = "kasir-1"
        )

        val tx2 = SaleTransactionDoc(
            id = "tx-search-2",
            invoiceNumber = "INV-20261110-0008",
            timestampMillis = 1_794_320_000_000L,
            dateKey = "2026-11-10",
            totalItems = 1,
            totalRevenue = 95_000L,
            discountAmount = 5_000L,
            finalRevenue = 90_000L,
            totalCost = 60_000L,
            netProfit = 30_000L,
            paymentMethod = PaymentMethod.QRIS.name,
            amountPaid = 90_000L,
            changeAmount = 0L,
            cashierUid = "owner-1",
            cashierName = "Ustadz Hasan",
            cashierRole = UserRole.OWNER.name,
            customerName = "Siti Aminah"
        )
        val item2 = SaleItemDoc(
            id = "i-2",
            transactionId = "tx-search-2",
            dateKey = "2026-11-10",
            timestampMillis = tx2.timestampMillis,
            productId = "p-buku",
            skuCode = "UMT-BK-202",
            productName = "Sirah Nabawiyah Hardcover",
            categoryName = ProductCategory.BUKU.displayName,
            quantity = 1,
            unitCostPrice = 60_000L,
            unitSellingPrice = 95_000L,
            subtotalRevenue = 95_000L,
            subtotalCost = 60_000L,
            subtotalProfit = 35_000L,
            cashierUid = "owner-1"
        )

        val all = listOf(CloudSaleWithItems(tx1, listOf(item1)), CloudSaleWithItems(tx2, listOf(item2)))

        // Blank query returns all
        assertEquals(2, TransactionSearchHelper.filterTransactions(all, "   ").size)

        // Filter by customer name
        val byCustomer = TransactionSearchHelper.filterTransactions(all, "Ahmad")
        assertEquals(1, byCustomer.size)
        assertEquals("tx-search-1", byCustomer.first().transaction.id)

        // Filter by sold item name
        val byItem = TransactionSearchHelper.filterTransactions(all, "Sirah")
        assertEquals(1, byItem.size)
        assertEquals("tx-search-2", byItem.first().transaction.id)

        // Filter by dateKey
        val byDate = TransactionSearchHelper.filterTransactions(all, "2026-11-10")
        assertEquals(1, byDate.size)
        assertEquals("tx-search-2", byDate.first().transaction.id)

        // Multi-token match (customer + item)
        val multiToken = TransactionSearchHelper.filterTransactions(all, "fauzi gamis")
        assertEquals(1, multiToken.size)
        assertEquals("tx-search-1", multiToken.first().transaction.id)

        // Non-matching multi-token
        assertTrue(TransactionSearchHelper.filterTransactions(all, "ahmad sirah").isEmpty())
    }

    @Test
    fun escPosReceiptFormatterGeneratesValidBytesAndExcludesCostOrProfit() {
        val tx = SaleTransactionDoc(
            id = "tx-esc-1",
            invoiceNumber = "INV-20261115-0042",
            timestampMillis = 1_794_750_000_000L,
            dateKey = "2026-11-15",
            totalItems = 1,
            totalRevenue = 250_000L,
            discountAmount = 15_000L,
            finalRevenue = 235_000L,
            totalCost = 140_000L,
            netProfit = 95_000L,
            paymentMethod = PaymentMethod.TUNAI.name,
            amountPaid = 250_000L,
            changeAmount = 15_000L,
            cashierUid = "kasir-1",
            cashierName = "Aisyah",
            cashierRole = UserRole.KASIR.name,
            customerName = "Bpk. Ridwan"
        )
        val item = SaleItemDoc(
            id = "i-esc-1",
            transactionId = "tx-esc-1",
            dateKey = "2026-11-15",
            timestampMillis = tx.timestampMillis,
            productId = "p-1",
            skuCode = "UMT-KM-01",
            productName = "Sajadah Turki Tebal",
            categoryName = ProductCategory.KEBUTUHAN_MUSLIM.displayName,
            quantity = 1,
            unitCostPrice = 140_000L,
            unitSellingPrice = 250_000L,
            subtotalRevenue = 250_000L,
            subtotalCost = 140_000L,
            subtotalProfit = 110_000L,
            cashierUid = "kasir-1"
        )
        val receipt = CloudSaleWithItems(tx, listOf(item))

        val preview = EscPosReceiptFormatter.buildPlainTextPreview(receipt, "Tajir", PrinterPaperWidth.MM_58)
        assertTrue(preview.contains("Tajir"))
        assertTrue(preview.contains("INV-20261115-0042"))
        assertTrue(preview.contains("Bpk. Ridwan"))
        assertTrue(preview.contains("Sajadah Turki Tebal"))
        assertTrue(preview.contains("235.000"))
        assertFalse(preview.contains("140.000"))
        assertFalse(preview.contains("95.000"))

        val bytes = EscPosReceiptFormatter.buildEscPosBytes(receipt, "Tajir", PrinterPaperWidth.MM_80)
        assertTrue(bytes.size > 50)
        // Starts with ESC @ initialization sequence (0x1B 0x40)
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
        val asciiPayload = String(bytes, Charsets.US_ASCII)
        assertTrue(asciiPayload.contains("INV-20261115-0042"))
        assertTrue(asciiPayload.contains("Bpk. Ridwan"))
        assertFalse(asciiPayload.contains("140.000"))
        assertFalse(asciiPayload.contains("95.000"))
    }

    @Test
    fun storeCategoryHelpersNormalizeSuggestAndResolveCorrectly() {
        assertEquals("PARFUM_ARAB", StoreCategoryDoc.normalizeCode("Parfum Arab"))
        assertEquals("PA", StoreCategoryDoc.suggestPrefix("Parfum Arab"))
        assertEquals("HI", StoreCategoryDoc.suggestPrefix("Hijab"))

        val customCats = listOf(
            StoreCategoryDoc(
                id = "cat_parfum_arab",
                code = "PARFUM_ARAB",
                displayName = "Parfum Arab",
                codePrefix = "PF",
                colorIndex = 4
            )
        )
        val resolvedCustom = StoreCategoryDoc.resolve("PARFUM_ARAB", customCats)
        assertEquals("Parfum Arab", resolvedCustom.displayName)
        assertEquals("PF", resolvedCustom.codePrefix)

        // Fallback to built-in preset name even if removed from active store list
        val resolvedRemovedPreset = StoreCategoryDoc.resolve(ProductCategory.BUKU.name, customCats)
        assertEquals("Buku Islam", resolvedRemovedPreset.displayName)

        // Fallback to pretty formatted name for an unknown/removed custom code
        val resolvedRemovedCustom = StoreCategoryDoc.resolve("ALAT_SHOLAT", emptyList())
        assertEquals("Alat Sholat", resolvedRemovedCustom.displayName)
    }

    @Test
    fun productAttributesVariantColorSizePropagateToReceiptsAndSearch() {
        val product = ProductDoc(
            id = "p-abaya",
            skuCode = "UMT-BJ-505",
            name = "Abaya Turki Silk",
            category = ProductCategory.BAJU.name,
            variantName = "Bordir Emas",
            color = "Hitam",
            size = "XL",
            costPrice = 150_000L,
            sellingPrice = 250_000L,
            stock = 8,
            minStockAlert = 2
        )

        assertTrue(product.hasAttributes)
        assertEquals(3, product.attributeBadges.size)
        assertEquals("Varian" to "Bordir Emas", product.attributeBadges[0])
        assertEquals("Warna" to "Hitam", product.attributeBadges[1])
        assertEquals("Ukuran" to "XL", product.attributeBadges[2])
        assertEquals("Varian: Bordir Emas • Warna: Hitam • Ukuran: XL", product.resolvedVariantSummary)

        val simpleProduct = ProductDoc(id = "p-plain", skuCode = "UMT-AK-09", name = "Tasbih Polos")
        assertFalse(simpleProduct.hasAttributes)
        assertEquals("", simpleProduct.resolvedVariantSummary)

        val tx = SaleTransactionDoc(
            id = "tx-attr",
            invoiceNumber = "INV-20261115-0901",
            timestampMillis = 1_794_750_000_000L,
            dateKey = "2026-11-15",
            totalItems = 1,
            totalRevenue = 250_000L,
            finalRevenue = 250_000L,
            totalCost = 150_000L,
            netProfit = 100_000L,
            paymentMethod = PaymentMethod.TUNAI.name,
            amountPaid = 250_000L,
            changeAmount = 0L,
            cashierUid = "kasir-1",
            cashierName = "Aisyah",
            cashierRole = UserRole.KASIR.name
        )
        val item = SaleItemDoc(
            id = "si-attr",
            transactionId = tx.id,
            dateKey = tx.dateKey,
            timestampMillis = tx.timestampMillis,
            productId = product.id,
            skuCode = product.skuCode,
            productName = product.name,
            variantSummary = product.resolvedVariantSummary,
            categoryName = ProductCategory.BAJU.displayName,
            quantity = 1,
            unitCostPrice = 150_000L,
            unitSellingPrice = 250_000L,
            subtotalRevenue = 250_000L,
            subtotalCost = 150_000L,
            subtotalProfit = 100_000L,
            cashierUid = "kasir-1"
        )
        val sale = CloudSaleWithItems(tx, listOf(item))

        val digitalReceipt = ReportExporter.buildCustomerDigitalReceipt(sale, "Tajir")
        assertTrue(digitalReceipt.contains("Warna: Hitam"))
        assertTrue(digitalReceipt.contains("Ukuran: XL"))

        val thermalPreview = EscPosReceiptFormatter.buildPlainTextPreview(sale, "Tajir", PrinterPaperWidth.MM_80)
        assertTrue(thermalPreview.contains("Bordir Emas"))
        assertTrue(thermalPreview.contains("Ukuran: XL"))

        assertEquals(1, TransactionSearchHelper.filterTransactions(listOf(sale), "hitam xl").size)
    }

    @Test
    fun cashierShiftRecapTextAndCsvExcludeOwnerCostAndProfit() {
        val tx = SaleTransactionDoc(
            id = "tx-shift-1",
            invoiceNumber = "INV-20261115-0555",
            timestampMillis = 1_794_750_000_000L,
            dateKey = "2026-11-15",
            totalItems = 3,
            totalRevenue = 450_000L,
            discountAmount = 25_000L,
            finalRevenue = 425_000L,
            totalCost = 270_000L,
            netProfit = 155_000L,
            paymentMethod = PaymentMethod.QRIS.name,
            amountPaid = 425_000L,
            changeAmount = 0L,
            cashierUid = "kasir-1",
            cashierName = "Aisyah (Kasir)",
            cashierRole = UserRole.KASIR.name,
            customerName = "Ummu Salma"
        )
        val item = SaleItemDoc(
            id = "si-shift-1",
            transactionId = tx.id,
            dateKey = tx.dateKey,
            timestampMillis = tx.timestampMillis,
            productId = "p-khimar",
            skuCode = "UMT-BJ-707",
            productName = "Khimar Syari Jetblack",
            variantSummary = "Varian: Non-Pet • Warna: Hitam • Ukuran: L",
            categoryName = ProductCategory.BAJU.displayName,
            quantity = 3,
            unitCostPrice = 90_000L,
            unitSellingPrice = 150_000L,
            subtotalRevenue = 450_000L,
            subtotalCost = 270_000L,
            subtotalProfit = 180_000L,
            cashierUid = "kasir-1"
        )
        val sales = listOf(CloudSaleWithItems(tx, listOf(item)))

        val recapText = ReportExporter.buildCashierShiftRecapText(
            storeName = "Tajir",
            cashierName = "Aisyah (Kasir)",
            periodLabel = "Minggu, 15 November 2026",
            transactions = sales
        )
        assertTrue(recapText.contains("REKAP TRANSAKSI PEGAWAI - Tajir"))
        assertTrue(recapText.contains("Aisyah (Kasir)"))
        assertTrue(recapText.contains("425.000"))
        assertTrue(recapText.contains("Khimar Syari Jetblack"))
        assertTrue(recapText.contains("Warna: Hitam"))
        assertTrue(recapText.contains("INV-20261115-0555"))
        // Must NEVER leak owner cost or profit
        assertFalse(recapText.contains("270.000"))
        assertFalse(recapText.contains("155.000"))

        val recapCsv = ReportExporter.buildCashierShiftRecapCsv(
            storeName = "Tajir",
            cashierName = "Aisyah (Kasir)",
            periodLabel = "Minggu, 15 November 2026",
            transactions = sales
        )
        assertTrue(recapCsv.contains("Rekap Transaksi Pegawai"))
        assertTrue(recapCsv.contains("Khimar Syari Jetblack"))
        assertTrue(recapCsv.contains("Varian/Warna/Ukuran"))
        assertTrue(recapCsv.contains("Ummu Salma"))
        assertFalse(recapCsv.contains("270000"))
        assertFalse(recapCsv.contains("155000"))
        assertFalse(recapCsv.contains("Keuntungan"))
        assertFalse(recapCsv.contains("Modal"))
    }

    @Test
    fun bestSellerAggregationRanksByQuantityAndIncludesInRecaps() {
        val tx1 = SaleTransactionDoc(
            id = "tx-bs-1",
            invoiceNumber = "INV-BS-001",
            timestampMillis = 1_794_750_000_000L,
            dateKey = "2026-11-15",
            totalItems = 6,
            totalRevenue = 430_000L,
            finalRevenue = 430_000L,
            totalCost = 250_000L,
            netProfit = 180_000L,
            paymentMethod = PaymentMethod.TUNAI.name,
            amountPaid = 430_000L,
            cashierUid = "kasir-1",
            cashierName = "Aisyah",
            cashierRole = UserRole.KASIR.name
        )
        val tx2 = SaleTransactionDoc(
            id = "tx-bs-2",
            invoiceNumber = "INV-BS-002",
            timestampMillis = 1_794_753_600_000L,
            dateKey = "2026-11-15",
            totalItems = 4,
            totalRevenue = 340_000L,
            finalRevenue = 340_000L,
            totalCost = 180_000L,
            netProfit = 160_000L,
            paymentMethod = PaymentMethod.QRIS.name,
            amountPaid = 340_000L,
            cashierUid = "kasir-1",
            cashierName = "Aisyah",
            cashierRole = UserRole.KASIR.name
        )

        // Item A sold 2 pcs @ 200k = 400k (higher revenue per unit)
        val highRevLowQty = SaleItemDoc(
            id = "i-a",
            transactionId = "tx-bs-1",
            dateKey = "2026-11-15",
            timestampMillis = tx1.timestampMillis,
            productId = "p-gamis",
            skuCode = "UMT-BJ-01",
            productName = "Gamis Sutera Premium",
            variantSummary = "Warna: Maroon • Ukuran: L",
            categoryName = "Baju Muslim",
            quantity = 2,
            unitCostPrice = 110_000L,
            unitSellingPrice = 200_000L,
            subtotalRevenue = 400_000L,
            subtotalCost = 220_000L,
            subtotalProfit = 180_000L,
            cashierUid = "kasir-1"
        )
        // Item B sold across 2 transactions (4 + 4 = 8 pcs) @ 45k/42.5k = 370k (highest qty -> #1 Best Seller!)
        val highQty1 = SaleItemDoc(
            id = "i-b1",
            transactionId = "tx-bs-1",
            dateKey = "2026-11-15",
            timestampMillis = tx1.timestampMillis,
            productId = "p-peci",
            skuCode = "UMT-AK-02",
            productName = "Peci Rajut Yaman",
            variantSummary = "Warna: Hitam • Ukuran: All Size",
            categoryName = "Aksesoris",
            quantity = 4,
            unitCostPrice = 7_500L,
            unitSellingPrice = 7_500L,
            subtotalRevenue = 30_000L,
            subtotalCost = 30_000L,
            subtotalProfit = 0L,
            cashierUid = "kasir-1"
        )
        val highQty2 = SaleItemDoc(
            id = "i-b2",
            transactionId = "tx-bs-2",
            dateKey = "2026-11-15",
            timestampMillis = tx2.timestampMillis,
            productId = "p-peci",
            skuCode = "UMT-AK-02",
            productName = "Peci Rajut Yaman",
            variantSummary = "Warna: Hitam • Ukuran: All Size",
            categoryName = "Aksesoris",
            quantity = 4,
            unitCostPrice = 45_000L,
            unitSellingPrice = 85_000L,
            subtotalRevenue = 340_000L,
            subtotalCost = 180_000L,
            subtotalProfit = 160_000L,
            cashierUid = "kasir-1"
        )

        val sales = listOf(
            CloudSaleWithItems(tx1, listOf(highRevLowQty, highQty1)),
            CloudSaleWithItems(tx2, listOf(highQty2))
        )

        val byQty = ReportExporter.aggregateBestSellers(sales, limit = 5, sortByRevenue = false)
        assertEquals(2, byQty.size)
        assertEquals("Peci Rajut Yaman", byQty[0].productName)
        assertEquals(8, byQty[0].totalQuantity)
        assertEquals(2, byQty[0].transactionCount)
        assertEquals("Gamis Sutera Premium", byQty[1].productName)
        assertEquals(2, byQty[1].totalQuantity)

        val byRevenue = ReportExporter.aggregateBestSellers(sales, limit = 5, sortByRevenue = true)
        assertEquals("Gamis Sutera Premium", byRevenue[0].productName)
        assertEquals(400_000L, byRevenue[0].totalRevenue)

        val ownerText = ReportExporter.buildReadableTextReport("15 Nov 2026", sales)
        assertTrue(ownerText.contains("BARANG BEST SELLER (TERLARIS)"))
        assertTrue(ownerText.contains("#1 Peci Rajut Yaman"))

        val cashierText = ReportExporter.buildCashierShiftRecapText("Tajir", "Aisyah", "15 Nov 2026", sales)
        assertTrue(cashierText.contains("BARANG BEST SELLER SHIFT"))
        assertTrue(cashierText.contains("#1 Peci Rajut Yaman"))
        assertFalse(cashierText.contains("250.000"))
        assertFalse(cashierText.contains("180.000"))
    }

    @Test
    fun stockHistorySummarizerFiltersCountsAndBuildsOwnerRecap() {
        val now = System.currentTimeMillis()
        val older = now - 10L * 24 * 60 * 60 * 1000L
        val logs = listOf(
            StockHistoryDoc(
                id = "h1",
                productId = "p1",
                productName = "Gamis Madinah",
                changeType = StockChangeType.RESTOK.name,
                quantityDelta = 12,
                stockAfter = 22,
                actorName = "Aisyah (Kasir)",
                note = "Barang masuk supplier",
                timestampMillis = now
            ),
            StockHistoryDoc(
                id = "h2",
                productId = "p1",
                productName = "Gamis Madinah",
                changeType = StockChangeType.KOREKSI.name,
                quantityDelta = -2,
                stockAfter = 20,
                actorName = "Aisyah (Kasir)",
                note = "Kain sobek / rusak",
                timestampMillis = now - 1000L
            ),
            StockHistoryDoc(
                id = "h3",
                productId = "p2",
                productName = "Peci Rajut Yaman",
                changeType = StockChangeType.PENJUALAN.name,
                quantityDelta = -4,
                stockAfter = 16,
                actorName = "Umar (Kasir)",
                note = "Penjualan INV-001",
                timestampMillis = now - 2000L
            ),
            StockHistoryDoc(
                id = "h4",
                productId = "p2",
                productName = "Peci Rajut Yaman",
                changeType = StockChangeType.PRODUK_BARU.name,
                quantityDelta = 20,
                stockAfter = 20,
                actorName = "Owner",
                note = "Input produk baru",
                timestampMillis = older
            )
        )

        val fullSummary = StockHistorySummarizer.summarize(logs)
        assertEquals(32, fullSummary.totalAddedUnits)
        assertEquals(2, fullSummary.totalAddedEvents)
        assertEquals(2, fullSummary.totalManualReducedUnits)
        assertEquals(1, fullSummary.totalManualReducedEvents)
        assertEquals(4, fullSummary.totalSalesReducedUnits)
        assertEquals(1, fullSummary.totalSalesEvents)
        assertEquals(26, fullSummary.netDeltaUnits)

        val todayOnly = StockHistorySummarizer.filterHistories(
            histories = logs,
            typeFilter = StockHistoryFilterType.ALL,
            periodFilter = StockHistoryPeriodFilter.TODAY
        )
        assertEquals(3, todayOnly.size)

        val reductionsOnly = StockHistorySummarizer.filterHistories(
            histories = logs,
            typeFilter = StockHistoryFilterType.REDUCTION,
            periodFilter = StockHistoryPeriodFilter.ALL
        )
        assertEquals(1, reductionsOnly.size)
        assertEquals("h2", reductionsOnly.first().id)

        val searchByActor = StockHistorySummarizer.filterHistories(
            histories = logs,
            query = "sobek aisyah"
        )
        assertEquals(1, searchByActor.size)
        assertEquals("h2", searchByActor.first().id)

        val recapText = StockHistorySummarizer.buildRecapText("Tajir", "Hari Ini", todayOnly)
        assertTrue(recapText.contains("REKAP PERGERAKAN STOK - Tajir"))
        assertTrue(recapText.contains("+12 pcs"))
        assertTrue(recapText.contains("-2 pcs"))
        assertTrue(recapText.contains("Aisyah (Kasir)"))
        assertTrue(recapText.contains("Kain sobek / rusak"))
    }

    @Test
    fun storeTypePresetsCoverAllSupportedStoreTypesAndCustomLabel() {
        val foodCats = StoreCategoryDoc.defaultPresetsForStoreType(StoreType.MAKANAN_MINUMAN)
        val grosirCats = StoreCategoryDoc.defaultPresetsForStoreType(StoreType.GROSIR)
        val bajuCats = StoreCategoryDoc.defaultPresetsForStoreType(StoreType.BAJU)
        val otherCats = StoreCategoryDoc.defaultPresetsForStoreType(StoreType.LAINNYA)

        assertEquals(4, foodCats.size)
        assertTrue(foodCats.any { it.code == "MAKANAN" && it.displayName == "Makanan" })
        assertTrue(foodCats.any { it.code == "MINUMAN" && it.displayName == "Minuman" })

        assertEquals(4, grosirCats.size)
        assertTrue(grosirCats.any { it.code == "SEMBAKO" && it.displayName == "Sembako" })

        assertEquals(4, bajuCats.size)
        assertTrue(bajuCats.any { it.code == "BAJU" && it.displayName == "Baju & Atasan" })

        assertEquals(4, otherCats.size)
        assertTrue(otherCats.any { it.code == "PRODUK_UTAMA" })

        val customStore = StoreDoc(
            storeName = "Tajir Pustaka",
            storeType = StoreType.LAINNYA.name,
            customStoreType = "Toko Buku"
        )
        assertEquals("Toko Buku", customStore.resolvedStoreTypeLabel)

        val defaultOtherStore = StoreDoc(
            storeName = "Tajir Serba Ada",
            storeType = StoreType.LAINNYA.name,
            customStoreType = ""
        )
        assertEquals("Lainnya", defaultOtherStore.resolvedStoreTypeLabel)

        val fnbStore = StoreDoc(
            storeName = "Kedai Tajir",
            storeType = StoreType.MAKANAN_MINUMAN.name
        )
        assertEquals("Toko Makanan & Minuman", fnbStore.resolvedStoreTypeLabel)
    }
}

