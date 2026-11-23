package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SoundboxRoute
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SoundboxScreen
import com.example.ui.theme.PaySpeakerTheme
import com.example.ui.viewmodel.PaySpeakerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PaySpeakerApp
        setContent {
            PaySpeakerTheme {
                val viewModel: PaySpeakerViewModel = viewModel(
                    factory = PaySpeakerViewModel.Factory(app, app.container)
                )
                PaySpeakerRoot(viewModel = viewModel)
            }
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
fun PaySpeakerRoot(viewModel: PaySpeakerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissions() },
    )

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
                                onSimulatePayment = viewModel::simulatePayment,
                                onReplayAlert = viewModel::replayAlert,
                                onViewAllHistory = { navigateTopLevel(HistoryRoute) },
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
                            )
                        }
                    }
                }
            }
        }
    }
}
