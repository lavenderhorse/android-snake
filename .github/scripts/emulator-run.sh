#!/usr/bin/env bash
# Runs inside the emulator job: installs the APK, plays a short scripted game,
# and saves screenshots plus any crash log into ./screenshots.
set +e
mkdir -p screenshots

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n dev.pocketsnake/.MainActivity
sleep 3

size=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${size%x*}
H=${size#*x}
X=$((W / 2))
Y=$((H * 6 / 10))
D=$((W / 4))

adb shell screencap -p /sdcard/1-ready.png

# Swipe up to start, then swipe left, and grab a frame while the snake is still alive.
adb shell "input swipe $X $((Y + D)) $X $((Y - D)) 100; input swipe $((X + D)) $Y $((X - D)) $Y 100; screencap -p /sdcard/2-playing.png"

# Let the snake run into a wall.
sleep 3
adb shell screencap -p /sdcard/3-game-over.png

# Tap to play again, then tap once more to pause.
adb shell "input tap $X $Y; input tap $X $Y; screencap -p /sdcard/4-paused.png"

for f in 1-ready 2-playing 3-game-over 4-paused; do
  adb pull "/sdcard/$f.png" "screenshots/$f.png"
done
adb logcat -d AndroidRuntime:E '*:S' > screenshots/crash-log.txt
adb shell wm size > screenshots/device-size.txt
