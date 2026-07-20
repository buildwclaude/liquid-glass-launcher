package com.wheelgallery.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GalleryApp()
            }
        }
    }
}

private enum class Tab(val label: String, val emoji: String) {
    Albums("Albums", "🖼"),
    Library("Library", "📚"),
    Downloads("Downloads", "⬇"),
    Favorites("Favorites", "♥"),
    Search("Search", "🔍"),
}

@Composable
private fun GalleryApp() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    val permissions =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    var granted by remember {
        mutableStateOf(
            permissions.all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(permissions) }

    var media by remember { mutableStateOf<List<MediaItem>?>(null) }
    LaunchedEffect(granted) {
        if (granted) media = withContext(Dispatchers.IO) { queryMedia(context) }
    }

    var favorites by remember { mutableStateOf(loadFavorites(context)) }
    fun toggleFavorite(m: MediaItem) {
        val k = m.uri.toString()
        favorites = if (k in favorites) favorites - k else favorites + k
        saveFavorites(context, favorites)
    }

    var tab by remember { mutableStateOf(Tab.Library) }
    var openAlbum by remember { mutableStateOf<String?>(null) }
    // viewer = the list being browsed + which item was tapped
    var viewer by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when {
                    !granted -> PermissionGate { permissionLauncher.launch(permissions) }
                    media == null -> Centered("Loading your library…")
                    else -> {
                        val items = media!!
                        when (tab) {
                            Tab.Library -> LibraryTab(items, favorites, imageLoader) { l, i -> viewer = l to i }
                            Tab.Albums -> AlbumsTab(items, imageLoader, openAlbum,
                                onOpen = { openAlbum = it },
                                onBack = { openAlbum = null },
                                onTap = { l, i -> viewer = l to i })
                            Tab.Downloads -> {
                                val dl = items.filter { it.bucket.equals("Download", true) || it.bucket.equals("Downloads", true) }
                                if (dl.isEmpty()) Centered("No downloaded media found")
                                else MediaGrid(dl, favorites, imageLoader) { i -> viewer = dl to i }
                            }
                            Tab.Favorites -> {
                                val favs = items.filter { it.uri.toString() in favorites }
                                if (favs.isEmpty()) Centered("No favorites yet — tap ♥ in the viewer")
                                else MediaGrid(favs, favorites, imageLoader) { i -> viewer = favs to i }
                            }
                            Tab.Search -> SearchTab(items, favorites, imageLoader) { l, i -> viewer = l to i }
                        }
                    }
                }
            }
            NavigationBar(containerColor = Color(0xFF0B0B0D)) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; openAlbum = null },
                        icon = { Text(t.emoji, fontSize = 17.sp) },
                        label = { Text(t.label, fontSize = 10.sp) },
                    )
                }
            }
        }

        viewer?.let { (list, index) ->
            Viewer(
                list = list,
                startIndex = index,
                favorites = favorites,
                imageLoader = imageLoader,
                onToggleFavorite = { toggleFavorite(it) },
                onClose = { viewer = null },
            )
        }
    }
}

@Composable
private fun PermissionGate(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Photo Wheel needs permission to show your photos and videos.",
            color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAsk) { Text("Allow access") }
    }
}

@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------------------
// Library: the grid plus the date picker wheel.
// ---------------------------------------------------------------------------

@Composable
private fun LibraryTab(
    items: List<MediaItem>,
    favorites: Set<String>,
    imageLoader: ImageLoader,
    onTap: (List<MediaItem>, Int) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val byDate = remember(items) {
        items.groupBy { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
    }
    val years = remember(byDate) {
        val ys = byDate.keys.map { it.year }
        val now = LocalDate.now().year
        ((ys.minOrNull() ?: now)..now).toList()
    }

    val today = remember { LocalDate.now() }
    var day by remember { mutableStateOf(today.dayOfMonth) }
    var month by remember { mutableStateOf(today.monthValue) }
    var year by remember { mutableStateOf(today.year) }
    var filterActive by remember { mutableStateOf(false) }

    val selectedDate = remember(day, month, year) {
        val safeDay = day.coerceAtMost(java.time.YearMonth.of(year, month).lengthOfMonth())
        LocalDate.of(year, month, safeDay)
    }
    val shown = if (filterActive) byDate[selectedDate] ?: emptyList() else items
    val label =
        if (filterActive)
            selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) +
                "  •  ${shown.size} item" + (if (shown.size == 1) "" else "s")
        else
            "All photos  •  ${items.size} items"

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                shown.isEmpty() && filterActive ->
                    Centered("No photos from this date — try spinning to a nearby day")
                filterActive -> MediaGrid(shown, favorites, imageLoader) { i -> onTap(shown, i) }
                else -> GroupedGrid(items, byDate, favorites, imageLoader, onTap)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            if (filterActive) {
                TextButton(onClick = { filterActive = false }) {
                    Text("Show all", color = Color(0xFF6FA8FF), fontSize = 13.sp)
                }
            }
        }

        DateWheel(
            day = day, month = month, year = year, years = years,
            onDayChange = { if (it != day) { day = it; filterActive = true } },
            onMonthChange = { if (it != month) { month = it; filterActive = true } },
            onYearChange = { if (it != year) { year = it; filterActive = true } },
        )
    }
}

