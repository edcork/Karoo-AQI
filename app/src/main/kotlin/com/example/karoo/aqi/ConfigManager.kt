package com.example.karoo.aqi

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.karoo.aqi.core.AqiConfig
import com.example.karoo.aqi.core.CoreParsers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ConfigManager(
    private val appContext: Context,
) {
    data class State(
        val config: AqiConfig?,
        val error: String? = null,
    ) {
        val setupRequired: Boolean get() = config?.apiKey.isNullOrBlank()
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val _state = MutableStateFlow(State(config = null, error = "Not loaded"))
    val state: StateFlow<State> = _state.asStateFlow()

    private var downloadsObserver: FileObserver? = null
    private var mediaObserver: ContentObserver? = null

    fun start() {
        reload()
        startWatchingDownloadsDir()
        startWatchingMediaStore()
    }

    fun stop() {
        downloadsObserver?.stopWatching()
        downloadsObserver = null
        mediaObserver?.let { appContext.contentResolver.unregisterContentObserver(it) }
        mediaObserver = null
    }

    fun reload() {
        scope.launch {
            val json = readConfigJson(appContext) ?: run {
                _state.value = State(config = null, error = "Missing /Downloads/aqi_config.json")
                return@launch
            }
            val parsed = CoreParsers.parseConfig(json).getOrNull()
            if (parsed == null) {
                _state.value = State(config = null, error = "Invalid config JSON")
            } else {
                _state.value = State(config = parsed, error = null)
            }
        }
    }

    private fun startWatchingDownloadsDir() {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsObserver?.stopWatching()
        downloadsObserver = object : FileObserver(downloadsDir.absolutePath, CREATE or MOVED_TO or MODIFY or CLOSE_WRITE or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (path.equals(CONFIG_FILE_NAME, ignoreCase = true)) reload()
            }
        }.also { it.startWatching() }
    }

    private fun startWatchingMediaStore() {
        val resolver = appContext.contentResolver
        val uri: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        mediaObserver?.let { resolver.unregisterContentObserver(it) }
        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reload()
            }
        }.also { resolver.registerContentObserver(uri, true, it) }
    }

    companion object {
        private const val CONFIG_FILE_NAME = "aqi_config.json"

        private fun readConfigJson(context: Context): String? {
            // Prefer MediaStore so this works better under scoped storage.
            readViaMediaStore(context.contentResolver)?.let { return it }

            // Fallback to direct path access (older devices / permissive storage).
            val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), CONFIG_FILE_NAME)
            if (!f.exists()) return null
            return runCatching { f.readText() }.getOrNull()
        }

        private fun readViaMediaStore(resolver: ContentResolver): String? {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            val args = arrayOf(CONFIG_FILE_NAME)
            val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            val cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, sort) ?: return null
            cursor.use {
                if (!it.moveToFirst()) return null
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val contentUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                return resolver.openInputStream(contentUri)?.use { input ->
                    input.bufferedReader().readText()
                }
            }
        }
    }
}

