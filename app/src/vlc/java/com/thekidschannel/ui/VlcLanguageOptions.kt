package com.thekidschannel.ui

import com.thekidschannel.MainUiState

internal fun vlcLanguageOptions(state: MainUiState): List<String> = mutableListOf(
    "--audio-language=${state.audioLanguage},any",
    "--sub-language=${state.subtitleLanguage},none",
    "--sub-text-scale=${state.subtitleFontSize}",
    if (state.subtitlesEnabled) "--spu" else "--no-spu",
)

internal fun subtitleFontSizeHint(): String = "Plain-text subtitles only; styled and picture subtitles keep their original size."
