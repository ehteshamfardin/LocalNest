package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_logs")
data class ServerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val bytesTransferred: Long = 0L,
    val eventType: String = "REQUEST", // REQUEST, DOWNLOAD, UPLOAD, ERROR, AUTH
    val message: String = ""
)
