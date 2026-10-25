package com.thekidschannel.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.thekidschannel.data.RootEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class VideoItem(
    val uri: Uri,
    val name: String,
)

data class ChannelFolder(
    val rootUri: String,
    val uri: String,
    val name: String,
    val lastModifiedMs: Long? = null,
)

class ChannelScanner(private val context: Context) {
    suspend fun discoverChannels(roots: List<RootEntity>): List<ChannelFolder> =
        withContext(Dispatchers.IO) {
            roots.flatMap { root ->
                val rootUri = root.uri.toUri()
                val rootDirectory = DocumentsContract.buildDocumentUriUsingTree(
                    rootUri, DocumentsContract.getTreeDocumentId(rootUri),
                )
                listChildren(rootDirectory)
                    .filter { it.mimeType == Document.MIME_TYPE_DIR }
                    .sortedWith { left, right ->
                        NaturalOrder.compare(left.name.orEmpty(), right.name.orEmpty())
                    }
                    .map { directory ->
                        ChannelFolder(
                            rootUri = root.uri,
                            uri = directory.uri.toString(),
                            name = directory.name ?: "Channel",
                            lastModifiedMs = directory.lastModifiedMs,
                        )
                    }
            }
        }

    suspend fun scan(channel: ChannelFolder): List<VideoItem> = withContext(Dispatchers.IO) {
        buildList { collectVideos(channel.uri.toUri(), mutableSetOf(), this) }
    }

    suspend fun createPreview(channel: ChannelFolder): Bitmap? = withContext(Dispatchers.IO) {
        for (video in scan(channel)) {
            ensureActive()
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(context, video.uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        640,
                        360,
                    )
                } else {
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
            if (bitmap != null) return@withContext bitmap
        }
        null
    }

    private fun collectVideos(
        directory: Uri,
        visitedDirectories: MutableSet<Uri>,
        videos: MutableList<VideoItem>,
    ) {
        if (!visitedDirectories.add(directory)) return

        val children = listChildren(directory)
            .sortedWith { left, right ->
                NaturalOrder.compare(left.name.orEmpty(), right.name.orEmpty())
            }

        children.forEach { child ->
            when {
                child.mimeType == Document.MIME_TYPE_DIR -> collectVideos(child.uri, visitedDirectories, videos)
                child.isVideo() -> videos += VideoItem(
                    uri = child.uri,
                    name = child.name ?: "Video",
                )
            }
        }
    }

    private data class DirectoryEntry(
        val uri: Uri,
        val name: String?,
        val mimeType: String?,
        val lastModifiedMs: Long?,
    )

    private fun listChildren(directory: Uri): List<DirectoryEntry> = runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directory, DocumentsContract.getDocumentId(directory),
        )
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val typeIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val modifiedIndex = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
            buildList {
                while (cursor.moveToNext()) {
                    add(DirectoryEntry(
                        DocumentsContract.buildDocumentUriUsingTree(directory, cursor.getString(idIndex)),
                        cursor.getString(nameIndex),
                        cursor.getString(typeIndex),
                        if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                            cursor.getLong(modifiedIndex).takeIf { it > 0 }
                        } else null,
                    ))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList()).filterNot {
        it.mimeType == Document.MIME_TYPE_DIR && it.name?.startsWith('.') == true
    }

    private fun DirectoryEntry.isVideo(): Boolean {
        if (mimeType?.startsWith("video/") == true) return true
        val extension = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    private companion object {
        val VIDEO_EXTENSIONS = setOf(
            "3gp",
            "avi",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mpeg",
            "mpg",
            "ts",
            "webm",
        )
    }
}
