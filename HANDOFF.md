# 交接文件：Wi-Fi 優先順序 App

> 給下一次接手的 Claude（或任何開發者）。先讀這份，再動程式碼。
> 最後更新：2026-11-19（v1.2：中英文切換）

## 1. 這是什麼
使用者要一個「可以控制手機優先連哪個 Wi-Fi」的 App。
做法：Android 原生 App，使用者自己排 SSID 順序，前景服務在背景定期掃描，
自動切到「附近收得到、訊號 ≥ 門檻、順位最高」的網路。
- 平台：**只有 Android**（iOS 不開放 App 控制 Wi-Fi 切換，已跟使用者說明）
- 語言：Java，**不使用 AndroidX / 任何第三方套件**（只用 Android framework，建置快、體積小）
- UI：全部用程式碼產生（沒有 layout XML），**繁體中文／英文雙語**（見第 10 節）、大字體

## 2. 目前狀態
- 版本：**v1.2（versionCode 3）**
  - v1.0（2026-11-12）：初版
  - v1.1（2026-11-19）：新增「贊助作者」頁面（`SponsorActivity`，連到 Ko-fi https://ko-fi.com/sagiriben），主畫面最下方有入口
  - v1.2（2026-11-19）：中英文切換（設定 → 語言 Language：跟隨系統／中文／English）；App 名稱依系統語言；英文說明 README.en.md
- ✅ 已編譯成功、簽章驗證通過（debug 簽章，v2 scheme）
- ✅ lint 無 Error（只剩 SetTextI18n、BatteryLife 等無害 Warning）
- ⚠ **尚未在實機測試過**。
  下次接手第一件事：問使用者在哪支手機／Android 版本上的實際表現、「切換紀錄」裡有沒有成功切換。
- 公開於 GitHub：https://github.com/pajh103082/android-wifi-priority-switcher（APK 放在 repo 的 `release/WiFi-Priority.apk`）

## 3. 最關鍵的設計決定（不要隨便改）
### targetSdk 必須維持 28
- Android 10（API 29）起，`WifiManager.enableNetwork / addNetwork / getConfiguredNetworks`
  對 targetSdk ≥ 29 的 App **一律失敗或回傳空清單**。
- targetSdk = 28 時這些 API 在新版 Android 上仍可用 → 這是整個 App 能運作的根本。
- 代價：無法上架 Play 商店；安裝時會跳「為舊版 Android 打造」警告（屬正常）。
- Android 15 最低可安裝 targetSdk 為 24，所以 28 還能裝。若未來 Android 把門檻拉到 > 28，這個做法就失效，
  屆時只能改用 `WifiNetworkSuggestion`（無法強制切換，只能建議）或 root / Shizuku 方案。

### 為什麼能切到「使用者在系統設定存的網路」
- AOSP 的 `WifiConfigManager.enableNetwork()` 不檢查建立者（註解：Apps are allowed to connect to networks），
  所以只要拿得到 networkId（`getConfiguredNetworks` + SSID 比對）就能切。
- 但 **不能修改**別人建立的網路設定 → 系統已存在同 SSID 時，`addNetwork` 會回傳 -1。
  所以 UI 流程是：系統已有 → 直接加入清單；系統沒有 → 才請使用者輸入密碼並 `addNetwork`。

### minSdk 26
- Android 6/7 的 `enableNetwork(id, disableOthers=true)` 會真的停用其他網路，造成副作用，故不支援。
- Android 8+ 的 disableOthers 只是標記「使用者最後選擇」。

### 前景服務類型 location
- 掃描結果需要位置權限；用 `foregroundServiceType="location"` 讓服務在背景仍可讀取。
- 前景服務算「前景」，掃描節流為 2 分鐘 4 次，所以設計成約 60 秒掃一次。

