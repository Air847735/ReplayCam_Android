# ReplayCam Android — 建構規劃

> 參考實作：`../replaycam`（Swift/iOS）  
> 目標：功能對等的 Android 原生 App，Kotlin + Jetpack Compose

---

## 技術選型對照

| 功能 | iOS（已實作） | Android（目標） |
|------|--------------|----------------|
| 相機 | `AVCaptureSession` | `Camera2 API` |
| 120fps 鎖定 | `minFrameDuration` | `CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE` |
| 環形 buffer | `CVPixelBuffer` + `NSLock` | `ImageReader` + `ArrayDeque` + `ReentrantLock` |
| 幀壓縮 | JPEG quality 0.6 | `Bitmap.compress(JPEG, 60)` |
| 影片匯出 | `AVAssetWriter` H.264 | `MediaCodec` + `MediaMuxer` H.264 |
| UI 框架 | SwiftUI | Jetpack Compose |
| 骨架偵測 2D | Vision `VNDetectHumanBodyPose` | ML Kit Pose Detection |
| 骨架偵測 3D | Vision `VNHumanBodyPose3DObservation` | MediaPipe Pose Landmarker（後期） |
| 儲存管理 | `FileManager` + `UserDefaults` | `File` + `SharedPreferences` / `DataStore` |
| 相簿存取 | `PHPhotoLibrary` | `MediaStore` |

---

## 專案結構

```
replaycam-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/replaycam/
│   │   │   ├── MainActivity.kt
│   │   │   ├── camera/
│   │   │   │   ├── CameraManager.kt        # Camera2 封裝，對應 iOS CameraManager
│   │   │   │   ├── FrameBuffer.kt          # 環形 buffer，對應 iOS FrameBuffer
│   │   │   │   └── TimestampedFrame.kt     # 幀資料 model
│   │   │   ├── export/
│   │   │   │   ├── VideoExporter.kt        # MediaCodec 匯出，對應 iOS VideoExporter
│   │   │   │   └── SkeletonVideoExporter.kt
│   │   │   ├── store/
│   │   │   │   └── ClipStore.kt            # 片段管理，對應 iOS ClipStore
│   │   │   ├── pose/
│   │   │   │   ├── PoseDetector.kt         # ML Kit，對應 iOS PoseDetector
│   │   │   │   └── PoseModels.kt           # Keypoint、PoseResult 資料結構
│   │   │   └── ui/
│   │   │       ├── home/
│   │   │       │   └── HomeScreen.kt       # 對應 iOS HomeView
│   │   │       ├── camera/
│   │   │       │   └── CameraScreen.kt     # 對應 iOS ContentView
│   │   │       ├── library/
│   │   │       │   ├── LibraryScreen.kt    # 對應 iOS DateLibraryView
│   │   │       │   └── PlayerScreen.kt     # 對應 iOS PlayerView
│   │   │       ├── pose/
│   │   │       │   ├── PoseAnalysisScreen.kt
│   │   │       │   └── PoseResultScreen.kt # 對應 iOS PoseAnalysisResultView
│   │   │       └── settings/
│   │   │           └── SettingsScreen.kt
│   │   └── res/
│   │       ├── drawable/                   # 圖示、tiss_pattern
│   │       └── values/
├── build.gradle.kts
└── PLAN.md
```

---

## 依賴套件（build.gradle）

```kotlin
// Jetpack Compose
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose:2.7.x")

// Camera2（透過 CameraX 簡化，但保留手動 fps 控制）
implementation("androidx.camera:camera-camera2:1.3.x")
implementation("androidx.camera:camera-lifecycle:1.3.x")
implementation("androidx.camera:camera-view:1.3.x")

// ML Kit Pose Detection
implementation("com.google.mlkit:pose-detection-accurate:18.0.x")

// MediaPipe（3D 骨架，後期）
// implementation("com.google.mediapipe:tasks-vision:0.10.x")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.x")

// DataStore（取代 UserDefaults）
implementation("androidx.datastore:datastore-preferences:1.0.x")

// Coil（縮圖載入）
implementation("io.coil-kt:coil-compose:2.x")
```

