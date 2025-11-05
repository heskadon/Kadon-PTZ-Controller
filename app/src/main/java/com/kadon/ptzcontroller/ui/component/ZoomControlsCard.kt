package com.kadon.ptzcontroller.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kadon.ptzcontroller.model.ZoomDirection
import com.kadon.ptzcontroller.ui.theme.MyAppTheme
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoomControlsCard(
    zoomSpeed: Float,
    onZoomSpeedChange: (Float) -> Unit,
    onStartContinuousZoom: (direction: ZoomDirection) -> Unit,
    onStopContinuousZoom: () -> Unit,
    onResetZoom: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(MaterialTheme.shapes.small)
    ) {
        // Zoom In (Continuous) - Right Aligned
        val interactionSourceZoomIn = remember { MutableInteractionSource() }
        val isPressedZoomIn by interactionSourceZoomIn.collectIsPressedAsState()
        LaunchedEffect(isPressedZoomIn) {
            if (isPressedZoomIn) {
                Timber.d("Zoom In button pressed")
                onStartContinuousZoom(ZoomDirection.TELE)
            } else {
                Timber.d("Zoom In button released")
                onStopContinuousZoom()
            }
        }
        FilledIconButton(
            onClick = { /* Handled by LaunchedEffect */ },
            interactionSource = interactionSourceZoomIn,
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ZoomIn,
                contentDescription = "Zoom In",
                modifier = Modifier.size(42.dp)
            )
        }
        // This Column contains the title and slider, with its own padding
        Column(
            modifier = Modifier
                .padding(16.dp) // This padding applies to the content within this Column
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Zoom Controls", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Zoom Speed Slider
            Text(
                text = "Zoom Speed: ${zoomSpeed.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = zoomSpeed,
                onValueChange = onZoomSpeedChange,
                valueRange = 0f..7f, // VISCA variable zoom speed range 0x00-0x07
                steps = 7, // 7 steps for 0 to 7
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // This Row is now outside the above Column, directly within the Card,
        // allowing its content to span the full width of the Card itself.
        Row(
            modifier = Modifier.fillMaxWidth(),
            //horizontalArrangement = Arrangement.SpaceBetween, // Distribute space evenly
            verticalAlignment = Alignment.CenterVertically // Align items vertically in the center
        ) {
            // Zoom Out (Continuous) - Left Aligned
            val interactionSourceZoomOut = remember { MutableInteractionSource() }
            val isPressedZoomOut by interactionSourceZoomOut.collectIsPressedAsState()
            LaunchedEffect(isPressedZoomOut) {
                if (isPressedZoomOut) {
                    Timber.d("Zoom Out button pressed")
                    onStartContinuousZoom(ZoomDirection.WIDE)
                } else {
                    Timber.d("Zoom Out button released")
                    onStopContinuousZoom()
                }
            }
            FilledIconButton(
                onClick = { /* Handled by LaunchedEffect */ },
                interactionSource = interactionSourceZoomOut,
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ZoomOut,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(42.dp)
                )
            }

            // Reset Zoom State - Center Aligned
            Box(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                OutlinedButton(onClick = onResetZoom) {
                    Text("Reset Zoom")
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun PreviewZoomControlsCard() {
    MyAppTheme {
        ZoomControlsCard(
            zoomSpeed = 4f,
            onZoomSpeedChange = {},
            onStartContinuousZoom = {},
            onStopContinuousZoom = {},
            onResetZoom = {}
        )
    }
}