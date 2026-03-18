package com.example.karoo.customfield

import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.extension.DataTypeImpl

class PeanutExtensionService : KarooExtension("peanut_locator", "1.0") {
    
    // Register the data type with the Hammerhead system
    override val types: List<DataTypeImpl>
        get() = listOf(PeanutDataType("peanut_locator"))

}