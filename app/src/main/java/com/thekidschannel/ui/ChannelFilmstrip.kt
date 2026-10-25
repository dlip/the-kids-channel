package com.thekidschannel.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thekidschannel.media.ChannelFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ChannelFilmstrip(
    channels: List<ChannelFolder>,
    selectedChannelUri: String?,
    previewUpdatedAt: Long,
    onChannelPreviewPath: suspend (String) -> String?,
    onSelectChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.85f))) {
        Text("Channels", color = Color.White, modifier = Modifier.padding(16.dp))
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = channels.indexOfFirst { it.uri == selectedChannelUri }
                .coerceAtLeast(0),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(channels, key = { it.uri }) { channel ->
                val preview by produceState<ImageBitmap?>(null, channel.uri, previewUpdatedAt) {
                    value = withContext(Dispatchers.IO) {
                        onChannelPreviewPath(channel.uri)?.let { path ->
                            BitmapFactory.decodeFile(path)?.asImageBitmap()
                        }
                    }
                }
                val selected = channel.uri == selectedChannelUri
                val shape = RoundedCornerShape(8.dp)
                Column(
                    Modifier.width(144.dp)
                        .clip(shape)
                        .background(Color.DarkGray)
                        .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelectChannel(channel.uri) })
                        .padding(4.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(4.dp))) {
                        preview?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                    Text(
                        channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
