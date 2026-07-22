package com.localmusic.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { MusicApp() }
        }
    }
}

@Composable
private fun MusicApp() {
    val context = LocalContext.current

    // --- permission ---
    val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(audioPermission)
        // Ask for notification permission too (needed to show controls on 13+).
        if (Build.VERSION.SDK_INT >= 33) {
            // best-effort; playback works regardless
        }
    }

    // --- connect to the playback service ---
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
        }, MoreExecutors.directExecutor())
        onDispose {
            controller?.release()
            controller = null
        }
    }

    // --- load songs ---
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    LaunchedEffect(granted) {
        if (granted) songs = withContext(Dispatchers.IO) { querySongs(context) }
    }

    // --- observe player state ---
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(-1) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showNowPlaying by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                currentIndex = c.currentMediaItemIndex
                durationMs = c.duration.coerceAtLeast(0)
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        currentIndex = c.currentMediaItemIndex
        onDispose { c.removeListener(listener) }
    }

    // Tick the progress bar while playing.
    LaunchedEffect(controller, isPlaying) {
        while (true) {
            controller?.let {
                positionMs = it.currentPosition.coerceAtLeast(0)
                durationMs = it.duration.coerceAtLeast(0)
            }
            delay(500)
        }
    }

    fun playFrom(index: Int, list: List<Song>) {
        val c = controller ?: return
        val mediaItems = list.map { s ->
            MediaItem.Builder()
                .setUri(s.uri)
                .setMediaId(s.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setArtworkUri(s.embeddedArtUri)
                        .build()
                )
                .build()
        }
        c.setMediaItems(mediaItems, index, 0)
        c.prepare()
        c.play()
    }

    val list = songs
    val nowSong = list?.getOrNull(currentIndex)

    Box(Modifier.fillMaxSize().background(Color(0xFF07070A))) {
        when {
            !granted -> Centered("Music needs permission to read the audio on your phone.\nTap to grant.") {
                permLauncher.launch(audioPermission)
            }
            list == null -> Centered("Scanning your music…")
            list.isEmpty() -> Centered("No music files found on this device.")
            else -> {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Text(
                        "Songs",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        items(list, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                isCurrent = song.id == nowSong?.id,
                                onClick = { playFrom(list.indexOf(song), list) },
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }

                // Mini player pinned above the nav bar.
                if (nowSong != null) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        MiniPlayer(
                            song = nowSong,
                            isPlaying = isPlaying,
                            onPlayPause = {
                                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                            onExpand = { showNowPlaying = true },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        // Full-screen now playing.
        AnimatedVisibility(
            visible = showNowPlaying && nowSong != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            nowSong?.let { song ->
                NowPlaying(
                    song = song,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = if (durationMs > 0) durationMs else song.durationMs,
                    onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                    onNext = { controller?.seekToNextMediaItem() },
                    onPrev = { controller?.seekToPreviousMediaItem() },
                    onSeek = { controller?.seekTo(it) },
                    onClose = { showNowPlaying = false },
                )
            }
        }
    }
    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
}

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Color(0xFFC9A7FF) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                color = Color(0xFF9A9AA5),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(formatTime(song.durationMs), color = Color(0xFF77777F), fontSize = 12.sp)
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A20))
            .clickable(onClick = onExpand)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color(0xFF9A9AA5), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (isPlaying) "⏸" else "▶",
            color = Color.White,
            fontSize = 22.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onPlayPause)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NowPlaying(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10))
            // Absorb taps so they don't fall through to the song list behind.
            .clickable(enabled = false) {},
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "▾",
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(8.dp),
            )
            Spacer(Modifier.weight(0.5f))
            Artwork(
                song,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                song.title, color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.artist, color = Color(0xFFB0B0BA), fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))

            val safeDuration = durationMs.coerceAtLeast(1)
            Slider(
                value = positionMs.coerceIn(0, safeDuration).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..safeDuration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0x40FFFFFF),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs), color = Color(0xFF9A9AA5), fontSize = 12.sp)
                Text("-" + formatTime((safeDuration - positionMs).coerceAtLeast(0)),
                    color = Color(0xFF9A9AA5), fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton("⏮", 34) { onPrev() }
                ControlButton(if (isPlaying) "⏸" else "▶", 52) { onPlayPause() }
                ControlButton("⏭", 34) { onNext() }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ControlButton(symbol: String, size: Int, onClick: () -> Unit) {
    Text(
        symbol,
        color = Color.White,
        fontSize = size.sp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    )
}

@Composable
private fun Artwork(song: Song, modifier: Modifier) {
    val context = LocalContext.current
    // Embedded art loads as a bitmap if present; otherwise a gradient tile.
    var bitmap by remember(song.id) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(song.id) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(song.embeddedArtUri)?.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s)
                        ?.asImageBitmapSafe()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier.background(gradientFor(song.title + song.artist)), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = song.album,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("♪", color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
        }
    }
}

private fun android.graphics.Bitmap.asImageBitmapSafe() =
    androidx.compose.ui.graphics.asImageBitmap(this)

/** Deterministic soft gradient from a string, for art-less tracks. */
private fun gradientFor(seed: String): androidx.compose.ui.graphics.Brush {
    val h = (seed.hashCode() and 0xFFFFFF)
    val c1 = Color((0xFF000000.toInt()) or h or 0x303030)
    val c2 = Color((0xFF000000.toInt()) or (h.rotateLeft(7) and 0xFFFFFF) or 0x202020)
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(c1, c2))
}

@Composable
private fun Centered(message: String, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxSize().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}
