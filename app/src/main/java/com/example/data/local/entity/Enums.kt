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

enum class StoreType(
    val displayName: String,
    val subtitle: String,
    val defaultCategoryHint: String
) {
    MAKANAN_MINUMAN(
        displayName = "Toko Makanan & Minuman",
        subtitle = "Kuliner, kafe, warung, kue & minuman",
        defaultCategoryHint = "Makanan, Minuman, Snack & Kue, Paket Menu"
    ),
    GROSIR(
        displayName = "Toko Grosir",
        subtitle = "Sembako, grosir eceran & kebutuhan rumah",
        defaultCategoryHint = "Sembako, Minuman Kemasan, Kebutuhan Rumah, Snack & Eceran"
    ),
    BAJU(
        displayName = "Toko Baju",
        subtitle = "Busana, fashion, hijab & aksesoris",
        defaultCategoryHint = "Baju & Atasan, Celana & Rok, Hijab & Kerudung, Aksesoris"
    ),
    LAINNYA(
        displayName = "Lainnya",
        subtitle = "Usaha ritel atau jenis toko custom lainnya",
        defaultCategoryHint = "Produk Utama, Kebutuhan Harian, Perlengkapan, Aksesoris"
    )
}
