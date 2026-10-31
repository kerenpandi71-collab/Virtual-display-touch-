# Virtual Display Touch

A standalone Android project for controlling an Android virtual display from a phone touchscreen. This repository separates the Virtual Display Touch tools developed inside **Saku-Taskbar** so they can be maintained and built independently.

> **Migration status:** The project scope is documented here. The Kotlin implementation still needs to be transferred with its Android build configuration and verified as a standalone app. This README does not claim the migration or an APK build is complete.

## Project goals

- Create and manage an Android virtual display, including its surface and resolution.
- Show a live virtual-display preview with a cursor overlay.
- Provide a movable/resizable touchpad with saved portrait and landscape settings.
- Support direct touch, mouse-style input, scrolling, keyboard input, and runtime diagnostics.
- Include a virtual MOBA gamepad with a left analog and right-side action controls.
- Support per-control touch-point mapping, control sizing, and saved preferences.
- Keep the assistant floating “S” control tappable above the gamepad overlay.
- Use the Shizuku bridge where the required privileges are available.

## Components to migrate

- `VirtualDisplayTouchActivity.kt` — touchpad, preview, cursor, keyboard, settings, and diagnostics UI.
- `VirtualMobaGamepadView.kt` — virtual gamepad and mapped touch controls.
- `VirtualDisplayController.kt` — virtual-display lifecycle and surface management.
- `ShizukuBridge.kt` and `ShizukuUserService.kt` — privileged service bridge.

A standalone app also needs a compatible Android manifest, Gradle setup, resources, and required input/runtime dependencies. Package names and Saku-Taskbar-specific references must be reviewed during extraction.

## Device and compatibility

- Intended for Android phones, including the Redmi 12C used during development.
- Virtual-display creation and input injection depend on Android version, ROM behavior, and available privileges.
- Shizuku/root support is device- and setup-dependent; availability is not guaranteed.
- Resolution, density, touch mapping, and gamepad placement need on-device testing.

## Build and verification

A standalone Gradle build and device test have **not yet been confirmed** for this repository. Before calling it release-ready:

1. Build from this repository without Saku-Taskbar modules.
2. Verify virtual-display creation and surface lifecycle.
3. Test touch mapping, multi-touch, cursor behavior, and gamepad controls.
4. Confirm the assistant “S” overlay remains clickable above the gamepad.
5. Check saved settings in portrait and landscape.

## Source

The initial implementation was developed in [Saku-Taskbar](https://github.com/kerenpandi71-collab/Saku-Taskbar), branch `feature/virtual-display-touch`. This repository is intended to become the independent home for that feature set.
