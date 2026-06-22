package com.qtv.app.player

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qtv.app.config.QtvSource
import kotlinx.coroutines.delay

private const val RETRY_DELAY_MS = 10_000L
private const val MAX_RETRY_ATTEMPTS = 3

@Composable
fun QtvPlayerPane(
    channelName: String,
    sources: List<QtvSource>,
    channelType: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }
    val primarySource = sources.firstOrNull()
    var playbackStatus by remember { mutableStateOf("Loading stream...") }
    var keepScreenOn by remember { mutableStateOf(false) }
    var lastErrorMessage by remember { mutableStateOf<String?>(null) }
    var lastErrorCategory by remember { mutableStateOf<String?>(null) }
    var retryAttemptCount by remember { mutableIntStateOf(0) }
    var pendingRetryAttempt by remember { mutableStateOf<Int?>(null) }
    val showErrorScreen = lastErrorCategory != null || lastErrorMessage != null
    val loadPrimarySource = {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(primarySource?.url)
                .setMimeType(resolveMimeType(primarySource?.type.orEmpty(), channelType))
                .build(),
        )
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                keepScreenOn = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (lastErrorMessage != null && state == Player.STATE_IDLE) {
                    return
                }
                if (state == Player.STATE_READY) {
                    lastErrorCategory = null
                    lastErrorMessage = null
                    retryAttemptCount = 0
                    pendingRetryAttempt = null
                }
                playbackStatus = when (state) {
                    Player.STATE_IDLE -> "Player idle"
                    Player.STATE_BUFFERING -> "Buffering..."
                    Player.STATE_READY -> "Playing"
                    Player.STATE_ENDED -> "Playback ended"
                    else -> "Loading stream..."
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val classifiedError = classifyPlaybackError(error)
                lastErrorCategory = classifiedError.category
                lastErrorMessage = classifiedError.detail
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                keepScreenOn = false

                if (retryAttemptCount < MAX_RETRY_ATTEMPTS) {
                    val nextAttempt = retryAttemptCount + 1
                    retryAttemptCount = nextAttempt
                    pendingRetryAttempt = nextAttempt
                    playbackStatus =
                        if (nextAttempt == 1) {
                            "Playback error: ${classifiedError.category} - retrying now ($nextAttempt/$MAX_RETRY_ATTEMPTS)"
                        } else {
                            "Playback error: ${classifiedError.category} - retrying in 10s ($nextAttempt/$MAX_RETRY_ATTEMPTS)"
                        }
                } else {
                    pendingRetryAttempt = null
                    playbackStatus = "Playback error: ${classifiedError.category}"
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(channelName, sources) {
        lastErrorCategory = null
        lastErrorMessage = null
        retryAttemptCount = 0
        pendingRetryAttempt = null
        if (primarySource == null) {
            playbackStatus = "No playable source configured"
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            keepScreenOn = false
            return@LaunchedEffect
        }

        playbackStatus = "Loading stream..."
        loadPrimarySource()
    }

    LaunchedEffect(channelName, sources, pendingRetryAttempt) {
        val retryAttempt = pendingRetryAttempt ?: return@LaunchedEffect
        if (primarySource == null) {
            pendingRetryAttempt = null
            return@LaunchedEffect
        }

        if (retryAttempt > 1) {
            delay(RETRY_DELAY_MS)
        }
        playbackStatus = "Retrying stream... ($retryAttempt/$MAX_RETRY_ATTEMPTS)"
        pendingRetryAttempt = null
        loadPrimarySource()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.keepScreenOn = keepScreenOn
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.keepScreenOn = keepScreenOn
            },
        )

        if (showErrorScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.94f)),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp),
                ) {
                    Text(
                        text = playbackStatus,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = buildErrorSummary(lastErrorCategory, lastErrorMessage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun resolveMimeType(sourceType: String, channelType: String): String {
    return when ((sourceType.ifBlank { channelType }).lowercase()) {
        "hls", "m3u8" -> MimeTypes.APPLICATION_M3U8
        else -> MimeTypes.APPLICATION_M3U8
    }
}

private fun buildErrorSummary(category: String?, detail: String?): String {
    val safeCategory = category ?: "Playback failed"
    val safeDetail = detail?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    return if (safeDetail.isBlank()) {
        "Last error: $safeCategory"
    } else {
        "Last error: $safeCategory  -  $safeDetail"
    }
}

private data class ClassifiedPlaybackError(
    val category: String,
    val detail: String,
)

private fun classifyPlaybackError(error: PlaybackException): ClassifiedPlaybackError {
    val rawDetail = error.cause?.message ?: error.message ?: error.errorCodeName
    val summary = rawDetail.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { error.errorCodeName }
    val normalized = "$summary ${error.errorCodeName}".lowercase()

    val category = when {
        "cleartext" in normalized || "not permitted" in normalized -> "HTTP blocked"
        "unrecognizedinputformatexception" in normalized ||
            "input format" in normalized ||
            "parser" in normalized -> "Format detection failed"
        "timed out" in normalized || "timeout" in normalized -> "Request timed out"
        "403" in normalized || "401" in normalized || "forbidden" in normalized || "unauthorized" in normalized ->
            "Access denied"
        "404" in normalized || "not found" in normalized -> "Stream not found"
        "unable to connect" in normalized ||
            "failed to connect" in normalized ||
            "connection refused" in normalized ||
            "network" in normalized -> "Network connection failed"
        else -> "Playback failed"
    }

    return ClassifiedPlaybackError(
        category = category,
        detail = summary,
    )
}
