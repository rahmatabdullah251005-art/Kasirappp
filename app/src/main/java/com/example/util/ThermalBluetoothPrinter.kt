package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.remote.CloudSaleWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class PairedPrinterDevice(
    val name: String,
    val address: String
)

sealed interface BluetoothPrinterQueryResult {
    data object PermissionRequired : BluetoothPrinterQueryResult
    data object BluetoothUnavailable : BluetoothPrinterQueryResult
    data object BluetoothDisabled : BluetoothPrinterQueryResult
    data class Ready(val devices: List<PairedPrinterDevice>) : BluetoothPrinterQueryResult
}

object ThermalBluetoothPrinter {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PREFS_NAME = "um_tazkiyah_printer_prefs"
    private const val KEY_PRINTER_ADDRESS = "saved_printer_address"
    private const val KEY_PRINTER_NAME = "saved_printer_name"
    private const val KEY_PAPER_WIDTH = "saved_paper_width"

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getSavedPrinterAddress(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRINTER_ADDRESS, null)

    fun getSavedPrinterName(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRINTER_NAME, null)

    fun getSavedPaperWidth(context: Context): PrinterPaperWidth {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PAPER_WIDTH, PrinterPaperWidth.MM_58.name)
        return runCatching { PrinterPaperWidth.valueOf(raw ?: PrinterPaperWidth.MM_58.name) }
            .getOrDefault(PrinterPaperWidth.MM_58)
    }

    fun savePrinterPreference(
        context: Context,
        device: PairedPrinterDevice,
        paperWidth: PrinterPaperWidth
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRINTER_ADDRESS, device.address)
            .putString(KEY_PRINTER_NAME, device.name)
            .putString(KEY_PAPER_WIDTH, paperWidth.name)
            .apply()
    }

    fun savePaperWidthOnly(context: Context, paperWidth: PrinterPaperWidth) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PAPER_WIDTH, paperWidth.name)
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices(context: Context): BluetoothPrinterQueryResult {
        if (!hasBluetoothPermission(context)) {
            return BluetoothPrinterQueryResult.PermissionRequired
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = manager?.adapter ?: return BluetoothPrinterQueryResult.BluetoothUnavailable
        if (!adapter.isEnabled) {
            return BluetoothPrinterQueryResult.BluetoothDisabled
        }

        val devices = try {
            adapter.bondedDevices.orEmpty().map { dev ->
                PairedPrinterDevice(
                    name = dev.name?.takeIf { it.isNotBlank() } ?: "Perangkat Bluetooth",
                    address = dev.address
                )
            }.sortedWith(
                compareByDescending<PairedPrinterDevice> { looksLikeThermalPrinter(it.name) }
                    .thenBy { it.name.lowercase() }
            )
        } catch (_: SecurityException) {
            return BluetoothPrinterQueryResult.PermissionRequired
        }

        return BluetoothPrinterQueryResult.Ready(devices)
    }

    @SuppressLint("MissingPermission")
    suspend fun printReceipt(
        context: Context,
        device: PairedPrinterDevice,
        receipt: CloudSaleWithItems,
        storeName: String,
        paperWidth: PrinterPaperWidth
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasBluetoothPermission(context)) {
                throw SecurityException("Izin Bluetooth diperlukan untuk terhubung ke printer.")
            }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
                ?: throw IllegalStateException("Perangkat ini tidak mendukung Bluetooth.")
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth masih nonaktif. Aktifkan Bluetooth terlebih dahulu.")
            }

            val btDevice: BluetoothDevice = adapter.getRemoteDevice(device.address)
            val payload = EscPosReceiptFormatter.buildEscPosBytes(
                receipt = receipt,
                storeName = storeName,
                paperWidth = paperWidth
            )

            val socket = btDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                socket.connect()
                val out = socket.outputStream
                // Write in 512-byte chunks to avoid overflowing small thermal printer buffers
                val chunkSize = 512
                var offset = 0
                while (offset < payload.size) {
                    val len = minOf(chunkSize, payload.size - offset)
                    out.write(payload, offset, len)
                    out.flush()
                    offset += len
                    if (offset < payload.size) {
                        delay(35L)
                    }
                }
                delay(180L)
            } catch (e: IOException) {
                throw IOException(
                    "Gagal mencetak ke '${device.name}'. Pastikan printer menyala, berdekatan, dan kertas terpasang.",
                    e
                )
            } finally {
                runCatching { socket.close() }
            }

            savePrinterPreference(context, device, paperWidth)
        }
    }

    private fun looksLikeThermalPrinter(name: String): Boolean {
        val n = name.lowercase()
        return listOf("print", "pos", "mtp", "rpp", "eppos", "xprinter", "panda", "kassen", "zq", "bt-", "thermal")
            .any { n.contains(it) }
    }
}