> **注意**：120fps 需要用 `Camera2` 直接控制，CameraX 的高階 API 無法精確鎖定。  
> 建議：用 CameraX 的 `Camera2CameraControl` interop 介面，兼顧便利性與精確控制。

---

## 實作優先順序

### Phase 1 — 核心相機（最高風險，先驗證）

**目標：** 確認 120fps 在 Android 上可行，建立 buffer pipeline

- [ ] `CameraManager.kt`
  - Camera2 session 建立（後置鏡頭，`CONTROL_AE_TARGET_FPS_RANGE = [120, 120]`）
  - `ImageReader` 接收 `YUV_420_888` 幀
  - 強制 30 / 60 / 120fps（依設定）
  - 前後鏡頭切換
  - 鏡像翻轉（`SCALER_CROP_REGION` 或 Compose 層 `scaleX = -1`）
- [ ] `FrameBuffer.kt`
  - `ReentrantLock` 保護的 `ArrayDeque<TimestampedFrame>`
  - 上限 35 秒 / 1200 幀提前清理
  - `findFrame(nearTimestamp)` / `framesSince(timestamp)`
- [ ] `CameraScreen.kt`
  - `PreviewView` 或手動 `SurfaceView` 顯示延遲畫面
  - 即時小視窗（可拖曳、可縮放）
  - 延遲選擇器（1–30 秒）
  - 儲存觸發按鈕

**驗收標準：** 120fps buffer 運作正常，延遲畫面流暢無卡頓，記憶體穩定。

---

### Phase 2 — 影片匯出

- [ ] `VideoExporter.kt`
  - `MediaCodec` encode H.264，`MediaMuxer` 封裝 MP4
  - 輸出 1080×1920，30fps，5Mbps（對應 iOS 設定）
  - `MediaStore` 存入相簿 / App 私有目錄
- [ ] `ClipStore.kt`
  - 掃描 `Documents/ReplayCamClips/` 目錄
  - 最愛清單（`SharedPreferences` 或 `DataStore`）
  - 資料夾管理（對應 iOS `ClipFolder`）

**驗收標準：** 匯出的 MP4 可正常播放，檔案在片段庫出現。

---

### Phase 3 — 片段庫與播放器

- [ ] `LibraryScreen.kt`
  - 依日期分組（`LazyColumn` + `stickyHeader`）
  - 方格縮圖（`LazyVerticalGrid`），pinch 調整欄數
  - 多選刪除模式
  - 縮圖非同步產生（`MediaMetadataRetriever`，對應 iOS `AVAssetImageGenerator`）
- [ ] `PlayerScreen.kt`
  - `ExoPlayer` 播放
  - 縮圖 scrubber（30 格，對應 iOS scrubber）
  - ¼× / ½× / 1× 速度切換（`ExoPlayer.setPlaybackSpeed`）
  - 最愛標記
  - 分享（`Intent.ACTION_SEND`）

---

### Phase 4 — 骨架分析（2D）

- [ ] `PoseDetector.kt`
  - ML Kit `PoseLandmarker`（accurate model）
  - 輸出 33 個 landmark（ML Kit）→ 映射到 17 個 COCO keypoint（對應 iOS）
  - 批次分析影片幀（`MediaMetadataRetriever` 逐幀抽取）
- [ ] `PoseResultScreen.kt`
  - 骨架疊圖（Canvas 繪製，對應 iOS `PoseOverlayView`）
  - 關節角度折線圖（`MPAndroidChart` 或 Compose Canvas）
  - 骨架疊圖影片匯出（`SkeletonVideoExporter.kt`）

---

### Phase 5 — 骨架分析 3D（後期）

- [ ] 整合 MediaPipe Pose Landmarker（world landmarks = 3D 座標，單位公尺）
- [ ] `TopDownPoseView` 俯視圖（對應 iOS `TopDownPoseView`）
- [ ] 角度計算從 3D world coordinates 計算（對應 iOS Vision 3D）

> MediaPipe world landmarks 精度不如 Apple Vision 的 camera-relative position，  
> 角度計算結果可能略有差異——這是平台限制，需在 UI 上說明。

