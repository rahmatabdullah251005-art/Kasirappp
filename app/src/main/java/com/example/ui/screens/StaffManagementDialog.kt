package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.UserRole
import com.example.data.remote.StaffPinProfileDoc
import com.example.data.remote.StoreInviteDoc
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer

@Composable
fun StaffManagementDialog(
    staffPins: List<StaffPinProfileDoc>,
    storeInvites: List<StoreInviteDoc>,
    onDismiss: () -> Unit,
    onCreateStaffPin: (username: String, fullName: String, rawPin: String, onSuccess: () -> Unit) -> Unit,
    onUpdateStaffPin: (profile: StaffPinProfileDoc, newFullName: String, newPin: String?, onSuccess: () -> Unit) -> Unit,
    onTogglePinActive: (StaffPinProfileDoc) -> Unit,
    onInviteCashierEmail: (email: String, name: String, onSuccess: () -> Unit) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = PIN Lokal, 1 = Undang HP Lain (Email)
    var showAddForm by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<StaffPinProfileDoc?>(null) }

    var newUsername by remember { mutableStateOf("") }
    var newFullName by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    var editName by remember { mutableStateOf("") }
    var editPin by remember { mutableStateOf("") }

    var inviteName by remember { mutableStateOf("") }
    var inviteEmail by remember { mutableStateOf("") }

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
                            text = "Kelola Pegawai & Akses Online",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Atur PIN perangkat toko & undang HP kasir via email Google",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Profil PIN (${staffPins.size})") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Undang Kasir (${storeInvites.size})") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (activeTab == 0) {
                    editingProfile?.let { target ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Ubah Profil: @${target.username}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Nama Tampilan") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editPin,
                                    onValueChange = { editPin = it.filter { c -> c.isDigit() }.take(6) },
                                    label = { Text("PIN Baru (kosongkan jika tidak diubah)") },
                                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { editingProfile = null }) {
                                        Text("Batal")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            onUpdateStaffPin(
                                                target,
                                                editName,
                                                editPin.takeIf { it.isNotBlank() }
                                            ) {
                                                editingProfile = null
                                            }
                                        },
                                        enabled = editName.isNotBlank() && (editPin.isBlank() || editPin.length >= 4)
                                    ) {
                                        Text("Simpan")
                                    }
                                }
                            }
                        }
                    }

                    if (showAddForm) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Tambah Profil PIN Kasir Baru",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                OutlinedTextField(
                                    value = newFullName,
                                    onValueChange = { newFullName = it },
                                    label = { Text("Nama Kasir (misal: Fatimah)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newUsername,
                                    onValueChange = { newUsername = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                                    label = { Text("Username Login (tanpa spasi)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newPin,
                                    onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6) },
                                    label = { Text("PIN Kasir (4-6 digit)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showAddForm = false }) {
                                        Text("Batal")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            onCreateStaffPin(newUsername, newFullName, newPin) {
                                                newUsername = ""
                                                newFullName = ""
                                                newPin = ""
                                                showAddForm = false
                                            }
                                        },
                                        enabled = newUsername.isNotBlank() && newFullName.isNotBlank() && newPin.length >= 4
                                    ) {
                                        Text("Simpan Kasir")
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                editingProfile = null
                                showAddForm = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tambah Profil PIN Kasir Baru")
                        }
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 310.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(staffPins, key = { it.id }) { profile ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (profile.roleEnum == UserRole.OWNER) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Badge,
                                        contentDescription = profile.roleEnum.displayName,
                                        tint = if (profile.roleEnum == UserRole.OWNER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = profile.fullName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (profile.roleEnum == UserRole.OWNER) MaterialTheme.colorScheme.primaryContainer else ProfitGreenContainer
                                            ) {
                                                Text(
                                                    text = profile.roleEnum.displayName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (profile.roleEnum == UserRole.OWNER) MaterialTheme.colorScheme.onPrimaryContainer else ProfitGreen,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "@${profile.username} • ${if (profile.isActive) "Aktif" else "Nonaktif"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            showAddForm = false
                                            editingProfile = profile
                                            editName = profile.fullName
                                            editPin = ""
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit profil")
                                    }
                                    if (profile.roleEnum != UserRole.OWNER) {
                                        Switch(
                                            checked = profile.isActive,
                                            onCheckedChange = { onTogglePinActive(profile) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Undang Pegawai Kasir (HP Terpisah)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Masukkan alamat Gmail pegawai kasir. Saat pegawai login di HP toko dengan akun Google tersebut, ia hanya bisa membuka Kasir & Cek Stok.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = inviteName,
                                onValueChange = { inviteName = it },
                                label = { Text("Nama Pegawai Kasir") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = inviteEmail,
                                onValueChange = { inviteEmail = it },
                                label = { Text("Email Google Pegawai") },
                                placeholder = { Text("contoh: kasir.aisyah@gmail.com") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    onInviteCashierEmail(inviteEmail, inviteName) {
                                        inviteEmail = ""
                                        inviteName = ""
                                    }
                                },
                                enabled = inviteName.isNotBlank() && inviteEmail.contains("@"),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Kirim Undangan Kasir Online")
                            }
                        }
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (storeInvites.isEmpty()) {
                            item {
                                Text(
                                    text = "Belum ada undangan email pegawai kasir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        } else {
                            items(storeInvites, key = { it.id }) { invite ->
                                val accepted = invite.status == "accepted"
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = invite.cashierName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = invite.cashierEmail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (accepted) ProfitGreenContainer else WarningAmberContainer
                                        ) {
                                            Text(
                                                text = if (accepted) "Terhubung" else "Menunggu",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (accepted) ProfitGreen else WarningAmber,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
