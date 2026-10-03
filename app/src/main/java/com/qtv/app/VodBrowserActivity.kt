package com.qtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

    val categoryState = rememberLazyListState()
    val catalogGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var tabs by remember { mutableStateOf<List<VodTab>>(emptyList()) }
    var selectedTab by remember { mutableStateOf<VodTab?>(null) }
    var categoryPage by remember { mutableStateOf(1) }
    var categoryHasMore by remember { mutableStateOf(false) }
    var cards by remember { mutableStateOf<List<VodCard>>(emptyList()) }

    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var searchRequest by remember { mutableStateOf(0) }
    var searchPage by remember { mutableStateOf(1) }
    var searchHasMore by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<VodCard>>(emptyList()) }

    var selectedCard by remember { mutableStateOf<VodCard?>(null) }
    var tracks by remember { mutableStateOf<List<VodTrack>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<VodTrack?>(null) }
    var stream by remember { mutableStateOf<QtvSource?>(null) }
    var message by remember { mutableStateOf("正在加载…") }
    var loadingMore by remember { mutableStateOf(false) }

    val submitSearch: () -> Unit = {
        val query = searchQuery.trim()
        if (query.isNotEmpty()) {
            submittedQuery = query
            searchPage = 1
            searchRequest += 1
            keyboard?.hide()
        }
    }

    val navigateBack: () -> Unit = {
        when {
            stream != null -> {
                stream = null
                selectedTrack = null
            }
            selectedCard != null -> {
                selectedCard = null
                selectedTrack = null
                tracks = emptyList()
                message = ""
            }
            searchMode -> {
                searchMode = false
                message = ""
            }
            else -> onExit()
        }
    }

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

    LaunchedEffect(selectedTab, categoryPage) {
        val tab = selectedTab ?: return@LaunchedEffect
        val append = categoryPage > 1
        if (!append) {
            selectedCard = null
            tracks = emptyList()
            cards = emptyList()
            catalogGridState.scrollToItem(0)
        }
        loadingMore = append
        message = if (append) "" else "正在加载${tab.name}…"
        runCatching { withContext(Dispatchers.IO) { resolver.cards(tab, categoryPage) } }
            .onSuccess { result ->
                cards = if (append) (cards + result).distinctBy { it.id } else result
                categoryHasMore = result.isNotEmpty()
                message = if (cards.isEmpty()) "这个分类暂时没有影片" else ""
            }
            .onFailure { message = it.message ?: "影片加载失败" }
        loadingMore = false
    }

    LaunchedEffect(selectedTab, tabs.size) {
        val index = tabs.indexOfFirst { it.id == selectedTab?.id }
        if (index >= 0) categoryState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }

    LaunchedEffect(searchMode) {
        if (searchMode) runCatching { searchFocusRequester.requestFocus() }
    }

    LaunchedEffect(searchRequest, searchPage) {
        if (searchRequest == 0 || submittedQuery.isBlank()) return@LaunchedEffect
        val append = searchPage > 1
        if (!append) {
            searchResults = emptyList()
            searchGridState.scrollToItem(0)
        }
        loadingMore = append
        message = if (append) "" else "正在搜索“$submittedQuery”…"
        runCatching { withContext(Dispatchers.IO) { resolver.search(submittedQuery, searchPage) } }
            .onSuccess { result ->
                searchResults = if (append) (searchResults + result).distinctBy { it.id } else result
                searchHasMore = result.isNotEmpty()
                message = if (searchResults.isEmpty()) "没有找到相关影片" else ""
            }
            .onFailure { message = it.message ?: "搜索失败" }
        loadingMore = false
    }

    LaunchedEffect(selectedCard) {
        val card = selectedCard ?: return@LaunchedEffect
        message = "正在加载剧集…"
        runCatching { withContext(Dispatchers.IO) { resolver.tracks(card) } }
            .onSuccess {
                tracks = it
                message = if (it.isEmpty()) "没有找到可播放剧集" else ""
            }
            .onFailure { message = it.message ?: "剧集加载失败" }
    }

    LaunchedEffect(selectedTrack) {
        val track = selectedTrack ?: return@LaunchedEffect
        message = "正在解析播放地址…"
        runCatching { withContext(Dispatchers.IO) { resolver.resolve(track) } }
            .onSuccess {
                stream = QtvSource(it.url, 1, "hls", "Primary", it.headers)
                message = ""
            }
            .onFailure { message = it.message ?: "播放地址解析失败" }
    }

    BackHandler { navigateBack() }

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
                onClick = navigateBack,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VodBackButton(onClick = navigateBack)
            Text(
                site.name,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.weight(1f))
            if (selectedCard == null) {
                OutlinedButton(
                    onClick = {
                        searchMode = !searchMode
                        message = ""
                    },
                ) {
                    Text(if (searchMode) "关闭搜索" else "搜索")
                }
            }
        }

        when {
            selectedCard != null -> {
                Text(
                    selectedCard!!.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tracks) { track ->
                        Button(
                            onClick = { selectedTrack = track },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(track.name) }
                    }
                }
            }

            searchMode -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                        label = { Text("输入影片名称") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    )
                    Button(onClick = submitSearch) { Text("搜索") }
                }
                if (submittedQuery.isNotBlank()) {
                    Text(
                        "搜索结果：$submittedQuery",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                VodMovieGrid(
                    cards = searchResults,
                    state = searchGridState,
                    hasMore = searchHasMore,
                    loadingMore = loadingMore,
                    onCardSelected = { selectedCard = it },
                    onLoadMore = { searchPage += 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                LazyRow(
                    state = categoryState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 12.dp),
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val selected = tab.id == selectedTab?.id
                        Button(
                            onClick = { selectedTab = tab; categoryPage = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) {
                            Text(tab.name)
                        }
                    }
                }
                selectedTab?.let {
                    Text(
                        it.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                VodMovieGrid(
                    cards = cards,
                    state = catalogGridState,
                    hasMore = categoryHasMore,
                    loadingMore = loadingMore,
                    onCardSelected = { selectedCard = it },
                    onLoadMore = { categoryPage += 1 },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun VodMovieGrid(
    cards: List<VodCard>,
    state: LazyGridState,
    hasMore: Boolean,
    loadingMore: Boolean,
    onCardSelected: (VodCard) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = modifier.fillMaxWidth(),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        gridItems(cards, key = { it.id }) { card ->
            Button(
                onClick = { onCardSelected(card) },
                modifier = Modifier.fillMaxWidth().height(96.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (card.remarks.isNotBlank()) {
                        Text(
                            card.remarks,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (cards.isNotEmpty() && hasMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (loadingMore) "正在加载…" else "加载更多") }
            }
        }
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
