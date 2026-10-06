package com.thekidschannel.ui

import androidx.media3.common.C
import androidx.media3.common.Player
import com.thekidschannel.MainUiState

internal fun Player.applyLanguagePreferences(state: MainUiState) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setPreferredAudioLanguage(state.audioLanguage)
        .setPreferredTextLanguage(state.subtitleLanguage)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !state.subtitlesEnabled)
        .build()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun androidx.media3.ui.SubtitleView.applySubtitleAppearance(percent: Int) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setStyle(androidx.media3.ui.CaptionStyleCompat(
        android.graphics.Color.WHITE,
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        android.graphics.Color.BLACK,
        null,
    ))
    setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * percent / 100f)
}

internal fun subtitleFontSizeHint(): String = "Text subtitles only; picture subtitles keep their original size."
