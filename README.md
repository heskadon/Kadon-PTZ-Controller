# 🎮 PTZ Controller Pro 🎮

Yo! Welcome to PTZ Controller Pro, the slickest way to boss around your Pan-Tilt-Zoom cameras. This app is your one-stop shop for smooth camera control, live video feeds, and saving those perfect shots as presets.

## 🚀 Tech Stack

This bad boy is built with the latest and greatest from the Android world:

*   **Kotlin**: For that clean, modern, and null-safe code.
*   **Jetpack Compose**: Building the UI with the future of Android development. It's all about that declarative goodness!
*   **ExoPlayer**: For buttery-smooth live video streaming from your camera's RTSP feed.
*   **Kotlin Coroutines**: To handle all the background network stuff without breaking a sweat.
*   **DataStore**: For saving your precious presets right on your device.

## 🛠️ How to Run

Ready to take control? Here's how to get up and running in Android Studio:

1.  **Clone the repo**: You know the drill.
2.  **Open in Android Studio**: Fire up Android Studio and open the project.
3.  **Sync Gradle**: Let the Gradle magic happen.
4.  **Run it**: Hit that green play button and deploy to your favorite Android device or emulator.
5.  **Connect your camera**: Make sure your PTZ camera is on the same network, and pop its IP address and port into the app.

## 🔮 Future Updates

We're always dreaming up new ways to make this app even more awesome. Here's what's on the horizon:

*   **More Camera Protocols**: We're looking to add support for other PTZ protocols beyond VISCA.
*   **Advanced Camera Settings**: Think exposure, white balance, and all that good stuff.
*   **Multi-Camera Support**: Juggling more than one camera? We've got you covered in a future update.
*   **Community-Sourced Presets**: Share your best camera positions with the world!

## 💻 Development Zone

### Challenges & Triumphs

*   **The VISCA Voyage**: Wrangling the VISCA protocol over UDP was a wild ride. We had to craft raw byte commands and sling them across the network. It was a bit like teaching a robot to dance, but we got there!
*   **Compose All the Things**: Going all-in on Jetpack Compose was a blast. We're super proud of the clean, modular, and reusable UI components we've built. It's a testament to the power of modern Android development.
*   **Joystick Jiggles**: Creating a virtual joystick that feels just right was a fun challenge. We think we've nailed that smooth, responsive feel for precise camera movements.

### Contributing

Got ideas? Found a bug? We'd love to hear from you! Feel free to open an issue or submit a pull request. Let's make this the best PTZ controller app on the planet!
