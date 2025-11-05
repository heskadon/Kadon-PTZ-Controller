package com.kadon.ptzcontroller.ui.component

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kadon.ptzcontroller.ui.theme.MyAppTheme
import kotlinx.coroutines.isActive
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Constants for Joystick dimensions
private val JOYSTICK_OUTER_RADIUS = 96.dp
private val JOYSTICK_THUMB_RADIUS = 24.dp

/**
 * Composable function for a joystick controller.
 * @param modifier Modifier for the joystick.
 * @param onMove Callback invoked when the joystick thumb moves. Provides offsetX, offsetY, and maxRadius.
 * @param onStop Callback invoked when the joystick thumb is released.
 */
@Composable
fun JoystickController(
    modifier: Modifier = Modifier,
    onMove: (offsetX: Float, offsetY: Float, maxRadius: Float) -> Unit,
    onStop: () -> Unit,
    snapThreshold: Dp = 24.dp
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val snapThresholdPx = with(density) { snapThreshold.toPx() }

    val outerRadiusPx = remember(density) { with(density) { JOYSTICK_OUTER_RADIUS.toPx() } }
    val thumbRadiusPx = remember(density) { with(density) { JOYSTICK_THUMB_RADIUS.toPx() } }

    // For smooth animation of thumb
    val animatedOffset by animateOffsetAsState(
        targetValue = thumbOffset,
        animationSpec = tween(durationMillis = 16)
    )

    // Haptic feedback
    val haptic = LocalHapticFeedback.current

    // Track previous snap state
    var wasSnappedHorizontal by remember { mutableStateOf(false) }
    var wasSnappedVertical by remember { mutableStateOf(false) }

    // Trigger haptic via state change (avoids calling haptics inside pointerInput)
    var playHaptic by remember { mutableStateOf(false) }

    // Haptic effect outside pointerInput
    LaunchedEffect(playHaptic) {
        if (playHaptic) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            playHaptic = false // Reset
        }
    }

    Box(
        modifier = modifier
            .size(JOYSTICK_OUTER_RADIUS * 2)
            .clip(CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        Timber.d("Joystick pressed at: ${down.position}")

                        var currentPointer: PointerInputChange
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            currentPointer = event.changes.firstOrNull { it.id == down.id } ?: break

                            val rawOffset = currentPointer.position - Offset(outerRadiusPx, outerRadiusPx)
                            val distance = rawOffset.getDistance()

                            val clampedRawOffset = if (distance > outerRadiusPx - thumbRadiusPx) {
                                val angle = atan2(rawOffset.y, rawOffset.x)
                                Offset(
                                    x = (outerRadiusPx - thumbRadiusPx) * cos(angle),
                                    y = (outerRadiusPx - thumbRadiusPx) * sin(angle)
                                )
                            } else {
                                rawOffset
                            }

                            val absX = kotlin.math.abs(clampedRawOffset.x)
                            val absY = kotlin.math.abs(clampedRawOffset.y)

                            val isSnappedHorizontal = absX >= snapThresholdPx && absY < snapThresholdPx
                            val isSnappedVertical = absY >= snapThresholdPx && absX < snapThresholdPx

                            var snappedOffset = clampedRawOffset
                            when {
                                isSnappedHorizontal -> snappedOffset = Offset(snappedOffset.x, 0f)
                                isSnappedVertical -> snappedOffset = Offset(0f, snappedOffset.y)
                                else -> {}
                            }

                            // Trigger haptic only when snapping *into* axis mode
                            if ((isSnappedHorizontal && !wasSnappedHorizontal) ||
                                (isSnappedVertical && !wasSnappedVertical)
                            ) {
                                playHaptic = true // Signal to trigger haptic
                            }

                            wasSnappedHorizontal = isSnappedHorizontal
                            wasSnappedVertical = isSnappedVertical

                            thumbOffset = snappedOffset
                            onMove(thumbOffset.x, thumbOffset.y, outerRadiusPx - thumbRadiusPx)
                        } while (currentPointer.pressed)

                        thumbOffset = Offset.Zero
                        wasSnappedHorizontal = false
                        wasSnappedVertical = false
                        onStop()
                        Timber.d("Joystick released. Resetting to center.")
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw outer circle
            drawCircle(
                color = Color.LightGray,
                radius = outerRadiusPx,
                center = Offset(outerRadiusPx, outerRadiusPx)
            )

            // === Cardinal Direction Notches (0°, 90°, 180°, 270°) ===
            val notchLength = 12.dp.toPx()
            val notchWidth = 3.dp.toPx()
            val notchColor = Color.Blue.copy(alpha = 0.7f)

            for (angle in listOf(0f, 90f, 180f, 270f)) {
                val rad = Math.toRadians(angle.toDouble()).toFloat()
                val start = Offset(
                    outerRadiusPx + (outerRadiusPx - notchLength) * cos(rad),
                    outerRadiusPx + (outerRadiusPx - notchLength) * sin(rad)
                )
                val end = Offset(
                    outerRadiusPx + outerRadiusPx * cos(rad),
                    outerRadiusPx + outerRadiusPx * sin(rad)
                )

                val isActive = when (angle) {
                    0f -> wasSnappedHorizontal && animatedOffset.x > 0
                    180f -> wasSnappedHorizontal && animatedOffset.x < 0
                    90f -> wasSnappedVertical && animatedOffset.y > 0
                    270f -> wasSnappedVertical && animatedOffset.y < 0
                    else -> false
                }

                drawLine(
                    color = if (isActive) notchColor else Color.Gray.copy(alpha = 0.4f),
                    start = start,
                    end = end,
                    strokeWidth = notchWidth
                )
            }

            // === Guide Lines When Snapped ===
            if (wasSnappedHorizontal) {
                drawLine(
                    color = Color.Blue.copy(alpha = 0.5f),
                    start = Offset(0f, outerRadiusPx),
                    end = Offset(size.width, outerRadiusPx),
                    strokeWidth = 2.dp.toPx()
                )
            } else if (wasSnappedVertical) {
                drawLine(
                    color = Color.Blue.copy(alpha = 0.5f),
                    start = Offset(outerRadiusPx, 0f),
                    end = Offset(outerRadiusPx, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // === Draw Thumb with Smooth Animation ===
            val thumbColor = if (wasSnappedHorizontal || wasSnappedVertical) {
                Color(0xFF0066FF) // Bright blue when snapped
            } else {
                Color.DarkGray
            }

            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(outerRadiusPx, outerRadiusPx) + animatedOffset
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanTiltControlsCard(
    panTiltSpeed: Float,
    onPanTiltSpeedChange: (Float) -> Unit,
    onStartContinuousPanTilt: (offsetX: Float, offsetY: Float, maxJoystickRadius: Float, isAnalog: Boolean) -> Unit,
    onStopContinuousPanTilt: () -> Unit,
    onGoToHomePosition: () -> Unit,
    rtspUrl: String ? = null,
) {
    val haptic = LocalHapticFeedback.current // ✅ Add this line

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Pan/Tilt Controls", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            /*RtspPlayer(
                rtspUrl = rtspUrl ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))*/

            // Pan/Tilt Speed Slider
            Text(
                text = "Pan/Tilt Speed: ${panTiltSpeed.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = panTiltSpeed,
                onValueChange = onPanTiltSpeedChange,
                valueRange = 1f..24f, // VISCA pan speed range 0x01-0x18 (24 decimal)
                steps = 23, // 24 - 1 = 23 steps
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // New Row to hold buttons and joystick side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically // Vertically center items in the row
            ) {
                // Directional Buttons (left side)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(0.5f) // Take up available space
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Up-Left
                        val interactionSourceUpLeft = remember { MutableInteractionSource() }
                        val isPressedUpLeft by interactionSourceUpLeft.collectIsPressedAsState()
                        LaunchedEffect(isPressedUpLeft) {
                            if (isPressedUpLeft) {
                                Timber.d("Up-Left button pressed")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartContinuousPanTilt(-1f, -1f, 1f, false)
                            } else {
                                Timber.d("Up-Left button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourceUpLeft,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.Filled.NorthWest, contentDescription = "Up Left") }

                        // Up
                        val interactionSourceTiltUp = remember { MutableInteractionSource() }
                        val isPressedTiltUp by interactionSourceTiltUp.collectIsPressedAsState()
                        LaunchedEffect(isPressedTiltUp) {
                            if (isPressedTiltUp) {
                                Timber.d("Up button pressed")
                                onStartContinuousPanTilt(0f, -1f, 1f, false)
                            } else {
                                Timber.d("Up button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourceTiltUp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Up") }

                        // Up-Right
                        val interactionSourceUpRight = remember { MutableInteractionSource() }
                        val isPressedUpRight by interactionSourceUpRight.collectIsPressedAsState()
                        LaunchedEffect(isPressedUpRight) {
                            if (isPressedUpRight) {
                                Timber.d("Up-Right button pressed")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartContinuousPanTilt(1f, -1f, 1f, false)
                            } else {
                                Timber.d("Up-Right button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourceUpRight,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.Filled.NorthEast, contentDescription = "Up Right") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Pan Left
                        val interactionSourcePanLeft = remember { MutableInteractionSource() }
                        val isPressedPanLeft by interactionSourcePanLeft.collectIsPressedAsState()
                        LaunchedEffect(isPressedPanLeft) {
                            if (isPressedPanLeft) {
                                Timber.d("Left button pressed")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartContinuousPanTilt(-1f, 0f, 1f, false)
                            } else {
                                Timber.d("Left button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourcePanLeft,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left") }

                        // HOME Button
                        Button(
                            onClick = onGoToHomePosition,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.Filled.Home, contentDescription = "Home Position") }

                        // Pan Right
                        val interactionSourcePanRight = remember { MutableInteractionSource() }
                        val isPressedPanRight by interactionSourcePanRight.collectIsPressedAsState()
                        LaunchedEffect(isPressedPanRight) {
                            if (isPressedPanRight) {
                                Timber.d("Right button pressed")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartContinuousPanTilt(1f, 0f, 1f, false)
                            } else {
                                Timber.d("Right button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourcePanRight,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Right"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Down-Left
                        val interactionSourceDownLeft = remember { MutableInteractionSource() }
                        val isPressedDownLeft by interactionSourceDownLeft.collectIsPressedAsState()
                        LaunchedEffect(isPressedDownLeft) {
                            if (isPressedDownLeft) {
                                Timber.d("Down-Left button pressed")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartContinuousPanTilt(-1f, 1f, 1f, false)
                            } else {
                                Timber.d("Down-Left button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourceDownLeft,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.Filled.SouthWest, contentDescription = "Down Left") }

                        // Down
                        val interactionSourceTiltDown = remember { MutableInteractionSource() }
                        val isPressedTiltDown by interactionSourceTiltDown.collectIsPressedAsState()
                        LaunchedEffect(isPressedTiltDown) {
                            if (isPressedTiltDown) {
                                Timber.d("Down button pressed")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartContinuousPanTilt(0f, 1f, 1f, false)
                            } else {
                                Timber.d("Down button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourceTiltDown,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Down") }

                        // Down-Right
                        val interactionSourceDownRight = remember { MutableInteractionSource() }
                        val isPressedDownRight by interactionSourceDownRight.collectIsPressedAsState()
                        LaunchedEffect(isPressedDownRight) {
                            if (isPressedDownRight) {
                                Timber.d("Down-Right button pressed")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartContinuousPanTilt(1f, 1f, 1f, false)
                            } else {
                                Timber.d("Down-Right button released")
                                onStopContinuousPanTilt()
                            }
                        }
                        Button(
                            onClick = { /* Handled by LaunchedEffect */ },
                            interactionSource = interactionSourceDownRight,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) { Icon(Icons.Filled.SouthEast, contentDescription = "Down Right") }
                    }
                }

                // Joystick Controller (right side)
                JoystickController(
                    modifier = Modifier.padding(start = 16.dp), // Add spacing to the left of the joystick
                    onMove = {
                            offsetX, offsetY, maxRadius -> onStartContinuousPanTilt(offsetX, offsetY, maxRadius, true)
                    },
                    onStop = {
                        onStopContinuousPanTilt()
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun PreviewPanTiltControlsCard() {
    MyAppTheme {
        PanTiltControlsCard(
            panTiltSpeed = 10f,
            onPanTiltSpeedChange = {},
            onStartContinuousPanTilt = { _, _, _, _ -> },
            onStopContinuousPanTilt = {},
            onGoToHomePosition = {}
        )
    }
}