@Composable
private fun GroupedGrid(
    items: List<MediaItem>,
    byDate: Map<LocalDate, List<MediaItem>>,
    favorites: Set<String>,
    imageLoader: ImageLoader,
    onTap: (List<MediaItem>, Int) -> Unit,
) {
    val today = LocalDate.now()
    val fmt = remember { DateTimeFormatter.ofPattern("d MMMM yyyy") }
    val dates = remember(byDate) { byDate.keys.sortedDescending() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
    ) {
        dates.forEach { date ->
            val group = byDate[date] ?: return@forEach
            item(span = { GridItemSpan(maxLineSpan) }) {
                val title = when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.format(fmt)
                }
                Text(
                    title, color = Color.White, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                )
            }
            items(group.size, key = { group[it].uri.toString() + group[it].dateMillis }) { i ->
                val m = group[i]
                MediaCell(m, m.uri.toString() in favorites, imageLoader) {
                    onTap(group, i)
                }
            }
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    favorites: Set<String>,
    imageLoader: ImageLoader,
    onTap: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
    ) {
        items(items.size, key = { items[it].uri.toString() + it }) { i ->
            MediaCell(items[i], items[i].uri.toString() in favorites, imageLoader) { onTap(i) }
        }
    }
}

@Composable
private fun MediaCell(
    m: MediaItem,
    isFavorite: Boolean,
    imageLoader: ImageLoader,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    // Decode a small thumbnail, never the full camera file — grids only
    // need ~120px squares, and full 12MP decodes are what cause stutter.
    val thumbRequest = remember(m.uri) {
        ImageRequest.Builder(context)
            .data(m.uri)
            .size(300)
            .crossfade(false)
            .build()
    }
    Box(
        Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF141416))
            .clickable(onClick = onTap)
    ) {
        AsyncImage(
            model = thumbRequest,
            contentDescription = m.name,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (m.isVideo) {
            Text("▶", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp))
        }
        if (isFavorite) {
            Text("♥", color = Color(0xFFFF5A76), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Albums
// ---------------------------------------------------------------------------

@Composable
private fun AlbumsTab(
    items: List<MediaItem>,
    imageLoader: ImageLoader,
    openAlbum: String?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onTap: (List<MediaItem>, Int) -> Unit,
) {
    val recentCutoff = remember { System.currentTimeMillis() - 30L * 24 * 3600 * 1000 }
    val albums = remember(items) {
        val byBucket = items.groupBy { it.bucket }
        val list = mutableListOf<Pair<String, List<MediaItem>>>()
        list += "Recent (30 days)" to items.filter { it.dateMillis >= recentCutoff }
        list += byBucket.entries.sortedByDescending { it.value.size }.map { it.key to it.value }
        list.filter { it.second.isNotEmpty() }
    }

    if (openAlbum != null) {
        val content = albums.firstOrNull { it.first == openAlbum }?.second ?: emptyList()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF6FA8FF)) }
                Text(openAlbum, color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MediaGrid(content, emptySet(), imageLoader) { i -> onTap(content, i) }
        }
        BackHandler(onBack = onBack)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
        ) {
            items(albums, key = { it.first }) { (name, content) ->
                Column(Modifier.padding(6.dp).clickable { onOpen(name) }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF141416))
                    ) {
                        AsyncImage(
                            model = content.first().uri,
                            contentDescription = name,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(name, color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp))
                    Text("${content.size} items", color = Color(0xFF9A9AA0), fontSize = 12.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

@Composable
private fun SearchTab(
    items: List<MediaItem>,
    favorites: Set<String>,
    imageLoader: ImageLoader,
    onTap: (List<MediaItem>, Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(items, query) {
        if (query.isBlank()) emptyList()
        else items.filter {
            it.name.contains(query.trim(), true) || it.bucket.contains(query.trim(), true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A1E))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (query.isEmpty())
                        Text("Search by file or folder name…",
                            color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                    inner()
                }
            },
        )
        when {
            query.isBlank() -> Centered("Type to search your library")
            results.isEmpty() -> Centered("Nothing found for \"$query\"")
            else -> MediaGrid(results, favorites, imageLoader) { i -> onTap(results, i) }
        }
    }
}

// ---------------------------------------------------------------------------
// Full-screen viewer
// ---------------------------------------------------------------------------

@Composable
private fun Viewer(
    list: List<MediaItem>,
    startIndex: Int,
    favorites: Set<String>,
    imageLoader: ImageLoader,
    onToggleFavorite: (MediaItem) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0)),
        pageCount = { list.size },
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val m = list[page]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = m.uri,
                    contentDescription = m.name,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (m.isVideo) {
                    Text(
                        "▶",
                        fontSize = 56.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(m.uri, "video/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                } catch (_: Exception) {
                                }
                            }
                            .padding(horizontal = 26.dp, vertical = 14.dp),
                    )
                }
            }
        }

        val current = list.getOrNull(pager.currentPage)
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("✕", color = Color.White, fontSize = 20.sp) }
            Spacer(Modifier.weight(1f))
            current?.let { m ->
                val fav = m.uri.toString() in favorites
                TextButton(onClick = { onToggleFavorite(m) }) {
                    Text(if (fav) "♥" else "♡",
                        color = if (fav) Color(0xFFFF5A76) else Color.White, fontSize = 22.sp)
                }
                TextButton(onClick = {
                    try {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(if (m.isVideo) "video/*" else "image/*")
                            .putExtra(Intent.EXTRA_STREAM, m.uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Share"))
                    } catch (_: Exception) {
                    }
                }) { Text("⇪", color = Color.White, fontSize = 20.sp) }
            }
        }
    }
}
