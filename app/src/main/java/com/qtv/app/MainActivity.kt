package com.qtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qtv.app.config.QtvChannel
import com.qtv.app.config.loadBundledChannels
import com.qtv.app.player.QtvPlayerPane
import com.qtv.app.ui.theme.QTVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TvHomeScreen()
                }
            }
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

    LaunchedEffect(Unit) {
        focusRequesters.firstOrNull()?.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .focusGroup(),
        ) {
            Text(
                text = "QTV",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Remote-first TV player from local qtv.json",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
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
                        onSelected = { selectedIndex = index },
                    )
                }
            }
        }

        val selectedChannel = channels[selectedIndex]
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(28.dp),
        ) {
            Text(
                text = selectedChannel.category.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = selectedChannel.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Status: ${selectedChannel.status}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            QtvPlayerPane(
                channelName = selectedChannel.name,
                streamUrl = selectedChannel.streamUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Use D-pad to move focus. Press OK to switch the active stream.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
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
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${channel.category}  -  ${channel.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
