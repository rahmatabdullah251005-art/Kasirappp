package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.ProductDoc
import com.example.data.remote.StoreCategoryDoc
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldOnContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.TerracottaContainer
import com.example.ui.theme.TerracottaOnContainer

@Composable
fun CategoryManagementDialog(
    categories: List<StoreCategoryDoc>,
    products: List<ProductDoc>,
    onCreateCategory: (displayName: String, customPrefix: String, colorIndex: Int, onSuccess: (StoreCategoryDoc) -> Unit) -> Unit,
    onDeleteCategory: (StoreCategoryDoc) -> Unit,
    onRestoreDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var prefixInput by remember { mutableStateOf("") }
    var prefixManuallyEdited by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableIntStateOf(categories.size % 6) }
    var deleteConfirmTarget by remember { mutableStateOf<StoreCategoryDoc?>(null) }

    val colorSwatches = listOf(
        EmeraldContainer to EmeraldOnContainer,
        GoldContainer to GoldOnContainer,
        ProfitGreenContainer to ProfitGreen,
        TerracottaContainer to TerracottaOnContainer,
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    )

    val trimmedName = nameInput.trim()
    val effectivePrefix = prefixInput.trim().uppercase()
        .replace(Regex("[^A-Z0-9]"), "")
        .take(4)
        .ifBlank { StoreCategoryDoc.suggestPrefix(trimmedName) }
    val isDuplicateName = trimmedName.isNotEmpty() &&
        categories.any { it.displayName.equals(trimmedName, ignoreCase = true) }

    val previewCategory = remember(trimmedName, effectivePrefix, selectedColorIndex) {
        StoreCategoryDoc(
            id = "preview",
            code = StoreCategoryDoc.normalizeCode(trimmedName.ifBlank { "Kategori Baru" }),
            displayName = trimmedName.ifBlank { "Pratinjau Label" },
            codePrefix = effectivePrefix,
            colorIndex = selectedColorIndex
        )
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Kelola Kategori Barang",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Tambah kategori baru atau kurangi kategori lama",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup kelola kategori")
                    }
                }

                // Quick Add Form
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tambah Kategori Baru",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            CategoryBadge(category = previewCategory)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { raw ->
                                    nameInput = raw.take(60)
                                    if (!prefixManuallyEdited) {
                                        prefixInput = if (raw.isBlank()) "" else StoreCategoryDoc.suggestPrefix(raw)
                                    }
                                },
                                label = { Text("Nama Kategori") },
                                placeholder = { Text("Misal: Parfum / Hijab Anak") },
                                isError = isDuplicateName,
                                supportingText = if (isDuplicateName) {
                                    { Text("Nama kategori sudah tersedia") }
                                } else null,
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1.7f)
                                    .testTag("new_category_name_input")
                            )

                            OutlinedTextField(
                                value = prefixInput,
                                onValueChange = { raw ->
                                    prefixManuallyEdited = true
                                    prefixInput = raw.uppercase().filter { it.isLetterOrDigit() }.take(4)
                                },
                                label = { Text("Prefix SKU") },
                                placeholder = { Text("CT") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("new_category_prefix_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Warna Label:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                colorSwatches.forEachIndexed { idx, (bg, fg) ->
                                    val selected = selectedColorIndex == idx
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(bg)
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) fg else MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColorIndex = idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Warna dipilih",
                                                tint = fg,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onCreateCategory(trimmedName, effectivePrefix, selectedColorIndex) {
                                    nameInput = ""
                                    prefixInput = ""
                                    prefixManuallyEdited = false
                                    selectedColorIndex = (selectedColorIndex + 1) % 6
                                }
                            },
                            enabled = trimmedName.isNotEmpty() && !isDuplicateName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("add_category_submit_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tambah Kategori")
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Kategori (${categories.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(
                        onClick = onRestoreDefaults,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("restore_default_categories_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Muat Default", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (categories.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Belum ada kategori aktif",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Tambahkan kategori baru di atas atau tekan \"Muat Default\" untuk mengisi 4 kategori awal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it.id }) { cat ->
                            val usageCount = products.count { it.category.equals(cat.code, ignoreCase = true) }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CategoryBadge(category = cat)
                                        Text(
                                            text = "Prefix: UMT-${cat.codePrefix}-xxx • $usageCount barang",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleteConfirmTarget = cat },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("delete_category_${cat.code}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Hapus kategori ${cat.displayName}",
                                            tint = DangerCrimson
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Selesai")
                }
            }
        }
    }

    deleteConfirmTarget?.let { target ->
        val usedByCount = products.count { it.category.equals(target.code, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Kurangi Kategori?") },
            text = {
                Text(
                    if (usedByCount > 0) {
                        "Kategori '${target.displayName}' masih digunakan oleh $usedByCount barang di katalog. Menghapus kategori tidak menghapus barangnya, tetapi kategori ini akan hilang dari filter dan pilihan baru. Tetap hapus?"
                    } else {
                        "Yakin ingin menghapus kategori '${target.displayName}' dari toko?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(target)
                        deleteConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
                    modifier = Modifier.testTag("confirm_delete_category_button")
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
