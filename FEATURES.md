# ReplayCam Android — 完整功能清單

> 對應 iOS 版所有功能，標注 Android 實作方式與注意事項

---

## 風險標注說明
- 🔴 高風險 — 需優先驗證
- 🟡 注意 — 與 iOS 有差異或需特別處理
- 🟢 可行 — 直接對應實作

---

## 相機畫面 (CameraScreen)

| 功能 | 說明 | Android 實作 | 風險 |
|------|------|-------------|------|
| 延遲畫面 | ImageReader 接收幀 → FrameBuffer → 找 N 秒前的幀全螢幕顯示 | ImageReader + Bitmap → SurfaceView / Compose Image | 🔴 |
| 即時小視窗（PiP） | 右上角預設，可拖曳移動，pinch 縮放 0.8x–3.0x，記憶位置 | pointerInput drag + transformable，offset + scale state | |
| 延遲 Slider（1–30s） | 滑動即時生效，不寫回設定（只改 selectedDelay） | Slider，remember state 獨立於 DataStore 設定值 | |
| 儲存時長 Slider | 30fps: 3–35s / 60fps: 3–30s / 120fps: 3–20s，上限隨 FPS 動態變化 | AlertDialog with Slider | |
| FPS 切換（30/60/120） | 動態偵測裝置支援 FPS，不支援的選項 disable，切換時重啟 Camera2 session | CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES 查詢，Camera2CameraControl | 🔴 |
| 鏡頭切換（前/後） | 切換時重建 Camera2 session，切回預設不鏡像 | 重新 openCamera() 指定不同 cameraId | |
| 鏡像翻轉 | 延遲畫面和即時小視窗同步左右翻轉，切換鏡頭時重置 | graphicsLayer { scaleX = -1f } 或 Matrix flip | |
| 點擊切換控制面板 | 點擊延遲畫面隱藏/顯示控制面板，隱藏時顯示「⏱ Ns」，動畫 0.22s | AnimatedVisibility + animateFloatAsState | |
| 儲存成功提示 | 儲存完成後顯示 Alert「儲存成功」 | AlertDialog 或 Snackbar | |

---

## 片段庫 (LibraryScreen)

| 功能 | 說明 | Android 實作 | 風險 |
|------|------|-------------|------|
| 資料夾/日期模式切換 | Segmented Control 切換，資料夾模式支援巢狀子資料夾 | TabRow 或 SegmentedButton | |
| 資料夾管理 | 新增（Alert 輸入名稱）、重新命名、刪除（遞迴刪子資料夾）、建立子資料夾、左滑刪除 | SwipeToDismiss / swipeableItem | |
| 日期分組 | 今天/昨天/月日，依建立日期降序，每組顯示片段數與總時長 | LazyColumn + stickyHeader | |
| 方格 Pinch 縮放欄數 | 2–5 欄，pinch 手勢即時調整 | detectTransformGestures → columCount.coerceIn(2,5) | 🟡 |
| 多選刪除模式 | 長按進入多選，底部操作列（刪除/移到資料夾/取消） | combinedClickable + BottomAppBar | |
| 最愛標記 | 縮圖右上角黃點，最愛置頂，DataStore 持久化 | DataStore 存 Set，排序 favorites first | |
| 縮圖非同步生成 | 9:16 比例，letterbox 填滿方格，非同步避免卡頓 | MediaMetadataRetriever.getFrameAtTime()，Coil 快取 | |
| 多選移到資料夾 | 多選後指派到資料夾或移出（unassign） | BottomSheet 選擇資料夾 | |

---

## 播放器 (PlayerScreen)

