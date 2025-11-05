package com.kadon.ptzcontroller

// ByteBuffer import might be unused now in sendCommand, but could be used elsewhere.
// Keeping it for now unless a linter/compiler flags it for the whole file.
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kadon.ptzcontroller.model.CameraPreset
import com.kadon.ptzcontroller.model.FocusDirection
import com.kadon.ptzcontroller.model.PanTiltInput
import com.kadon.ptzcontroller.model.ZoomDirection
import com.kadon.ptzcontroller.ui.component.AboutScreen
import com.kadon.ptzcontroller.ui.component.FocusControlsCard
import com.kadon.ptzcontroller.ui.component.PanTiltControlsCard
import com.kadon.ptzcontroller.ui.component.PresetControlsCard
import com.kadon.ptzcontroller.ui.component.PtzCommandReceiverScreen
import com.kadon.ptzcontroller.ui.component.ZoomControlsCard
import com.kadon.ptzcontroller.ui.theme.MyAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.io.encoding.Base64
import kotlin.math.min
import kotlin.math.sqrt

// DataStore instance for presets (defined at top-level)
val Context.presetDataStore: DataStore<Preferences> by preferencesDataStore(name = "preset_data")

// Class to manage DataStore operations for presets
class PresetDataStoreManager(private val context: Context) {

    private val presetKey = stringPreferencesKey("presets_json")
    private val gson = Gson()

    suspend fun savePresets(cameraPresets: List<CameraPreset>) {
        context.presetDataStore.edit { preferences ->
            val json = gson.toJson(cameraPresets)
            preferences[presetKey] = json
            //Timber.d("Presets saved to DataStore: $json")
        }
    }

    suspend fun loadPresets(): List<CameraPreset> {
        val presetsJson = context.presetDataStore.data.map { preferences ->
            preferences[presetKey] ?: "[]" // Default to empty JSON array
        }.first() // Get the first emitted value

        return try {
            val type = object : TypeToken<List<CameraPreset>>() {}.type
            val loadedPresets = gson.fromJson<List<CameraPreset>>(presetsJson, type)
            Timber.d("Presets loaded from DataStore: ${loadedPresets.size} items")
            loadedPresets
        } catch (e: Exception) {
            Timber.e(e, "Error loading presets from DataStore JSON: $presetsJson")
            emptyList() // Return empty list on error
        }
    }
}


// MainActivity.kt - Main activity for the PTZ Camera Control App
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) { // Only plant Timber in debug builds
            Timber.plant(Timber.DebugTree())
        }

        setContent {
            MyAppTheme {
                MainScreen()
            }
        }
    }
}

// Define an interface for the ViewModel's public API
interface IPtzCameraViewModel {
    val cameraIpAddress: MutableState<String>
    val cameraPort: MutableState<Int>
    val rtspUrl: MutableState<String>
    val statusMessage: MutableState<String>
    val panTiltSpeed: MutableFloatState
    val zoomSpeed: MutableFloatState
    val focusSpeed: MutableFloatState
    val cameraPresets: SnapshotStateList<CameraPreset>

    fun startContinuousPanTilt(offsetX: Float, offsetY: Float, maxJoystickRadius: Float, isAnalog: Boolean = true)
    fun stopContinuousPanTilt()
    fun startContinuousZoom(direction: ZoomDirection)
    fun stopContinuousZoom()
    fun resetZoom()
    fun setAutoFocus()
    fun setManualFocus()
    fun startContinuousFocus(direction: FocusDirection)
    fun stopContinuousFocus()
    fun setPreset(presetNumber: Int)
    fun recallPreset(presetNumber: Int)
    fun captureImageForPreset(presetNumber: Int)
    fun updatePresetName(presetNumber: Int, newName: String)
    fun goToHomePosition()
}

