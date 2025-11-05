package com.kadon.ptzcontroller.ui.component

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.gestures.forEach
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import kotlin.experimental.and

@Composable
fun PtzCommandReceiverScreen() {
    val context = LocalContext.current
    val ipAddress = remember { getDeviceIpAddress(context) }
    val receivedCommands = remember { mutableStateListOf<String>() }
    val udpListenerJob = remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        udpListenerJob.value = scope.launch {
            listenForPtzCommands(52381) { command ->
                withContext(Dispatchers.Main) {
                    receivedCommands.add(0, command) // Add to top
                }
            }
        }
        onDispose {
            udpListenerJob.value?.cancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PTZ Command Receiver", style = MaterialTheme.typography.headlineSmall)
        Text("Listening on:", style = MaterialTheme.typography.titleMedium)
        Text(ipAddress, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Text("Received Commands:", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(receivedCommands) { command ->
                Text(command, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

private fun getDeviceIpAddress(context: Context): String {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    //val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    //return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)

    val activeNetwork = connectivityManager?.activeNetwork
    if (activeNetwork != null) {
        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
        linkProperties?.linkAddresses?.forEach { linkAddress ->
            // You might want to filter for IPv4 or IPv6 specifically
            // For example, to get only IPv4:
            if (linkAddress.address is Inet4Address) {
                return linkAddress.address.hostAddress ?: "❌ Unable to retrieve IPv4 address" // Returns the string representation
            }
        }
    }
    return "❌ Unable to retrieve IP address" // Or handle as "N/A", "Unknown", etc.
}

private suspend fun listenForPtzCommands(port: Int, onCommandReceived: suspend (String) -> Unit) {
    withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(port)
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isActive) {
                socket.receive(packet)
                val commandBytes = packet.data.copyOfRange(0, packet.length)
                val translatedCommand = translateViscaCommand(commandBytes)
                onCommandReceived(translatedCommand)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error receiving UDP packets")
        } finally {
            socket?.close()
        }
    }
}

private fun translateViscaCommand(command: ByteArray): String {
    if (command.isEmpty() || command[0] != 0x81.toByte()) {
        return "❌ Unknown: ${command.toHexString()}"
    }

    if (command.size < 3) {
        return "❌ Too short: ${command.toHexString()}"
    }

    return when {
        // --- Pan/Tilt Drive: 0x06 0x01 ---
        command[2] == 0x06.toByte() && command[3] == 0x01.toByte() && command.size >= 8 -> {
            val panSpeed = command[4].toUByte().toInt()
            val tiltSpeed = command[5].toUByte().toInt()
            val panDir = command[6]
            val tiltDir = command[7]

            val actions = mutableListOf<String>()

            if (panDir and 0x04.toByte() != 0x00.toByte()) actions.add("Pan Left")
            if (panDir and 0x08.toByte() != 0x00.toByte()) actions.add("Pan Right")
            if (tiltDir and 0x01.toByte() != 0x00.toByte()) actions.add("Tilt Up")
            if (tiltDir and 0x02.toByte() != 0x00.toByte()) actions.add("Tilt Down")

            if (actions.isEmpty()) {
                "🛑 PTZ STOP"
            } else {
                val speeds = "PAN $panSpeed, TILT $tiltSpeed"
                "🟢 PTZ ${actions.joinToString(" + ")}: $speeds"
            }
        }

        // --- Zoom: 0x04 0x07 ---
        command[2] == 0x04.toByte() && command[3] == 0x07.toByte() && command.size >= 5 -> {
            val zoomVal = command[4]
            val highNibble = (zoomVal.toInt() ushr 4) and 0x0F
            val lowNibble = zoomVal.toUByte().toInt() and 0x0F

            when (highNibble) {
                0x00 -> "🛑 ZOOM STOP"
                0x02 -> "🔍 ZOOM IN: Speed $lowNibble"
                0x03 -> "🔭 ZOOM OUT: Speed $lowNibble"
                else -> "❓ ZOOM UNKNOWN: ${zoomVal.toUByte()}"
            }
        }

        // --- Focus Mode: 0x04 0x38 ---
        command[2] == 0x04.toByte() && command[3] == 0x38.toByte() && command.size >= 5 -> {
            when (command[4]) {
                0x02.toByte() -> "🔁 FOCUS MODE: Auto"
                0x03.toByte() -> ">manual FOCUS MODE: Manual"
                else -> "❓ FOCUS MODE: Unknown ${command[4].toUByte()}"
            }
        }

        // --- Focus Drive: 0x04 0x08 ---
        command[2] == 0x04.toByte() && command[3] == 0x08.toByte() && command.size >= 5 -> {
            val focusVal = command[4]
            val highNibble = (focusVal.toInt() ushr 4) and 0x0F
            val lowNibble = focusVal.toUByte().toInt() and 0x0F

            when (highNibble) {
                0x00 -> "🛑 FOCUS STOP"
                0x02 -> "🔦 FOCUS FAR: Speed $lowNibble"
                0x03 -> "🔦 FOCUS NEAR: Speed $lowNibble"
                else -> "❓ FOCUS UNKNOWN: ${focusVal.toUByte()}"
            }
        }

        // --- Preset: 0x04 0x3F ---
        command[2] == 0x04.toByte() && command[3] == 0x3F.toByte() && command.size >= 6 -> {
            val action = command[4]
            val slot = command[5].toUByte().toInt() + 1 // 0-based → 1-based
            when (action) {
                0x01.toByte() -> "💾 PRESET SET: Slot $slot"
                0x02.toByte() -> "🎯 PRESET RECALL: Slot $slot"
                0x05.toByte() -> "🗑️ PRESET CLEAR: Slot $slot"
                else -> "❓ PRESET UNKNOWN: Action ${action.toUByte()}, Slot $slot"
            }
        }

        // --- Go to Home / Set Home ---
        command[2] == 0x06.toByte() && command[3] == 0x04.toByte() && command.size == 5 -> "🏠 PTZ GO TO HOME POSITION"
        command[2] == 0x06.toByte() && command[3] == 0x05.toByte() && command.size == 5 -> "📍 PTZ SET HOME POSITION"

        // --- Reset Zoom (Inquiry) - Not a command ---
        command[2] == 0x04.toByte() && command[3] == 0x47.toByte() && command.size >= 5 -> "⚠️ ZOOM: Reset Command (0x47) - Not Standard"

        else -> "❓ Unknown VISCA: ${command.toHexString()}"
    }
}

private fun ByteArray.toHexString() = joinToString(" ") { "%02x".format(it) }