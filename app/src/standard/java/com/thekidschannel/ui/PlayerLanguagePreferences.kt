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
internal fun androidx.media3.ui.SubtitleView.applyFontSize(percent: Int) {
    setApplyEmbeddedFontSizes(false)
    setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * percent / 100f)
}

internal fun subtitleFontSizeHint(): String = "Text subtitles only; picture subtitles keep their original size."
