package com.thekidschannel.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.thekidschannel.KidsChannelApplication
import org.junit.Assert.*
import org.junit.Test

class PlaybackLanguagePreferencesTest {
    @Test fun defaultsAreEnglishAndPreferencesAreSavedToDisk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val keys = listOf("audio_language", "subtitle_language", "subtitles_enabled", "subtitle_font_size")
        val original = prefs.all.filterKeys { it in keys }
        val repository = (context.applicationContext as KidsChannelApplication).rootRepository
        try {
            prefs.edit().apply { keys.forEach { remove(it) } }.commit()
            assertEquals("en", repository.audioLanguage)
            assertEquals("en", repository.subtitleLanguage)
            assertTrue(repository.subtitlesEnabled)
            assertEquals(100, repository.subtitleFontSize)
            repository.audioLanguage = "ja"
            repository.subtitleLanguage = "fr"
            repository.subtitlesEnabled = false
            repository.subtitleFontSize = 150
            // A synchronous commit waits for the preceding asynchronous preference writes.
            assertTrue(prefs.edit().commit())
            val file = java.io.File(context.applicationInfo.dataDir, "shared_prefs/playback.xml")
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file)
            val strings = document.getElementsByTagName("string")
            val saved = (0 until strings.length).associate { index ->
                val node = strings.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
            assertEquals("ja", saved["audio_language"])
            assertEquals("fr", saved["subtitle_language"])
            val integers = document.getElementsByTagName("int")
            val fontSize = (0 until integers.length).map { integers.item(it) }
                .first { it.attributes.getNamedItem("name").nodeValue == "subtitle_font_size" }
            assertEquals("150", fontSize.attributes.getNamedItem("value").nodeValue)
            val booleans = document.getElementsByTagName("boolean")
            val enabled = (0 until booleans.length).map { booleans.item(it) }
                .first { it.attributes.getNamedItem("name").nodeValue == "subtitles_enabled" }
            assertEquals("false", enabled.attributes.getNamedItem("value").nodeValue)
        } finally {
            prefs.edit().apply {
                keys.forEach { remove(it) }
                original.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                    }
                }
            }.commit()
        }
    }
}
