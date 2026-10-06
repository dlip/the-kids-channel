package com.thekidschannel.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.thekidschannel.media.ChannelFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class RootRepository(
    private val context: Context,
    private val rootDao: RootDao,
    private val progressDao: ChannelProgressDao,
    private val statsDao: ChannelStatsDao,
) {
    val roots: Flow<List<RootEntity>> = rootDao.observeAll()
    val channelStats: Flow<List<ChannelStatsEntity>> = statsDao.observeAll()

    private val preferences =
        context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    var selectedChannelUri: String?
        get() = preferences.getString(SELECTED_CHANNEL_KEY, null)
        set(value) {
            preferences.edit { putString(SELECTED_CHANNEL_KEY, value) }
        }

    var normalizeAudio: Boolean
        get() = preferences.getBoolean(NORMALIZE_AUDIO_KEY, true)
        set(value) {
            preferences.edit { putBoolean(NORMALIZE_AUDIO_KEY, value) }
        }

    var audioLanguage: String
        get() = preferences.getString("audio_language", "en") ?: "en"
        set(value) { preferences.edit { putString("audio_language", value) } }

    var subtitleLanguage: String
        get() = preferences.getString("subtitle_language", "en") ?: "en"
        set(value) { preferences.edit { putString("subtitle_language", value) } }

    var subtitlesEnabled: Boolean
        get() = preferences.getBoolean("subtitles_enabled", true)
        set(value) { preferences.edit { putBoolean("subtitles_enabled", value) } }

    var subtitleFontSize: Int
        get() = preferences.getInt("subtitle_font_size", 100).coerceIn(50, 200)
        set(value) { preferences.edit { putInt("subtitle_font_size", value.coerceIn(50, 200)) } }

    suspend fun addRoot(uri: Uri) {
        val name = DocumentFile.fromTreeUri(context, uri)?.name
            ?.takeIf(String::isNotBlank)
            ?: "Root folder"
        rootDao.insert(
            RootEntity(
                uri = uri.toString(),
                name = name,
                sortOrder = rootDao.nextSortOrder(),
            ),
        )
    }

    suspend fun setRootEnabled(uri: String, enabled: Boolean) = rootDao.setEnabled(uri, enabled)

    suspend fun addWatchTime(channel: ChannelFolder, elapsedMs: Long) {
        statsDao.recordWatchTime(
            ChannelStatsEntity(channel.uri, channel.rootUri, channel.name),
            elapsedMs,
        )
    }

    suspend fun rememberChannels(channels: List<ChannelFolder>) {
        channels.forEach { channel ->
            statsDao.rememberChannel(ChannelStatsEntity(channel.uri, channel.rootUri, channel.name))
        }
    }

    suspend fun removeRoot(uri: String) {
        val channelUris = progressDao.getChannelUrisForRoot(uri)
        progressDao.deleteForRoot(uri)
        withContext(Dispatchers.IO) {
            channelUris.forEach { channelUri ->
                channelPreviewDirectory(channelUri).deleteRecursively()
                previewFile(channelUri).delete()
                File(File(context.filesDir, LEGACY_PREVIEW_DIRECTORY), "${hash(channelUri)}.jpg")
                    .delete()
            }
        }
        rootDao.delete(uri)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    suspend fun getProgress(channelUri: String): ChannelProgressEntity? =
        progressDao.get(channelUri)

    suspend fun saveProgress(
        rootUri: String,
        channelUri: String,
        videoUri: String,
        videoIndex: Int,
        positionMs: Long,
    ) {
        progressDao.save(
            ChannelProgressEntity(
                channelUri = channelUri,
                rootUri = rootUri,
                currentVideoUri = videoUri,
                currentVideoIndex = videoIndex,
                positionMs = positionMs.coerceAtLeast(0),
            ),
        )
    }

    fun getPreviewPath(channelUri: String): String? =
        previewFile(channelUri).takeIf(File::isFile)?.absolutePath

    suspend fun savePreview(channelUri: String, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            val destination = previewFile(channelUri)
            val directory = destination.parentFile ?: return@withContext null
            if (!directory.exists() && !directory.mkdirs()) return@withContext null
            val temporary = File(directory, "${destination.name}.tmp")
            val saved = runCatching {
                temporary.outputStream().buffered().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
                }
            }.getOrDefault(false)
            if (!saved) {
                temporary.delete()
                return@withContext null
            }
            if (!temporary.renameTo(destination)) {
                runCatching { temporary.copyTo(destination, overwrite = true) }
                    .onFailure {
                        temporary.delete()
                        return@withContext null
                    }
                temporary.delete()
            }
            destination.absolutePath
        }

    private fun previewFile(channelUri: String): File =
        File(File(context.filesDir, PREVIEW_DIRECTORY), "${hash(channelUri)}.jpg")

    private fun channelPreviewDirectory(channelUri: String): File =
        File(File(context.filesDir, PREVIEW_DIRECTORY), hash(channelUri))

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val SELECTED_CHANNEL_KEY = "selected_channel_uri"
        const val NORMALIZE_AUDIO_KEY = "normalize_audio"
        const val PREVIEW_DIRECTORY = "channel-previews-v2"
        const val LEGACY_PREVIEW_DIRECTORY = "channel-previews"
    }
}
