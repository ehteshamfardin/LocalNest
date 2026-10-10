package com.example.data.repository

import android.content.Context
import com.example.data.dao.ServerLogDao
import com.example.data.dao.ShareDao
import com.example.data.dao.SharedItemDao
import com.example.data.model.ServerLogEntity
import com.example.data.model.ShareEntity
import com.example.data.model.SharedItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class LocalShareRepository(
    private val context: Context,
    private val shareDao: ShareDao,
    private val sharedItemDao: SharedItemDao,
    private val serverLogDao: ServerLogDao
) {
    val allShares: Flow<List<ShareEntity>> = shareDao.getAllShares()
    val enabledShares: Flow<List<ShareEntity>> = shareDao.getEnabledShares()
    val recentLogs: Flow<List<ServerLogEntity>> = serverLogDao.getRecentLogs()

    suspend fun getEnabledSharesSync(): List<ShareEntity> = withContext(Dispatchers.IO) {
        shareDao.getEnabledSharesSync()
    }

    suspend fun getShareById(id: String): ShareEntity? = withContext(Dispatchers.IO) {
        shareDao.getShareById(id)
    }

    suspend fun getItemsForShare(shareId: String): List<SharedItemEntity> = withContext(Dispatchers.IO) {
        sharedItemDao.getItemsForShareSync(shareId)
    }

    fun getItemsForShareFlow(shareId: String): Flow<List<SharedItemEntity>> {
        return sharedItemDao.getItemsForShare(shareId)
    }

    suspend fun createShare(
        name: String,
        description: String = "",
        allowUploads: Boolean = false,
        password: String? = null,
        expiresAt: Long? = null,
        maxUploadSizeMb: Long = 100L
    ): ShareEntity = withContext(Dispatchers.IO) {
        val shareId = UUID.randomUUID().toString().take(8)
        val (hash, salt) = if (!password.isNullOrBlank()) {
            val s = generateSalt()
            Pair(hashPassword(password, s), s)
        } else {
            Pair("", "")
        }

        val share = ShareEntity(
            id = shareId,
            name = name.trim(),
            description = description.trim(),
            allowUploads = allowUploads,
            isEnabled = true,
            isPasswordProtected = !password.isNullOrBlank(),
            passwordHash = hash,
            salt = salt,
            createdAt = System.currentTimeMillis(),
            expiresAt = expiresAt,
            maxUploadSizeMb = maxUploadSizeMb
        )
        shareDao.insertShare(share)

        // Ensure a dedicated upload directory exists for this share
        val uploadDir = getShareUploadDir(shareId)
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }

        share
    }

    suspend fun updateShare(share: ShareEntity) = withContext(Dispatchers.IO) {
        shareDao.updateShare(share)
    }

    suspend fun deleteShare(shareId: String) = withContext(Dispatchers.IO) {
        sharedItemDao.deleteItemsForShare(shareId)
        shareDao.deleteShareById(shareId)
    }

    suspend fun toggleShareStatus(shareId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val share = shareDao.getShareById(shareId)
        if (share != null) {
            shareDao.updateShare(share.copy(isEnabled = isEnabled))
        }
    }

    suspend fun addItemToShare(
        shareId: String,
        displayName: String,
        file: File,
        uriString: String = "",
        relativeSubPath: String = ""
    ): SharedItemEntity = withContext(Dispatchers.IO) {
        val item = SharedItemEntity(
            id = UUID.randomUUID().toString(),
            shareId = shareId,
            displayName = displayName,
            relativeSubPath = relativeSubPath,
            absolutePath = file.absolutePath,
            uriString = uriString,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isDirectory) calculateDirSize(file) else file.length(),
            mimeType = determineMimeType(file.name),
            addedAt = System.currentTimeMillis()
        )
        sharedItemDao.insertItem(item)
        item
    }

    suspend fun removeItem(itemId: String) = withContext(Dispatchers.IO) {
        sharedItemDao.deleteItemById(itemId)
    }

    suspend fun logRequest(
        clientIp: String,
        method: String,
        path: String,
        statusCode: Int,
        bytesTransferred: Long,
        eventType: String = "REQUEST",
        message: String = ""
    ) = withContext(Dispatchers.IO) {
        serverLogDao.insertLog(
            ServerLogEntity(
                timestamp = System.currentTimeMillis(),
                clientIp = clientIp,
                method = method,
                path = path,
                statusCode = statusCode,
                bytesTransferred = bytesTransferred,
                eventType = eventType,
                message = message
            )
        )
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        serverLogDao.clearLogs()
    }

    fun getShareUploadDir(shareId: String): File {
        val baseDir = File(context.filesDir, "shares/$shareId/uploads")
        if (!baseDir.exists()) baseDir.mkdirs()
        return baseDir
    }

    fun getShareRootDir(shareId: String): File {
        val baseDir = File(context.filesDir, "shares/$shareId")
        if (!baseDir.exists()) baseDir.mkdirs()
        return baseDir
    }

    suspend fun ensureDefaultSampleShares() = withContext(Dispatchers.IO) {
        val existing = shareDao.getShareById("docs")
        if (existing == null) {
            createSampleData()
        }
    }

    private suspend fun createSampleData() {
        // 1. Documents share
        val docsShare = ShareEntity(
            id = "docs",
            name = "Project Documents",
            description = "Reports, specifications, and architecture notes",
            allowUploads = true,
            isEnabled = true,
            isPasswordProtected = false,
            createdAt = System.currentTimeMillis() - 86400000L
        )
        shareDao.insertShare(docsShare)

        val docsDir = File(context.filesDir, "shares/docs/content")
        docsDir.mkdirs()
        getShareUploadDir("docs")

        val readmeFile = File(docsDir, "README_LocalShare.md")
        readmeFile.writeText(
            """# Welcome to LocalShare 🌐
            |
            |LocalShare turns your device into a high-performance personal file-sharing server!
            |
            |## Features
            |- 🚀 Blazing fast local network transfers over Wi-Fi or Hotspot
            |- 💻 macOS Finder-inspired responsive web browser for connected devices
            |- 🔒 Fine-grained share permissions, read-only or upload-enabled modes
            |- 📁 Instant multi-file streaming downloads & on-the-fly ZIP generation
            |- 🎬 Media streaming with HTTP Range requests (seek videos & audio)
            |- 🛡️ Strict path traversal security & isolated share directories
            |
            |Enjoy seamless sharing across phones, tablets, laptops, and smart TVs!
            """.trimMargin()
        )
        addItemToShare("docs", readmeFile.name, readmeFile)

        val projectOverview = File(docsDir, "System_Architecture.txt")
        projectOverview.writeText(
            """LOCALSHARE ARCHITECTURE SPECIFICATION
            |====================================
            |Platform: Android Kotlin Native + Jetpack Compose
            |Server Engine: Coroutine Asynchronous HTTP/1.1 Engine
            |Web Client: macOS Finder Single Page Application
            |Security: Canonical Path Allowlist, SHA-256 Auth, Zero Path-Traversal
            |Protocol: HTTP/1.1 with Range, Multipart Upload, Dynamic ZIP Stream
            """.trimMargin()
        )
        addItemToShare("docs", projectOverview.name, projectOverview)

        // 2. Photos & Media share
        val mediaShare = ShareEntity(
            id = "media",
            name = "Photos & Media",
            description = "High-resolution photos, graphics, and video clips",
            allowUploads = false,
            isEnabled = true,
            isPasswordProtected = false,
            createdAt = System.currentTimeMillis() - 43200000L
        )
        shareDao.insertShare(mediaShare)
        val mediaDir = File(context.filesDir, "shares/media/content")
        mediaDir.mkdirs()

        // Copy generated localshare icon as a shared image
        val iconSrc = File(context.filesDir.parentFile, "app/src/main/res/drawable/ic_localshare_icon.jpg")
        val sampleImg = File(mediaDir, "LocalShare_Cover_Art.jpg")
        if (iconSrc.exists()) {
            iconSrc.copyTo(sampleImg, overwrite = true)
        } else {
            // Write a sample SVG/bitmap
            sampleImg.writeText("LocalShare Brand Art")
        }
        addItemToShare("media", sampleImg.name, sampleImg)

        val audioSample = File(mediaDir, "Chime_Notification.json")
        audioSample.writeText("""{"sound": "ambient_chime", "sampleRate": 44100, "channels": 2}""")
        addItemToShare("media", audioSample.name, audioSample)

        // 3. Protected Vault share
        val salt = generateSalt()
        val vaultShare = ShareEntity(
            id = "vault",
            name = "Confidential Vault",
            description = "Password-protected share (Default password: share123)",
            allowUploads = false,
            isEnabled = true,
            isPasswordProtected = true,
            passwordHash = hashPassword("share123", salt),
            salt = salt,
            createdAt = System.currentTimeMillis()
        )
        shareDao.insertShare(vaultShare)
        val vaultDir = File(context.filesDir, "shares/vault/content")
        vaultDir.mkdirs()
        val secretDoc = File(vaultDir, "confidential_credentials.txt")
        secretDoc.writeText("Access Granted: Authorized LocalShare Session\nToken ID: SECURE-LS-8921\n")
        addItemToShare("vault", secretDoc.name, secretDoc)
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    fun verifyPassword(share: ShareEntity, candidate: String): Boolean {
        if (!share.isPasswordProtected) return true
        val computed = hashPassword(candidate, share.salt)
        return computed == share.passwordHash
    }

    companion object {
        fun generateSalt(): String {
            val random = SecureRandom()
            val saltBytes = ByteArray(16)
            random.nextBytes(saltBytes)
            return saltBytes.joinToString("") { "%02x".format(it) }
        }

        fun hashPassword(password: String, salt: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val input = "$salt:$password".toByteArray(Charsets.UTF_8)
            val digest = md.digest(input)
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun determineMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "svg" -> "image/svg+xml"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "m4a" -> "audio/mp4"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain; charset=utf-8"
                "md" -> "text/markdown; charset=utf-8"
                "json" -> "application/json"
                "js" -> "application/javascript"
                "ts" -> "text/plain"
                "kt" -> "text/plain"
                "py" -> "text/plain"
                "html" -> "text/html; charset=utf-8"
                "css" -> "text/css"
                "zip" -> "application/zip"
                "tar", "gz" -> "application/gzip"
                else -> "application/octet-stream"
            }
        }
    }
}
