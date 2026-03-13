# android_virtual_cam

[简体中文](./README.md) | [繁體中文](./README_tc.md) | [English](./README_en.md)

[![Android Build](https://github.com/w2016561536/android_virtual_cam/workflows/Android%20Build/badge.svg)](https://github.com/w2016561536/android_virtual_cam/actions)
[![Version](https://img.shields.io/badge/version-4.4-blue.svg)](https://github.com/w2016561536/android_virtual_cam/releases)
[![Platform](https://img.shields.io/badge/platform-Android%205.0%2B-green.svg)](https://github.com/w2016561536/android_virtual_cam)

An Xposed-based virtual camera module that replaces Android camera preview and photo capture with custom videos and images.

## ⚠️ Disclaimer

**DO NOT USE FOR ANY ILLEGAL PURPOSE! YOU MUST TAKE ALL RESPONSIBILITY AND CONSEQUENCES!**

This tool is for learning and research purposes only. Do not use it for any illegal activities. Users must assume all consequences and responsibilities arising from using this tool.

## 🌟 Key Features

- ✅ **Dual API Support**: Supports both Camera1 and Camera2 APIs
- ✅ **Video Replacement**: Replace camera preview with custom MP4 videos
- ✅ **Photo Replacement**: Replace captured photos with custom BMP images
- ✅ **Audio Playback**: Optional video audio playback
- ✅ **App Configuration UI**: 5 visual switches, no need to create files manually
- ✅ **Private Directory Support**: Separate video configuration for each app
- ✅ **Real-time Effect**: Configuration changes take effect immediately without restart
- ✅ **Toast Message Control**: Option to disable toast notifications
- ✅ **Automated Build**: GitHub Actions for automated building and releasing

## 📱 Supported Platforms

- Android 5.0+ (API 21+)
- Requires Xposed framework (such as LSPosed, EdXposed, etc.)

## 📦 Download & Installation

### Download

- **GitHub Releases**: [Latest Version](https://github.com/w2016561536/android_virtual_cam/releases)
- **China Mainland Mirror (Gitee)**: https://gitee.com/w2016561536/android_virtual_cam

### Installation Steps

1. Download the latest APK file
2. Install the APK on your Android device
3. Enable the module in Xposed framework
4. Reboot device to activate the module

## 📖 Usage

### Basic Configuration

1. **Enable Module**
   - Enable this module in Xposed framework (such as LSPosed)
   - For scoped frameworks like LSPosed, select the target app, **no need to select System Framework**
   - Reboot target app or device

2. **Grant Permissions**
   - In system settings, grant storage read permission to target app
   - Force stop the target app
   - If the app doesn't request this permission, see step 3

3. **Determine Working Directory**
   - Open target app; if it lacks storage permission, a toast message will appear
   - Private directory mode: `/[Internal Storage]/Android/data/[package name]/files/Camera1/`
   - Shared directory mode: `/[Internal Storage]/DCIM/Camera1/`
   - Create the directory manually if it doesn't exist
   
   > 💡 **Tip**: `Camera1` in private directory only affects that specific app

### Video and Photo Configuration

4. **Prepare Replacement Video**
   - Open camera preview in target app
   - Check the resolution (width × height) shown in toast message
   - Create a replacement video matching this resolution
   - Name the video as `virtual.mp4` and place it in `Camera1` directory
   - If no toast message appears, no need to adjust video resolution

5. **Prepare Replacement Photo (Optional)**
   - When taking photos in target app, if real photo shows and toast message appears with "发现拍照"
   - Check the photo resolution in toast message
   - Prepare an image with the same resolution, name it as `1000.bmp`
   - Place it in `Camera1` directory (supports other formats renamed to bmp)
   - If no toast message when taking photos, no need for `1000.bmp`

### Function Switch Configuration

This module provides 5 function switches with two configuration methods:

#### Method 1: Use App Interface (Recommended)

Open the VCAM app and configure using visual switches:

- **Force Show Permission Warning**: Repeatedly show directory redirection toast
- **Temporarily Disable Module**: Temporarily stop video replacement
- **Play Video Sound**: Play audio from replacement video
- **Force Private Directory**: Separate video for each app
- **Disable Toast Messages**: Turn off all toast notifications

#### Method 2: Manual File Creation

Create corresponding files in `/[Internal Storage]/DCIM/Camera1/` directory:

| Function | Filename | Description |
|----------|----------|-------------|
| Play video sound | `no-silent.jpg` | Enable video audio playback |
| Temporarily disable | `disable.jpg` | Temporarily stop replacement |
| Disable toast | `no_toast.jpg` | Turn off toast notifications |
| Force show warning | `force_show.jpg` | Repeatedly show redirection message |
| Force private dir | `private_dir.jpg` | Force all apps to use private directory |

> 💡 **Tip**: All configuration switches take effect globally in real-time without restart

## ❓ FAQ

### Q1. Front camera orientation issue?
**A1.** In most cases, videos for replacing front camera need to be flipped horizontally and rotated 90 degrees clockwise, and the video resolution ***after processing*** should match the resolution in toast message. But sometimes this is not needed; please judge according to actual situation.

### Q2. Black screen or camera fails to start?
**A2.** Currently some apps cannot be hooked successfully (especially system camera). Or it's due to wrong video path (whether two levels of Camera1 directory were created, like `./DCIM/Camera1/Camera1/virtual.mp4`, but only one level is needed).

### Q3. Blurred screen?
**A3.** Wrong video resolution.

### Q4. Distorted or deformed picture?
**A4.** Please use video editing software to modify the original video to match the screen.

### Q5. Creating `disable.jpg` doesn't work?
**A5.**
- If app version `<=4.0`, files in `[Internal Storage]/DCIM/Camera1` directory work for apps **with storage access permission**; apps without permission should create in **private directory**
- If app version `>=4.1`, should create in `[Internal Storage]/DCIM/Camera1` regardless of whether target app has permission

## 🔧 Development & Build

### Version Info

- **Current Version**: v4.4 (versionCode 28)
- **Compile SDK**: Android 12 (API 31)
- **Minimum Support**: Android 5.0 (API 21)
- **Target SDK**: Android 8.0 (API 26)

### Automated Build

This project uses GitHub Actions for automated building and releasing:

- **Continuous Integration**: Automatic compilation verification on every code push
- **Auto Release**: Automatically build signed APK and publish Release when creating new tags
- **Workflow Config**: See [.github/workflows/android-build.yml](.github/workflows/android-build.yml)
- **Detailed Documentation**: See [WORKFLOW_CHANGES.md](WORKFLOW_CHANGES.md)

### Local Build

```bash
# Clone repository
git clone https://github.com/w2016561536/android_virtual_cam.git
cd android_virtual_cam

# Build debug version
./gradlew assembleDebug

# Build release version (requires signing configuration)
./gradlew assembleRelease
```

## 🐛 Report Issues

Please report directly in [Issues](https://github.com/w2016561536/android_virtual_cam/issues).

For bug reports, please include:
- Xposed **module** log information
- Android system version
- Xposed framework type and version
- Target app information
- Steps to reproduce

## 🙏 Credits

- HOOK method reference: [CameraHook](https://github.com/wangwei1237/CameraHook)
- H264 hardware decode: [Android-VideoToImages](https://github.com/zhantong/Android-VideoToImages)
- JPEG to YUV conversion: [Blog post](https://blog.csdn.net/jacke121/article/details/73888732)

## 📜 License

This project is for learning and research purposes only. Do not use for any illegal activities.

## ⭐ Support

If this project helps you, please give it a Star ⭐️
