package com.example.util

import com.example.data.local.entity.PaymentMethod
import com.example.data.remote.CloudSaleWithItems
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

enum class PrinterPaperWidth(val label: String, val charsPerLine: Int) {
    MM_58("58mm (Standar)", 32),
    MM_80("80mm (Lebar)", 48)
}

object EscPosReceiptFormatter {

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val SIZE_DOUBLE_HEIGHT_WIDTH = byteArrayOf(0x1D, 0x21, 0x11)
    private val CUT_PAPER_FEED = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    private val asciiCharset: Charset = Charsets.US_ASCII

    fun formatTwoColumns(left: String, right: String, width: Int): String {
        val cleanLeft = sanitizeAscii(left)
        val cleanRight = sanitizeAscii(right)
        if (cleanLeft.length + cleanRight.length + 1 <= width) {
            val spaces = width - cleanLeft.length - cleanRight.length
            return cleanLeft + " ".repeat(spaces) + cleanRight
        }
        val maxLeft = (width - cleanRight.length - 1).coerceAtLeast(4)
        val trimmedLeft = if (cleanLeft.length > maxLeft) {
            cleanLeft.take((maxLeft - 1).coerceAtLeast(1)) + "."
        } else {
            cleanLeft
        }
        val spaces = (width - trimmedLeft.length - cleanRight.length).coerceAtLeast(1)
        return trimmedLeft + " ".repeat(spaces) + cleanRight
    }

    fun buildPlainTextPreview(
        receipt: CloudSaleWithItems,
        storeName: String = "Tajir",
        paperWidth: PrinterPaperWidth = PrinterPaperWidth.MM_58
    ): String {
        val tx = receipt.transaction
        val w = paperWidth.charsPerLine
        val divider = "-".repeat(w)
        val effectiveStore = sanitizeAscii(storeName.ifBlank { "Tajir" })

        val sb = StringBuilder()
        sb.appendLine(centerText(effectiveStore, w))
        sb.appendLine(centerText("Kasir Pintar - Tajir", w))
        sb.appendLine(divider)
        sb.appendLine(formatTwoColumns("No:", tx.invoiceNumber, w))
        sb.appendLine(formatTwoColumns("Tgl:", DateUtils.formatDateTime(tx.timestampMillis), w))
        sb.appendLine(formatTwoColumns("Kasir:", tx.cashierName, w))
        if (tx.customerName.isNotBlank()) {
            sb.appendLine(formatTwoColumns("Pelanggan:", tx.customerName, w))
        }
        sb.appendLine(divider)

        receipt.items.forEach { item ->
            sb.appendLine(sanitizeAscii(item.productName).take(w))
            if (item.variantSummary.isNotBlank()) {
                wrapIndented(item.variantSummary, w).forEach { sb.appendLine(it) }
            }
            val leftQty = "${item.quantity} x ${CurrencyFormatter.formatRupiah(item.unitSellingPrice)}"
            val rightSub = CurrencyFormatter.formatRupiah(item.subtotalRevenue)
            sb.appendLine(formatTwoColumns(leftQty, rightSub, w))
        }

        sb.appendLine(divider)
        if (tx.discountAmount > 0L) {
            sb.appendLine(formatTwoColumns("Subtotal", CurrencyFormatter.formatRupiah(tx.totalRevenue), w))
            sb.appendLine(formatTwoColumns("Diskon", "-${CurrencyFormatter.formatRupiah(tx.discountAmount)}", w))
        }
        sb.appendLine(formatTwoColumns("TOTAL", CurrencyFormatter.formatRupiah(tx.finalRevenue), w))
        sb.appendLine(formatTwoColumns("Metode", tx.paymentMethodEnum.displayName, w))
        if (tx.qrisReference.isNotBlank()) {
            sb.appendLine(formatTwoColumns("Ref QRIS", tx.qrisReference.take(18), w))
        }
        if (tx.paymentMethodEnum == PaymentMethod.TUNAI) {
            sb.appendLine(formatTwoColumns("Dibayar", CurrencyFormatter.formatRupiah(tx.amountPaid), w))
            sb.appendLine(formatTwoColumns("Kembali", CurrencyFormatter.formatRupiah(tx.changeAmount), w))
        }
        if (tx.notes.isNotBlank()) {
            sb.appendLine("Catatan: ${sanitizeAscii(tx.notes)}".take(w))
        }
        sb.appendLine(divider)
        sb.appendLine(centerText("Jazakumullahu khairan", w))
        sb.appendLine(centerText("Terima kasih telah berbelanja", w))
        return sb.toString().trimEnd()
    }

