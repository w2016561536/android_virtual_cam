# android_VCAM-Revise
原作者[@w2016561536](https://github.com/w2016561536/android_virtual_cam)

[简体中文](./README.md) | [繁體中文](./README_tc.md) | [English](./README_en.md)

[![Android Build](https://github.com/Cross2pro/android_virtual_cam/workflows/Android%20Build/badge.svg)](https://github.com/Cross2pro/android_virtual_cam/actions)
[![Version](https://img.shields.io/badge/version-4.4-blue.svg)](https://github.com/w2016561536/android_virtual_cam/releases)
[![Platform](https://img.shields.io/badge/platform-Android%205.0%2B-green.svg)](https://github.com/w2016561536/android_virtual_cam)

基于Xposed的虚拟摄像头模块，可将Android相机预览和拍照替换为自定义视频和图片。

## ⚠️ 免责声明

**请勿用于任何非法用途，所有后果自负。**

本工具仅供学习和研究使用，请勿用于任何违法违规行为。使用者需承担使用本工具产生的一切后果和责任。

## 🌟 主要特性

- ✅ **双API支持**：支持Camera1和Camera2 API
- ✅ **视频替换**：将相机预览替换为自定义MP4视频
- ✅ **照片替换**：将拍照结果替换为自定义BMP图片
- ✅ **音频播放**：可选择播放视频音频
- ✅ **应用配置界面**：5个可视化开关，无需手动创建文件
- ✅ **私有目录支持**：支持为每个应用单独配置视频
- ✅ **实时生效**：配置文件修改后立即生效，无需重启
- ✅ **Toast消息控制**：可关闭提示消息
- ✅ **自动化构建**：GitHub Actions自动构建和发布

## 📱 支持平台

- Android 5.0+ (API 21+)
- 需要Xposed框架（如LSPosed、EdXposed等）

## 📦 下载与安装

### 下载地址

- **GitHub Releases**：[最新版本](https://github.com/w2016561536/android_virtual_cam/releases)
- **中国大陆加速（Gitee）**：https://gitee.com/w2016561536/android_virtual_cam

### 安装步骤

1. 下载最新版本的APK文件
2. 在Android设备上安装APK
3. 在Xposed框架中启用此模块
4. 重启设备使模块生效

## 📖 使用方法

### 基础配置

1. **启用模块**
   - 在Xposed框架（如LSPosed）中启用本模块
   - LSPosed等包含作用域的框架需要选择目标app，**无需选择系统框架**
   - 重启目标应用或设备

2. **授予权限**
   - 在系统设置中，授予目标应用读取本地存储的权限
   - 强制结束目标应用程序
   - 若应用程序未申请此权限，请见步骤3

3. **确定工作目录**
   - 打开目标应用，若应用未能获得读取存储的权限，会以Toast消息提示
   - 私有目录模式：`/[内部存储]/Android/data/[应用包名]/files/Camera1/`
   - 共享目录模式：`/[内部存储]/DCIM/Camera1/`
   - 若目录不存在，请手动创建
   
   > 💡 **提示**：私有目录下的`Camera1`仅对该应用单独生效

### 视频和照片配置

4. **准备替换视频**
   - 在目标应用中打开相机预览
   - 查看Toast消息显示的分辨率（宽×高）
   - 根据此分辨率制作替换视频
   - 将视频命名为`virtual.mp4`，放置于`Camera1`目录下
   - 若无提示消息，则无需调整视频分辨率

5. **准备替换照片（可选）**
   - 在目标应用中拍照时，若显示真实图片且有Toast提示`发现拍照`
   - 查看Toast消息显示的照片分辨率
   - 准备一张相同分辨率的照片，命名为`1000.bmp`
   - 放入`Camera1`目录下（支持其它格式改后缀为bmp）
   - 如果拍照时无Toast提示，则无需准备`1000.bmp`

### 功能开关配置

本模块提供了5个功能开关，有两种配置方式：

#### 方式一：使用应用界面配置（推荐）

打开VCAM应用，使用可视化开关进行配置：

- **强制显示权限提醒**：重复显示目录重定向的Toast消息
- **临时禁用模块**：临时停用视频替换功能
- **播放视频声音**：播放替换视频的音频
- **强制使用私有目录**：为每个应用单独分配视频
- **禁用Toast消息**：关闭所有Toast提示

#### 方式二：手动创建文件配置

在`/[内部存储]/DCIM/Camera1/`目录下创建对应文件：

| 功能 | 文件名 | 说明 |
|------|--------|------|
| 播放视频声音 | `no-silent.jpg` | 启用视频音频播放 |
| 临时禁用模块 | `disable.jpg` | 临时停用视频替换 |
| 禁用Toast消息 | `no_toast.jpg` | 关闭Toast提示 |
| 强制显示权限提醒 | `force_show.jpg` | 重复显示目录重定向消息 |
| 强制使用私有目录 | `private_dir.jpg` | 强制所有应用使用私有目录 |

> 💡 **提示**：所有配置开关全局实时生效，修改后无需重启应用

## ❓ 常见问题

### Q1. 前置摄像头方向问题？
**A1.** 大多数情况下，替换前置摄像头的视频需要水平翻转并右旋90度，并且视频***处理后***的分辨率应与Toast消息内分辨率相同。但有时这并不需要，具体请根据实际情况判断。

### Q2. 画面黑屏，相机启动失败？
**A2.** 目前有些应用并不能成功替换（特别是系统相机）。或者是因为视频路径不对（是否创建了两级Camera1目录，如`./DCIM/Camera1/Camera1/virtual.mp4`，但只需要一级目录）。

### Q3. 画面花屏？
**A3.** 视频分辨率不对。

### Q4. 画面扭曲、变形？
**A4.** 请使用剪辑软件修改原视频来匹配屏幕。

### Q5. 创建`disable.jpg`无效？
**A5.** 
- 如果应用版本`<=4.0`，那么`[内部存储]/DCIM/Camera1`目录下的文件对**具有访问存储权限**的应用生效，其余无权限应用应在**私有目录**下创建
- 如果应用版本`>=4.1`，那么应在`[内部存储]/DCIM/Camera1`创建，无论目标应用是否具有权限

## 🔧 开发与构建

### 版本信息

- **当前版本**：v4.4 (versionCode 28)
- **编译SDK**：Android 12 (API 31)
- **最低支持**：Android 5.0 (API 21)
- **目标SDK**：Android 8.0 (API 26)

### 自动化构建

本项目使用GitHub Actions实现自动化构建和发布：

- **持续集成**：每次代码推送都会自动编译验证
- **自动发布**：创建新标签（tag）时自动构建签名APK并发布Release
- **工作流配置**：参见 [.github/workflows/android-build.yml](.github/workflows/android-build.yml)
- **详细说明**：参见 [WORKFLOW_CHANGES.md](WORKFLOW_CHANGES.md)

### 本地构建

```bash
# 克隆仓库
git clone https://github.com/w2016561536/android_virtual_cam.git
cd android_virtual_cam

# 构建调试版本
./gradlew assembleDebug

# 构建发布版本（需要签名配置）
./gradlew assembleRelease
```

## 🐛 反馈问题

请直接在[Issues](https://github.com/w2016561536/android_virtual_cam/issues)中反馈。

如果是BUG反馈，请附带以下信息：
- Xposed**模块**日志信息
- Android系统版本
- Xposed框架类型和版本
- 目标应用信息
- 复现步骤

## 🙏 致谢

- @w2016561536 [原项目](https://github.com/w2016561536/android_virtual_cam)
- 提供HOOK思路：[CameraHook](https://github.com/wangwei1237/CameraHook)
- H264硬解码：[Android-VideoToImages](https://github.com/zhantong/Android-VideoToImages)
- JPEG转YUV：[博客文章](https://blog.csdn.net/jacke121/article/details/73888732)

## 📜 许可证

本项目仅供学习和研究使用，请勿用于任何违法违规用途。

## ⭐ 支持项目

如果这个项目对你有帮助，请给个Star ⭐️
