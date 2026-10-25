package com.thekidschannel.media

enum class ChannelOrder(val label: String) {
    WATCH_TIME("Watched time"),
    ALPHABETICAL("Alphabetical"),
    DIRECTORY_AGE("Directory age"),
}

fun sortChannels(
    channels: List<ChannelFolder>,
    watchTimeMs: Map<String, Long>,
    order: ChannelOrder,
    descending: Boolean,
): List<ChannelFolder> {
    val alphabetical = Comparator<ChannelFolder> { left, right ->
        NaturalOrder.compare(left.name, right.name)
    }.thenBy { it.uri }
    val primary = when (order) {
        ChannelOrder.WATCH_TIME -> compareBy<ChannelFolder> { watchTimeMs[it.uri] ?: 0L }
        ChannelOrder.ALPHABETICAL -> alphabetical
        ChannelOrder.DIRECTORY_AGE -> compareBy<ChannelFolder> { it.lastModifiedMs ?: 0L }
    }
    val directed = if (descending) primary.reversed() else primary
    val comparator = if (order == ChannelOrder.DIRECTORY_AGE) {
        compareBy<ChannelFolder> { it.lastModifiedMs == null }.then(directed)
    } else directed
    return channels.sortedWith(comparator.then(alphabetical))
}