## 4. 檔案結構
```
WifiPriority/
├─ settings.gradle / build.gradle / gradle.properties
├─ gradlew, gradle/wrapper/           Gradle 8.14.3 wrapper
├─ README.md                          中文說明（GitHub 首頁）
├─ README.en.md                       英文說明（內容要跟 README.md 同步）
├─ .github/FUNDING.yml                GitHub 的 Sponsor 按鈕（Ko-fi）
├─ .gitignore
├─ HANDOFF.md                         本文件
└─ app/
   ├─ build.gradle                    AGP 8.7.3，compileSdk 34，minSdk 26，targetSdk 28
   └─ src/main/
      ├─ AndroidManifest.xml          權限、服務、開機接收器
      ├─ res/values/strings.xml       App 名稱（英文，預設）
      ├─ res/values-zh/strings.xml    App 名稱（中文，手機語言為中文時）
      ├─ res/drawable/                向量圖示（ic_fg/ic_bg/ic_notif）
      └─ java/com/claude/wifipriority/
         ├─ MainActivity.java         UI：狀態、清單排序、新增網路、設定、紀錄、贊助入口
         ├─ SponsorActivity.java      贊助頁面（Ko-fi 按鈕，網址常數 KOFI_URL）
         ├─ WifiHelper.java           ★ 核心：掃描、找 netId、addNetwork、evaluate() 判斷是否切換
         ├─ WifiPriorityService.java  前景服務：每 20 秒 evaluate、每 60 秒 startScan、監聽 Wi-Fi 廣播
         ├─ L.java                    雙語工具 L.t(ctx, 中文, English)
         ├─ Prefs.java                SharedPreferences：順序(JSON)、開關、門檻、語言、失敗紀錄、log(最多40行)
         └─ BootReceiver.java         開機／App 更新後，若開關為開則自動啟動服務
```

## 5. 切換邏輯（WifiHelper.evaluate）
1. 檢查 Wi-Fi 開啟、精確位置權限、定位開啟、清單非空。
2. 從清單由上往下找第一個：掃描得到 && 訊號 ≥ 門檻 && 5 分鐘內沒失敗過 → `best`
3. 是否切換：
   - 目前未連線 → 切
   - 目前網路不在清單 → 依設定「from_unlisted」（預設切）
   - best 順位較高 → 切
   - 目前網路順位較高但訊號 < 門檻 − 5 dB（遲滯）→ 切
4. 切換後 45 秒冷卻；40 秒後還沒連上目標就標記失敗、5 分鐘內跳過。
5. 實際切換：`enableNetwork(netId, true)` + `reconnect()`，並寫入 log。

可調常數：`WifiHelper` 的 `COOLDOWN_MS / FAIL_CHECK_MS / HYSTERESIS_DB`；
`WifiPriorityService` 的 `TICK_MS / SCAN_EVERY`；門檻預設 -75（Prefs，UI 可調 -90 ~ -50）。

## 6. 建置環境（雲端工作區重建步驟）
工作區不會保留，下次要重新裝：
```bash
# Android SDK（dl.google.com 可連）
mkdir -p /opt/android && cd /opt/android
curl -sSL -o cmd.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmd.zip && mkdir -p sdk/cmdline-tools && mv cmdline-tools sdk/cmdline-tools/latest
export ANDROID_HOME=/opt/android/sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 編譯（環境內建 JDK 21 與 /opt/gradle 8.14.3）
cd WifiPriority && echo "sdk.dir=/opt/android/sdk" > local.properties
gradle assembleDebug --no-daemon     # 或 ./gradlew assembleDebug
# 輸出 app/build/outputs/apk/debug/app-debug.apk
```
- 注意：apksigner 對中文檔名會失敗，驗證時用英文路徑（`LC_ALL=C.UTF-8` 也無效）。
- **簽章金鑰：** v1.0、v1.1 都用同一把 debug 金鑰簽（SHA-256 憑證指紋 `868c1771…d1d5be`）。
  雲端工作區重置後 `~/.android/debug.keystore` 會不見，新產生的金鑰簽出來的 APK
  **無法覆蓋安裝**舊版。金鑰備份在使用者電腦專案資料夾的 `簽章金鑰_勿上傳/debug.keystore`，
  下次編譯前先用 device_stage_files 拿回來放到 `~/.android/debug.keystore`。
  （debug 金鑰密碼都是 android、alias 是 androiddebugkey。）**金鑰絕對不能上傳 GitHub。**

## 7. 已知限制 / 風險
- 未經實機驗證；部分廠牌（小米、OPPO、vivo、華為等）可能額外限制背景或 Wi-Fi 操作。
- 深度休眠（Doze）時 Handler 計時器會暫停，主要靠 Wi-Fi 廣播喚醒；關螢幕久了切換可能延遲。
- 位置權限需「一律允許」才保證背景可讀掃描結果（Android 11+ 只能到設定頁手動改）。
- 手動新增只支援 開放 / WPA-PSK；WPA3、企業網路請使用者先在系統設定連一次再加入清單。
- 使用者選「大概位置」時看不到掃描結果。