---

### Phase 6 — 設定與收尾

- [ ] `SettingsScreen.kt`
  - 預設延遲 Slider
  - 錄製 FPS 選擇（30 / 60 / 120）
  - 預設鏡頭（前/後）
  - 儲存空間使用量
  - 清除所有片段
  - App 版本

- [ ] `HomeScreen.kt`
  - 對應 iOS 卡片式首頁
  - 直/橫向雙排版（`BoxWithConstraints` 判斷）
  - 品牌漸層背景 + tiss_pattern 紋理

- [ ] 權限處理
  - `CAMERA`
  - `RECORD_AUDIO`（若需要）
  - `READ_MEDIA_VIDEO` / `WRITE_EXTERNAL_STORAGE`（依 API level）

---

## 重要技術注意事項

### 120fps 驗證

不是所有 Android 裝置都支援 120fps，上架前需在 `StreamConfigurationMap` 確認：

```kotlin
val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
// 確認 [120, 120] 在列表中
```

建議 UI 動態顯示裝置支援的最高 fps，不支援 120 時自動降為 60。

### 幀格式

Camera2 預設輸出 `YUV_420_888`，匯出時需轉換：
- 顯示：`ImageReader` → `Bitmap` → `Canvas`
- 匯出：`YUV_420_888` → `NV12` → `MediaCodec` input surface

### 記憶體管理

35 秒 × 120fps = 4,200 幀，每幀 JPEG ~50KB → 峰值約 **200MB**。  
需設定 `largeHeap=true` 並在 buffer 滿時積極清理（對應 iOS 的 1200 幀門檻）。

### ML Kit vs Vision 差異

| | iOS Vision | Android ML Kit |
|--|-----------|---------------|
| Keypoint 數 | 17（COCO） | 33（COCO + 額外臉部/手部） |
| 3D 座標 | ✅ camera-relative | ⚠️ 只有 world landmarks（body-local） |
| 批次處理 | 需手動逐幀 | 需手動逐幀 |
| 準確度 | 高 | 高（accurate model） |

---

## 開始前的環境設定

1. Android Studio Hedgehog 以上
2. Min SDK: API 26（Android 8.0）— Camera2 穩定，ML Kit 最低需求
3. Target SDK: API 34
4. 測試裝置建議：Pixel 7 以上（支援 120fps）或 Samsung Galaxy S22 以上

---

## 里程碑時程估算

| Phase | 內容 | 估計工時 |
|-------|------|---------|
| 1 | 核心相機 + Buffer | 2–3 週 |
| 2 | 影片匯出 | 1–2 週 |
| 3 | 片段庫 + 播放器 | 2 週 |
| 4 | 骨架分析 2D | 2 週 |
| 5 | 骨架分析 3D | 2–3 週 |
| 6 | 設定 + 收尾 | 1 週 |
| **合計** | | **10–13 週** |

> Phase 1 的 120fps 驗證是最大未知數，建議優先完成再決定是否繼續。

---

## 參考對照表（iOS → Android）

| iOS 檔案 | Android 對應 |
|---------|------------|
| `CameraManager.swift` | `camera/CameraManager.kt` |
| `FrameBuffer.swift` | `camera/FrameBuffer.kt` |
| `VideoExporter.swift` | `export/VideoExporter.kt` |
| `SkeletonVideoExporter.swift` | `export/SkeletonVideoExporter.kt` |
| `ClipStore.swift` | `store/ClipStore.kt` |
| `PoseDetector.swift` | `pose/PoseDetector.kt` |
| `HomeView.swift` | `ui/home/HomeScreen.kt` |
| `ContentView.swift` | `ui/camera/CameraScreen.kt` |
| `DateLibraryView.swift` | `ui/library/LibraryScreen.kt` |
| `PlayerView.swift` | `ui/library/PlayerScreen.kt` |
| `PoseAnalysisResultView.swift` | `ui/pose/PoseResultScreen.kt` |
| `SettingsView.swift` | `ui/settings/SettingsScreen.kt` |
