package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun SharesScreen(
    shares: List<ShareEntity>,
    onToggleShareStatus: (String, Boolean) -> Unit,
    onDeleteShare: (String) -> Unit,
    onSelectShareForFiles: (String) -> Unit,
    onCopyShareLink: (String) -> Unit,
    onShowQr: (String) -> Unit,
    onCreateShare: (
        name: String,
        description: String,
        allowUploads: Boolean,
        password: String?,
        expiresInHours: Int?,
        maxUploadMb: Long
    ) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Public Share Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure isolated folders, permissions, and security",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("add_share_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Share")
                    }
                }
            }

            if (shares.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "No Shares Configured",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Click 'New Share' above to create a folder with custom permissions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(shares, key = { it.id }) { share ->
                    ShareItemCard(
                        share = share,
                        onToggleEnabled = { isEnabled -> onToggleShareStatus(share.id, isEnabled) },
                        onDelete = { onDeleteShare(share.id) },
                        onManageFiles = { onSelectShareForFiles(share.id) },
                        onCopyLink = { onCopyShareLink(share.id) },
                        onShowQr = { onShowQr(share.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    if (showCreateDialog) {
        CreateShareDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc, uploads, pwd, exp, maxUpload ->
                onCreateShare(name, desc, uploads, pwd, exp, maxUpload)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ShareItemCard(
    share: ShareEntity,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onManageFiles: () -> Unit,
    onCopyLink: () -> Unit,
    onShowQr: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("share_card_${share.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Folder Icon + Title + Enable Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (share.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (share.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = share.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (share.description.isNotBlank()) {
                            Text(
                                text = share.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Switch(
                    checked = share.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StatusGreen,
                        checkedTrackColor = StatusGreen.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("toggle_share_${share.id}")
                )
            }

            // Badges row: Permissions & Security
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Upload permission badge
                val (uploadText, uploadColor) = if (share.allowUploads) {
                    Pair("Uploads Allowed (${share.maxUploadSizeMb}MB)", StatusGreen)
                } else {
                    Pair("Read-only", MaterialTheme.colorScheme.primary)
                }
                BadgeChip(text = uploadText, color = uploadColor)

                // Password protection badge
                if (share.isPasswordProtected) {
                    BadgeChip(text = "Password Protected", color = StatusAmber, icon = Icons.Default.Lock)
                }

                // Expiration badge
                if (share.expiresAt != null) {
                    val remaining = share.expiresAt - System.currentTimeMillis()
                    if (remaining > 0) {
                        val hours = (remaining / 3600000L).coerceAtLeast(1)
                        BadgeChip(text = "Expires in ${hours}h", color = StatusAmber, icon = Icons.Default.Schedule)
                    } else {
                        BadgeChip(text = "Expired", color = StatusRed)
                    }
                }
            }

            // Action Buttons Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onManageFiles,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("manage_files_${share.id}")
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Files", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onCopyLink,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("copy_link_${share.id}")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onShowQr,
                        modifier = Modifier.size(36.dp).testTag("qr_${share.id}")
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp).testTag("delete_${share.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Share",
                        tint = StatusRed.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun BadgeChip(
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontSize = 11.sp
        )
    }
}

@Composable
fun CreateShareDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        description: String,
        allowUploads: Boolean,
        password: String?,
        expiresInHours: Int?,
        maxUploadMb: Long
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var allowUploads by remember { mutableStateOf(false) }
    var enablePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var expiresInHours by remember { mutableIntStateOf(0) } // 0 = never
    var maxUploadMb by remember { mutableFloatStateOf(100f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Named Share", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Share Name (e.g., Photos, Docs)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_share_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Allow Uploads Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Allow Visitors to Upload", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Permits uploading into dedicated folder", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = allowUploads, onCheckedChange = { allowUploads = it })
                }

                if (allowUploads) {
                    Column {
                        Text("Max Upload Size: ${maxUploadMb.toInt()} MB", fontSize = 12.sp)
                        Slider(
                            value = maxUploadMb,
                            onValueChange = { maxUploadMb = it },
                            valueRange = 10f..500f,
                            steps = 4
                        )
                    }
                }

                // Password Protection Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Password Protection", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Require password to access share", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = enablePassword, onCheckedChange = { enablePassword = it })
                }

                if (enablePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Share Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_share_password_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name.trim(),
                            description.trim(),
                            allowUploads,
                            if (enablePassword && password.isNotBlank()) password.trim() else null,
                            if (expiresInHours > 0) expiresInHours else null,
                            maxUploadMb.toLong()
                        )
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_share")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
