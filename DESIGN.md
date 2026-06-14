# ReplayCam Android — 視覺設計規格

> 基於 iOS 截圖（image/ 資料夾）反推，供 Android Compose UI 實作參考

---

## 色彩系統

### 背景漸層
```
主背景：LinearGradient
  起點（左上）：#0A2950（深海軍藍）
  終點（右下）：#043838（深青）
  方向：topStart → bottomEnd
```

### 紋理疊層
```
tiss_pattern 紋理圖（tile 重複）
opacity：0.13
疊在漸層背景上方
```

### 卡片顏色
```
延遲錄影卡片：LinearGradient #1A72CC → #0D9980（藍→青綠，左上→右下）
骨架分析卡片：LinearGradient #6619B3 → #330D80（紫→深紫，左上→右下）
小卡片（日期/設定）：rgba(255,255,255,0.08)，邊框 rgba(255,255,255,0.12)
```

### 文字顏色
```
主要文字：#FFFFFF
次要文字：rgba(255,255,255,0.75)
提示文字：rgba(255,255,255,0.55)
強調色（數值）：#4DA8FF（藍）
危險操作：#FF6B6B（紅）
```

### NavigationBar
```
背景色：#0A2950
文字色：#FFFFFF
標題樣式：inline（置中）
colorScheme：dark
```

### 骨架分析顏色
```
骨架線條 / keypoint：白色圓點（iOS 截圖顯示）
角度數值：
  正常範圍：#4CAF50（綠）
  警示範圍：#FF9800（橘）
  異常範圍：#F44336（紅）
折線圖線條：#CC44FF（紫）
```

---

## 字體規格

```
App 標題（ReplayCam）：28sp Bold
大卡片標題（延遲錄影）：22sp Bold
大卡片副標：13sp Regular，opacity 0.75
小卡片標題：15sp SemiBold
小卡片副標：12sp Regular，opacity 0.55
NavigationBar 標題：17sp SemiBold
設定列 label：15sp Regular
設定列 value：15sp Regular，opacity 0.6
骨架角度數值：32sp Bold
骨架角度 label：13sp Regular，opacity 0.7
```

---

## 圓角規格

```
大卡片（延遲錄影/骨架分析）：22dp
小卡片（日期記錄/設定）：18dp
設定卡片群組：16dp
未分類區塊卡片：16dp
Segmented Control 外框：10dp
按鈕（儲存）：∞（Capsule）
TISS Logo 欄：16dp
```

---

## 間距規格

```
畫面左右 padding：20dp
元件垂直間距：14dp
大卡片內距：22dp
小卡片內距：16dp
設定列 padding：14dp 水平 / 12dp 垂直
TISS Logo bar padding：垂直 12dp / 水平 20dp
```

---

## 畫面規格

---

### 1. 首頁 (HomeScreen)
**截圖參考：** IMG_5732.PNG

#### 結構（由上到下）
```
SafeArea top padding
├── App 標題「ReplayCam」28sp Bold 白色
├── 大卡片：延遲錄影（高度約 180dp）
│     ├── icon 左上：camera（28sp 白色）
│     └── 文字左下：
│           標題「延遲錄影」22sp Bold
│           副標「設定延遲秒數，即時觀看動作回放」13sp
├── 大卡片：骨架分析（高度約 180dp）
│     ├── icon 左上：figure.run（28sp 白色）
│     └── 文字左下：
│           標題「骨架分析」22sp Bold
│           副標「選取影片，分析關節角度與動作數據」13sp
├── 小卡片 Row（高度 100dp）
│     ├── 日期記錄（flex 1）
│     │     icon：calendar，title：「日期記錄」，subtitle：「N 個片段」
│     └── 設定（flex 1）
│           icon：gear，title：「設定」，subtitle：「延遲與偏好」
├── Spacer
└── TISS Logo bar（白底 94% opacity，圓角 16dp，高 44dp）
      內含 tiss_logo 圖片 + 「國家運動科學中心」文字
```

#### 注意事項
- 大卡片有 tiss_pattern 紋理疊層（opacity 0.12）
- 點擊大卡片有觸覺回饋
- 直向：垂直排列；橫向：左右分欄（相機左、骨架+小卡右）

