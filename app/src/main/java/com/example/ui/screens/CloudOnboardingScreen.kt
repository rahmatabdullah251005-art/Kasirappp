package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddBusiness
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.data.remote.StoreInviteDoc
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.DangerCrimsonContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer

@Composable
fun GoogleAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var isCheckingSilent by remember { mutableStateOf(true) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSilent = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSilent = false
            },
            scope = scope
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_store_banner),
                        contentDescription = "Banner Toko UM TAZKIYAH",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF062C22).copy(alpha = 0.2f),
                                        Color(0xFF062C22).copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f)
                        ) {
                            Text(
                                text = "CLOUD POS TERINTEGRASI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.store_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Masuk & Sinkronisasi Online",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Hubungkan kasir dan pantauan owner secara real-time dengan akun Google Anda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        CloudFeaturePoint(
                            icon = Icons.Rounded.Devices,
                            title = "Pantau dari HP Lain",
                            subtitle = "Owner dapat memantau omzet, modal, keuntungan & stok dari jarak jauh secara langsung."
                        )
                        CloudFeaturePoint(
                            icon = Icons.Rounded.QrCodeScanner,
                            title = "Kasir QR & Pembayaran QRIS",
                            subtitle = "Pindai QR produk masuk keranjang otomatis & dukung scan pembayaran QRIS."
                        )
                        CloudFeaturePoint(
                            icon = Icons.Rounded.Security,
                            title = "Pemisahan Owner & Pegawai",
                            subtitle = "Undang email pegawai kasir tanpa membuka laporan laba/modal."
                        )

                        if (!authError.isNullOrBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = DangerCrimsonContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = authError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DangerCrimson,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                authError = null
                                isSigningIn = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isSigningIn = false
                                        onAuthSuccess()
                                    },
                                    onAuthError = { msg ->
                                        isSigningIn = false
                                        authError = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isSigningIn = false
                                    }
                                )
                            },
                            enabled = !isSigningIn && !isCheckingSilent,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            if (isSigningIn || isCheckingSilent) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(if (isCheckingSilent) "Memeriksa Sesi…" else "Menghubungkan…")
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Masuk dengan Google",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudFeaturePoint(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(7.dp)
                    .size(18.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StoreLinkingScreen(
    userDisplayName: String,
    userEmail: String,
    pendingInvites: List<StoreInviteDoc>,
    isBusy: Boolean,
    errorMessage: String?,
    onAcceptInvite: (StoreInviteDoc) -> Unit,
    onCreateOwnerStore: (storeName: String, ownerName: String) -> Unit,
    onSignOutGoogle: () -> Unit
) {
    var storeNameInput by remember { mutableStateOf("UM TAZKIYAH") }
    var ownerNameInput by remember(userDisplayName) {
        mutableStateOf(userDisplayName.ifBlank { "Owner UM Tazkiyah" })
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = ProfitGreenContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = ProfitGreen)
                            Column {
                                Text(
                                    text = userDisplayName.ifBlank { "Akun Google Terhubung" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ProfitGreen
                                )
                                Text(
                                    text = userEmail.ifBlank { "Email tidak tersedia" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ProfitGreen
                                )
                            }
                        }
                        OutlinedButton(onClick = onSignOutGoogle) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ganti")
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = DangerCrimsonContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerCrimson,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (pendingInvites.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.MailOutline, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Text(
                                    text = "Undangan Pegawai Kasir Ditemukan",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            pendingInvites.forEach { invite ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Toko: ${invite.storeName}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Peran: Pegawai Kasir (${invite.cashierName})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = { onAcceptInvite(invite) },
                                            enabled = !isBusy,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("accept_invite_button")
                                        ) {
                                            Text("Gabung ke Toko sebagai Kasir")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AddBusiness, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Buat Toko Cloud Baru (Owner)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "Jika Anda pemilik toko UM TAZKIYAH, buat toko cloud sekarang. Setelah dibuat, login dengan akun Google yang sama di HP lain untuk memantau toko, atau undang email Google pegawai kasir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = storeNameInput,
                            onValueChange = { storeNameInput = it },
                            label = { Text("Nama Toko") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_store_name_input")
                        )

                        OutlinedTextField(
                            value = ownerNameInput,
                            onValueChange = { ownerNameInput = it },
                            label = { Text("Nama Pemilik (Owner)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_store_owner_input")
                        )

                        Button(
                            onClick = { onCreateOwnerStore(storeNameInput, ownerNameInput) },
                            enabled = !isBusy && storeNameInput.isNotBlank() && ownerNameInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("create_owner_store_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Aktifkan Toko Online Sekarang")
                            }
                        }

                        HorizontalDivider()
                        Text(
                            text = "Catatan untuk Pegawai Kasir: Pastikan Owner sudah mengundang alamat email Google Anda (${userEmail.ifBlank { "-" }}) di menu Kelola Pegawai & PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