// ViewModel to manage camera state and network operations
@OptIn(FlowPreview::class)
class PtzCameraViewModel(application: Application) :
    AndroidViewModel(application), IPtzCameraViewModel { // Changed to AndroidViewModel
    // Mutable state for the camera's IP address
    override val cameraIpAddress = mutableStateOf("192.168.1.98") // Default IP
    override val cameraPort = mutableIntStateOf(52381)
    override val rtspUrl = mutableStateOf("rtsp://192.168.1.98:554/stream/sub")

    // Mutable state for status messages displayed to the user
    override val statusMessage = mutableStateOf("Ready")

    // Add this with your other properties
    private val _panTiltOffset = MutableStateFlow(Pair(0f, 0f))

    // Speed controls
    override val panTiltSpeed =
        mutableFloatStateOf(5f) // Default pan/tilt speed (0x01-0x18 for pan, 0x01-0x14 for tilt)
    override val zoomSpeed = mutableFloatStateOf(7f)    // Default zoom speed (0-7 for variable)
    override val focusSpeed = mutableFloatStateOf(7f)   // Default focus speed (0-7 for variable)

    // DataStore manager instance
    private val presetDataStoreManager: PresetDataStoreManager?

    // List to hold all preset data (modified initialization to load from DataStore)
    override val cameraPresets = androidx.compose.runtime.mutableStateListOf<CameraPreset>()

    private val _panTiltInput = MutableStateFlow(PanTiltInput(0f, 0f, isAnalog = true))

    init {
        if (application.applicationContext != null) {
            presetDataStoreManager = PresetDataStoreManager(application.applicationContext)
            viewModelScope.launch {
                val loaded = presetDataStoreManager.loadPresets()
                if (loaded.isNotEmpty()) {
                    val existingNumbers = loaded.map { it.number }.toSet()
                    cameraPresets.addAll(loaded)
                    for (i in 0 until 15) { // Iterate 0 to 14 for preset numbers 1 to 15
                        if ((i + 1) !in existingNumbers) {
                            cameraPresets.add(CameraPreset(number = i + 1))
                        }
                    }
                    cameraPresets.sortBy { it.number }
                } else {
                    for (i in 0 until 15) { // Initialize 15 presets (0 to 14 for numbers 1 to 15)
                        cameraPresets.add(CameraPreset(number = i + 1))
                    }
                }
                Timber.d("ViewModel initialized. Presets count: ${cameraPresets.size}")
            }
        } else {
            for (i in 0 until 15) {
                cameraPresets.add(CameraPreset(number = i + 1))
            }
            presetDataStoreManager = null
            Timber.w("PtzCameraViewModel initialized without DataStoreManager (likely preview).")
        }

        viewModelScope.launch(Dispatchers.IO) {
            _panTiltInput
                .sample(50)
                .collect { input ->
                    val offsetX = input.offsetX
                    val offsetY = input.offsetY

                    // Stop command for zero offset
                    if (offsetX == 0f && offsetY == 0f) {
                        sendPanTiltCommand(
                            panTiltSpeed.floatValue.toInt().toByte(),
                            panTiltSpeed.floatValue.toInt().toByte(),
                            0x03,
                            0x03
                        )
                        Timber.d("StateFlow: Stopped pan/tilt.")
                        return@collect
                    }

                    var snappedOffsetX = offsetX
                    var snappedOffsetY = offsetY

                    // Only apply snap logic if this is an analog (joystick) input
                    if (input.isAnalog) {
                        val snapThreshold = 24.0f
                        if (kotlin.math.abs(offsetX) > kotlin.math.abs(offsetY)) {
                            if (kotlin.math.abs(offsetY) < snapThreshold) {
                                snappedOffsetY = 0f
                            }
                        } else {
                            if (kotlin.math.abs(offsetX) < snapThreshold) {
                                snappedOffsetX = 0f
                            }
                        }
                    }
                    // Use snapped values for command
                    /*val magnitude =
                        sqrt(snappedOffsetX * snappedOffsetX + snappedOffsetY * snappedOffsetY)
                    val maxJoystickRadius = 100f // Use a reasonable default
                    val normalizedMagnitude = min(1f, magnitude / maxJoystickRadius)
                    val effectiveSpeed =
                        (normalizedMagnitude * (panTiltSpeed.floatValue - 1) + 1).coerceAtLeast(1f)
                            .toInt().toByte()*/
                    val effectiveSpeed: Byte = if (input.isAnalog) {
                        // For joystick: use normalized magnitude based on how far thumb is from center
                        val magnitude = sqrt(snappedOffsetX * snappedOffsetX + snappedOffsetY * snappedOffsetY)
                        val maxJoystickRadius = 100f
                        val normalizedMagnitude = min(1f, magnitude / maxJoystickRadius)
                        (normalizedMagnitude * (panTiltSpeed.floatValue - 1) + 1).coerceAtLeast(1f).toInt().toByte()
                    } else {
                        // For buttons: use full current panTiltSpeed
                        panTiltSpeed.floatValue.coerceAtLeast(1f).toInt().toByte()
                    }

                    val panDirection: Byte = when {
                        snappedOffsetX > 0f -> 0x02  // Right
                        snappedOffsetX < 0f -> 0x01  // Left
                        else -> 0x03                // Stop
                    }
                    val tiltDirection: Byte = when {
                        snappedOffsetY > 0f -> 0x02  // Down
                        snappedOffsetY < 0f -> 0x01  // Up
                        else -> 0x03                // Stop
                    }

                    sendPanTiltCommand(effectiveSpeed, effectiveSpeed, panDirection, tiltDirection)
                }
        }
    }

    // VISCA UDP port
    private val viscaUdpPort = 52381

    // Jobs for continuous movement (pan/tilt and zoom)
    private var currentPanTiltJob: Job? = null
    private var currentZoomJob: Job? = null
    private var currentFocusJob: Job? = null // Job for continuous focus

    fun updatePanTilt(offsetX: Float, offsetY: Float, isAnalog: Boolean = true) {
        _panTiltInput.value = PanTiltInput(offsetX, offsetY, isAnalog)
    }

    // Function to save all presets to DataStore
    fun saveAllPresets() {
        viewModelScope.launch {
            presetDataStoreManager?.let { manager ->
                manager.savePresets(cameraPresets.toList()) // Save a copy of the list
                Timber.d("All presets saved to DataStore.")
            } ?: Timber.w("Cannot save presets: DataStoreManager is null.")
        }
    }

    // Function to update a preset's name and save to DataStore
    override fun updatePresetName(presetNumber: Int, newName: String) {
        val index = cameraPresets.indexOfFirst { it.number == presetNumber }
        if (index != -1) {
            cameraPresets[index] = cameraPresets[index].copy(name = newName)
            saveAllPresets()
            statusMessage.value = "Preset $presetNumber name updated to: $newName"
            Timber.d("Preset $presetNumber name updated to: $newName")
        } else {
            statusMessage.value = "Invalid preset number for updating name."
            Timber.w("Invalid preset number for updatePresetName: $presetNumber")
        }
    }

    // Function to send a VISCA command over UDP
    private fun sendCommand(command: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("Sending RAW VISCA command: ${command.toHexString()}")

                // All local variables for VISCA-over-IP wrapper are removed.
                // Increments of 'sequenceNumber' that were part of the wrapper logic are also removed.
                // The class member 'sequenceNumber' itself remains (and its increments elsewhere),
                // as it might be used by VISCA command structures themselves or for other ViewModel logic.

                val ipAddress = cameraIpAddress.value
                val address = InetAddress.getByName(ipAddress)
                val socket = DatagramSocket()
                // Send the raw command directly
                val packet = DatagramPacket(command, command.size, address, viscaUdpPort)
                socket.send(packet)
                socket.close()

                withContext(Dispatchers.Main) {
                    statusMessage.value = "Command sent to $ipAddress"
                    Timber.d("Command successfully sent to $ipAddress")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage.value = "Error sending command: ${e.message}"
                    Timber.e(e, "Error sending command: ${e.message}")
                }
                e.printStackTrace()
            }
        }
    }

    private fun ByteArray.toHexString() =
        joinToString(separator = " ") { byte -> "%02x".format(byte) }

    private fun sendPanTiltCommand(
        panSpeed: Byte,
        tiltSpeed: Byte,
        panDirection: Byte,
        tiltDirection: Byte
    ) {
        val command = byteArrayOf(
            0x81.toByte(),
            0x01,
            0x06,
            0x01,
            panSpeed,
            tiltSpeed,
            panDirection,
            tiltDirection,
            0xFF.toByte()
        )
        sendCommand(command)
    }

    override fun startContinuousPanTilt(offsetX: Float, offsetY: Float, maxJoystickRadius: Float, isAnalog: Boolean) {
        updatePanTilt(offsetX, offsetY, isAnalog)
        //updatePanTilt(offsetX, offsetY)

        /*currentPanTiltJob?.cancel()
        currentPanTiltJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val magnitude = sqrt(offsetX * offsetX + offsetY * offsetY)
                val normalizedMagnitude =
                    if (maxJoystickRadius > 0) min(1f, magnitude / maxJoystickRadius) else 0f

                val effectivePanSpeed =
                    (normalizedMagnitude * (panTiltSpeed.floatValue - 1) + 1).toInt().toByte()
                val effectiveTiltSpeed =
                    (normalizedMagnitude * (panTiltSpeed.floatValue - 1) + 1).toInt().toByte()

                val panDirection: Byte = when {
                    offsetX > 0.1f -> 0x02 // Right ✅
                    offsetX < -0.1f -> 0x01 // Left ✅
                    else -> 0x00 // Stop
                }
                val tiltDirection: Byte = when {
                    offsetY > 0.1f -> 0x02 // Down (Canvas Y is positive downwards, VISCA 0x02 is Down)
                    offsetY < -0.1f -> 0x01 // Up (Canvas Y is negative upwards, VISCA 0x01 is Up)
                    else -> 0x00 // Stop
                }

                // ✅ Send command unconditionally
                sendPanTiltCommand(
                    effectivePanSpeed,
                    effectiveTiltSpeed,
                    panDirection,
                    tiltDirection
                )
                delay(100) // Send command every 100ms
            }
        }*/
    }

    override fun stopContinuousPanTilt() {
        updatePanTilt(0f, 0f, isAnalog = true)
        //updatePanTilt(0f, 0f)

        /*currentPanTiltJob?.cancel()
        sendPanTiltCommand(0x00, 0x00, 0x00, 0x00)
        Timber.d("Stopped continuous pan/tilt movement.")*/
    }

    private fun sendZoomCommand(zoomValue: Byte) {
        val command = byteArrayOf(0x81.toByte(), 0x01, 0x04, 0x07, zoomValue, 0xFF.toByte())
        sendCommand(command)
    }

    override fun startContinuousZoom(direction: ZoomDirection) {
        currentZoomJob?.cancel()
        currentZoomJob = viewModelScope.launch(Dispatchers.IO) {
            val speed = zoomSpeed.floatValue.toInt().toByte()
            val zoomValue = when (direction) {
                ZoomDirection.TELE -> (0x20 + speed).toByte()
                ZoomDirection.WIDE -> (0x30 + speed).toByte()
            }
            while (true) {
                sendZoomCommand(zoomValue)
                delay(100)
            }
        }
    }

    override fun stopContinuousZoom() {
        currentZoomJob?.cancel()
        sendZoomCommand(0x00)
        Timber.d("Stopped continuous zoom movement.")
    }

    override fun resetZoom() {
        Timber.d("Reset Zoom button clicked")
        /*val command =
            byteArrayOf(0x81.toByte(), 0x01, 0x04, 0x47, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())
        sendCommand(command)
        statusMessage.value = "Zoom state reset"*/
        viewModelScope.launch {
            sendZoomCommand(0x37) // Zoom Out fast
            delay(2500) // Adjust time based on your camera's zoom range
            sendZoomCommand(0x00) // Stop
            statusMessage.value = "Zoom reset to wide position"
        }
    }

    private fun sendFocusModeCommand(mode: Byte) {
        val command = byteArrayOf(0x81.toByte(), 0x01, 0x04, 0x38, mode, 0xFF.toByte())
        sendCommand(command)
    }

    override fun setAutoFocus() {
        Timber.d("Auto Focus button clicked")
        sendFocusModeCommand(0x02)
        statusMessage.value = "Focus mode: Auto"
    }

    override fun setManualFocus() {
        Timber.d("Manual Focus button clicked")
        sendFocusModeCommand(0x03)
        statusMessage.value = "Focus mode: Manual"
    }

    override fun startContinuousFocus(direction: FocusDirection) {
        currentFocusJob?.cancel()
        currentFocusJob = viewModelScope.launch(Dispatchers.IO) {
            val speed = focusSpeed.floatValue.toInt().toByte()
            val focusValue = when (direction) {
                FocusDirection.NEAR -> (0x30 + speed).toByte()
                FocusDirection.FAR -> (0x20 + speed).toByte()
            }
            while (true) {
                sendFocusCommand(focusValue)
                delay(100)
            }
        }
    }

    override fun stopContinuousFocus() {
        currentFocusJob?.cancel()
        val command =
            byteArrayOf(0x81.toByte(), 0x01, 0x04, 0x08, 0x00, 0xFF.toByte())
        sendCommand(command)
        Timber.d("Stopped continuous focus movement.")
    }

    private fun sendFocusCommand(focusValue: Byte) {
        val command = byteArrayOf(0x81.toByte(), 0x01, 0x04, 0x08, focusValue, 0xFF.toByte())
        sendCommand(command)
    }

    override fun setPreset(presetNumber: Int) {
        Timber.d("Set Preset $presetNumber button clicked")
        if (presetNumber in 0..14) { // VISCA presets 0-14
            val command = byteArrayOf(
                0x81.toByte(),
                0x01,
                0x04,
                0x3F,
                0x01,
                presetNumber.toByte(),
                0xFF.toByte()
            )
            sendCommand(command)
            statusMessage.value = "Setting preset ${presetNumber + 1}"
            val index = cameraPresets.indexOfFirst { it.number == presetNumber + 1 }
            if (index != -1) {
                cameraPresets[index] = cameraPresets[index].copy(isSaved = true)
                saveAllPresets()
            }
        } else {
            statusMessage.value = "Preset number must be between 1 and 15"
            Timber.w("Invalid preset number for setPreset: $presetNumber")
        }
    }

    override fun recallPreset(presetNumber: Int) {
        Timber.d("Recall Preset $presetNumber button clicked")
        if (presetNumber in 0..14) { // VISCA presets 0-14
            val command = byteArrayOf(
                0x81.toByte(),
                0x01,
                0x04,
                0x3F,
                0x02,
                presetNumber.toByte(),
                0xFF.toByte()
            )
            sendCommand(command)
            statusMessage.value = "Recalling preset ${presetNumber + 1}"
        } else {
            statusMessage.value = "Preset number must be between 1 and 15"
            Timber.w("Invalid preset number for recallPreset: $presetNumber")
        }
    }

    override fun captureImageForPreset(presetNumber: Int) {
        val ipAddress = cameraIpAddress.value
        val snapshotUrl = "https://picsum.photos/seed/picsum-${System.currentTimeMillis()}/300/200"


        if (ipAddress.isEmpty()) {
            statusMessage.value = "Please enter a camera IP address."
            return
        }

        viewModelScope.launch {
            try {
                val base64String = withContext(Dispatchers.IO) {
                    val url = java.net.URL(snapshotUrl)
                    Timber.d("Snapshot URL: $url")

                    val connection = url.openConnection()
                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(CompressFormat.JPEG, 80, byteArrayOutputStream)
                    val byteArray = byteArrayOutputStream.toByteArray()

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        Base64.Default.encode(byteArray)
                    } else {
                        // For older Android versions
                        android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
                    }
                }

                Timber.d("Base64 String for preset $presetNumber: ${base64String.length}")

                val index = cameraPresets.indexOfFirst { it.number == presetNumber + 1 }
                if (index != -1) {
                    cameraPresets[index] = cameraPresets[index].copy(
                        base64Image = base64String,
                        isSaved = true // Optional: mark as saved
                    )
                    saveAllPresets()
                    statusMessage.value = "Image captured and saved for preset ${presetNumber + 1}"
                    Timber.d("Image captured for preset ${presetNumber + 1} as Base64")
                }
            } catch (e: Exception) {
                statusMessage.value = "Failed to capture image: ${e.message}"
                Timber.e(e, "Error capturing snapshot for preset")
            }
        }
    }

    override fun goToHomePosition() {
        Timber.d("Go to Home Position button clicked")
        val command = byteArrayOf(0x81.toByte(), 0x01, 0x06, 0x04, 0xFF.toByte())
        sendCommand(command)
        statusMessage.value = "Moving to home position"
    }

    override fun onCleared() {
        super.onCleared()
        saveAllPresets()
        Timber.d("ViewModel cleared. Presets saved.")
    }
}

class PtzCameraViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PtzCameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PtzCameraViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class PreviewApplication : Application()

class MockPtzCameraViewModel : IPtzCameraViewModel, ViewModel() {
    override val cameraIpAddress = mutableStateOf("192.168.1.100")
    override val cameraPort = mutableIntStateOf(52381)
    override val rtspUrl = mutableStateOf("192.168.1.100")

    override val statusMessage = mutableStateOf("Preview Ready")
    override val panTiltSpeed = mutableFloatStateOf(10f)
    override val zoomSpeed = mutableFloatStateOf(4f)
    override val focusSpeed = mutableFloatStateOf(3f)
    override val cameraPresets = androidx.compose.runtime.mutableStateListOf<CameraPreset>().apply {
        for (i in 0 until 15) {
            add(CameraPreset(number = i + 1, name = "Mock Preset ${i + 1}"))
        }
        this[1].imageUrl = "https://placehold.co/128x72/000000/FFFFFF?text=P2"
        this[1].isSaved = true
    }

    override fun startContinuousPanTilt(offsetX: Float, offsetY: Float, maxJoystickRadius: Float, isAnalog: Boolean) {
        Timber.d("Mock Pan/Tilt: $offsetX, $offsetY")
    }

    override fun stopContinuousPanTilt() {
        Timber.d("Mock Stop Pan/Tilt")
    }

