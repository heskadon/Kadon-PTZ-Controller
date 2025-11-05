package com.kadon.ptzcontroller.ui.component

import androidx.annotation.OptIn
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kadon.ptzcontroller.ui.theme.MyAppTheme

@OptIn(UnstableApi::class)
@Composable
fun RtspPlayer(
    rtspUrl: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(rtspUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Adjust resizeMode as needed
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RtspPlayerPreview() {
    MyAppTheme {
        RtspPlayer(
            rtspUrl = "rtsp://example.com/stream" // Replace with a valid RTSP URL for preview if available
        )
    }
}
