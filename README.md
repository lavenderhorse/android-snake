# android-snake
A simple game of snake for android

Swipe or use the on-screen d-pad to steer, and tap the board to pause. The screen is styled like a pocket LCD, and your best score is saved on the device.

## Getting the app

Every push to `main` builds the app on GitHub. Open the repo's **Releases** page, download `pocket-snake.apk` from the "Pocket Snake (latest build)" release, and open it on your phone. Android will ask you to allow installs from your browser or file manager the first time.

The same file is also attached to each run on the **Actions** tab as `pocket-snake-apk`.

## Building locally

With Android Studio, open this folder and run the `app` configuration. From a terminal with Gradle 8.9 or newer, JDK 17 and the Android SDK:

```
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Layout

- `SnakeGame.java` holds the rules and has no Android dependencies.
- `SnakeView.java` draws the board and handles swipes and taps.
- `MainActivity.java` hosts the view and pauses the game when the app leaves the screen.