    override fun startContinuousZoom(direction: ZoomDirection) {
        Timber.d("Mock Zoom: $direction")
    }

    override fun stopContinuousZoom() {
        Timber.d("Mock Stop Zoom")
    }

    override fun resetZoom() {
        Timber.d("Mock Reset Zoom")
    }

    override fun setAutoFocus() {
        Timber.d("Mock Auto Focus")
    }

    override fun setManualFocus() {
        Timber.d("Mock Manual Focus")
    }

    override fun startContinuousFocus(direction: FocusDirection) {
        Timber.d("Mock Focus: $direction")
    }

    override fun stopContinuousFocus() {
        Timber.d("Mock Stop Focus")
    }

    override fun setPreset(presetNumber: Int) {
        Timber.d("Mock Set Preset: $presetNumber")
        val index = cameraPresets.indexOfFirst { it.number == presetNumber + 1 }
        if (index != -1) {
            cameraPresets[index] = cameraPresets[index].copy(isSaved = true)
        }
    }

    override fun recallPreset(presetNumber: Int) {
        Timber.d("Mock Recall Preset: $presetNumber")
    }

    override fun captureImageForPreset(presetNumber: Int) {
        Timber.d("Mock Capture Image for Preset: $presetNumber")
        val index = cameraPresets.indexOfFirst { it.number == presetNumber + 1 }
        if (index != -1) {
            cameraPresets[index] =
                cameraPresets[index].copy(imageUrl = "https://placehold.co/128x72/00FF00/000000?text=Captured+${presetNumber + 1}")
        }
    }

