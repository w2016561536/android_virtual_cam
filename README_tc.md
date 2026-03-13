# android_virtual_cam

[简体中文](./README.md) | [繁體中文](./README_tc.md) | [English](./README_en.md)

[![Android Build](https://github.com/w2016561536/android_virtual_cam/workflows/Android%20Build/badge.svg)](https://github.com/w2016561536/android_virtual_cam/actions)
[![Version](https://img.shields.io/badge/version-4.4-blue.svg)](https://github.com/w2016561536/android_virtual_cam/releases)
[![Platform](https://img.shields.io/badge/platform-Android%205.0%2B-green.svg)](https://github.com/w2016561536/android_virtual_cam)

基於Xposed的虛擬攝影機模組，可將Android相機預覽和拍照替換為自訂影片和圖片。

## ⚠️ 免責聲明

**請勿用於任何非法用途，所有後果自負。**

本工具僅供學習和研究使用，請勿用於任何違法違規行為。使用者需承擔使用本工具產生的一切後果和責任。

## 🌟 主要特性

- ✅ **雙API支援**：支援Camera1和Camera2 API
- ✅ **影片替換**：將相機預覽替換為自訂MP4影片
- ✅ **照片替換**：將拍照結果替換為自訂BMP圖片
- ✅ **音訊播放**：可選擇播放影片音訊
- ✅ **應用程式配置介面**：5個可視化開關，無需手動建立檔案
- ✅ **私有目錄支援**：支援為每個應用程式單獨配置影片
- ✅ **即時生效**：配置檔案修改後立即生效，無需重啟
- ✅ **Toast訊息控制**：可關閉提示訊息
- ✅ **自動化建置**：GitHub Actions自動建置和發布

## 📱 支援平台

- Android 5.0+ (API 21+)
- 需要Xposed框架（如LSPosed、EdXposed等）

## 📦 下載與安裝

### 下載位址

- **GitHub Releases**：[最新版本](https://github.com/w2016561536/android_virtual_cam/releases)
- **中國大陸加速（Gitee）**：https://gitee.com/w2016561536/android_virtual_cam

### 安裝步驟

1. 下載最新版本的APK檔案
2. 在Android裝置上安裝APK
3. 在Xposed框架中啟用此模組
4. 重啟裝置使模組生效

## 📖 使用方法

### 基礎配置

1. **啟用模組**
   - 在Xposed框架（如LSPosed）中啟用本模組
   - LSPosed等包含作用域的框架需要選擇目標app，**無需選擇系統框架**
   - 重啟目標應用程式或裝置

2. **授予許可權**
   - 在系統設定中，授予目標應用程式讀取本機儲存的許可權
   - 強制結束目標應用程式
   - 若應用程式未申請此許可權，請見步驟3

3. **確定工作目錄**
   - 打開目標應用程式，若應用程式未能獲得讀取儲存的許可權，會以Toast訊息提示
   - 私有目錄模式：`/[內部儲存]/Android/data/[應用程式套件名稱]/files/Camera1/`
   - 共用目錄模式：`/[內部儲存]/DCIM/Camera1/`
   - 若目錄不存在，請手動建立
   
   > 💡 **提示**：私有目錄下的`Camera1`僅對該應用程式單獨生效

### 影片和照片配置

4. **準備替換影片**
   - 在目標應用程式中打開相機預覽
   - 檢視Toast訊息顯示的解析度（寬×高）
   - 根據此解析度製作替換影片
   - 將影片命名為`virtual.mp4`，放置於`Camera1`目錄下
   - 若無提示訊息，則無需調整影片解析度

5. **準備替換照片（可選）**
   - 在目標應用程式中拍照時，若顯示真實圖片且有Toast提示`發現拍照`
   - 檢視Toast訊息顯示的照片解析度
   - 準備一張相同解析度的照片，命名為`1000.bmp`
   - 放入`Camera1`目錄下（支援其它格式改尾碼為bmp）
   - 如果拍照時無Toast提示，則無需準備`1000.bmp`

### 功能開關配置

本模組提供了5個功能開關，有兩種配置方式：

#### 方式一：使用應用程式介面配置（推薦）

打開VCAM應用程式，使用可視化開關進行配置：

- **強制顯示許可權提醒**：重複顯示目錄重新導向的Toast訊息
- **臨時禁用模組**：臨時停用影片替換功能
- **播放影片聲音**：播放替換影片的音訊
- **強制使用私有目錄**：為每個應用程式單獨分配影片
- **禁用Toast訊息**：關閉所有Toast提示

#### 方式二：手動建立檔案配置

在`/[內部儲存]/DCIM/Camera1/`目錄下建立對應檔案：

| 功能 | 檔案名稱 | 說明 |
|------|---------|------|
| 播放影片聲音 | `no-silent.jpg` | 啟用影片音訊播放 |
| 臨時禁用模組 | `disable.jpg` | 臨時停用影片替換 |
| 禁用Toast訊息 | `no_toast.jpg` | 關閉Toast提示 |
| 強制顯示許可權提醒 | `force_show.jpg` | 重複顯示目錄重新導向訊息 |
| 強制使用私有目錄 | `private_dir.jpg` | 強制所有應用程式使用私有目錄 |

> 💡 **提示**：所有配置開關全域即時生效，修改後無需重啟應用程式

## ❓ 常見問題

### Q1. 前置攝影機方向問題？
**A1.** 大多數情況下，替換前置攝影機的影片需要水平翻轉並右旋90度，並且影片***處理後***的解析度應與Toast訊息內解析度相同。但有時這並不需要，具體請根據實際情況判斷。

### Q2. 畫面黑屏，相機啟動失敗？
**A2.** 目前有些應用程式並不能成功替換（特別是系統相機）。或者是因為影片路徑不對（是否建立了兩級Camera1目錄，如`./DCIM/Camera1/Camera1/virtual.mp4`，但只需要一級目錄）。

### Q3. 畫面花屏？
**A3.** 影片解析度不對。

### Q4. 畫面扭曲、變形？
**A4.** 請使用剪輯軟體修改原影片來匹配螢幕。

### Q5. 建立`disable.jpg`無效？
**A5.**
- 如果應用程式版本`<=4.0`，那麼`[內部儲存]/DCIM/Camera1`目錄下的檔案對**具有存取儲存許可權**的應用程式生效，其餘無許可權應用程式應在**私有目錄**下建立
- 如果應用程式版本`>=4.1`，那麼應在`[內部儲存]/DCIM/Camera1`建立，無論目標應用程式是否具有許可權

## 🔧 開發與建置

### 版本資訊

- **當前版本**：v4.4 (versionCode 28)
- **編譯SDK**：Android 12 (API 31)
- **最低支援**：Android 5.0 (API 21)
- **目標SDK**：Android 8.0 (API 26)

### 自動化建置

本專案使用GitHub Actions實現自動化建置和發布：

- **持續整合**：每次程式碼推送都會自動編譯驗證
- **自動發布**：建立新標籤（tag）時自動建置簽名APK並發布Release
- **工作流配置**：參見 [.github/workflows/android-build.yml](.github/workflows/android-build.yml)
- **詳細說明**：參見 [WORKFLOW_CHANGES.md](WORKFLOW_CHANGES.md)

### 本機建置

```bash
# 複製儲存庫
git clone https://github.com/w2016561536/android_virtual_cam.git
cd android_virtual_cam

# 建置除錯版本
./gradlew assembleDebug

# 建置發布版本（需要簽名配置）
./gradlew assembleRelease
```

## 🐛 回饋問題

請直接在[Issues](https://github.com/w2016561536/android_virtual_cam/issues)中回饋。

如果是BUG回饋，請附帶以下資訊：
- Xposed**模組**日誌資訊
- Android系統版本
- Xposed框架類型和版本
- 目標應用程式資訊
- 復現步驟

## 🙏 致謝

- 提供HOOK思路：[CameraHook](https://github.com/wangwei1237/CameraHook)
- H264硬解碼：[Android-VideoToImages](https://github.com/zhantong/Android-VideoToImages)
- JPEG轉YUV：[部落格文章](https://blog.csdn.net/jacke121/article/details/73888732)

## 📜 授權

本專案僅供學習和研究使用，請勿用於任何違法違規用途。

## ⭐ 支援專案

如果這個專案對你有幫助，請給個Star ⭐️
