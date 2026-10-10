package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LocalShareApplication
import com.example.data.model.ServerLogEntity
import com.example.data.model.ShareEntity
import com.example.data.model.SharedItemEntity
import com.example.network.NetworkAddressInfo
import com.example.network.NetworkUtils
import com.example.server.ServerState
import com.example.service.LocalShareServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AppNavigationTab {
    DASHBOARD,
    SHARES,
    FILES,
    MONITOR,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LocalShareApplication
    private val repository = app.repository
    private val httpServer = app.httpServer

    // Server State
    private val _serverState = MutableStateFlow<ServerState>(ServerState.Offline)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    // Network & Config
    private val _availableAddresses = MutableStateFlow<List<NetworkAddressInfo>>(emptyList())
    val availableAddresses: StateFlow<List<NetworkAddressInfo>> = _availableAddresses.asStateFlow()

    private val _selectedIp = MutableStateFlow("127.0.0.1")
    val selectedIp: StateFlow<String> = _selectedIp.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _runInBackground = MutableStateFlow(true)
    val runInBackground: StateFlow<Boolean> = _runInBackground.asStateFlow()

    // Active Tab
    private val _currentTab = MutableStateFlow(AppNavigationTab.DASHBOARD)
    val currentTab: StateFlow<AppNavigationTab> = _currentTab.asStateFlow()

    // QR Code Dialog state
    private val _showQrDialog = MutableStateFlow(false)
    val showQrDialog: StateFlow<Boolean> = _showQrDialog.asStateFlow()

    private val _qrUrl = MutableStateFlow("")
    val qrUrl: StateFlow<String> = _qrUrl.asStateFlow()

    // Active Share selection for File Explorer view
    private val _selectedShareForFiles = MutableStateFlow<String?>(null)
    val selectedShareForFiles: StateFlow<String?> = _selectedShareForFiles.asStateFlow()

    // Database Flows
    val shares: StateFlow<List<ShareEntity>> = repository.allShares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ServerLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshNetworkAddresses()
        httpServer.onStateChanged = { state ->
            _serverState.value = state
        }
    }

    fun refreshNetworkAddresses() {
        val addresses = NetworkUtils.getAvailableNetworkAddresses()
        _availableAddresses.value = addresses
        val preferred = NetworkUtils.getPreferredIpAddress()
        if (_selectedIp.value == "127.0.0.1" || _availableAddresses.value.none { it.ipAddress == _selectedIp.value }) {
            _selectedIp.value = preferred
        }
    }

    fun setNavigationTab(tab: AppNavigationTab) {
        _currentTab.value = tab
    }

    fun setSelectedIp(ip: String) {
        _selectedIp.value = ip
    }

    fun setServerPort(port: Int) {
        _serverPort.value = port.coerceIn(1024, 65535)
    }

    fun setRunInBackground(enabled: Boolean) {
        _runInBackground.value = enabled
    }

    fun toggleServer() {
        if (_serverState.value is ServerState.Running) {
            stopServer()
        } else {
            startServer()
        }
    }

    fun startServer() {
        val ip = _selectedIp.value
        val port = _serverPort.value
        val started = httpServer.start(port = port, preferredIp = ip)
        if (started && _runInBackground.value) {
            val url = NetworkUtils.formatShareUrl(ip, port)
            LocalShareServerService.startService(getApplication(), url)
        }
    }

    fun stopServer() {
        httpServer.stop()
        LocalShareServerService.stopService(getApplication())
    }

    fun getActiveUrl(shareId: String? = null): String {
        return NetworkUtils.formatShareUrl(_selectedIp.value, _serverPort.value, shareId)
    }

    fun copyToClipboard(text: String, label: String = "LocalShare URL") {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun showQr(url: String) {
        _qrUrl.value = url
        _showQrDialog.value = true
    }

    fun dismissQrDialog() {
        _showQrDialog.value = false
    }

    fun selectShareForFiles(shareId: String?) {
        _selectedShareForFiles.value = shareId
    }

    fun createShare(
        name: String,
        description: String,
        allowUploads: Boolean,
        password: String?,
        expiresInHours: Int?,
        maxUploadMb: Long
    ) {
        viewModelScope.launch {
            val expiresAt = if (expiresInHours != null && expiresInHours > 0) {
                System.currentTimeMillis() + (expiresInHours * 3600000L)
            } else null

            repository.createShare(
                name = name,
                description = description,
                allowUploads = allowUploads,
                password = password,
                expiresAt = expiresAt,
                maxUploadSizeMb = maxUploadMb
            )
        }
    }

    fun toggleShareStatus(shareId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleShareStatus(shareId, isEnabled)
        }
    }

    fun deleteShare(shareId: String) {
        viewModelScope.launch {
            repository.deleteShare(shareId)
            if (_selectedShareForFiles.value == shareId) {
                _selectedShareForFiles.value = null
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun getItemsForShareFlow(shareId: String): StateFlow<List<SharedItemEntity>> {
        return repository.getItemsForShareFlow(shareId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            repository.removeItem(itemId)
        }
    }

    fun importFilesFromUris(shareId: String, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val shareContentDir = File(context.filesDir, "shares/$shareId/content")
            if (!shareContentDir.exists()) shareContentDir.mkdirs()

            for (uri in uris) {
                try {
                    val displayName = getFileNameFromUri(context, uri) ?: "file_${System.currentTimeMillis()}"
                    val destFile = File(shareContentDir, displayName)

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    repository.addItemToShare(
                        shareId = shareId,
                        displayName = destFile.name,
                        file = destFile,
                        uriString = uri.toString()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun createSampleDocument(shareId: String, title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val shareContentDir = File(context.filesDir, "shares/$shareId/content")
            if (!shareContentDir.exists()) shareContentDir.mkdirs()

            val fileName = if (title.contains(".")) title else "$title.txt"
            val file = File(shareContentDir, fileName)
            file.writeText(content)

            repository.addItemToShare(
                shareId = shareId,
                displayName = file.name,
                file = file
            )
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }
}
