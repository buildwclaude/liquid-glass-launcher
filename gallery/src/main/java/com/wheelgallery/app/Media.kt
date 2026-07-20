package com.wheelgallery.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore
import androidx.compose.runtime.Immutable

/** One photo or video on the phone. */
@Immutable
data class MediaItem(
    val uri: Uri,
    val dateMillis: Long,
    val bucket: String,
    val name: String,
    val isVideo: Boolean,
)

/** Read every photo and video the system knows about, newest first. */
fun queryMedia(context: Context): List<MediaItem> {
    val out = mutableListOf<MediaItem>()
    val projection = arrayOf(
        BaseColumns._ID,
        MediaStore.MediaColumns.DATE_TAKEN,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.DISPLAY_NAME,
    )

    fun scan(base: Uri, isVideo: Boolean) {
        try {
            context.contentResolver.query(base, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(BaseColumns._ID)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (c.moveToNext()) {
                    // Prefer the moment the photo was taken; fall back to
                    // when the file appeared on the phone.
                    val taken = c.getLong(iTaken).takeIf { it > 0 }
                        ?: (c.getLong(iAdded) * 1000)
                    out += MediaItem(
                        uri = ContentUris.withAppendedId(base, c.getLong(iId)),
                        dateMillis = taken,
                        bucket = c.getString(iBucket) ?: "Other",
                        name = c.getString(iName) ?: "",
                        isVideo = isVideo,
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
    scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
    return out.sortedByDescending { it.dateMillis }
}

fun loadFavorites(context: Context): Set<String> =
    context.getSharedPreferences("gallery", Context.MODE_PRIVATE)
        .getStringSet("favorites", emptySet()) ?: emptySet()

fun saveFavorites(context: Context, favorites: Set<String>) {
    context.getSharedPreferences("gallery", Context.MODE_PRIVATE)
        .edit().putStringSet("favorites", favorites).apply()
}
