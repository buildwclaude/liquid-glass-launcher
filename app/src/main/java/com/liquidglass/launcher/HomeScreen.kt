package com.liquidglass.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many icons fit on one home page (4 columns x 5 rows). */
private const val PAGE_CAPACITY = 20

/** A single entry in a long-press menu. */
data class MenuAction(val label: String, val action: () -> Unit)

@Composable
fun LauncherRoot(tilt: Tilt, homePresses: Int, setWallpaperBlur: (Float) -> Unit) {
    val context = LocalContext.current
    var reloadKey by remember { mutableIntStateOf(0) }

    // Reload the app list whenever something is installed or uninstalled.
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                reloadKey++
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Load the installed apps off the main thread so opening feels instant.
    val apps by produceState<List<AppEntry>?>(null, reloadKey) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }

    val loaded = apps
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(Color(0x33000000)))
    } else {
        LauncherContent(loaded, tilt, homePresses, setWallpaperBlur)
    }
}

@Composable
private fun LauncherContent(
    apps: List<AppEntry>,
    tilt: Tilt,
    homePresses: Int,
    setWallpaperBlur: (Float) -> Unit,
) {
    val context = LocalContext.current
    val byKey = remember(apps) { apps.associateBy { it.key } }

    var pages by remember { mutableStateOf(loadPages(context)) }
    var dock by remember { mutableStateOf(loadDock(context)) }
    var drawerOpen by remember { mutableStateOf(false) }

    // First run ever: pre-fill the dock with familiar apps if present.
    LaunchedEffect(apps) {
        if (!isInitialized(context)) {
            val preferred = listOf(
                "com.google.android.dialer",
                "com.google.android.apps.messaging",
                "com.android.chrome",
                "com.nothing.camera",
                "com.google.android.GoogleCamera",
                "com.google.android.gm",
                "com.google.android.apps.photos",
                "com.android.vending",
            )
            dock = preferred
                .mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
                .map { it.key }
                .take(4)
            saveDock(context, dock)
            setInitialized(context)
        }
    }

    val drawerProgress by animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "drawer",
    )
    // Drive the system's wallpaper blur along with the drawer animation.
    LaunchedEffect(drawerProgress) { setWallpaperBlur(drawerProgress) }
    // Home gesture while already in the launcher: close the drawer.
    LaunchedEffect(homePresses) { drawerOpen = false }
    // Back never exits the launcher; it only closes the drawer.
    BackHandler { if (drawerOpen) drawerOpen = false }

    fun launch(app: AppEntry) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(app.packageName, app.activityName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun appInfo(app: AppEntry) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun uninstall(app: AppEntry) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun addToHome(app: AppEntry) {
        if (pages.any { it.contains(app.key) }) {
            Toast.makeText(context, "${app.label} is already on Home", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = pages.map { it.toMutableList() }.toMutableList()
        val slot = updated.indexOfFirst { it.size < PAGE_CAPACITY }
        if (slot >= 0) updated[slot].add(app.key) else updated.add(mutableListOf(app.key))
        pages = updated
        savePages(context, pages)
        Toast.makeText(context, "${app.label} added to Home", Toast.LENGTH_SHORT).show()
    }

    fun removeFromHome(app: AppEntry) {
        var updated: List<List<String>> = pages.map { page -> page.filter { it != app.key } }
        while (updated.size > 1 && updated.last().isEmpty()) updated = updated.dropLast(1)
        pages = updated
        savePages(context, pages)
    }

    fun addToDock(app: AppEntry) {
        when {
            dock.contains(app.key) ->
                Toast.makeText(context, "${app.label} is already in the dock", Toast.LENGTH_SHORT).show()
            dock.size >= 4 ->
                Toast.makeText(context, "Dock is full — long-press a dock icon to remove it", Toast.LENGTH_SHORT).show()
            else -> {
                dock = dock + app.key
                saveDock(context, dock)
                Toast.makeText(context, "${app.label} added to dock", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeFromDock(app: AppEntry) {
        dock = dock.filter { it != app.key }
        saveDock(context, dock)
    }

    Box(Modifier.fillMaxSize()) {
        // ----- Home layer: blurs, shrinks and dims as the drawer rises over it.
        Box(
            Modifier
                .fillMaxSize()
                .blur((14f * drawerProgress).dp)
                .graphicsLayer {
                    val s = 1f - 0.06f * drawerProgress
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.25f * drawerProgress
                }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x14000000), Color(0x4D000000))
                    )
                )
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -140f) drawerOpen = true },
                    ) { change, amount ->
                        total += amount
                        change.consume()
                    }
                }
        ) {
            HomeLayer(
                pages = pages,
                dock = dock,
                byKey = byKey,
                tilt = tilt,
                onLaunch = { launch(it) },
                onRemoveHome = { removeFromHome(it) },
                onRemoveDock = { removeFromDock(it) },
                onAppInfo = { appInfo(it) },
                onUninstall = { uninstall(it) },
            )
        }

        // ----- App drawer layer.
        if (drawerProgress > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f * drawerProgress))
            )
            AppDrawer(
                apps = apps,
                tilt = tilt,
                progress = drawerProgress,
                onClose = { drawerOpen = false },
                onLaunch = { drawerOpen = false; launch(it) },
                onAddHome = { addToHome(it) },
                onAddDock = { addToDock(it) },
                onAppInfo = { appInfo(it) },
                onUninstall = { uninstall(it) },
            )
        }
    }
}

