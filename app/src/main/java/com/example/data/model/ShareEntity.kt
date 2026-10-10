package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shares")
data class ShareEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val allowUploads: Boolean = false,
    val isEnabled: Boolean = true,
    val isPasswordProtected: Boolean = false,
    val passwordHash: String = "",
    val salt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val maxUploadSizeMb: Long = 100L
)
