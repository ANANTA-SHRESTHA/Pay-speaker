package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ads.AdMobManager
import com.example.domain.StatementExportResult
import com.example.domain.StatementExporter
import com.example.service.DailySummaryNotifier
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutFromFirebase
import com.example.ui.components.AdMobBannerBar
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SoundboxRoute
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SoundboxScreen
import com.example.ui.theme.PaySpeakerTheme
import com.example.ui.viewmodel.PaySpeakerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PaySpeakerApp
        val openHistoryFromNotification = intent?.getBooleanExtra(
            DailySummaryNotifier.EXTRA_OPEN_HISTORY,
            false,
        ) == true
        setContent {
            PaySpeakerTheme {
                AppNavigation(
                    app = app,
                    openHistoryOnStart = openHistoryFromNotification,
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    app: PaySpeakerApp,
    openHistoryOnStart: Boolean = false,
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        key(uid) {
            val viewModel: PaySpeakerViewModel = viewModel(
                key = uid,
                factory = PaySpeakerViewModel.Factory(
                    application = app,
                    container = app.container,
                    currentUserId = uid,
                    userEmail = user.email,
                    userDisplayName = user.displayName,
                ),
            )
            PaySpeakerRoot(
                viewModel = viewModel,
                adMobManager = app.container.adMobManager,
                openHistoryOnStart = openHistoryOnStart,
                onSignOut = {
                    viewModel.stopSpeaking()
                    signOutFromFirebase(
                        credentialManager = credentialManager,
                        scope = scope,
                    )
                },
            )
        }
    }
}

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val matches: (androidx.navigation.NavDestination?) -> Boolean,
)

