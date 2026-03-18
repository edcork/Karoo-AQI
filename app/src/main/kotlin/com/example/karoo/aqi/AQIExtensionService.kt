package com.example.karoo.aqi

import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.extension.DataTypeImpl

class AQIExtensionService : KarooExtension("aqi_monitor", "1.0") {
    
    // Register the data type with the Hammerhead system
    override val types: List<DataTypeImpl>
        get() = listOf(AqiDataType("aqi_monitor"))

}