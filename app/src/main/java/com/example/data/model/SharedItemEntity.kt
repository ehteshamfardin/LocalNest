package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_items",
    indices = [Index(value = ["shareId"])]
)
data class SharedItemEntity(
    @PrimaryKey val id: String,
    val shareId: String,
    val displayName: String,
    val relativeSubPath: String = "",
    val absolutePath: String,
    val uriString: String = "",
    val isDirectory: Boolean = false,
    val sizeBytes: Long = 0L,
    val mimeType: String = "*/*",
    val addedAt: Long = System.currentTimeMillis()
)
