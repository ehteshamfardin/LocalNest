package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ShareEntity
import com.example.data.model.SharedItemEntity
import com.example.ui.MainViewModel
import java.io.File

@Composable
fun FileExplorerScreen(
    viewModel: MainViewModel,
    shares: List<ShareEntity>,
    selectedShareId: String?,
    onSelectShare: (String) -> Unit
) {
    // If no share selected, default to the first one
    val activeShareId = selectedShareId ?: shares.firstOrNull()?.id
    val activeShare = shares.firstOrNull { it.id == activeShareId }

    val itemsState = remember(activeShareId) {
        if (activeShareId != null) viewModel.getItemsForShareFlow(activeShareId) else null
    }
    val items by itemsState?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<SharedItemEntity?>(null) }

    // SAF file picker for picking multiple documents from device storage
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && activeShareId != null) {
            viewModel.importFilesFromUris(activeShareId, uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "File Explorer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage files shared with network visitors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Shares Selector Horizontal Row
        if (shares.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(shares) { share ->
                    val isSelected = share.id == activeShareId
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectShare(share.id) },
                        label = { Text(share.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("filter_chip_${share.id}")
                    )
                }
            }
        }

        // Actions Row for Active Share
        if (activeShare != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f).testTag("select_files_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Files")
                }

                OutlinedButton(
                    onClick = { showCreateNoteDialog = true },
                    modifier = Modifier.weight(1f).testTag("create_note_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Document")
                }
            }
        }

        // Files List
        if (items.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No Files in this Share",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Use 'Select Files' to pick device files or 'Add Document' to create a new file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    FileItemRow(
                        item = item,
                        onPreview = { previewItem = item },
                        onDelete = { viewModel.removeItem(item.id) }
                    )
                }
            }
        }
    }

    // Create Document Dialog
    if (showCreateNoteDialog && activeShareId != null) {
        CreateNoteDialog(
            onDismiss = { showCreateNoteDialog = false },
            onConfirm = { title, content ->
                viewModel.createSampleDocument(activeShareId, title, content)
                showCreateNoteDialog = false
            }
        )
    }

    // Preview Dialog
    previewItem?.let { item ->
        FilePreviewDialog(
            item = item,
            onDismiss = { previewItem = null }
        )
    }
}

@Composable
fun FileItemRow(
    item: SharedItemEntity,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("file_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                val (icon, tint) = getFileIconAndColor(item.mimeType)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }

                Column {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier.size(36.dp).testTag("preview_${item.id}")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp).testTag("delete_${item.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Shared Document", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("File Name (e.g. notes.md, plan.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("doc_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("File Content") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("doc_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) onConfirm(title.trim(), content)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save to Share")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FilePreviewDialog(
    item: SharedItemEntity,
    onDismiss: () -> Unit
) {
    val file = File(item.absolutePath)
    val textContent = remember(item.absolutePath) {
        if (file.exists() && file.length() < 200000 && (item.mimeType.startsWith("text/") || item.mimeType.contains("json"))) {
            try { file.readText() } catch (e: Exception) { "Binary file" }
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(item.displayName, fontWeight = FontWeight.Bold, maxLines = 1)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Size: ${formatFileSize(item.sizeBytes)} • Type: ${item.mimeType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (textContent != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = textContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = "File is stored locally and streamable by connected devices.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

fun getFileIconAndColor(mimeType: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val m = mimeType.lowercase()
    return when {
        m.startsWith("image/") -> Pair(Icons.Default.Image, Color(0xFFEC4899))
        m.startsWith("video/") -> Pair(Icons.Default.Movie, Color(0xFF8B5CF6))
        m.startsWith("audio/") -> Pair(Icons.Default.Audiotrack, Color(0xFF10B981))
        m == "application/pdf" -> Pair(Icons.Default.PictureAsPdf, Color(0xFFEF4444))
        m.contains("json") || m.contains("javascript") || m.contains("python") -> Pair(Icons.Default.Code, Color(0xFF38BDF8))
        m.startsWith("text/") -> Pair(Icons.Default.Description, Color(0xFF38BDF8))
        else -> Pair(Icons.Default.InsertDriveFile, Color(0xFF64748B))
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024
    val sizes = arrayOf("B", "KB", "MB", "GB")
    val i = (Math.log(bytes.toDouble()) / Math.log(k.toDouble())).toInt().coerceAtMost(sizes.size - 1)
    val v = bytes / Math.pow(k.toDouble(), i.toDouble())
    return "%.1f %s".format(v, sizes[i])
}
