package com.kadon.ptzcontroller.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.twotone.Face
import androidx.compose.material.icons.twotone.PeopleAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kadon.ptzcontroller.model.FocusDirection
import com.kadon.ptzcontroller.ui.theme.MyAppTheme
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusControlsCard(
    focusSpeed: Float,
    onFocusSpeedChange: (Float) -> Unit,
    onStartContinuousFocus: (direction: FocusDirection) -> Unit,
    onStopContinuousFocus: () -> Unit,
    onSetAutoFocus: () -> Unit,
    onSetManualFocus: () -> Unit // Keep this as it's used by the new logic
) {
    // State to manage whether auto focus is currently active
    var isAutoFocus by remember { mutableStateOf(true) } // Default to auto focus

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
            Text(text = "Focus Controls", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Focus Speed Slider
            Text(
                text = "Focus Speed: ${focusSpeed.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = focusSpeed,
                onValueChange = onFocusSpeedChange,
                valueRange = 0f..7f, // VISCA variable focus speed range 0x00-0x07
                steps = 7, // 7 steps for 0 to 7
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Focus Near (Continuous)
                    val interactionSourceFocusNear = remember { MutableInteractionSource() }
                    val isPressedFocusNear by interactionSourceFocusNear.collectIsPressedAsState()
                    LaunchedEffect(isPressedFocusNear) {
                        if (isPressedFocusNear) {
                            Timber.d("Focus Near button pressed")
                            onStartContinuousFocus(FocusDirection.NEAR)
                        } else {
                            Timber.d("Focus Near button released")
                            onStopContinuousFocus()
                        }
                    }
                    OutlinedButton(
                        onClick = { /* Handled by LaunchedEffect */ },
                        interactionSource = interactionSourceFocusNear,
                        modifier = Modifier.weight(1f),
                        enabled = !isAutoFocus // Enabled only when not in auto focus
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Face,
                            contentDescription = "Focus Near",
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Focus Near")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Focus Far (Continuous)
                    val interactionSourceFocusFar = remember { MutableInteractionSource() }
                    val isPressedFocusFar by interactionSourceFocusFar.collectIsPressedAsState()
                    LaunchedEffect(isPressedFocusFar) {
                        if (isPressedFocusFar) {
                            Timber.d("Focus Far button pressed")
                            onStartContinuousFocus(FocusDirection.FAR)
                        } else {
                            Timber.d("Focus Far button released")
                            onStopContinuousFocus()
                        }
                    }
                    OutlinedButton(
                        onClick = { /* Handled by LaunchedEffect */ },
                        interactionSource = interactionSourceFocusFar,
                        modifier = Modifier.weight(1f),
                        enabled = !isAutoFocus // Enabled only when not in auto focus
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.PeopleAlt,
                            contentDescription = "Focus Far",
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Focus Far")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center // Center the single button
                ) {
                    // Auto Focus / Manual Focus Toggle Button
                    if (isAutoFocus) {
                        // When in Auto Focus mode, show a filled button to switch to Manual Focus
                        Button(
                            modifier = Modifier.fillMaxWidth(0.8f), // Make it a bit wider
                            onClick = {
                                isAutoFocus = false
                                onSetManualFocus() // Call manual focus when switching off auto
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "Auto Focus",
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auto Focus (Active)")
                        }
                    } else {
                        // When in Manual Focus mode, show an outlined button to switch to Auto Focus
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(0.8f), // Make it a bit wider
                            onClick = {
                                isAutoFocus = true
                                onSetAutoFocus() // Call auto focus when switching on auto
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.BackHand, // Icon for manual focus
                                contentDescription = "Manual Focus",
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manual Focus (Active)")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun PreviewFocusControlsCard() {
    MyAppTheme {
        FocusControlsCard(
            focusSpeed = 3f,
            onFocusSpeedChange = {},
            onStartContinuousFocus = {},
            onStopContinuousFocus = {},
            onSetAutoFocus = {},
            onSetManualFocus = {}
        )
    }
}