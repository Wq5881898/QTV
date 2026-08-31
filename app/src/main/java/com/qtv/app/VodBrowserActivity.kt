package com.qtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.qtv.app.config.QtvSource
import com.qtv.app.config.XptvVodSite
import com.qtv.app.player.QtvPlayerPane
import com.qtv.app.ui.theme.QTVTheme
import com.qtv.app.vod.VodCard
import com.qtv.app.vod.VodTab
import com.qtv.app.vod.VodTrack
import com.qtv.app.vod.XptvVodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VodBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = XptvVodSite(
            name = intent.getStringExtra(EXTRA_NAME) ?: "点播",
            api = intent.getStringExtra(EXTRA_API).orEmpty(),
            extensionUrl = intent.getStringExtra(EXTRA_EXTENSION).orEmpty(),
        )
        setContent { QTVTheme { VodBrowser(site, onExit = ::finish) } }
    }

    companion object {
        const val EXTRA_NAME = "xptv_name"
        const val EXTRA_API = "xptv_api"
        const val EXTRA_EXTENSION = "xptv_extension"
    }
}

@Composable
private fun VodBrowser(site: XptvVodSite, onExit: () -> Unit) {
    val resolver = remember(site.api) { XptvVodRepository().resolverFor(site) }
    if (resolver == null) {
        Text("暂不支持这个点播接口：${site.api}", modifier = Modifier.padding(24.dp))
        return
    }
    var tabs by remember { mutableStateOf<List<VodTab>>(emptyList()) }
    var selectedTab by remember { mutableStateOf<VodTab?>(null) }
    var cards by remember { mutableStateOf<List<VodCard>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<VodCard?>(null) }
    var tracks by remember { mutableStateOf<List<VodTrack>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<VodTrack?>(null) }
    var stream by remember { mutableStateOf<QtvSource?>(null) }
    var message by remember { mutableStateOf("Loading…") }

    LaunchedEffect(Unit) {
        message = "正在加载分类…"
        runCatching { withContext(Dispatchers.IO) { resolver.tabs() } }
            .onSuccess {
                tabs = it
                selectedTab = it.firstOrNull()
                message = if (it.isEmpty()) "没有找到分类" else ""
            }
            .onFailure { message = it.message ?: "分类加载失败" }
    }
    LaunchedEffect(selectedTab) {
        val tab = selectedTab ?: return@LaunchedEffect
        selectedCard = null; tracks = emptyList(); message = "Loading ${tab.name}…"
        runCatching { withContext(Dispatchers.IO) { resolver.cards(tab) } }
            .onSuccess { cards = it; message = if (it.isEmpty()) "No titles found" else "" }
            .onFailure { message = it.message ?: "Failed to load titles" }
    }
    LaunchedEffect(selectedCard) {
        val card = selectedCard ?: return@LaunchedEffect
        message = "Loading episodes…"
        runCatching { withContext(Dispatchers.IO) { resolver.tracks(card) } }
            .onSuccess { tracks = it; message = if (it.isEmpty()) "No episodes found" else "" }
            .onFailure { message = it.message ?: "Failed to load episodes" }
    }
    LaunchedEffect(selectedTrack) {
        val track = selectedTrack ?: return@LaunchedEffect
        message = "Resolving stream…"
        runCatching { withContext(Dispatchers.IO) { resolver.resolve(track) } }
            .onSuccess { stream = QtvSource(it.url, 1, "hls", "Primary", it.headers); message = "" }
            .onFailure { message = it.message ?: "Failed to resolve stream" }
    }
    BackHandler {
        when {
            stream != null -> { stream = null; selectedTrack = null }
            selectedCard != null -> { selectedCard = null; tracks = emptyList() }
            else -> onExit()
        }
    }
    stream?.let { playable ->
        Box(Modifier.fillMaxSize()) {
            QtvPlayerPane(
                channelName = site.name,
                sources = listOf(playable),
                channelType = "hls",
                headers = playable.headers,
                modifier = Modifier.fillMaxSize(),
            )
            VodBackButton(
                onClick = { stream = null; selectedTrack = null },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VodBackButton(
                onClick = {
                    if (selectedCard != null) {
                        selectedCard = null
                        selectedTrack = null
                        tracks = emptyList()
                    } else {
                        onExit()
                    }
                },
            )
            Text(site.name, style = MaterialTheme.typography.headlineMedium)
        }
        if (selectedCard == null) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                items(tabs) { tab ->
                    Button(onClick = { selectedTab = tab }) { Text(tab.name) }
                }
            }
            selectedTab?.let { Text(it.name, style = MaterialTheme.typography.titleLarge) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cards) { card ->
                    Button(onClick = { selectedCard = card }, modifier = Modifier.fillMaxWidth()) {
                        Text(card.name + card.remarks.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
                    }
                }
            }
        } else {
            Text(selectedCard!!.name, style = MaterialTheme.typography.titleLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(tracks) { track -> Button(onClick = { selectedTrack = track }, modifier = Modifier.fillMaxWidth()) { Text(track.name) } } }
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(message) }
    }
}

@Composable
private fun VodBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text("<", style = MaterialTheme.typography.titleLarge)
    }
}
