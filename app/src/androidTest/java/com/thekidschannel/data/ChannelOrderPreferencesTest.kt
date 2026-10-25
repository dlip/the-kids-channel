package com.thekidschannel.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.thekidschannel.KidsChannelApplication
import com.thekidschannel.MainViewModel
import com.thekidschannel.MainViewModelFactory
import com.thekidschannel.media.ChannelOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelOrderPreferencesTest {
    @Test fun defaultsToMostWatchedAndRestoresSavedOrdering() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as KidsChannelApplication
        val repository = application.rootRepository
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val keys = listOf("channel_order", "channel_order_descending")
        val original = preferences.all.filterKeys { it in keys }
        val store = ViewModelStore()
        try {
            assertTrue(preferences.edit().apply { keys.forEach { remove(it) } }.commit())
            assertEquals(ChannelOrder.WATCH_TIME, repository.channelOrder)
            assertTrue(repository.channelOrderDescending)
            preferences.edit().putString("channel_order", "invalid").commit()
            assertEquals(ChannelOrder.WATCH_TIME, repository.channelOrder)

            instrumentation.runOnMainSync {
                val model = ViewModelProvider(store, MainViewModelFactory(application))[MainViewModel::class.java]
                assertEquals(ChannelOrder.WATCH_TIME, model.uiState.value.channelOrder)
                assertTrue(model.uiState.value.channelOrderDescending)
                model.setChannelOrder(ChannelOrder.DIRECTORY_AGE)
                model.setChannelOrderDescending(false)
                assertEquals(ChannelOrder.DIRECTORY_AGE, model.uiState.value.channelOrder)
                assertFalse(model.uiState.value.channelOrderDescending)
                store.clear()
            }
            assertTrue(preferences.edit().commit())
            assertEquals(ChannelOrder.DIRECTORY_AGE, repository.channelOrder)
            assertFalse(repository.channelOrderDescending)
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(java.io.File(context.applicationInfo.dataDir, "shared_prefs/playback.xml"))
            val strings = document.getElementsByTagName("string")
            val savedOrder = (0 until strings.length).map { strings.item(it) }
                .first { it.attributes.getNamedItem("name").nodeValue == "channel_order" }
            assertEquals("DIRECTORY_AGE", savedOrder.textContent)
            val booleans = document.getElementsByTagName("boolean")
            val savedDirection = (0 until booleans.length).map { booleans.item(it) }
                .first { it.attributes.getNamedItem("name").nodeValue == "channel_order_descending" }
            assertEquals("false", savedDirection.attributes.getNamedItem("value").nodeValue)

            instrumentation.runOnMainSync {
                val restored = ViewModelProvider(store, MainViewModelFactory(application))[MainViewModel::class.java]
                assertEquals(ChannelOrder.DIRECTORY_AGE, restored.uiState.value.channelOrder)
                assertFalse(restored.uiState.value.channelOrderDescending)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            preferences.edit().apply {
                keys.forEach { remove(it) }
                original.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                    }
                }
            }.commit()
        }
    }
}
