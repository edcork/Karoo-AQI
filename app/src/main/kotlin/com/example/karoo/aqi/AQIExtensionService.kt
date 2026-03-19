package com.example.karoo.aqi

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.extension.DataTypeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AQIExtensionService : KarooExtension("aqi_monitor", "1.0") {

    private val karooSystem by lazy { KarooSystemService(this) }
    private val configManager by lazy { ConfigManager(applicationContext) }
    private var serviceJob: Job? = null

    override val types: List<DataTypeImpl> by lazy {
        listOf(AqiDataType(karooSystem = karooSystem, extensionId = extension, configManager = configManager))
    }

    override fun onCreate() {
        super.onCreate()
        configManager.start()
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { /* no-op */ }
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        configManager.stop()
        karooSystem.disconnect()
        super.onDestroy()
    }

}