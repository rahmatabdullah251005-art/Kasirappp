package com.example.data.local.entity

enum class ProductCategory(val displayName: String, val codePrefix: String) {
    BAJU("Baju Muslim", "BJ"),
    BUKU("Buku Islam", "BK"),
    KEBUTUHAN_MUSLIM("Kebutuhan Muslim", "KM"),
    AKSESORIS("Aksesoris", "AK")
}

enum class PaymentMethod(val displayName: String) {
    TUNAI("Tunai"),
    QRIS("QRIS"),
    TRANSFER("Transfer")
}

enum class StockChangeType(val displayName: String) {
    PRODUK_BARU("Produk Baru"),
    RESTOK("Restok Masuk"),
    PENJUALAN("Terjual Kasir"),
    KOREKSI("Koreksi Stok")
}

enum class UserRole(val displayName: String) {
    OWNER("Owner"),
    KASIR("Pegawai Kasir")
}
