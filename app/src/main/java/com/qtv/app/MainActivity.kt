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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.qtv.app.config.loadBundledChannels
import com.qtv.app.player.QtvPlayerPane
import com.qtv.app.ui.theme.QTVTheme

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
    val channels = remember(context) { loadBundledChannels(context) }
    if (channels.isEmpty()) {
        EmptyConfigState(modifier = modifier)
        return
    }
    val focusRequesters = remember(channels.size) {
        List(channels.size) { FocusRequester() }
    }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
    var showChannelList by rememberSaveable { mutableStateOf(false) }
    val playerFocusRequester = remember { FocusRequester() }
    val selectedChannel = channels[selectedIndex]
    val playerTouchInteraction = remember { MutableInteractionSource() }
    val overlayTouchInteraction = remember { MutableInteractionSource() }
    val drawerTouchInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    BackHandler(enabled = showChannelList) {
        showChannelList = false
    }

    LaunchedEffect(showChannelList, focusedIndex) {
        if (showChannelList) {
            focusRequesters.getOrNull(focusedIndex)?.requestFocus()
        } else {
            playerFocusRequester.requestFocus()
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

                    event.key == Key.Back ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK -> {
                        if (showChannelList) {
                            showChannelList = false
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
                                    focusRequester = focusRequesters[index],
                                    onFocused = { focusedIndex = index },
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
                            .width(320.dp)
                            .padding(top = 44.dp, start = 24.dp),
                    ) {
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
private fun ChannelRow(
    channel: QtvChannel,
    focused: Boolean,
    selected: Boolean,
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
            .clickable(onClick = onSelected)
            .focusable()
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
private fun EmptyConfigState(modifier: Modifier = Modifier) {
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
                text = "No channels found in bundled qtv.json",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun TvHomeScreenPreview() {
    QTVTheme {
        TvHomeScreen()
    }
}
