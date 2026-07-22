package com.localmusic.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
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
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(audioPermission) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())
        onDispose {
            controller?.release()
            controller = null
        }
    }

    var songs by remember { mutableStateOf<List<Song>?>(null) }
    LaunchedEffect(granted) {
        if (granted) songs = withContext(Dispatchers.IO) { querySongs(context) }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(-1) }
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

    LaunchedEffect(controller, isPlaying) {
        while (true) {
            controller?.let {
                positionMs = it.currentPosition.coerceAtLeast(0)
                durationMs = it.duration.coerceAtLeast(0)
            }
            delay(250)
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
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }

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

// ---------------------------------------------------------------------------
// Artwork loading (bitmap + a color pulled from it for the color-bleed look)
// ---------------------------------------------------------------------------

private data class Art(val bitmap: ImageBitmap?, val color: Color)

@Composable
private fun rememberArt(song: Song, withPalette: Boolean): Art {
    val context = LocalContext.current
    var art by remember(song.id) { mutableStateOf(Art(null, Color(0xFF3A2E52))) }
    LaunchedEffect(song.id) {
        art = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(song.embeddedArtUri)?.use { s ->
                    val bmp = BitmapFactory.decodeStream(s) ?: return@use Art(null, Color(0xFF3A2E52))
                    var color = Color(0xFF3A2E52)
                    if (withPalette) {
                        val p = Palette.from(bmp).clearFilters().generate()
                        val sw = p.vibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch
                        if (sw != null) color = Color(sw.rgb)
                    }
                    Art(bmp.asImageBitmap(), color)
                } ?: Art(null, Color(0xFF3A2E52))
            } catch (_: Exception) {
                Art(null, Color(0xFF3A2E52))
            }
        }
    }
    return art
}

@Composable
private fun ArtworkBox(song: Song, modifier: Modifier) {
    val art = rememberArt(song, withPalette = false)
    Box(modifier.background(gradientFor(song.title + song.artist)), contentAlignment = Alignment.Center) {
        val bmp = art.bitmap
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = song.album,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("♪", color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Song list + mini player
// ---------------------------------------------------------------------------

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(song, Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (isCurrent) Color(0xFFC9A7FF) else Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color(0xFF9A9AA5), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        ArtworkBox(song, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color(0xFF9A9AA5), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) { PlayPauseGlyph(isPlaying, 20.dp, Color.White) }
    }
}

// ---------------------------------------------------------------------------
// Full-screen now playing — the beautiful screen
// ---------------------------------------------------------------------------

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
    val context = LocalContext.current
    val art = rememberArt(song, withPalette = true)
    val bgColor by animateColorAsState(art.color, label = "bg")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10))
            .clickable(enabled = false) {},
    ) {
        // ----- background: blurred artwork, or a color gradient -----
        if (art.bitmap != null) {
            Image(
                bitmap = art.bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.85f),
                        )
                    )
                )
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(bgColor.copy(alpha = 0.9f), Color(0xFF07070A)))
                )
            )
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // close chevron
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp), contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(40.dp).height(5.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.4f))
                        .clickable(onClick = onClose)
                )
            }
            Spacer(Modifier.weight(0.4f))

            // album art
            ArtworkBox(
                song,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(30.dp))

            // title + artist
            Column(Modifier.fillMaxWidth()) {
                Text(song.title, color = Color.White, fontSize = 23.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(song.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(18.dp))

            // ----- smooth scrubber: follows the finger, seeks on release -----
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val safeDuration = durationMs.coerceAtLeast(1)
            val shownPos = if (dragging) dragValue else positionMs.coerceIn(0, safeDuration).toFloat()

            Slider(
                value = shownPos,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
                valueRange = 0f..safeDuration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(shownPos.toLong()), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text("-" + formatTime((safeDuration - shownPos.toLong()).coerceAtLeast(0)),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))

            // ----- transport controls -----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onPrev),
                    contentAlignment = Alignment.Center) { SkipGlyph(forward = false, 30.dp) }
                Box(Modifier.size(76.dp).clip(CircleShape).clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center) { PlayPauseGlyph(isPlaying, 44.dp, Color.White) }
                Box(Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onNext),
                    contentAlignment = Alignment.Center) { SkipGlyph(forward = true, 30.dp) }
            }
            Spacer(Modifier.height(22.dp))

            // ----- volume slider -----
            VolumeBar(context)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun VolumeBar(context: Context) {
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SpeakerGlyph(loud = false)
        Slider(
            value = vol.toFloat(),
            onValueChange = {
                vol = it.toInt()
                am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
            },
            valueRange = 0f..maxVol.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
        )
        SpeakerGlyph(loud = true)
    }
}

// ---------------------------------------------------------------------------
// Hand-drawn thin icons (match the reference screenshots)
// ---------------------------------------------------------------------------

@Composable
private fun PlayPauseGlyph(isPlaying: Boolean, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        if (isPlaying) {
            val barW = this.size.width * 0.28f
            val gap = this.size.width * 0.16f
            val h = this.size.height
            val r = barW * 0.3f
            drawRoundRect(
                color = color,
                topLeft = Offset(this.size.width / 2 - gap / 2 - barW, 0f),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(this.size.width / 2 + gap / 2, 0f),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        } else {
            val p = Path().apply {
                moveTo(this@Canvas.size.width * 0.12f, 0f)
                lineTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height)
                lineTo(this@Canvas.size.width * 0.95f, this@Canvas.size.height / 2)
                close()
            }
            drawPath(p, color)
        }
    }
}

@Composable
private fun SkipGlyph(forward: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val color = Color.White
        fun tri(x0: Float, x1: Float) {
            val p = Path().apply {
                if (forward) {
                    moveTo(x0, 0f); lineTo(x0, h); lineTo(x1, h / 2)
                } else {
                    moveTo(x1, 0f); lineTo(x1, h); lineTo(x0, h / 2)
                }
                close()
            }
            drawPath(p, color)
        }
        tri(0f, w * 0.5f)
        tri(w * 0.5f, w)
    }
}

@Composable
private fun SpeakerGlyph(loud: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        val w = this.size.width
        val h = this.size.height
        val color = Color.White.copy(alpha = 0.7f)
        // cone
        val p = Path().apply {
            moveTo(w * 0.05f, h * 0.36f)
            lineTo(w * 0.28f, h * 0.36f)
            lineTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.5f, h * 0.85f)
            lineTo(w * 0.28f, h * 0.64f)
            lineTo(w * 0.05f, h * 0.64f)
            close()
        }
        drawPath(p, color)
        if (loud) {
            drawArc(color, -40f, 80f, false,
                topLeft = Offset(w * 0.45f, h * 0.2f),
                size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f))
        }
    }
}

// ---------------------------------------------------------------------------

private fun gradientFor(seed: String): Brush {
    val h = (seed.hashCode() and 0xFFFFFF)
    val c1 = Color((0xFF000000.toInt()) or h or 0x303030)
    val c2 = Color((0xFF000000.toInt()) or (h.rotateLeft(7) and 0xFFFFFF) or 0x202020)
    return Brush.linearGradient(listOf(c1, c2))
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
