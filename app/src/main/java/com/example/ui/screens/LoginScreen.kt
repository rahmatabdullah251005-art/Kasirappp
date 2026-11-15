package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.UserRole
import com.example.data.remote.StaffPinProfileDoc
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.DangerCrimsonContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    storeName: String,
    googleEmail: String,
    cloudMemberRole: UserRole,
    activeCashiers: List<StaffPinProfileDoc>,
    loginError: String?,
    onClearError: () -> Unit,
    onLogin: (username: String, pin: String, role: UserRole) -> Unit,
    onSignOutGoogle: () -> Unit
) {
    val canAccessOwner = cloudMemberRole == UserRole.OWNER
    var selectedRole by remember(canAccessOwner) {
        mutableStateOf(if (canAccessOwner) UserRole.OWNER else UserRole.KASIR)
    }
    var username by remember { mutableStateOf(if (canAccessOwner) "owner" else "aisyah") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    LaunchedEffect(selectedRole, activeCashiers) {
        onClearError()
        pin = ""
        username = if (selectedRole == UserRole.OWNER) {
            "owner"
        } else {
            activeCashiers.firstOrNull()?.username ?: "aisyah"
        }
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Store Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(166.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_store_banner),
                        contentDescription = "Ilustrasi Toko UM Tazkiyah",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF062C22).copy(alpha = 0.25f),
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
                                text = "SISTEM KASIR & STOK ONLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = storeName.ifBlank { stringResource(R.string.app_name) },
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

                // Cloud Connected Session Strip
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = ProfitGreenContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = ProfitGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${googleEmail.ifBlank { "Akun Google" }} (${cloudMemberRole.displayName})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ProfitGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = onSignOutGoogle) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = ProfitGreen
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Ganti Google", color = ProfitGreen)
                        }
                    }
                }

                // Role Separation Selector
                Text(
                    text = "Pilih Akses Masuk Toko",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (canAccessOwner) {
                        RoleOptionCard(
                            title = "Owner",
                            subtitle = "Akses penuh: Modal, Laba, Stok & Pantau",
                            icon = Icons.Rounded.AdminPanelSettings,
                            selected = selectedRole == UserRole.OWNER,
                            onClick = { selectedRole = UserRole.OWNER },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_owner_card")
                        )
                    }
                    RoleOptionCard(
                        title = "Pegawai Kasir",
                        subtitle = "Kasir, Scan QR/QRIS & Cek Stok saja",
                        icon = Icons.Rounded.Badge,
                        selected = selectedRole == UserRole.KASIR,
                        onClick = { selectedRole = UserRole.KASIR },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_kasir_card")
                    )
                }

                // Login Form Card
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
                            text = if (selectedRole == UserRole.OWNER) {
                                "Masuk sebagai Pemilik Toko"
                            } else {
                                "Masuk sebagai Pegawai Kasir"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        if (selectedRole == UserRole.KASIR && activeCashiers.isNotEmpty()) {
                            Text(
                                text = "Pilih Profil Kasir Aktif:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                activeCashiers.forEach { staff ->
                                    FilterChip(
                                        selected = username.equals(staff.username, ignoreCase = true),
                                        onClick = {
                                            username = staff.username
                                            onClearError()
                                        },
                                        label = { Text(staff.fullName) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                onClearError()
                            },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_username_input")
                        )

                        OutlinedTextField(
                            value = pin,
                            onValueChange = { input ->
                                pin = input.filter { it.isDigit() }.take(6)
                                onClearError()
                            },
                            label = { Text("PIN Keamanan (4-6 digit)") },
                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPin = !showPin }) {
                                    Icon(
                                        imageVector = if (showPin) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Tampilkan atau sembunyikan PIN"
                                    )
                                }
                            },
                            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { onLogin(username, pin, selectedRole) }
                            ),
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_pin_input")
                        )

                        if (!loginError.isNullOrBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = DangerCrimsonContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = loginError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DangerCrimson,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { onLogin(username, pin, selectedRole) },
                            enabled = username.isNotBlank() && pin.length >= 4,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            Text(
                                text = if (selectedRole == UserRole.OWNER) {
                                    "Masuk sebagai Owner"
                                } else {
                                    "Masuk sebagai Pegawai Kasir"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Helpful Default Credentials Info Card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "PIN Awal Toko (Dapat diubah di menu Owner):",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = buildString {
                                    if (canAccessOwner) appendLine("• Owner: username 'owner' • PIN 0000")
                                    append("• Kasir: 'aisyah' (PIN 1234) atau 'umar' (PIN 2345)")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = bgColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Terpilih",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