@Composable
fun PaySpeakerRoot(
    viewModel: PaySpeakerViewModel,
    adMobManager: AdMobManager,
    openHistoryOnStart: Boolean = false,
    onSignOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bannerRetryTrigger by adMobManager.bannerRetryCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hostActivity = context as? android.app.Activity
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var lastHandledExportId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.lastExportResult) {
        val exported = uiState.lastExportResult ?: return@LaunchedEffect
        val key = "${exported.fileName}:${exported.savedLocationLabel}"
        if (lastHandledExportId != key) {
            lastHandledExportId = key
            adMobManager.maybeShowInterstitial(
                activity = hostActivity,
                isSpeaking = uiState.isSpeaking,
            )
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissions() },
    )

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissions() },
    )

    var smsPromptAttempted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val smsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!smsGranted && !smsPromptAttempted) {
            smsPromptAttempted = true
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.refreshPermissions()
            if (granted) {
                viewModel.startRecordingOwnVoice()
            } else {
                viewModel.setVoiceRecorderError(context.getString(R.string.settings_mic_denied))
            }
        },
    )

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importCustomVoiceUri(uri)
            }
        },
    )

    val requestStartRecording = {
        if (uiState.permissions.recordAudioGranted) {
            viewModel.startRecordingOwnVoice()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val openListenerSettings = {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val requestPostNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val requestSmsPermission = {
        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
    }

    var pendingIncludeFiltered by rememberSaveable { mutableStateOf(true) }

    val createPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.exportStatementPdf(
                    targetUri = uri,
                    includeFilteredOnly = pendingIncludeFiltered,
                )
            }
        },
    )

    val createCsvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.exportStatementCsv(
                    targetUri = uri,
                    includeFilteredOnly = pendingIncludeFiltered,
                )
            }
        },
    )

    val triggerPdfExport: (Boolean) -> Unit = { filteredOnly ->
        pendingIncludeFiltered = filteredOnly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            viewModel.exportStatementPdf(targetUri = null, includeFilteredOnly = filteredOnly)
        } else {
            createPdfDocumentLauncher.launch(StatementExporter.buildFileName("pdf"))
        }
    }

    val triggerCsvExport: (Boolean) -> Unit = { filteredOnly ->
        pendingIncludeFiltered = filteredOnly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            viewModel.exportStatementCsv(targetUri = null, includeFilteredOnly = filteredOnly)
        } else {
            createCsvDocumentLauncher.launch(StatementExporter.buildFileName("csv"))
        }
    }

    val openExportedStatement: (StatementExportResult) -> Unit = { exported ->
        runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(exported.contentUri, exported.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, exported.fileName))
        }
    }

    val shareExportedStatement: (StatementExportResult) -> Unit = { exported ->
        runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = exported.mimeType
                putExtra(Intent.EXTRA_STREAM, exported.contentUri)
                putExtra(Intent.EXTRA_SUBJECT, exported.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Account Statement"))
        }
    }

    val installTtsData = {
        runCatching {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        Unit
    }

    val destinations = listOf(
        TopLevelDestination(
            route = SoundboxRoute,
            labelRes = R.string.nav_soundbox,
            selectedIcon = Icons.Rounded.Speaker,
            unselectedIcon = Icons.Outlined.Speaker,
            testTag = "nav_soundbox",
            matches = { it?.hasRoute<SoundboxRoute>() == true },
        ),
        TopLevelDestination(
            route = HistoryRoute,
            labelRes = R.string.nav_history,
            selectedIcon = Icons.Rounded.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "nav_history",
            matches = { it?.hasRoute<HistoryRoute>() == true },
        ),
        TopLevelDestination(
            route = SettingsRoute,
            labelRes = R.string.nav_settings,
            selectedIcon = Icons.Rounded.Tune,
            unselectedIcon = Icons.Outlined.Tune,
            testTag = "nav_settings",
            matches = { it?.hasRoute<SettingsRoute>() == true },
        ),
    )

    val navigateTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AdMobBannerBar(
                        adUnitId = adMobManager.bannerAdUnitId,
                        isSdkInitialized = uiState.adSdkInitialized,
                        retryTrigger = bannerRetryTrigger,
                        onBannerLoading = adMobManager::reportBannerLoading,
                        onBannerLoaded = adMobManager::reportBannerLoaded,
                        onBannerFailed = adMobManager::reportBannerFailed,
                    )
                    if (!useNavigationRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            destinations.forEach { dest ->
                                val selected = dest.matches(currentDestination)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes),
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        destinations.forEach { dest ->
                            val selected = dest.matches(currentDestination)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    LaunchedEffect(openHistoryOnStart) {
                        if (openHistoryOnStart) {
                            navigateTopLevel(HistoryRoute)
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = SoundboxRoute,
                    ) {
                        composable<SoundboxRoute> {
                            SoundboxScreen(
                                uiState = uiState,
                                onToggleSpeaker = viewModel::toggleSpeakerEnabled,
                                onStopSpeaking = viewModel::stopSpeaking,
                                onGrantListenerAccess = openListenerSettings,
                                onRequestPostNotifications = requestPostNotifications,
                                onRequestSmsPermission = requestSmsPermission,
                                onSimulatePayment = viewModel::simulatePayment,
                                onReplayAlert = viewModel::replayAlert,
                                onViewAllHistory = { navigateTopLevel(HistoryRoute) },
                                onSelectReadMode = viewModel::setAnnouncementReadMode,
                                onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
                                onDownloadStatementPdf = { triggerPdfExport(false) },
                                onOpenExportedStatement = openExportedStatement,
                                onShareExportedStatement = shareExportedStatement,
                                onDismissExportBanner = viewModel::dismissStatementExportBanner,
                                onDismissCloudError = viewModel::dismissCloudError,
                            )
                        }
                        composable<HistoryRoute> {
                            HistoryScreen(
                                uiState = uiState,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onFilterChannelSelect = viewModel::selectFilterChannel,
                                onDateFilterSelect = viewModel::selectDateFilter,
                                onSortOptionSelect = viewModel::selectSortOption,
                                onResetFilters = viewModel::resetHistoryFilters,
                                onReplayAlert = viewModel::replayAlert,
                                onDeleteAlert = viewModel::deleteAlert,
                                onClearHistory = viewModel::clearHistory,
                                onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
                                onDownloadStatementPdf = { triggerPdfExport(true) },
                                onDownloadStatementCsv = { triggerCsvExport(true) },
                                onOpenExportedStatement = openExportedStatement,
                                onShareExportedStatement = shareExportedStatement,
                                onDismissExportBanner = viewModel::dismissStatementExportBanner,
                                onDismissCloudError = viewModel::dismissCloudError,
                            )
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                uiState = uiState,
                                onMerchantNameChange = viewModel::setMerchantName,
                                onLanguageChange = viewModel::setLanguage,
                                onSelectedVoiceChange = viewModel::setSelectedVoiceName,
                                onVoiceSourceModeChange = viewModel::setVoiceSourceMode,
                                onSpeechRateChange = viewModel::setSpeechRate,
                                onSpeechPitchChange = viewModel::setSpeechPitch,
                                onApplyVoicePreset = viewModel::applyVoicePreset,
                                onPlayChimeChange = viewModel::setPlayChime,
                                onBoostVolumeChange = viewModel::setBoostVolume,
                                onAnnounceSenderChange = viewModel::setAnnounceSender,
                                onAnnounceSourceChange = viewModel::setAnnounceSource,
                                onRepeatTwiceChange = viewModel::setRepeatTwice,
                                onForegroundGuardChange = viewModel::toggleForegroundGuard,
                                onToggleChannel = viewModel::toggleChannel,
                                onTestVoice = viewModel::testCurrentVoice,
                                onInstallTtsData = installTtsData,
                                onStartRecordingOwnVoice = requestStartRecording,
                                onStopRecordingOwnVoice = viewModel::stopRecordingOwnVoice,
                                onImportCustomAudio = { importAudioLauncher.launch(arrayOf("audio/*")) },
                                onPreviewOwnVoice = viewModel::previewOwnVoice,
                                onDeleteOwnVoice = viewModel::deleteOwnVoice,
                                onSelectReadMode = viewModel::setAnnouncementReadMode,
                                onDailySummaryEnabledChange = viewModel::setDailySummaryEnabled,
                                onDailySummaryTimeChange = viewModel::setDailySummaryTime,
                                onSendDailySummaryNow = viewModel::sendDailySummaryNow,
                                onRequestPostNotifications = requestPostNotifications,
                                onRetryBannerAd = viewModel::retryBannerAd,
                                onSignOut = onSignOut,
                            )
                        }
                    }
                }
            }
        }
    }
}
