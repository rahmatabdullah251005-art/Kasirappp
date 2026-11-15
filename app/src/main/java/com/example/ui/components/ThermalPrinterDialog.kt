package com.example.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.CloudSaleWithItems
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.DangerCrimsonContainer
import com.example.util.BluetoothPrinterQueryResult
import com.example.util.EscPosReceiptFormatter
import com.example.util.PairedPrinterDevice
import com.example.util.PrinterPaperWidth
import com.example.util.ThermalBluetoothPrinter
import kotlinx.coroutines.launch

@Composable
fun ThermalPrintDialog(
    receipt: CloudSaleWithItems,
    storeName: String,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var paperWidth by remember { mutableStateOf(ThermalBluetoothPrinter.getSavedPaperWidth(context)) }
    var queryResult by remember { mutableStateOf(ThermalBluetoothPrinter.loadPairedDevices(context)) }
    var selectedAddress by remember { mutableStateOf(ThermalBluetoothPrinter.getSavedPrinterAddress(context)) }
    var isPrinting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val refreshPrinters: () -> Unit = {
        val res = ThermalBluetoothPrinter.loadPairedDevices(context)
        queryResult = res
        if (res is BluetoothPrinterQueryResult.Ready && res.devices.isNotEmpty()) {
            val savedExists = res.devices.any { it.address == selectedAddress }
            if (!savedExists) {
                selectedAddress = res.devices.first().address
            }
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshPrinters()
    }

    val btEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPrinters()
    }

    val btSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPrinters()
    }

    LaunchedEffect(Unit) {
        if (queryResult is BluetoothPrinterQueryResult.PermissionRequired &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            refreshPrinters()
        }
    }

    val receiptPreview = remember(receipt, storeName, paperWidth) {
        EscPosReceiptFormatter.buildPlainTextPreview(receipt, storeName, paperWidth)
    }

    Dialog(
        onDismissRequest = { if (!isPrinting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Print,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Cetak Struk Thermal",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Bluetooth ESC/POS (${receipt.transaction.invoiceNumber})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, enabled = !isPrinting) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                }

                // Paper Width Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kertas:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrinterPaperWidth.entries.forEach { widthOption ->
                        FilterChip(
                            selected = paperWidth == widthOption,
                            onClick = {
                                paperWidth = widthOption
                                ThermalBluetoothPrinter.savePaperWidthOnly(context, widthOption)
                            },
                            label = { Text(widthOption.label) }
                        )
                    }
                }

                // Bluetooth Printer Selection Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pilih Printer Bluetooth",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = refreshPrinters) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Muat Ulang")
                    }
                }

                when (val state = queryResult) {
                    BluetoothPrinterQueryResult.PermissionRequired -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Izin Bluetooth Diperlukan",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Berikan izin perangkat terdekat (Bluetooth) agar aplikasi bisa menghubungkan printer thermal Anda.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Izinkan Bluetooth")
                                }
                            }
                        }
                    }
                    BluetoothPrinterQueryResult.BluetoothUnavailable -> {
                        Text(
                            text = "Perangkat ini tidak memiliki adaptor Bluetooth.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DangerCrimson
                        )
                    }
                    BluetoothPrinterQueryResult.BluetoothDisabled -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.BluetoothDisabled, contentDescription = null, tint = DangerCrimson)
                                    Text(
                                        text = "Bluetooth Belum Aktif",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = "Aktifkan Bluetooth untuk menampilkan daftar printer thermal yang sudah di-pairing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Nyalakan Bluetooth")
                                }
                            }
                        }
                    }
                    is BluetoothPrinterQueryResult.Ready -> {
                        if (state.devices.isEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Belum Ada Printer Terhubung",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Pasangkan (pair) printer thermal Bluetooth lewat Pengaturan Bluetooth HP, lalu tekan Muat Ulang.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            btSettingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Rounded.SettingsBluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Buka Pengaturan Bluetooth")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 170.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(state.devices, key = { it.address }) { dev ->
                                    val isSelected = dev.address == selectedAddress
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAddress = dev.address }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedAddress = dev.address }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dev.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = dev.address,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    btSettingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.SettingsBluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pairing Printer Baru di Pengaturan Bluetooth")
                            }
                        }
                    }
                }

                // Physical Receipt Preview
                HorizontalDivider()
                Text(
                    text = "Pratinjau Struk Fisik",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = receiptPreview,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = DangerCrimsonContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerCrimson,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                val readyDevices = (queryResult as? BluetoothPrinterQueryResult.Ready)?.devices.orEmpty()
                val chosenDevice: PairedPrinterDevice? = readyDevices.firstOrNull { it.address == selectedAddress }
                Button(
                    onClick = {
                        val dev = chosenDevice ?: return@Button
                        errorMessage = null
                        isPrinting = true
                        scope.launch {
                            ThermalBluetoothPrinter.printReceipt(
                                context = context,
                                device = dev,
                                receipt = receipt,
                                storeName = storeName,
                                paperWidth = paperWidth
                            ).onSuccess {
                                isPrinting = false
                                onShowMessage("Struk ${receipt.transaction.invoiceNumber} berhasil dicetak ke ${dev.name}")
                                onDismiss()
                            }.onFailure { err ->
                                isPrinting = false
                                errorMessage = err.message ?: "Gagal mencetak struk"
                            }
                        }
                    },
                    enabled = chosenDevice != null && !isPrinting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("confirm_print_thermal_button")
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Mengirim ke Printer…")
                    } else {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cetak Struk Sekarang")
                    }
                }
            }
        }
    }
}
