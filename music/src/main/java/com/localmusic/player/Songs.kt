package com.localmusic.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable

/** One audio track on the device. */
@Immutable
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
) {
    /** Embedded album-art location, if the file has any. */
    val embeddedArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), albumId
        )
}

/** Read every real music track on the device. */
fun querySongs(context: Context): List<Song> {
    val out = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DISPLAY_NAME,
    )
    // IS_MUSIC filters out ringtones, notification blips, etc.
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    try {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val rawTitle = c.getString(iTitle)
                // Fall back to the file name when a track has no title tag.
                val title = rawTitle?.takeIf { it.isNotBlank() }
                    ?: c.getString(iName)?.substringBeforeLast('.') ?: "Unknown"
                val artist = c.getString(iArtist)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                out += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ),
                    title = title,
                    artist = artist,
                    album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "",
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDur),
                )
            }
        }
    } catch (_: Exception) {
    }
    return out
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
