package com.example.server

import com.example.data.model.ShareEntity
import com.example.data.model.SharedItemEntity
import com.example.data.repository.LocalShareRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class ServerState {
    object Offline : ServerState()
    object Starting : ServerState()
    data class Running(
        val ipAddress: String,
        val port: Int,
        val activeConnections: Int,
        val startTime: Long
    ) : ServerState()
    data class Error(val message: String) : ServerState()
}

class LocalShareHttpServer(
    private val context: android.content.Context,
    private val repository: LocalShareRepository,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var activePort: Int = 8080
    private var boundIp: String = "0.0.0.0"
    private var startTimeMillis: Long = 0L

    val activeConnections = AtomicInteger(0)
    val totalBytesTransferred = AtomicLong(0L)

    // Valid authenticated tokens per share: token -> (shareId, expiryTime)
    private val authTokens = ConcurrentHashMap<String, Pair<String, Long>>()

    var onStateChanged: ((ServerState) -> Unit)? = null
    var currentState: ServerState = ServerState.Offline
        private set(value) {
            field = value
            onStateChanged?.invoke(value)
        }

    @Synchronized
    fun start(port: Int = 8080, preferredIp: String = "127.0.0.1"): Boolean {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            return true
        }

        currentState = ServerState.Starting

        return try {
            activePort = port
            boundIp = preferredIp
            // Bind to all interfaces (0.0.0.0) so other devices on Wi-Fi/Hotspot can connect!
            val socket = ServerSocket(port, 100, InetAddress.getByName("0.0.0.0"))
            serverSocket = socket
            startTimeMillis = System.currentTimeMillis()

            currentState = ServerState.Running(
                ipAddress = preferredIp,
                port = activePort,
                activeConnections = 0,
                startTime = startTimeMillis
            )

            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        activeConnections.incrementAndGet()
                        updateRunningState()
                        launch(Dispatchers.IO) {
                            try {
                                handleClient(clientSocket)
                            } catch (e: Exception) {
                                // Silent client disconnect
                            } finally {
                                activeConnections.decrementAndGet()
                                updateRunningState()
                                try { clientSocket.close() } catch (ignored: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        if (socket.isClosed) break
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            currentState = ServerState.Error("Failed to start server on port $port: ${e.message}")
            false
        }
    }

    private fun updateRunningState() {
        if (currentState is ServerState.Running) {
            currentState = ServerState.Running(
                ipAddress = boundIp,
                port = activePort,
                activeConnections = activeConnections.get(),
                startTime = startTimeMillis
            )
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
            activeConnections.set(0)
            currentState = ServerState.Offline
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val inputStream = BufferedInputStream(socket.getInputStream())
        val outputStream = BufferedOutputStream(socket.getOutputStream())
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"

        // 1. Read HTTP request line and headers
        val headerLines = mutableListOf<String>()
        val headerSb = StringBuilder()
        var lastChar = -1
        while (true) {
            val c = inputStream.read()
            if (c == -1) break
            headerSb.append(c.toChar())
            if (c == '\n'.code && lastChar == '\r'.code) {
                val line = headerSb.toString().trim()
                if (line.isEmpty()) break
                headerLines.add(line)
                headerSb.clear()
            }
            lastChar = c
        }

        if (headerLines.isEmpty()) return

        val requestLine = headerLines[0].split(" ")
        if (requestLine.size < 2) return

        val method = requestLine[0].uppercase()
        val rawUri = requestLine[1]
        val path = if (rawUri.contains("?")) rawUri.substringBefore("?") else rawUri
        val queryString = if (rawUri.contains("?")) rawUri.substringAfter("?") else ""
        val queryParams = parseQueryParams(queryString)

        val headers = mutableMapOf<String, String>()
        for (i in 1 until headerLines.size) {
            val line = headerLines[i]
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val k = line.substring(0, colonIdx).trim().lowercase()
                val v = line.substring(colonIdx + 1).trim()
                headers[k] = v
            }
        }

        // Handle CORS preflight
        if (method == "OPTIONS") {
            sendCorsOptionsResponse(outputStream)
            return
        }

        // 2. Dispatch routes
        when {
            // Main Web Client
            path == "/" || path == "/index.html" || path.startsWith("/share") -> {
                val html = LocalShareWebHtml.getIndexHtml(context)
                val bytes = html.toByteArray(Charsets.UTF_8)
                sendResponse(outputStream, 200, "OK", "text/html; charset=utf-8", bytes.size.toLong())
                outputStream.write(bytes)
                outputStream.flush()
                repository.logRequest(clientIp, method, path, 200, bytes.size.toLong(), "REQUEST")
            }

            // API: List Public Shares
            path == "/api/shares" && method == "GET" -> {
                handleGetShares(outputStream, clientIp)
            }

            // API: List Files for Share
            path.startsWith("/api/shares/") && path.endsWith("/files") && method == "GET" -> {
                val shareId = path.removePrefix("/api/shares/").removeSuffix("/files").trim('/')
                handleGetShareFiles(shareId, headers, outputStream, clientIp)
            }

            // API: Single File Preview (Inline)
            path.startsWith("/api/shares/") && path.endsWith("/preview") && method == "GET" -> {
                val shareId = path.removePrefix("/api/shares/").removeSuffix("/preview").trim('/')
                val itemId = queryParams["id"]
                handleStreamFile(shareId, itemId, headers, outputStream, clientIp, isDownload = false)
            }

            // API: Single File Download (Attachment)
            path.startsWith("/api/shares/") && path.endsWith("/download") && method == "GET" -> {
                val shareId = path.removePrefix("/api/shares/").removeSuffix("/download").trim('/')
                val itemId = queryParams["id"]
                handleStreamFile(shareId, itemId, headers, outputStream, clientIp, isDownload = true)
            }

            // API: Download Multiple Files as ZIP
            path.startsWith("/api/shares/") && path.endsWith("/download-zip") && method == "GET" -> {
                val shareId = path.removePrefix("/api/shares/").removeSuffix("/download-zip").trim('/')
                val fileIds = queryParams["files"]?.split(",") ?: emptyList()
                handleDownloadZip(shareId, fileIds, headers, outputStream, clientIp)
            }

            // API: File Upload
            path.startsWith("/api/shares/") && path.endsWith("/upload") && method == "POST" -> {
                val shareId = path.removePrefix("/api/shares/").removeSuffix("/upload").trim('/')
                val filename = queryParams["filename"] ?: headers["x-file-name"] ?: "uploaded_file_${System.currentTimeMillis()}"
                handleFileUpload(shareId, filename, headers, inputStream, outputStream, clientIp)
            }

            // API: Password Authentication for Protected Share
            path.startsWith("/api/shares/") && path.endsWith("/auth") && method == "POST" -> {
                val shareId = path.removePrefix("/api/shares/").removeSuffix("/auth").trim('/')
                handleShareAuth(shareId, headers, inputStream, outputStream, clientIp)
            }

            // Status ping
            path == "/api/status" -> {
                val statusJson = JSONObject().apply {
                    put("status", "online")
                    put("uptimeSeconds", (System.currentTimeMillis() - startTimeMillis) / 1000)
                    put("activeConnections", activeConnections.get())
                }.toString()
                val bytes = statusJson.toByteArray(Charsets.UTF_8)
                sendResponse(outputStream, 200, "OK", "application/json", bytes.size.toLong())
                outputStream.write(bytes)
                outputStream.flush()
            }

            else -> {
                sendError(outputStream, 404, "Not Found", clientIp, path)
            }
        }
    }

    private suspend fun handleGetShares(output: OutputStream, clientIp: String) {
        val sharesData = withContext(Dispatchers.IO) {
            val res = JSONArray()
            val shareEntities = repository.getEnabledSharesSync()
            for (s in shareEntities) {
                val isExpired = s.expiresAt != null && s.expiresAt < System.currentTimeMillis()
                if (!isExpired) {
                    res.put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("description", s.description)
                        put("allowUploads", s.allowUploads)
                        put("isPasswordProtected", s.isPasswordProtected)
                    })
                }
            }
            res
        }

        val jsonBytes = sharesData.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "OK", "application/json", jsonBytes.size.toLong())
        output.write(jsonBytes)
        output.flush()
        repository.logRequest(clientIp, "GET", "/api/shares", 200, jsonBytes.size.toLong(), "REQUEST")
    }

    private suspend fun handleGetShareFiles(
        shareId: String,
        headers: Map<String, String>,
        output: OutputStream,
        clientIp: String
    ) {
        val share = repository.getShareById(shareId)
        if (share == null || !share.isEnabled) {
            sendError(output, 404, "Share not found or disabled", clientIp, "/api/shares/$shareId/files")
            return
        }

        // Check password auth if protected
        if (share.isPasswordProtected && !isAuthorized(shareId, headers)) {
            sendError(output, 401, "Authentication required for this share", clientIp, "/api/shares/$shareId/files")
            return
        }

        val items = repository.getItemsForShare(shareId)
        val responseJson = JSONObject().apply {
            put("share", JSONObject().apply {
                put("id", share.id)
                put("name", share.name)
                put("description", share.description)
                put("allowUploads", share.allowUploads)
                put("isPasswordProtected", share.isPasswordProtected)
            })
            val itemsArray = JSONArray()
            for (it in items) {
                val f = File(it.absolutePath)
                if (f.exists()) {
                    itemsArray.put(JSONObject().apply {
                        put("id", it.id)
                        put("displayName", it.displayName)
                        put("isDirectory", it.isDirectory)
                        put("sizeBytes", if (it.isDirectory) it.sizeBytes else f.length())
                        put("mimeType", it.mimeType)
                        put("addedAt", it.addedAt)
                    })
                }
            }
            put("items", itemsArray)
        }

        val bytes = responseJson.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "OK", "application/json", bytes.size.toLong())
        output.write(bytes)
        output.flush()
        repository.logRequest(clientIp, "GET", "/api/shares/$shareId/files", 200, bytes.size.toLong(), "REQUEST")
    }

    private suspend fun handleStreamFile(
        shareId: String,
        itemId: String?,
        headers: Map<String, String>,
        output: OutputStream,
        clientIp: String,
        isDownload: Boolean
    ) {
        if (itemId.isNullOrBlank()) {
            sendError(output, 400, "Missing file ID", clientIp, "/api/shares/$shareId/stream")
            return
        }

        val share = repository.getShareById(shareId)
        if (share == null || !share.isEnabled) {
            sendError(output, 404, "Share not found", clientIp, "/api/shares/$shareId/stream")
            return
        }

        if (share.isPasswordProtected && !isAuthorized(shareId, headers)) {
            sendError(output, 401, "Authentication required", clientIp, "/api/shares/$shareId/stream")
            return
        }

        val items = repository.getItemsForShare(shareId)
        val item = items.firstOrNull { it.id == itemId }
        if (item == null) {
            sendError(output, 404, "File not found in share", clientIp, "/api/shares/$shareId/stream")
            return
        }

        val file = File(item.absolutePath)
        // SECURITY: Verify file exists and is within designated storage or item root
        if (!file.exists() || !file.isFile) {
            sendError(output, 404, "File not accessible", clientIp, "/api/shares/$shareId/stream")
            return
        }

        val fileLength = file.length()
        val mimeType = item.mimeType
        val disposition = if (isDownload) {
            "attachment; filename=\"${sanitizeHeaderFilename(item.displayName)}\""
        } else {
            "inline; filename=\"${sanitizeHeaderFilename(item.displayName)}\""
        }

        val rangeHeader = headers["range"]
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            // Range Request Handling (206 Partial Content)
            val rangeValue = rangeHeader.substring(6).trim()
            val parts = rangeValue.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
                parts[1].toLongOrNull() ?: (fileLength - 1)
            } else {
                fileLength - 1
            }

            if (start > end || start >= fileLength) {
                // 416 Range Not Satisfiable
                val resp = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$fileLength\r\n\r\n"
                output.write(resp.toByteArray())
                output.flush()
                return
            }

            val clampedEnd = end.coerceAtMost(fileLength - 1)
            val contentLength = clampedEnd - start + 1

            val headerStr = StringBuilder()
                .append("HTTP/1.1 206 Partial Content\r\n")
                .append("Content-Type: ").append(mimeType).append("\r\n")
                .append("Content-Length: ").append(contentLength).append("\r\n")
                .append("Content-Range: bytes ").append(start).append("-").append(clampedEnd).append("/").append(fileLength).append("\r\n")
                .append("Content-Disposition: ").append(disposition).append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("Connection: keep-alive\r\n\r\n")
                .toString()

            output.write(headerStr.toByteArray())

            var transferred = 0L
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(64 * 1024)
                var bytesToRead = contentLength
                while (bytesToRead > 0) {
                    val read = raf.read(buffer, 0, bytesToRead.coerceAtMost(buffer.size.toLong()).toInt())
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesToRead -= read
                    transferred += read
                }
            }
            output.flush()
            totalBytesTransferred.addAndGet(transferred)
            repository.logRequest(clientIp, "GET", "/api/shares/$shareId/preview", 206, transferred, "STREAM")
        } else {
            // Full File Stream (200 OK)
            val headerStr = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ").append(mimeType).append("\r\n")
                .append("Content-Length: ").append(fileLength).append("\r\n")
                .append("Content-Disposition: ").append(disposition).append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("Connection: keep-alive\r\n\r\n")
                .toString()

            output.write(headerStr.toByteArray())

            var transferred = 0L
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    transferred += read
                }
            }
            output.flush()
            totalBytesTransferred.addAndGet(transferred)
            repository.logRequest(
                clientIp, "GET",
                if (isDownload) "/api/shares/$shareId/download" else "/api/shares/$shareId/preview",
                200, transferred,
                if (isDownload) "DOWNLOAD" else "PREVIEW"
            )
        }
    }

    private suspend fun handleDownloadZip(
        shareId: String,
        fileIds: List<String>,
        headers: Map<String, String>,
        output: OutputStream,
        clientIp: String
    ) {
        val share = repository.getShareById(shareId)
        if (share == null || !share.isEnabled) {
            sendError(output, 404, "Share not found", clientIp, "/api/shares/$shareId/download-zip")
            return
        }

        if (share.isPasswordProtected && !isAuthorized(shareId, headers)) {
            sendError(output, 401, "Authentication required", clientIp, "/api/shares/$shareId/download-zip")
            return
        }

        val allItems = repository.getItemsForShare(shareId)
        val selectedItems = if (fileIds.isEmpty()) allItems else allItems.filter { fileIds.contains(it.id) }

        val zipName = "${sanitizeHeaderFilename(share.name)}_bundle.zip"
        val headerStr = StringBuilder()
            .append("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: application/zip\r\n")
            .append("Content-Disposition: attachment; filename=\"").append(zipName).append("\"\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Transfer-Encoding: chunked\r\n")
            .append("X-Content-Type-Options: nosniff\r\n\r\n")
            .toString()

        output.write(headerStr.toByteArray())

        // Stream zip using ChunkedOutputStream wrapper
        var transferred = 0L
        val chunkedOut = ChunkedOutputStream(output)
        ZipOutputStream(chunkedOut).use { zos ->
            val addedNames = mutableSetOf<String>()
            for (item in selectedItems) {
                val f = File(item.absolutePath)
                if (f.exists() && f.isFile) {
                    var entryName = item.displayName
                    var counter = 1
                    while (addedNames.contains(entryName)) {
                        entryName = "${item.displayName.substringBeforeLast('.')}_$counter.${item.displayName.substringAfterLast('.')}"
                        counter++
                    }
                    addedNames.add(entryName)

                    val entry = ZipEntry(entryName)
                    entry.time = f.lastModified()
                    entry.size = f.length()
                    zos.putNextEntry(entry)

                    FileInputStream(f).use { fis ->
                        val buffer = ByteArray(32 * 1024)
                        var r: Int
                        while (fis.read(buffer).also { r = it } != -1) {
                            zos.write(buffer, 0, r)
                            transferred += r
                        }
                    }
                    zos.closeEntry()
                }
            }
            zos.finish()
        }
        chunkedOut.finish()
        output.flush()

        totalBytesTransferred.addAndGet(transferred)
        repository.logRequest(clientIp, "GET", "/api/shares/$shareId/download-zip", 200, transferred, "DOWNLOAD_ZIP")
    }

    private suspend fun handleFileUpload(
        shareId: String,
        rawFilename: String,
        headers: Map<String, String>,
        input: InputStream,
        output: OutputStream,
        clientIp: String
    ) {
        val share = repository.getShareById(shareId)
        if (share == null || !share.isEnabled) {
            sendError(output, 404, "Share not found", clientIp, "/api/shares/$shareId/upload")
            return
        }

        if (!share.allowUploads) {
            sendError(output, 403, "Uploads are disabled for this share", clientIp, "/api/shares/$shareId/upload")
            return
        }

        if (share.isPasswordProtected && !isAuthorized(shareId, headers)) {
            sendError(output, 401, "Authentication required", clientIp, "/api/shares/$shareId/upload")
            return
        }

        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
        val maxBytes = share.maxUploadSizeMb * 1024 * 1024
        if (contentLength > maxBytes) {
            sendError(output, 413, "File exceeds maximum upload size of ${share.maxUploadSizeMb} MB", clientIp, "/api/shares/$shareId/upload")
            return
        }

        // Clean filename to prevent path traversal
        val safeName = sanitizeFilename(URLDecoder.decode(rawFilename, "UTF-8"))
        val uploadDir = repository.getShareUploadDir(shareId)
        val targetFile = File(uploadDir, safeName)

        // Avoid accidental overwrites with version suffix
        var destFile = targetFile
        var counter = 1
        val baseName = safeName.substringBeforeLast('.')
        val ext = safeName.substringAfterLast('.', "")
        while (destFile.exists()) {
            destFile = if (ext.isNotEmpty()) {
                File(uploadDir, "${baseName}_$counter.$ext")
            } else {
                File(uploadDir, "${baseName}_$counter")
            }
            counter++
        }

        // Stream body to file
        var bytesWritten = 0L
        FileOutputStream(destFile).use { fos ->
            val buffer = ByteArray(32 * 1024)
            var remaining = contentLength
            while (remaining > 0) {
                val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                fos.write(buffer, 0, read)
                bytesWritten += read
                remaining -= read
            }
        }

        // Register item in database
        val newItem = repository.addItemToShare(
            shareId = shareId,
            displayName = destFile.name,
            file = destFile,
            relativeSubPath = "uploads"
        )

        val resJson = JSONObject().apply {
            put("success", true)
            put("id", newItem.id)
            put("displayName", newItem.displayName)
            put("sizeBytes", destFile.length())
        }.toString()

        val bytes = resJson.toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "OK", "application/json", bytes.size.toLong())
        output.write(bytes)
        output.flush()

        repository.logRequest(clientIp, "POST", "/api/shares/$shareId/upload", 200, bytesWritten, "UPLOAD", "Uploaded: ${destFile.name}")
    }

    private suspend fun handleShareAuth(
        shareId: String,
        headers: Map<String, String>,
        input: InputStream,
        output: OutputStream,
        clientIp: String
    ) {
        val share = repository.getShareById(shareId)
        if (share == null || !share.isEnabled) {
            sendError(output, 404, "Share not found", clientIp, "/api/shares/$shareId/auth")
            return
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyBytes = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val r = input.read(bodyBytes, totalRead, contentLength - totalRead)
            if (r == -1) break
            totalRead += r
        }

        val bodyString = String(bodyBytes, Charsets.UTF_8)
        val json = try { JSONObject(bodyString) } catch (e: Exception) { JSONObject() }
        val password = json.optString("password", "")

        val valid = repository.verifyPassword(share, password)
        if (valid) {
            val token = UUID.randomUUID().toString()
            // 24 hour expiry
            authTokens[token] = Pair(shareId, System.currentTimeMillis() + 86400000L)

            val res = JSONObject().apply {
                put("success", true)
                put("token", token)
            }.toString()

            val bytes = res.toByteArray(Charsets.UTF_8)
            sendResponse(output, 200, "OK", "application/json", bytes.size.toLong())
            output.write(bytes)
            output.flush()
            repository.logRequest(clientIp, "POST", "/api/shares/$shareId/auth", 200, bytes.size.toLong(), "AUTH_SUCCESS")
        } else {
            sendError(output, 401, "Invalid password", clientIp, "/api/shares/$shareId/auth")
            repository.logRequest(clientIp, "POST", "/api/shares/$shareId/auth", 401, 0L, "AUTH_FAIL")
        }
    }

    private fun isAuthorized(shareId: String, headers: Map<String, String>): Boolean {
        val authHeader = headers["authorization"] ?: ""
        val token = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim()
        } else {
            headers["x-share-token"] ?: ""
        }

        if (token.isEmpty()) return false
        val entry = authTokens[token] ?: return false
        if (entry.first != shareId) return false
        if (entry.second < System.currentTimeMillis()) {
            authTokens.remove(token)
            return false
        }
        return true
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        contentLength: Long
    ) {
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: $contentLength\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
    }

    private fun sendCorsOptionsResponse(output: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Authorization, Content-Type, Range, X-File-Name, X-Share-Token\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private suspend fun sendError(
        output: OutputStream,
        code: Int,
        message: String,
        clientIp: String,
        path: String
    ) {
        val json = JSONObject().apply {
            put("error", true)
            put("statusCode", code)
            put("message", message)
        }.toString()
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code Error\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
        repository.logRequest(clientIp, "ERR", path, code, bytes.size.toLong(), "ERROR", message)
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isEmpty()) return map
        val pairs = queryString.split("&")
        for (p in pairs) {
            val idx = p.indexOf('=')
            if (idx > 0) {
                val k = URLDecoder.decode(p.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(p.substring(idx + 1), "UTF-8")
                map[k] = v
            }
        }
        return map
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("/", "_")
            .replace("\\", "_")
            .replace("..", "_")
            .replace("\u0000", "")
            .trim()
            .take(120)
    }

    private fun sanitizeHeaderFilename(name: String): String {
        return name.replace("\"", "").replace("\r", "").replace("\n", "").trim()
    }

    // Helper output stream for HTTP chunked transfer
    private class ChunkedOutputStream(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len == 0) return
            val chunkHeader = Integer.toHexString(len) + "\r\n"
            out.write(chunkHeader.toByteArray())
            out.write(b, off, len)
            out.write("\r\n".toByteArray())
        }

        fun finish() {
            out.write("0\r\n\r\n".toByteArray())
            out.flush()
        }
    }
}
