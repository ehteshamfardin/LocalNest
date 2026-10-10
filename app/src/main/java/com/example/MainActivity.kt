package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.AppNavigationTab
import com.example.ui.MainViewModel
import com.example.ui.components.MacNavigationBar
import com.example.ui.components.MacToolbar
import com.example.ui.components.QrCodeDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FileExplorerScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SharesScreen
import com.example.ui.theme.LocalShareTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocalShareTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val serverState by viewModel.serverState.collectAsState()
    val selectedIp by viewModel.selectedIp.collectAsState()
    val availableAddresses by viewModel.availableAddresses.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val runInBackground by viewModel.runInBackground.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val shares by viewModel.shares.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()
    val showQrDialog by viewModel.showQrDialog.collectAsState()
    val qrUrl by viewModel.qrUrl.collectAsState()
    val selectedShareForFiles by viewModel.selectedShareForFiles.collectAsState()

    val activeUrl = viewModel.getActiveUrl()

    // BackHandler: Return to Dashboard if on a sub-tab
    BackHandler(enabled = currentTab != AppNavigationTab.DASHBOARD) {
        viewModel.setNavigationTab(AppNavigationTab.DASHBOARD)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Scaffold(
            topBar = {
                MacToolbar(
                    serverState = serverState,
                    activeUrl = activeUrl,
                    onToggleServer = { viewModel.toggleServer() },
                    onCopyUrl = { viewModel.copyToClipboard(it) },
                    onShowQr = { viewModel.showQr(it) }
                )
            },
            bottomBar = {
                MacNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.setNavigationTab(it) }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    AppNavigationTab.DASHBOARD -> {
                        DashboardScreen(
                            serverState = serverState,
                            selectedIp = selectedIp,
                            availableAddresses = availableAddresses,
                            serverPort = serverPort,
                            shares = shares,
                            recentLogs = recentLogs,
                            onToggleServer = { viewModel.toggleServer() },
                            onSelectIp = { viewModel.setSelectedIp(it) },
                            onRefreshAddresses = { viewModel.refreshNetworkAddresses() },
                            onCopyUrl = { viewModel.copyToClipboard(it) },
                            onShowQr = { viewModel.showQr(it) },
                            onNavigateTab = { viewModel.setNavigationTab(it) },
                            onOpenNewShareDialog = { viewModel.setNavigationTab(AppNavigationTab.SHARES) }
                        )
                    }

                    AppNavigationTab.SHARES -> {
                        SharesScreen(
                            shares = shares,
                            onToggleShareStatus = { id, enabled -> viewModel.toggleShareStatus(id, enabled) },
                            onDeleteShare = { viewModel.deleteShare(it) },
                            onSelectShareForFiles = { shareId ->
                                viewModel.selectShareForFiles(shareId)
                                viewModel.setNavigationTab(AppNavigationTab.FILES)
                            },
                            onCopyShareLink = { shareId ->
                                val shareUrl = viewModel.getActiveUrl(shareId)
                                viewModel.copyToClipboard(shareUrl, "Share Link")
                            },
                            onShowQr = { shareId ->
                                val shareUrl = viewModel.getActiveUrl(shareId)
                                viewModel.showQr(shareUrl)
                            },
                            onCreateShare = { name, desc, uploads, pwd, exp, maxUpload ->
                                viewModel.createShare(name, desc, uploads, pwd, exp, maxUpload)
                            }
                        )
                    }

                    AppNavigationTab.FILES -> {
                        FileExplorerScreen(
                            viewModel = viewModel,
                            shares = shares,
                            selectedShareId = selectedShareForFiles,
                            onSelectShare = { viewModel.selectShareForFiles(it) }
                        )
                    }

                    AppNavigationTab.MONITOR -> {
                        MonitorScreen(
                            logs = recentLogs,
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }

                    AppNavigationTab.SETTINGS -> {
                        SettingsScreen(
                            currentPort = serverPort,
                            runInBackground = runInBackground,
                            availableAddresses = availableAddresses,
                            onSetPort = { viewModel.setServerPort(it) },
                            onSetRunInBackground = { viewModel.setRunInBackground(it) },
                            onRefreshAddresses = { viewModel.refreshNetworkAddresses() }
                        )
                    }
                }
            }
        }
    }

    // QR Code Dialog overlay
    if (showQrDialog) {
        QrCodeDialog(
            url = qrUrl,
            onDismiss = { viewModel.dismissQrDialog() },
            onCopyUrl = { viewModel.copyToClipboard(it) }
        )
    }
}
