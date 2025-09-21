# Android Virtual Camera (Xposed Module)

> **Disclaimer:** Do not use this project for any illegal purpose. You are solely responsible for every action performed with it.

Android Virtual Camera is an Xposed/LSPosed module that replaces the live camera feed of other applications with user-provided media. It supports both the legacy `Camera` API and the modern `camera2` API.

## Requirements

- Android 5.0 or newer.
- A rooted device with an Xposed-compatible framework (e.g. LSPosed) that can scope the target application.
- Storage access for the target application so it can read the replacement media files.

## Quick start

1. Install the module and enable it for the applications where you want to spoof the camera feed. Frameworks with per-app scopes must target the desired applications instead of the system framework.
2. Grant the target application **Read** and **Write** storage permissions in Android settings, then force-stop the app so it can pick up the change. If the application cannot request the permission, the module will redirect the storage location automatically (see below).
3. Launch the target application and open its camera. If the app cannot access shared storage you will receive a toast message indicating that the `Camera1` directory has been redirected to the private path `Android/data/<package-name>/files/Camera1/`. Otherwise the default path is `/storage/emulated/0/DCIM/Camera1/`. Create the directory manually if it does not exist.
4. Place a replacement video named `virtual.mp4` inside the `Camera1` directory. When the preview opens the module logs the required resolution; the replacement video **must** match this resolution for the preview to display correctly.
5. (Optional) To spoof captured photos, place a still image named `1000.bmp` inside the same directory. The module now checks the file before using it—if the bitmap is missing or cannot be decoded it will keep the original capture data so the calling application no longer crashes.
6. Configure optional behaviour using the control files listed below or via the switches exposed in the companion application.

### Control files

| File name | Location | Effect | Scope |
|-----------|----------|--------|-------|
| `disable.jpg` | `Camera1/` | Temporarily disables all camera replacement features. | Global |
| `no-silent.jpg` | `Camera1/` | Plays audio when the replacement video is rendered. | Global |
| `no_toast.jpg` | `Camera1/` | Silences in-app toast messages. | Global |
| `force_show.jpg` | `Camera1/` | Forces directory redirection toasts to display again. | Global |
| `private_dir.jpg` | `Camera1/` | Forces every scoped application to use its private storage directory. | Per app |

> The companion app toggles create and delete these files automatically. You can also manage them manually if preferred.

## Logging and diagnostics

- The module now logs detailed capture information (missing bitmap, compression failures, generated buffer sizes) to the Xposed log. Use this to verify whether replacement media is being applied.
- The companion application writes additional Logcat entries under the `VCamMainActivity` tag when switches are toggled or permissions are requested, which helps diagnosing configuration problems.

## Troubleshooting

- **Front camera orientation issues** – Most front-camera previews require the replacement video to be flipped horizontally and rotated 90° clockwise. Always ensure the processed video resolution matches the value shown in the toast message. Some devices may not require any rotation.
- **Black preview or camera fails to open** – A few applications (notably some system cameras) cannot be hooked reliably. Ensure the `virtual.mp4` file resides directly in `DCIM/Camera1/` and not in a nested `Camera1/Camera1/` structure.
- **Distorted or garbled preview** – The replacement video resolution does not match the preview resolution reported by the module.
- **Captured photo shows the real camera** – Ensure `1000.bmp` matches the capture resolution shown in the toast message. If the image cannot be decoded the module falls back to the original photo and logs the reason in the Xposed log.
- **`disable.jpg` appears to have no effect** – For module versions up to 4.0, control files under `/DCIM/Camera1/` apply only to applications that can access shared storage. Applications without that permission must create the files inside their private `Camera1` directory. Starting with version 4.1, the shared storage location is honoured regardless of the target app's storage permission state.

## Reporting issues

Please open a GitHub issue and attach the Xposed module log as well as any relevant logcat output. Including the module's diagnostic log lines greatly speeds up reproducing camera issues.

## Credits

- Hooking approach inspired by [wangwei1237/CameraHook](https://github.com/wangwei1237/CameraHook)
- Hardware H.264 decoding adapted from [zhantong/Android-VideoToImages](https://github.com/zhantong/Android-VideoToImages)
- JPEG to YUV conversion based on [jacke121's article](https://blog.csdn.net/jacke121/article/details/73888732)

