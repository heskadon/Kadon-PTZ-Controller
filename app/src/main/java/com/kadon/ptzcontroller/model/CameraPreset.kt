package com.kadon.ptzcontroller.model

import android.graphics.Bitmap

// Data class to hold preset information (updated with 'name' field)
// NOTE: This class was renamed to Preset. If you were using PresetData elsewhere,
// ensure you update those references as well.
data class CameraPreset(
    val number: Int = 0,
    var imageUrl: String? = null,
    var isSaved: Boolean = false,
    var name: String = "Preset $number", // Added a name field with a default value
    var imageBitmap: Bitmap? = null, // For holding the actual image
    val base64Image: String = "",
    val position: PtzPosition? = null // Only used for virtual presets
)