## 8. 待辦 / 可能的下一步
- [ ] 取得使用者實機測試回報（手機型號、Android 版本、是否成功切換）
- [x] 固定簽章金鑰：目前沿用 debug 金鑰並備份在使用者電腦（見第 6 節）
- [ ] 視需求：清單拖曳排序、依時段／地點的不同優先清單、桌面小工具「一鍵切換」
- [ ] 視需求：Shizuku 方案（不受 targetSdk 限制）

## 9. GitHub 發佈
- Repo：https://github.com/pajh103082/android-wifi-priority-switcher（公開）
- commit 作者一律用 `pajh103082 <pajh103082@users.noreply.github.com>`，**不要用使用者的真實 email**
- APK 放在 repo 的 `release/WiFi-Priority.apk`（固定檔名，每版覆蓋；README 的下載連結指向它）。
  雲端工作區的 git proxy **不允許**用 API 建 GitHub Release 或改 topics，所以改用這個方式；topics 要在網頁上設。
- **會上傳：** 原始碼、`release/WiFi-Priority.apk`、Gradle wrapper、README.md、README.en.md、HANDOFF.md（公開版）、.github/FUNDING.yml、.gitignore
- **絕對不上傳：** `local.properties`、`build/`、`.gradle/`、任何 `.keystore/.jks`、`簽章金鑰_勿上傳/`、
  作者本機的私人交接內容（公開版 HANDOFF.md 已移除）
- 上傳前檢查：`git grep -niE "@gmail|Users\\|C:\\|keystore|password|<本名或暱稱>" $(git rev-list --all)`，
  確認只剩程式邏輯中正常出現的字（例如 WifiConfiguration 的密碼欄位）
- 更新流程：改版本號 → 編譯 → 複製 APK 到 release/ → **README.md 與 README.en.md 兩份的版本字樣都要改** → 掃描 → commit/push → 驗證下載連結 → 更新電腦上的資料夾

## 10. 多語系（中文／英文）維護方式
- **做法：** 沒有用 Android 的 strings.xml 翻譯（因為 UI 全是程式碼產生，且要能在 App 內切換語言，
  不受系統語言限制），而是用 `L.t(context, "中文", "English")`，在呼叫當下依設定回傳其中一種。
  - `MainActivity` 內有捷徑 `t("中文", "English")`
  - `WifiHelper.signalWord(ctx, rssi)`、`describe(ctx, …)`、`evaluate()` 的狀態文字都已雙語
  - 通知文字（`WifiPriorityService`）、贊助頁（`SponsorActivity`）都已雙語
- **語言設定：** `Prefs.getLang/setLang`，值為 `auto`／`zh`／`en`，預設 `auto`。
  `auto` 的規則：手機語言是中文（`Locale.getDefault().getLanguage()` 為 `zh`，含繁簡）→ 中文；其他所有語言 → 英文。
- **切換 UI：** 設定卡片最下方「語言 Language」三個選項；選了就 `recreate()` 重畫畫面。
  標題「語言 Language」刻意固定雙語，避免使用者切到看不懂的語言後找不到改回來的地方。
- **App 名稱（桌面圖示下的字）：** 只能跟隨系統語言 → `values/strings.xml`（Wi-Fi Priority）與 `values-zh/strings.xml`（Wi-Fi 優先順序）。
  畫面標題列則在 `onCreate` 用 `setTitle(t(...))`，會跟著 App 內語言設定。
- **通知列文字：** 服務每次檢查都會重新產生文字，切換語言後下一次檢查（最多 20 秒）就會換成新語言。
- **切換紀錄（log）：** 每一行是寫入當下的語言，切換語言後舊紀錄不會被翻譯（屬正常）。
- **新增或修改任何顯示文字時的規則：**
  1. 一律寫成 `t("中文", "English")` / `L.t(c, "中文", "English")`，不要再寫單一語言的字串。
  2. 檢查有沒有漏翻：`grep -n '"[^"]*[一-龥]' app/src/main/java/com/claude/wifipriority/*.java | grep -v 't("'`，
     只應剩下多行 `L.t(...)` 中間的中文行。
  3. 功能或說明有變，`README.md` 和 `README.en.md` 兩份都要更新。
- **想加第三種語言時：** 把 `L.t` 改成依語言代碼查表，或改用 strings.xml + 自訂 Context locale；
  目前只有兩種語言，用 `t(zh, en)` 最簡單。