@Composable
private fun HomeLayer(
    pages: List<List<String>>,
    dock: List<String>,
    byKey: Map<String, AppEntry>,
    tilt: Tilt,
    onLaunch: (AppEntry) -> Unit,
    onRemoveHome: (AppEntry) -> Unit,
    onRemoveDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 10.dp)
    ) {
        val pagerState = rememberPagerState(pageCount = { pages.size })
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val entries = pages[index].mapNotNull { byKey[it] }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Swipe up for all your apps\nLong-press one to add it here",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 6f)
                        ),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp),
                ) {
                    items(entries, key = { it.key }) { app ->
                        GlassAppIcon(
                            app = app,
                            tilt = tilt,
                            showLabel = true,
                            onTap = { onLaunch(app) },
                            menu = listOf(
                                MenuAction("Remove from Home") { onRemoveHome(app) },
                                MenuAction("App info") { onAppInfo(app) },
                                MenuAction("Uninstall") { onUninstall(app) },
                            ),
                        )
                    }
                }
            }
        }

        if (pages.size > 1) PageDots(current = pagerState.currentPage, count = pages.size)
        Spacer(Modifier.height(10.dp))

        val dockApps = dock.mapNotNull { byKey[it] }
        if (dockApps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(30.dp), tilt, tintAlpha = 0.12f)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                dockApps.forEach { app ->
                    DockIcon(
                        app = app,
                        onTap = { onLaunch(app) },
                        menu = listOf(
                            MenuAction("Remove from Dock") { onRemoveDock(app) },
                            MenuAction("App info") { onAppInfo(app) },
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassAppIcon(
    app: AppEntry,
    tilt: Tilt,
    showLabel: Boolean,
    onTap: () -> Unit,
    menu: List<MenuAction>,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .glass(RoundedCornerShape(18.dp), tilt)
                    .combinedClickable(onClick = onTap, onLongClick = { menuOpen = true }),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(46.dp),
                )
            }
            IconMenu(menuOpen, { menuOpen = false }, menu)
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 5f)
                ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockIcon(app: AppEntry, onTap: () -> Unit, menu: List<MenuAction>) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onTap, onLongClick = { menuOpen = true }),
        )
        IconMenu(menuOpen, { menuOpen = false }, menu)
    }
}

@Composable
private fun IconMenu(open: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        actions.forEach { a ->
            DropdownMenuItem(
                text = { Text(a.label) },
                onClick = {
                    onDismiss()
                    a.action()
                },
            )
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.35f))
            )
        }
    }
}

@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    tilt: Tilt,
    progress: Float,
    onClose: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAddHome: (AppEntry) -> Unit,
    onAddDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, ((1f - progress) * screenHeightPx).roundToInt()) }
            .systemBarsPadding()
            .padding(top = 26.dp)
            .padding(horizontal = 10.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .glass(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp), tilt, tintAlpha = 0.13f)
                .padding(horizontal = 14.dp)
        ) {
            // Grab handle — drag it down to close the drawer.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 120f) onClose() },
                        ) { change, amount ->
                            total += amount
                            change.consume()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }

            // Search field in a glass pill.
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(22.dp), tilt, tintAlpha = 0.10f)
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Search apps…",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(6.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp),
            ) {
                items(shown, key = { it.key }) { app ->
                    GlassAppIcon(
                        app = app,
                        tilt = tilt,
                        showLabel = true,
                        onTap = { onLaunch(app) },
                        menu = listOf(
                            MenuAction("Add to Home") { onAddHome(app) },
                            MenuAction("Add to Dock") { onAddDock(app) },
                            MenuAction("App info") { onAppInfo(app) },
                            MenuAction("Uninstall") { onUninstall(app) },
                        ),
                    )
                }
            }
        }
    }
}