    fun buildEscPosBytes(
        receipt: CloudSaleWithItems,
        storeName: String = "Tajir",
        paperWidth: PrinterPaperWidth = PrinterPaperWidth.MM_58
    ): ByteArray {
        val tx = receipt.transaction
        val w = paperWidth.charsPerLine
        val divider = "-".repeat(w)
        val effectiveStore = sanitizeAscii(storeName.ifBlank { "Tajir" })

        val out = ByteArrayOutputStream()
        fun writeLine(text: String = "") {
            out.write(sanitizeAscii(text).toByteArray(asciiCharset))
            out.write('\n'.code)
        }

        out.write(ESC_INIT)

        // Store Header
        out.write(ALIGN_CENTER)
        out.write(BOLD_ON)
        out.write(SIZE_DOUBLE_HEIGHT_WIDTH)
        writeLine(effectiveStore)
        out.write(SIZE_NORMAL)
        out.write(BOLD_OFF)
        writeLine("Kasir Pintar - Tajir")
        writeLine(divider)

        // Transaction Metadata
        out.write(ALIGN_LEFT)
        writeLine(formatTwoColumns("No:", tx.invoiceNumber, w))
        writeLine(formatTwoColumns("Tgl:", DateUtils.formatDateTime(tx.timestampMillis), w))
        writeLine(formatTwoColumns("Kasir:", tx.cashierName, w))
        if (tx.customerName.isNotBlank()) {
            writeLine(formatTwoColumns("Pelanggan:", tx.customerName, w))
        }
        writeLine(divider)

        // Line Items (No cost/profit ever printed on customer receipt)
        receipt.items.forEach { item ->
            val name = sanitizeAscii(item.productName)
            if (name.length <= w) {
                writeLine(name)
            } else {
                writeLine(name.take(w))
                writeLine(name.drop(w).take(w))
            }
            if (item.variantSummary.isNotBlank()) {
                wrapIndented(item.variantSummary, w).forEach { writeLine(it) }
            }
            val leftQty = "${item.quantity} x ${CurrencyFormatter.formatRupiah(item.unitSellingPrice)}"
            val rightSub = CurrencyFormatter.formatRupiah(item.subtotalRevenue)
            writeLine(formatTwoColumns(leftQty, rightSub, w))
        }

        writeLine(divider)
        if (tx.discountAmount > 0L) {
            writeLine(formatTwoColumns("Subtotal", CurrencyFormatter.formatRupiah(tx.totalRevenue), w))
            writeLine(formatTwoColumns("Diskon", "-${CurrencyFormatter.formatRupiah(tx.discountAmount)}", w))
        }

        out.write(BOLD_ON)
        writeLine(formatTwoColumns("TOTAL", CurrencyFormatter.formatRupiah(tx.finalRevenue), w))
        out.write(BOLD_OFF)

        writeLine(formatTwoColumns("Metode", tx.paymentMethodEnum.displayName, w))
        if (tx.qrisReference.isNotBlank()) {
            writeLine(formatTwoColumns("Ref QRIS", tx.qrisReference.take(18), w))
        }
        if (tx.paymentMethodEnum == PaymentMethod.TUNAI) {
            writeLine(formatTwoColumns("Dibayar", CurrencyFormatter.formatRupiah(tx.amountPaid), w))
            writeLine(formatTwoColumns("Kembali", CurrencyFormatter.formatRupiah(tx.changeAmount), w))
        }
        if (tx.notes.isNotBlank()) {
            writeLine("Catatan: ${sanitizeAscii(tx.notes)}".take(w))
        }

        writeLine(divider)
        out.write(ALIGN_CENTER)
        writeLine("Jazakumullahu khairan")
        writeLine("Terima kasih telah berbelanja")
        writeLine()
        writeLine()
        writeLine()
        out.write(CUT_PAPER_FEED)
        return out.toByteArray()
    }

    private fun centerText(text: String, width: Int): String {
        val clean = sanitizeAscii(text).take(width)
        val pad = ((width - clean.length) / 2).coerceAtLeast(0)
        return " ".repeat(pad) + clean
    }

    private fun wrapIndented(text: String, width: Int): List<String> {
        val clean = sanitizeAscii(text).replace(Regex("\\s{2,}"), " ")
        val maxContent = (width - 2).coerceAtLeast(10)
        return clean.chunked(maxContent).map { "  $it" }
    }

    private fun sanitizeAscii(raw: String): String {
        return raw.replace(Regex("[^\\x20-\\x7E]"), " ").trim()
    }
}
