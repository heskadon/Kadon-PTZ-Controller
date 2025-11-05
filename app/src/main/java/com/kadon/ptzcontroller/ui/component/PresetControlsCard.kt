package com.kadon.ptzcontroller.ui.component

//import androidx.compose.foundation.layout.aspectRatio // Not used in new PresetItem
//import androidx.compose.foundation.layout.width // Not directly used in new PresetItem top level
// import androidx.compose.material.icons.filled.Videocam // Not used as placeholder in new PresetItem
// import androidx.compose.material3.Button // Not used in new PresetItem
// import androidx.compose.material3.OutlinedButton // Not used in new PresetItem
// import android.graphics.Bitmap // No longer used in Previews for this Composable
// import androidx.compose.ui.graphics.asImageBitmap // No longer used by PresetItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kadon.ptzcontroller.model.CameraPreset
import com.kadon.ptzcontroller.ui.theme.MyAppTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetControlsCard(
    cameraPresets: SnapshotStateList<CameraPreset>,
    onRecallPreset: (Int) -> Unit,
    onCaptureImageForPreset: (Int) -> Unit,
    onSetPreset: (Int) -> Unit,
    onUpdatePresetName: (Int, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedPresetIndex by remember { mutableIntStateOf(-1) }
    var tempPresetName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Presets", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // Display 2 presets per row
                contentPadding = PaddingValues(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(
                    1f,
                    fill = false
                ) // Allow grid to take space but not fill all
            ) {
                itemsIndexed(cameraPresets) { index, preset ->
                    PresetItem(
                        cameraPreset = preset,
                        onRecall = { onRecallPreset(index) },
                        onCaptureImage = { onCaptureImageForPreset(index) },
                        onSet = { onSetPreset(index) },
                        onEditName = {
                            selectedPresetIndex = index
                            tempPresetName = preset.name
                            showDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showDialog && selectedPresetIndex != -1) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Edit Preset Name") },
            text = {
                OutlinedTextField(
                    value = tempPresetName,
                    onValueChange = { tempPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdatePresetName(selectedPresetIndex, tempPresetName)
                        showDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PresetItem(
    cameraPreset: CameraPreset,
    onRecall: () -> Unit,
    onCaptureImage: () -> Unit,
    onSet: () -> Unit,
    onEditName: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onRecall() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp) // Fixed height from your design
                    .clip(MaterialTheme.shapes.small)
                    // The direct background on the Box is mostly for cases where AsyncImage might not fill it,
                    // or if all states of AsyncImage had transparent backgrounds.
                    // With current placeholders, it might be less visible.
                    .background(Color.LightGray)
            ) {
                // Display Image
                val imageBase64 = cameraPreset.base64Image

                if (imageBase64.isNotEmpty()) {

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("data:image/jpeg;base64,${cameraPreset.base64Image.trim()}")
                            .build(),
                        contentDescription = "Preset ${cameraPreset.number} Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop, // Crop to fill bounds
                        alignment = Alignment.Center,
                        error = painterResource(android.R.drawable.stat_notify_error),
                    )
                } else {
                    // Placeholder text if no image
                    Text(
                        text = "No Image",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }

                Text(
                    text = cameraPreset.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )

                IconButton(
                    onClick = onEditName,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .padding(4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit Preset Name",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onCaptureImage,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(32.dp)
                        .padding(4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Capture Image",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onSet,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .padding(4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "Save Preset",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun PreviewPresetControlsCard() {
    val sampleCameraPresets = remember {
        listOf(
            CameraPreset(
                number = 1,
                name = "Stage Center",
                imageUrl = "https://placehold.co/128x72/E8E8E8/AAAAAA?text=Preset1"
            ),
            CameraPreset(number = 2, name = "Podium", imageUrl = null), // Test error state
            CameraPreset(
                number = 3,
                name = "Audience Left",
                imageUrl = "https://placehold.co/128x72/D8D8D8/999999?text=Preset3"
            ),
            CameraPreset(
                number = 4,
                name = "Door",
                imageUrl = "https://invalid-url-should-error.com/image.jpg"
            ) // Test actual error
        ).toMutableStateList()
    }
    MyAppTheme {
        PresetControlsCard(
            cameraPresets = sampleCameraPresets,
            onRecallPreset = {},
            onCaptureImageForPreset = {},
            onSetPreset = {},
            onUpdatePresetName = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
fun PreviewPresetItem() {
    val cameraPresetWithImage = CameraPreset(
        number = 1,
        name = "Living Room",
        imageUrl = "https://placehold.co/128x72/AACCFF/000033"
    )
    val cameraPresetWithoutImage =
        CameraPreset(number = 2, name = "Kitchen", imageUrl = null) // Test error state

    MyAppTheme {
        Column(Modifier.padding(8.dp)) {
            PresetItem(
                cameraPreset = cameraPresetWithImage,
                onRecall = { },
                onCaptureImage = { },
                onSet = { },
                onEditName = { }
            )
            Spacer(Modifier.height(2.dp))
            PresetItem(
                cameraPreset = cameraPresetWithoutImage,
                onRecall = { },
                onCaptureImage = { },
                onSet = { },
                onEditName = { }
            )
        }
    }
}