    override fun updatePresetName(presetNumber: Int, newName: String) {
        Timber.d("Mock Update Preset Name: $presetNumber to $newName")
        val index = cameraPresets.indexOfFirst { it.number == presetNumber + 1 }
        if (index != -1) {
            cameraPresets[index] = cameraPresets[index].copy(name = newName)
        }
    }

    override fun goToHomePosition() {
        Timber.d("Mock Go to Home")
    }
}

enum class Screen {
    CONTROLLER,
    ABOUT,
    PTZ_COMMAND_RECEIVER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PtzCameraViewModel = viewModel(
        factory = PtzCameraViewModelFactory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    var currentScreen by remember { mutableStateOf(Screen.CONTROLLER) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PTZ Controller") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = {
                                currentScreen = Screen.ABOUT
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "About"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("PTZ Command Receiver") },
                            onClick = {
                                currentScreen = Screen.PTZ_COMMAND_RECEIVER
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "PTZ Command Receiver"
                                )
                            }
                        )
                    }
                },
                navigationIcon = {
                    if (currentScreen != Screen.CONTROLLER) {
                        IconButton(onClick = { currentScreen = Screen.CONTROLLER }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                Screen.CONTROLLER -> PtzCameraControlScreen(viewModel = viewModel)
                Screen.ABOUT -> AboutScreen()
                Screen.PTZ_COMMAND_RECEIVER -> PtzCommandReceiverScreen()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PtzCameraControlScreen(
    viewModel: IPtzCameraViewModel
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = viewModel.cameraIpAddress.value,
                    onValueChange = { viewModel.cameraIpAddress.value = it },
                    label = { Text("Camera IP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = viewModel.cameraPort.value.toString(),
                    onValueChange = { viewModel.cameraPort.value = it.toIntOrNull() ?: 0 },
                    label = { Text("PORT") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = viewModel.rtspUrl.value,
                    onValueChange = { viewModel.rtspUrl.value = it },
                    label = { Text("RTSP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                )
            }
            Text(
                text = "Status: ${viewModel.statusMessage.value}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1.2f)
                    .padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    ZoomControlsCard(
                        zoomSpeed = viewModel.zoomSpeed.floatValue,
                        onZoomSpeedChange = { viewModel.zoomSpeed.floatValue = it },
                        onStartContinuousZoom = viewModel::startContinuousZoom,
                        onStopContinuousZoom = viewModel::stopContinuousZoom,
                        onResetZoom = viewModel::resetZoom
                    )
                }
                item {
                    FocusControlsCard(
                        focusSpeed = viewModel.focusSpeed.floatValue,
                        onFocusSpeedChange = { viewModel.focusSpeed.floatValue = it },
                        onStartContinuousFocus = viewModel::startContinuousFocus,
                        onStopContinuousFocus = viewModel::stopContinuousFocus,
                        onSetAutoFocus = viewModel::setAutoFocus,
                        onSetManualFocus = viewModel::setManualFocus
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PresetControlsCard(
                    cameraPresets = viewModel.cameraPresets,
                    onRecallPreset = { viewModel.recallPreset(it) }, // Pass index directly
                    onCaptureImageForPreset = { viewModel.captureImageForPreset(it) }, // Pass index
                    onSetPreset = { viewModel.setPreset(it) }, // Pass index
                    onUpdatePresetName = { index, name ->
                        viewModel.updatePresetName(
                            index + 1,
                            name
                        )
                    } // Adjust index for 1-based preset number
                )
            }
        }

        PanTiltControlsCard(
            panTiltSpeed = viewModel.panTiltSpeed.floatValue,
            onPanTiltSpeedChange = { viewModel.panTiltSpeed.floatValue = it },
            onStartContinuousPanTilt = viewModel::startContinuousPanTilt,
            onStopContinuousPanTilt = viewModel::stopContinuousPanTilt,
            onGoToHomePosition = viewModel::goToHomePosition,
            rtspUrl = viewModel.rtspUrl.value
        )
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(
    showBackground = true,
    device = "spec:width=800dp,height=1336dp,dpi=480",
    showSystemUi = true,
)
@Composable
fun PreviewPtzCameraControlScreen() {
    MyAppTheme {
        PtzCameraControlScreen(viewModel = MockPtzCameraViewModel())
    }
}