---

### 2. 相機畫面 (CameraScreen)
**截圖參考：** IMG_5728.PNG

#### 結構
```
全螢幕
├── 延遲畫面（全螢幕背景，scaledToFill）
├── 返回按鈕（左上角）
│     圓形 ultraThinMaterial，chevron.left icon，48x48dp
├── 即時小視窗（右上角，可拖曳）
│     比例 9:16，圓角 8dp，白色邊框 1.5dp
│     pinch 縮放 0.8x–3.0x
└── 底部控制列（ultraThinMaterial 背景，圓角 16dp）
      ├── Row 1：左側相機切換 icon | 延遲 Slider（1-30s）| 值顯示「5s」
      ├── Row 2：左側鏡像 icon | 儲存長度 Slider | 值顯示「10s」
      └── Row 3 右側：藍色「儲存」按鈕（Capsule，#007AFF，下載 icon）
```

#### 控制列細節
```
控制列背景：rgba(10,15,26,0.85) + blur
slider track：rgba(255,255,255,0.2)，高度 3dp
slider thumb：白色圓形 12dp
值文字：白色 13sp Bold
儲存按鈕：#007AFF，白色文字+icon，Capsule，padding 12dp x 20dp
```

#### 互動
```
點擊延遲畫面：切換控制列顯示/隱藏（0.22s easeInOut）
隱藏時：底部顯示「⏱ Ns」capsule 提示
即時小視窗：拖曳移動，pinch 縮放，雙指手勢同時支援
```

---

### 3. 片段庫 (LibraryScreen)
**截圖參考：** IMG_5729.PNG / IMG_5730.PNG

#### NavigationBar
```
標題：「日期記錄」或「骨架分析」
右上角：新增資料夾 icon（folder.badge.plus）
```

#### Segmented Control
```
位置：NavigationBar 下方
選項：「資料夾」｜「日期」
選中：白底白字（白色背景 pill）
未選中：透明背景，白色文字 opacity 0.6
```

#### 資料夾模式
```
Section 1「資料夾」：
  └── 「新增資料夾」列（folder.badge.plus icon，白色文字 opacity 0.75）

Section 2「未分類」：
  └── 圓角卡片（rgba(255,255,255,0.08)，圓角 16dp）
        ├── Jun 10（粗體）
        │     片段數 icon + 「1 個片段」· 時鐘 icon + 「10 秒」
        ├── 分隔線
        └── Jun 9
              片段數 icon + 「1 個片段」· 時鐘 icon + 「10 秒」
```

#### 日期模式（DayDetailView）
```
方格縮圖，預設 3 欄
縮圖比例：9:16（填滿方格，letterbox）
最愛標記：黃色圓點右上角
長按：進入多選模式
Pinch：調整欄數（2–5 欄）
左滑縮圖：顯示刪除按鈕
```

---

### 4. 播放器 (PlayerScreen)

#### 結構（由上到下）
```
全螢幕黑色背景
├── 影片播放區（上半，scaledToFill）
│     左上：X 關閉按鈕（圓形灰色）
│     右上：分享 icon（圓形灰色）
│     右側中間：折疊箭頭（<）
├── 分隔拖曳條（可上下調整影片/控制列比例）
└── 控制列（下半，深色 #0A0F1A）
      ├── 縮圖 Scrubber（30 格，可拖曳）
      │     拖曳時顯示時間氣泡
      ├── 關節名稱標題（點擊某關節後顯示）
      ├── 統計列：最小值 | 平均值（大字）| 最大值
      └── 折線圖（高度約 120dp）
            x 軸：時間（秒）
            y 軸：角度（度）
            線條：#CC44FF 紫色
            目前位置：白色虛線
```

---

### 5. 骨架分析結果 (PoseResultScreen)
**截圖參考：** IMG_5733.PNG / IMG_5734.PNG

