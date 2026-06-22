package com.qtv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.qtv.app.config.QtvChannel
import com.qtv.app.config.QtvConfigCatalog
import com.qtv.app.config.QtvConfigLocation
import com.qtv.app.config.QtvConfigPreferences
import com.qtv.app.config.QtvConfigRepository
import com.qtv.app.player.QtvPlayerPane
import com.qtv.app.ui.theme.QTVTheme
import com.qtv.app.updater.QtvUpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveFullscreen()
        setContent {
            QTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TvHomeScreen()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    private fun enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun TvHomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configPreferences = remember(context) { QtvConfigPreferences(context) }
    val configRepository = remember { QtvConfigRepository() }
    var preferredConfigLocation by remember {
        mutableStateOf(configPreferences.resolvePreferredLocation())
    }
    var lastUpdatedAtMillis by rememberSaveable {
        mutableLongStateOf(configPreferences.getLastUpdatedAtMillis() ?: 0L)
    }
    var lastAppUpdateCheckAtMillis by rememberSaveable {
        mutableLongStateOf(configPreferences.getLastAppUpdateCheckAtMillis() ?: 0L)
    }
    var reloadNonce by rememberSaveable { mutableIntStateOf(0) }
    val catalogUiState by produceState<QtvCatalogUiState>(
        initialValue = QtvCatalogUiState(isLoading = true),
        context,
        configPreferences,
        configRepository,
        preferredConfigLocation,
        reloadNonce,
    ) {
        value = try {
            val catalog = withContext(Dispatchers.IO) {
                loadInitialCatalog(
                    context = context,
                    configRepository = configRepository,
                    configPreferences = configPreferences,
                    preferredLocation = preferredConfigLocation,
                )
            }
            val updatedAtMillis = System.currentTimeMillis()
            configPreferences.saveLastUpdatedAtMillis(updatedAtMillis)
            lastUpdatedAtMillis = updatedAtMillis
            QtvCatalogUiState(catalog = catalog)
        } catch (error: Throwable) {
            QtvCatalogUiState(
                errorMessage = error.message ?: "Unknown config load error",
            )
        }
    }

    if (catalogUiState.isLoading) {
        LoadingConfigState(modifier = modifier)
        return
    }

    val configErrorMessage = catalogUiState.errorMessage
    if (configErrorMessage != null) {
        ConfigErrorState(
            message = configErrorMessage,
            modifier = modifier,
        )
        return
    }

    val catalog = catalogUiState.catalog
    if (catalog == null) {
        ConfigErrorState(
            message = "Config catalog was not available",
            modifier = modifier,
        )
        return
    }

    val channels = catalog.channels
    if (channels.isEmpty()) {
        EmptyConfigState(
            sourceSummary = catalog.sourceSummary,
            warningMessage = catalog.warningMessage,
            modifier = modifier,
        )
        return
    }
    val focusRequesters = remember(channels.size) {
        List(channels.size) { FocusRequester() }
    }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showChannelList by rememberSaveable { mutableStateOf(false) }
    var showSettingsPanel by rememberSaveable { mutableStateOf(false) }
    var settingsExternalUrlDraft by rememberSaveable { mutableStateOf("") }
    var settingsUpdateUrlDraft by rememberSaveable { mutableStateOf("") }
    val playerFocusRequester = remember { FocusRequester() }
    val settingsButtonFocusRequester = remember { FocusRequester() }
    val settingsUrlFocusRequester = remember { FocusRequester() }
    val updateUrlFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val updateRepository = remember { QtvUpdateRepository() }
    val selectedChannel = channels[selectedIndex]
    val playerTouchInteraction = remember { MutableInteractionSource() }
    val overlayTouchInteraction = remember { MutableInteractionSource() }
    val drawerTouchInteraction = remember { MutableInteractionSource() }
    var drawerFocusArea by rememberSaveable { mutableStateOf(DrawerFocusArea.ChannelList) }
    var updateUiState by remember { mutableStateOf(QtvUpdateUiState()) }
    var pendingStartupUpdatePrompt by remember { mutableStateOf<QtvUpdateUiState?>(null) }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(channels.size) {
        val resolvedSelectedIndex =
            selectedChannelId
                ?.let { currentId -> channels.indexOfFirst { it.id == currentId } }
                ?.takeIf { it >= 0 }
                ?: selectedIndex.coerceIn(0, channels.lastIndex)
        selectedIndex = resolvedSelectedIndex
        focusedIndex = focusedIndex.coerceIn(0, channels.lastIndex)
        selectedChannelId = channels[resolvedSelectedIndex].id
    }

    LaunchedEffect(selectedChannel.id) {
        selectedChannelId = selectedChannel.id
    }

    BackHandler(enabled = showChannelList) {
        if (showSettingsPanel) {
            showSettingsPanel = false
            settingsButtonFocusRequester.requestFocus()
        } else {
            showChannelList = false
        }
    }

    LaunchedEffect(showChannelList, showSettingsPanel, focusedIndex) {
        if (showChannelList && !showSettingsPanel) {
            focusRequesters.getOrNull(focusedIndex)?.requestFocus()
        } else if (!showChannelList) {
            playerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(showSettingsPanel, preferredConfigLocation) {
        if (showSettingsPanel) {
            settingsExternalUrlDraft = configPreferences.getConfiguredExternalUrl()
            settingsUpdateUrlDraft = configPreferences.getConfiguredUpdateUrl()
            updateUiState = QtvUpdateUiState()
            settingsUrlFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(preferredConfigLocation) {
        val remoteLocation = preferredConfigLocation as? QtvConfigLocation.ExternalUrl ?: run {
            pendingStartupUpdatePrompt = null
            return@LaunchedEffect
        }

        val syncResult =
            withContext(Dispatchers.IO) {
                syncRemoteCatalogIfNeeded(
                    context = context,
                    configRepository = configRepository,
                    configPreferences = configPreferences,
                    remoteLocation = remoteLocation,
                )
            }

        if (syncResult.catalogChanged) {
            reloadNonce += 1
            return@LaunchedEffect
        }

        val startupUpdateState = fetchStartupUpdateState(updateRepository, configPreferences)
        pendingStartupUpdatePrompt = startupUpdateState
        if (startupUpdateState != null) {
            updateUiState = startupUpdateState
        }
    }

    LaunchedEffect(reloadNonce) {
        if (reloadNonce == 0) {
            return@LaunchedEffect
        }
        val startupUpdateState = fetchStartupUpdateState(updateRepository, configPreferences)
        pendingStartupUpdatePrompt = startupUpdateState
        if (startupUpdateState != null) {
            updateUiState = startupUpdateState
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != AndroidKeyEvent.ACTION_UP) {
                    return@onPreviewKeyEvent false
                }

                when {
                    event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (!showChannelList) {
                            showChannelList = true
                            true
                        } else {
                            false
                        }
                    }

                    showChannelList &&
                        !showSettingsPanel &&
                        event.key == Key.DirectionRight &&
                        drawerFocusArea == DrawerFocusArea.ChannelList -> {
                        settingsButtonFocusRequester.requestFocus()
                        drawerFocusArea = DrawerFocusArea.SettingsButton
                        true
                    }

                    showChannelList &&
                        !showSettingsPanel &&
                        event.key == Key.DirectionLeft &&
                        drawerFocusArea == DrawerFocusArea.SettingsButton -> {
                        focusRequesters.getOrNull(focusedIndex)?.requestFocus()
                        drawerFocusArea = DrawerFocusArea.ChannelList
                        true
                    }

                    event.key == Key.Back ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK -> {
                        if (showChannelList) {
                            if (showSettingsPanel) {
                                showSettingsPanel = false
                                settingsButtonFocusRequester.requestFocus()
                            } else {
                                showChannelList = false
                            }
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            },
    ) {
        QtvPlayerPane(
            channelName = selectedChannel.name,
            sources = selectedChannel.sources,
            channelType = selectedChannel.sourceType,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = playerTouchInteraction,
                    indication = null,
                ) {
                    if (!showChannelList) {
                        showChannelList = true
                    }
                },
        )

        if (showChannelList) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .zIndex(1f)
                    .clickable(
                        interactionSource = overlayTouchInteraction,
                        indication = null,
                    ) {
                        showChannelList = false
                    },
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                            .clickable(
                                interactionSource = drawerTouchInteraction,
                                indication = null,
                            ) {}
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                            .focusGroup(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "QTV",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Channels",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = { showSettingsPanel = true },
                                enabled = !showSettingsPanel,
                                modifier = Modifier
                                    .focusRequester(settingsButtonFocusRequester)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            drawerFocusArea = DrawerFocusArea.SettingsButton
                                        }
                                    },
                            ) {
                                Text(text = "Settings")
                            }
                        }
                        if (catalog.warningMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = catalog.warningMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            itemsIndexed(channels) { index, channel ->
                                ChannelRow(
                                    channel = channel,
                                    focused = index == focusedIndex,
                                    selected = index == selectedIndex,
                                    enabled = !showSettingsPanel,
                                    focusRequester = focusRequesters[index],
                                    onFocused = {
                                        focusedIndex = index
                                        drawerFocusArea = DrawerFocusArea.ChannelList
                                    },
                                    onSelected = {
                                        selectedIndex = index
                                        focusedIndex = index
                                        selectedChannelId = channel.id
                                        showChannelList = false
                                    },
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .width(420.dp)
                            .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                            .then(
                                if (showSettingsPanel) {
                                    Modifier.clickable(
                                        interactionSource = drawerTouchInteraction,
                                        indication = null,
                                    ) {}
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (showSettingsPanel) {
                            SettingsPanel(
                                warningMessage = catalog.warningMessage,
                                externalUrl = settingsExternalUrlDraft,
                                updateUrl = settingsUpdateUrlDraft,
                                lastUpdatedAtMillis = lastUpdatedAtMillis.takeIf { it > 0L },
                                lastAppUpdateCheckAtMillis = lastAppUpdateCheckAtMillis.takeIf { it > 0L },
                                urlFocusRequester = settingsUrlFocusRequester,
                                updateUrlFocusRequester = updateUrlFocusRequester,
                                updateUiState = updateUiState,
                                onExternalUrlChange = { settingsExternalUrlDraft = it },
                                onUpdateUrlChange = { settingsUpdateUrlDraft = it },
                                onSaveExternalUrl = {
                                    configPreferences.saveExternalUrl(settingsExternalUrlDraft)
                                    preferredConfigLocation = configPreferences.resolvePreferredLocation()
                                    reloadNonce += 1
                                    showSettingsPanel = false
                                    showChannelList = false
                                },
                                onSaveUpdateUrl = {
                                    configPreferences.saveUpdateUrl(settingsUpdateUrlDraft)
                                    lastAppUpdateCheckAtMillis =
                                        configPreferences.getLastAppUpdateCheckAtMillis() ?: lastAppUpdateCheckAtMillis
                                },
                                onReloadChannels = {
                                    preferredConfigLocation = configPreferences.resolvePreferredLocation()
                                    reloadNonce += 1
                                    showSettingsPanel = false
                                    showChannelList = false
                                },
                                onCheckUpdate = {
                                    configPreferences.saveUpdateUrl(settingsUpdateUrlDraft)
                                    val checkedAt = System.currentTimeMillis()
                                    configPreferences.saveLastAppUpdateCheckAtMillis(checkedAt)
                                    lastAppUpdateCheckAtMillis = checkedAt
                                    checkForUpdate(
                                        scope = coroutineScope,
                                        updateRepository = updateRepository,
                                        updateUrl = settingsUpdateUrlDraft,
                                        onStateChange = { updateUiState = it },
                                    )
                                },
                                onOpenUpdate = {
                                    val targetUrl =
                                        updateUiState.downloadUrl
                                            ?: updateUiState.releasePageUrl
                                            ?: settingsUpdateUrlDraft
                                    openExternalUrl(context, targetUrl)
                                },
                            )
                        } else {
                            Text(
                                text = selectedChannel.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tap a channel to play. Back or tap outside closes the list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f),
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        pendingStartupUpdatePrompt?.let { startupPrompt ->
            AlertDialog(
                onDismissRequest = { pendingStartupUpdatePrompt = null },
                title = { Text(text = "Update available") },
                text = {
                    Text(
                        text =
                            buildString {
                                append("Current: ${startupPrompt.currentVersion ?: BuildConfig.VERSION_NAME}")
                                startupPrompt.latestVersion?.let { latest ->
                                    append("\nLatest: $latest")
                                }
                                startupPrompt.statusMessage?.let { message ->
                                    append("\n\n$message")
                                }
                            },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetUrl =
                                startupPrompt.downloadUrl
                                    ?: startupPrompt.releasePageUrl
                                    ?: configPreferences.getConfiguredUpdateUrl()
                            openExternalUrl(context, targetUrl)
                            pendingStartupUpdatePrompt = null
                        },
                    ) {
                        Text("Update now")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { pendingStartupUpdatePrompt = null },
                    ) {
                        Text("Later")
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    warningMessage: String?,
    externalUrl: String,
    updateUrl: String,
    lastUpdatedAtMillis: Long?,
    lastAppUpdateCheckAtMillis: Long?,
    urlFocusRequester: FocusRequester,
    updateUrlFocusRequester: FocusRequester,
    updateUiState: QtvUpdateUiState,
    onExternalUrlChange: (String) -> Unit,
    onUpdateUrlChange: (String) -> Unit,
    onSaveExternalUrl: () -> Unit,
    onSaveUpdateUrl: () -> Unit,
    onReloadChannels: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        if (lastUpdatedAtMillis != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Source updates",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Last updated: ${formatLastUpdatedAt(lastUpdatedAtMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (warningMessage != null) {
            Text(
                text = warningMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = externalUrl,
                onValueChange = onExternalUrlChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .focusRequester(urlFocusRequester),
                label = { Text("External qtv.json URL") },
                singleLine = true,
            )
            Button(
                onClick = onSaveExternalUrl,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Save")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "App updates",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (lastAppUpdateCheckAtMillis != null) {
                Text(
                    text = "Last updated: ${formatLastUpdatedAt(lastAppUpdateCheckAtMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = updateUrl,
                onValueChange = onUpdateUrlChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .focusRequester(updateUrlFocusRequester),
                label = { Text("Update app URL") },
                singleLine = true,
            )
            Button(
                onClick = onSaveUpdateUrl,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Save")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCheckUpdate,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (updateUiState.isChecking) "Updating..." else "Update app",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = onOpenUpdate,
                enabled = updateUiState.downloadUrl != null || updateUiState.releasePageUrl != null || updateUrl.isNotBlank(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Open update",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = onReloadChannels,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Update source",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        if (updateUiState.statusMessage != null) {
            Text(
                text = updateUiState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (updateUiState.isError) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.82f),
            )
        }
        if (updateUiState.latestVersion != null) {
            Text(
                text = "Current: ${updateUiState.currentVersion ?: BuildConfig.VERSION_NAME}  |  Latest: ${updateUiState.latestVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: QtvChannel,
    focused: Boolean,
    selected: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        focused -> MaterialTheme.colorScheme.surfaceBright
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }
    val titleColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onFocused()
                }
            }
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onSelected)
            .focusable(enabled = enabled)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
        )
        Column {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LoadingConfigState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.padding(40.dp)) {
            Text(
                text = "QTV",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading channel config...",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyConfigState(
    sourceSummary: String,
    warningMessage: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.padding(40.dp)) {
            Text(
                text = "QTV",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No channels found in $sourceSummary",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (warningMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ConfigErrorState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.padding(40.dp)) {
            Text(
                text = "QTV",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Failed to load channel config",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private data class QtvCatalogUiState(
    val isLoading: Boolean = false,
    val catalog: QtvConfigCatalog? = null,
    val errorMessage: String? = null,
)

private data class QtvUpdateUiState(
    val isChecking: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val currentVersion: String? = null,
    val latestVersion: String? = null,
    val downloadUrl: String? = null,
    val releasePageUrl: String? = null,
)

private data class RemoteCatalogSyncResult(
    val catalogChanged: Boolean,
)

private enum class DrawerFocusArea {
    ChannelList,
    SettingsButton,
}

private fun formatLastUpdatedAt(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

private suspend fun loadInitialCatalog(
    context: android.content.Context,
    configRepository: QtvConfigRepository,
    configPreferences: QtvConfigPreferences,
    preferredLocation: QtvConfigLocation,
): QtvConfigCatalog {
    val remoteLocation = preferredLocation as? QtvConfigLocation.ExternalUrl
    if (remoteLocation != null) {
        configPreferences.getCachedRemoteJson(remoteLocation.url)
            ?.takeIf { it.isNotBlank() }
            ?.let { cachedJson ->
                return configRepository.loadCatalogFromRawJson(cachedJson, remoteLocation)
            }
    }
    return configRepository.loadCatalog(context, preferredLocation)
}

private suspend fun syncRemoteCatalogIfNeeded(
    context: android.content.Context,
    configRepository: QtvConfigRepository,
    configPreferences: QtvConfigPreferences,
    remoteLocation: QtvConfigLocation.ExternalUrl,
): RemoteCatalogSyncResult {
    val cachedRemoteJson = configPreferences.getCachedRemoteJson(remoteLocation.url)
    val remoteJson =
        runCatching {
            configRepository.fetchRawJson(context, remoteLocation)
        }.getOrElse {
            return RemoteCatalogSyncResult(catalogChanged = false)
        }

    if (remoteJson == cachedRemoteJson) {
        configPreferences.saveCachedRemoteJson(remoteLocation.url, remoteJson)
        return RemoteCatalogSyncResult(catalogChanged = false)
    }

    configRepository.loadCatalogFromRawJson(remoteJson, remoteLocation)
    configPreferences.saveCachedRemoteJson(remoteLocation.url, remoteJson)
    configPreferences.saveLastUpdatedAtMillis(System.currentTimeMillis())
    return RemoteCatalogSyncResult(catalogChanged = true)
}

private suspend fun fetchStartupUpdateState(
    updateRepository: QtvUpdateRepository,
    configPreferences: QtvConfigPreferences,
): QtvUpdateUiState? {
    val updateUrl = configPreferences.getConfiguredUpdateUrl()
    configPreferences.saveLastAppUpdateCheckAtMillis(System.currentTimeMillis())
    return runCatching {
        withContext(Dispatchers.IO) {
            updateRepository.checkForUpdate(updateUrl)
        }
    }.getOrNull()
        ?.takeIf { it.updateAvailable }
        ?.let { result ->
            QtvUpdateUiState(
                isChecking = false,
                statusMessage = "A newer app version is available.",
                currentVersion = result.currentVersion,
                latestVersion = result.latestVersion,
                downloadUrl = result.downloadUrl,
                releasePageUrl = result.releasePageUrl,
            )
        }
}

private fun checkForUpdate(
    scope: CoroutineScope,
    updateRepository: QtvUpdateRepository,
    updateUrl: String,
    onStateChange: (QtvUpdateUiState) -> Unit,
) {
    onStateChange(
        QtvUpdateUiState(
            isChecking = true,
            statusMessage = "Checking latest version...",
        ),
    )
    scope.launch {
        val nextState =
            try {
                val result = withContext(Dispatchers.IO) {
                    updateRepository.checkForUpdate(updateUrl)
                }
                QtvUpdateUiState(
                    isChecking = false,
                    statusMessage =
                        if (result.updateAvailable) {
                            "Update available. Open update to download the latest APK."
                        } else {
                            "Already on the latest version."
                        },
                    currentVersion = result.currentVersion,
                    latestVersion = result.latestVersion,
                    downloadUrl = result.downloadUrl,
                    releasePageUrl = result.releasePageUrl,
                )
            } catch (error: Throwable) {
                QtvUpdateUiState(
                    isChecking = false,
                    statusMessage = error.message ?: "Failed to check updates.",
                    isError = true,
                )
            }
        onStateChange(nextState)
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun TvHomeScreenPreview() {
    QTVTheme {
        TvHomeScreen()
    }
}
