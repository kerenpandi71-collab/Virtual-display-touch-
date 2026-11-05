# Virtual Display Touch

A standalone Android app for creating a Shizuku-backed virtual display and controlling it from a phone touchscreen. The virtual display is rendered directly to a `SurfaceView`; the app does not copy each frame through an `ImageReader` or bitmap.

## Features

- Create and manage a virtual display, with configurable resolution and density.
- Direct **Touch** mode for forwarding multi-touch input to the virtual display.
- Trackpad mode with mouse movement, tap, drag, and two-finger scrolling.
- Assistant Ball shortcuts for Android/custom keyboards, navigation, and gamepad controls.
- Virtual MOBA gamepad overlay with a left analog and right-side Skill 1–3, Attack, Spell, HP/Regen, and Recall buttons.
- Drag each gamepad control to map it to a different location on the virtual screen; positions save automatically.
- Adjust the overall gamepad control size, or hide the gamepad while keeping Touch mode available.
- Runtime diagnostics and display/input status.

## Using the gamepad

Tap the floating **V** Assistant Ball and choose **Tampilkan gamepad**. Select **Atur posisi gamepad** to drag the analog or buttons directly over the virtual display; positions save as you move them. Use **Atur ukuran gamepad** to change control size from 65% to 150%. The Assistant Ball menu can hide the controls at any time. The gamepad supports up to five simultaneous injected touches, limited by Android's touch-event path.

## Performance and compatibility

The display output is connected directly to the `SurfaceView` to avoid an unnecessary CPU-side image-copy path. Touch MOVE samples are coalesced when the injection queue is behind, while pointer down/up/cancel transitions are preserved. Cursor and gamepad redraws are constrained to their overlay regions.

These changes reduce work inside the app, but cannot guarantee the same frame pacing as the device's physical screen. Virtual-display composition, refresh scheduling, GPU bandwidth, Android/ROM implementation, resolution, and Shizuku privileges remain device-dependent. Test on the target ROM/device and lower the virtual resolution if GPU/compositor load is high.

## Build

The project builds as a standalone Android app with Gradle. GitHub Actions builds the debug APK on pushes to `main` and `feature/fanny-gamepad-smoothing`.

## Requirements

- Android 8.0+ (API 26+).
- Shizuku running and permission granted for the app.
- Virtual-display creation and input injection can still be restricted by the Android version, ROM, or device policy.
