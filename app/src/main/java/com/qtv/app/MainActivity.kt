package com.qtv.app

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.Dispatchers
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
                configRepository.loadCatalog(context, preferredConfigLocation)
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
    var showChannelList by rememberSaveable { mutableStateOf(false) }
    var showSettingsPanel by rememberSaveable { mutableStateOf(false) }
    var settingsExternalUrlDraft by rememberSaveable { mutableStateOf("") }
    val playerFocusRequester = remember { FocusRequester() }
    val settingsButtonFocusRequester = remember { FocusRequester() }
    val settingsUrlFocusRequester = remember { FocusRequester() }
    val selectedChannel = channels[selectedIndex]
    val playerTouchInteraction = remember { MutableInteractionSource() }
    val overlayTouchInteraction = remember { MutableInteractionSource() }
    val drawerTouchInteraction = remember { MutableInteractionSource() }
    var drawerFocusArea by rememberSaveable { mutableStateOf(DrawerFocusArea.ChannelList) }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(channels.size) {
        val lastIndex = channels.lastIndex
        if (selectedIndex > lastIndex) {
            selectedIndex = lastIndex
        }
        if (focusedIndex > lastIndex) {
            focusedIndex = lastIndex
        }
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
            settingsUrlFocusRequester.requestFocus()
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
                                Text(
                                    text = "QTV",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
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
                                currentSourceSummary = catalog.sourceSummary,
                                warningMessage = catalog.warningMessage,
                                externalUrl = settingsExternalUrlDraft,
                                lastUpdatedAtMillis = lastUpdatedAtMillis.takeIf { it > 0L },
                                urlFocusRequester = settingsUrlFocusRequester,
                                onExternalUrlChange = { settingsExternalUrlDraft = it },
                                onSaveExternalUrl = {
                                    configPreferences.saveExternalUrl(settingsExternalUrlDraft)
                                    preferredConfigLocation = configPreferences.resolvePreferredLocation()
                                    reloadNonce += 1
                                    showSettingsPanel = false
                                    showChannelList = false
                                },
                                onReloadChannels = {
                                    preferredConfigLocation = configPreferences.resolvePreferredLocation()
                                    reloadNonce += 1
                                    showSettingsPanel = false
                                    showChannelList = false
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
    }
}

@Composable
private fun SettingsPanel(
    currentSourceSummary: String,
    warningMessage: String?,
    externalUrl: String,
    lastUpdatedAtMillis: Long?,
    urlFocusRequester: FocusRequester,
    onExternalUrlChange: (String) -> Unit,
    onSaveExternalUrl: () -> Unit,
    onReloadChannels: () -> Unit,
) {
    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Current source: $currentSourceSummary",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
        if (lastUpdatedAtMillis != null) {
            Text(
                text = "Last updated: ${formatLastUpdatedAt(lastUpdatedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
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
        Button(
            onClick = onReloadChannels,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Update channel list")
        }
        Text(
            text = "Update app: reserved for a later step.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
        )
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

private enum class DrawerFocusArea {
    ChannelList,
    SettingsButton,
}

private fun formatLastUpdatedAt(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun TvHomeScreenPreview() {
    QTVTheme {
        TvHomeScreen()
    }
}
