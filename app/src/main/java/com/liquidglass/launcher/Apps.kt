package com.liquidglass.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray

/** One installed app: its name, where it lives, and its icon. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap,
) {
    /** Stable identifier used when saving the home/dock layout. */
    val key: String get() = "$packageName/$activityName"
}

/** Ask Android for every app a normal launcher would show. */
fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launchable, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .mapNotNull { ri ->
            try {
                AppEntry(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    activityName = ri.activityInfo.name,
                    icon = ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                )
            } catch (_: Exception) {
                null
            }
        }
        .sortedBy { it.label.lowercase() }
}

// ---------------------------------------------------------------------------
// Saving and loading the layout (which apps are on which page, and the dock).
// Stored on the phone in the app's private settings file.
// ---------------------------------------------------------------------------

private fun prefs(context: Context) =
    context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

fun loadPages(context: Context): List<List<String>> {
    val raw = prefs(context).getString("home_pages", null) ?: return listOf(emptyList())
    return try {
        val outer = JSONArray(raw)
        val pages = (0 until outer.length()).map { i ->
            val inner = outer.getJSONArray(i)
            (0 until inner.length()).map { j -> inner.getString(j) }
        }
        pages.ifEmpty { listOf(emptyList()) }
    } catch (_: Exception) {
        listOf(emptyList())
    }
}

fun savePages(context: Context, pages: List<List<String>>) {
    val outer = JSONArray()
    pages.forEach { page -> outer.put(JSONArray(page)) }
    prefs(context).edit().putString("home_pages", outer.toString()).apply()
}

fun loadDock(context: Context): List<String> {
    val raw = prefs(context).getString("dock", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveDock(context: Context, dock: List<String>) {
    prefs(context).edit().putString("dock", JSONArray(dock).toString()).apply()
}

fun isInitialized(context: Context) = prefs(context).getBoolean("initialized", false)

fun setInitialized(context: Context) {
    prefs(context).edit().putBoolean("initialized", true).apply()
}
