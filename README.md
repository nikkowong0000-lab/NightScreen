# NightScreen

Android **blue-light / night filter** overlay app (project folder name: `screenlight`).

Dims or tints the screen with a system overlay, supports scheduling, Quick Settings tile, and optional AdMob banner.

## Features

- Screen colour / brightness filter overlay
- Foreground service for the filter
- Schedule on/off
- Quick Settings tile
- AdMob banner (real ad unit IDs in source)

## Permissions

Needs **Display over other apps** (`SYSTEM_ALERT_WINDOW`) for the filter to work.

## Open in Android Studio

1. Open this folder as an Android Studio project
2. Gradle sync → Run
3. Grant overlay permission when prompted

## Stack

Kotlin · Jetpack Compose · Foreground Service · DataStore · AdMob

## Status

Personal / portfolio project. Usable prototype.

## Note

Application ID package is `com.yourcompany.screenlight` (legacy template name). App display name is **NightScreen**.