#### 畫面 A — 關節角度總覽（IMG_5733）
```
上半：影片畫面 + 骨架疊圖
  骨架：白色圓點 keypoint + 白色連線
  背景：原始影片畫面

下半：2 欄方格，每格一個關節
  ├── 左肘：138°（綠色）
  ├── 右肘：101°（橘色）
  ├── 左膝：155°（綠色）
  ├── 右膝：153°（綠色）
  ├── 左髖：164°（藍色）
  ├── 右髖：161°（藍色）
  ├── 左肩：16°（紅色）
  └── 右肩：29°（橘色）

每個關節格子：
  背景：#0D1520（深藍黑）
  label：13sp，opacity 0.7
  角度數值：32sp Bold，顏色依範圍判斷
  下方：折線小圖示（可點擊展開）
```

#### 角度顏色判斷邏輯
```
綠色 #4CAF50：正常範圍
橘色 #FF9800：邊界範圍
紅色 #F44336：異常範圍
藍色 #2196F3：特定關節（髖部）
```

#### 畫面 B — 單一關節折線圖（IMG_5734）
```
上半：影片畫面（縮小）+ 骨架疊圖

下半（深色面板，可上拉展開）：
  ├── 關節名稱標題（「右肘」）
  ├── 統計列：
  │     最小 61°（紅色）| 平均 99°（白色大字）| 最大 101°（藍色）
  │     下方：「101°」標注
  └── 折線圖
        線條：#CC44FF 紫色
        背景：#0A0F1A
        x 軸：0.0s, 2.0s, 4.0s, 6.0s
        y 軸：0°, 100°, 200°, 300°
        目前時間：白色虛線垂直標記
```

---

### 6. 設定 (SettingsScreen)
**截圖參考：** IMG_5731.PNG

#### 結構
```
NavigationBar：標題「設定」，返回按鈕

Section 1（無標題卡片）：
  ├── 預設延遲
  │     右側顯示「5 秒」（藍色 #4DA8FF）
  │     下方：Slider（1–30s）
  │     說明文字：「開啟拍攝時預設的延遲秒數」
  ├── 錄影幀率
  │     Segmented：[30 fps] [60 fps] [120 fps]
  │     說明文字：「標準幀率，最多可儲存 35 秒」
  └── 預設鏡頭
        Segmented：[後鏡頭] [前鏡頭]
        說明文字：「開啟拍攝時預設使用的鏡頭」

Section 2（無標題）：
  ├── 片段數量：右側「2 個」
  ├── 佔用空間：右側「11.8 MB」
  └── 清除所有片段（紅色文字 + 垃圾桶 icon）

Section 3（無標題）：
  ├── 版本：右側「2.0」
  └── 建置：右側「1」
```

#### 設定卡片樣式
```
背景：rgba(255,255,255,0.08)
邊框：rgba(255,255,255,0.10)
圓角：16dp
列分隔線：rgba(255,255,255,0.08)
Segmented Control 選中：白色背景，黑色文字
Segmented Control 未選中：透明背景，白色文字
```

---

## 共用元件規格

### TISS Logo Bar
```
背景：rgba(255,255,255,0.94)
圓角：16dp
高度：44dp
內容：tiss_logo 圖片（scaledToFit）
```

### 骨架載入畫面
```
背景：純黑 #000000
中間：骨架人形動畫（紫色 #8844CC）
文字：「分析中...」白色 20sp Bold
進度條：紫色 #CC44FF，灰色背景
進度百分比：灰色 13sp
關閉按鈕：左上角，圓形灰色背景，X icon
```

### 返回 / 關閉按鈕
```
尺寸：48x48dp（相機返回） / 44x44dp（其他）
形狀：圓形
背景：ultraThinMaterial（毛玻璃）或 rgba(255,255,255,0.15)
icon：chevron.left（返回）/ xmark（關閉）
icon 大小：19sp SemiBold
```

---

## 截圖對照表

| 截圖檔案 | 畫面 |
|---------|------|
| IMG_5728.PNG | 相機拍攝畫面（含控制列） |
| IMG_5729.PNG | 骨架分析 → 片段庫（資料夾模式） |
| IMG_5730.PNG | 日期記錄（資料夾模式） |
| IMG_5731.PNG | 設定頁 |
| IMG_5732.PNG | 首頁 |
| IMG_5733.PNG | 骨架分析結果（關節角度總覽） |
| IMG_5734.PNG | 骨架分析結果（單一關節折線圖） |