| 功能 | 說明 | Android 實作 | 風險 |
|------|------|-------------|------|
| 縮圖 Scrubber（30 格） | 水平 30 格縮圖，拖曳定位，拖曳時顯示時間氣泡，放開繼續播放 | ExoPlayer seekTo，自製 scrubber Row + pointerInput drag | |
| 播放速度（¼× / ½× / 1×） | 三個速度按鈕，選中高亮，切換即時生效 | ExoPlayer.setPlaybackParameters(PlaybackParameters(speed)) | |
| 最愛切換 | 播放器內直接切換，連動 ClipStore | ClipStore.toggleFavorite() | |
| 分享影片 | 自訂分享，排除「複製」，中文「儲存影片」取代系統 Save Video | Intent.ACTION_SEND，自訂 chooser，MediaStore 儲存相簿 | |

---

## 骨架分析 (PoseAnalysisScreen)

| 功能 | 說明 | Android 實作 | 風險 |
|------|------|-------------|------|
| 選片段入口 | 從片段庫選取影片，進入分析結果畫面 | 複用 LibraryScreen，callback 傳回選中 clip | |
| 骨架 2D 偵測 | 批次抽幀分析，17 COCO keypoint，置信度門檻 0.1 | ML Kit PoseDetection.getClient(accurate)，33→17 keypoint 映射 | 🟢 |
| 骨架 3D 世界座標 | iOS 用 Vision camera-relative position，Android 用 world landmarks（body-local，精度略差） | PoseLandmark.worldLandmarks | 🟡 |
| 關節角度折線圖 | 膝、髖、肘、肩角度隨時間變化，可切換顯示不同關節 | MPAndroidChart LineChart 或 Compose Canvas | |
| 骨架疊圖影片匯出 | 骨架線條疊在原始影片，匯出新 MP4 | MediaCodec encode，Canvas 每幀繪製骨架後 encode | |
| 俯視 3D 骨架視圖 | 3D world 座標俯視視角，顯示身體旋轉方向 | Compose Canvas，world landmarks XZ 平面投影 | |

---

## 設定 (SettingsScreen)

| 功能 | 說明 | Android 實作 |
|------|------|-------------|
| 預設延遲（1–30s） | Slider，DataStore 持久化，開啟相機時讀取 | DataStore Preferences |
| 預設 FPS | 30 / 60 / 120，DataStore 持久化 | DataStore Preferences |
| 預設鏡頭 | 前置/後置，DataStore 持久化 | DataStore Preferences |
| 儲存空間顯示 | 片段數量 + MB 用量即時計算 | File.walkTopDown().sumOf { it.length() } |
| 清除所有片段 | AlertDialog 二次確認，刪除後更新 ClipStore | AlertDialog |
| App 版本/建置號 | 版本名稱 + 版本號 | BuildConfig.VERSION_NAME + VERSION_CODE |

---

## 系統 / 全域

| 功能 | 說明 | Android 實作 |
|------|------|-------------|
| 權限請求 | CAMERA、READ_MEDIA_VIDEO（API 33+）/ READ_EXTERNAL_STORAGE（API 32-），拒絕後引導設定 | rememberLauncherForActivityResult |
| 深色模式 | 固定深色主題，不跟隨系統 | darkTheme = true 強制 |
| 直/橫向佈局 | 首頁支援直向（垂直排列）和橫向（左右分欄）；相機畫面鎖定直向 | BoxWithConstraints 或 LocalConfiguration |
| Thread 安全 | FrameBuffer 用 ReentrantLock，Camera callback 在 background，UI 更新到 Main | withContext(Dispatchers.Main)，StateFlow |

---

## 實作優先順序

1. 🔴 CameraManager（120fps 驗證）
2. 🔴 FrameBuffer（環形 buffer）
3. VideoExporter（MediaCodec）
4. ClipStore（片段管理）
5. CameraScreen UI
6. LibraryScreen UI（含 pinch 欄數、多選、資料夾）
7. PlayerScreen UI（含 scrubber）
8. SettingsScreen UI
9. PoseDetector（ML Kit）
10. PoseAnalysisScreen UI（骨架疊圖、圖表）
11. SkeletonVideoExporter
12. 骨架 3D / TopDownPoseView
