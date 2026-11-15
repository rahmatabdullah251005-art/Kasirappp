package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.local.entity.UserRole
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.QrScanOutcome
import com.example.util.CurrencyFormatter
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch

@Composable
fun PosQrScannerDialog(
    userRole: UserRole,
    onDismiss: () -> Unit,
    onScanCode: suspend (String) -> QrScanOutcome,
    onRequestAddUnregisteredProduct: (String) -> Unit,
    onSwitchToOwnerForAdd: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var manualCode by remember { mutableStateOf("") }
    var lastOutcome by remember { mutableStateOf<QrScanOutcome?>(null) }
    var lastScannedCode by remember { mutableStateOf("") }
    var lastScanTime by remember { mutableLongStateOf(0L) }
    var torchEnabled by remember { mutableStateOf(false) }

    fun submitCode(raw: String) {
        val code = raw.trim()
        if (code.isBlank()) return
        val now = System.currentTimeMillis()
        if (code == lastScannedCode && (now - lastScanTime) < 1700L) return
        lastScannedCode = code
        lastScanTime = now
        scope.launch {
            lastOutcome = onScanCode(code)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Pindai QR / Barcode Barang",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Otomatis masuk ke keranjang belanja",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_qr_scanner_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup pemindai")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color(0xFF0E1714)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        CameraBarcodePreview(
                            torchEnabled = torchEnabled,
                            onBarcodeDetected = { value ->
                                submitCode(value)
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(176.dp)
                                .border(
                                    width = 2.5.dp,
                                    color = GoldSecondary,
                                    shape = RoundedCornerShape(18.dp)
                                )
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            IconButton(onClick = { torchEnabled = !torchEnabled }) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                    contentDescription = "Lampu kilat",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(R.string.camera_permission_rationale),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.testTag("grant_camera_button")
                            ) {
                                Text(stringResource(R.string.grant_camera_permission))
                            }
                        }
                    }
                }

                when (val outcome = lastOutcome) {
                    is QrScanOutcome.AddedToCart -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = ProfitGreenContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = ProfitGreen
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${outcome.product.name} (+1)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ProfitGreen
                                    )
                                    val promoLabel = if (outcome.product.discountPercent > 0) " (-${outcome.product.discountPercent}%)" else ""
                                    Text(
                                        text = "Harga: ${CurrencyFormatter.formatRupiah(outcome.product.effectiveSellingPrice)}$promoLabel • Di keranjang: ${outcome.newCartQty} pcs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ProfitGreen
                                    )
                                }
                            }
                        }
                    }
                    is QrScanOutcome.OutOfStock -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = WarningAmberContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = WarningAmber
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Stok ${outcome.product.name} tidak cukup",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = WarningAmber
                                    )
                                    Text(
                                        text = "Sisa stok di gudang: ${outcome.product.stock} pcs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningAmber
                                    )
                                }
                            }
                        }
                    }
                    is QrScanOutcome.NotRegistered -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Produk dengan kode '${outcome.scannedCode}' belum terdaftar",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (userRole == UserRole.OWNER) {
                                    Button(
                                        onClick = { onRequestAddUnregisteredProduct(outcome.scannedCode) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("add_unregistered_qr_button")
                                    ) {
                                        Icon(Icons.Rounded.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Daftarkan Produk Baru Sekarang")
                                    }
                                } else {
                                    Text(
                                        text = "Pegawai kasir tidak memiliki akses menambah produk & harga modal. Minta Owner untuk mendaftarkannya.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    OutlinedButton(
                                        onClick = onSwitchToOwnerForAdd,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Ganti ke Login Owner")
                                    }
                                }
                            }
                        }
                    }
                    null -> {}
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        label = { Text("Atau ketik kode QR / SKU manual") },
                        placeholder = { Text("Contoh: UMT-BJ-001") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                submitCode(manualCode)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_qr_code_input")
                    )
                    FilledTonalButton(
                        onClick = { submitCode(manualCode) },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("submit_manual_qr_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Cari kode")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Selesai Pindai")
                    }
                }
            }
        }
    }
}

@Composable
fun SingleCodeQrScannerDialog(
    title: String = "Scan QR / Barcode Produk",
    subtitle: String = "Arahkan kamera ke kode QR atau masukkan kode secara manual",
    manualHint: String = "Kode QR / Payload",
    onDismiss: () -> Unit,
    onCodeCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    var manualInput by remember { mutableStateOf("") }
    var torchEnabled by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
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
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color(0xFF0E1714)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        CameraBarcodePreview(
                            torchEnabled = torchEnabled,
                            onBarcodeDetected = { value ->
                                if (value.isNotBlank()) {
                                    onCodeCaptured(value.trim())
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .border(2.5.dp, GoldSecondary, RoundedCornerShape(16.dp))
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            IconButton(onClick = { torchEnabled = !torchEnabled }) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                    contentDescription = "Lampu kilat",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.camera_permission_rationale),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(stringResource(R.string.grant_camera_permission))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text(manualHint) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            if (manualInput.isNotBlank()) {
                                onCodeCaptured(manualInput.trim())
                            }
                        },
                        enabled = manualInput.isNotBlank(),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Gunakan kode")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraBarcodePreview(
    torchEnabled: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(lifecycleOwner, cameraController) {
        val executor = ContextCompat.getMainExecutor(context)
        cameraController.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(
                listOf(barcodeScanner),
                CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                executor
            ) { result ->
                val barcodes = result?.getValue(barcodeScanner)
                val firstValue = barcodes?.firstOrNull()?.rawValue
                if (!firstValue.isNullOrBlank()) {
                    onBarcodeDetected(firstValue)
                }
            }
        )
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            barcodeScanner.close()
        }
    }

    cameraController.enableTorch(torchEnabled)

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = cameraController
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
