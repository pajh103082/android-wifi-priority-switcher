# Wi-Fi 優先順序（Android）

[English](README.en.md) | **繁體中文**

自己排 Wi-Fi 的優先順序，App 會在背景自動切到「附近收得到、訊號夠強、順位最高」的那個。

*Android app that lets you rank your Wi-Fi networks and automatically switches to the highest-ranked one in range. English & Traditional Chinese UI — [read in English](README.en.md).*

[![Ko-fi 贊助](https://img.shields.io/badge/Ko--fi-請作者喝咖啡-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/sagiriben)

## 下載
👉 **[直接下載 APK（v1.3）](https://github.com/pajh103082/android-wifi-priority-switcher/raw/main/release/WiFi-Priority.apk)**

（檔案在 repo 的 [`release/`](release/) 資料夾，永遠是最新版）

| 項目 | 說明 |
|---|---|
| 支援系統 | Android 8.0 以上（iPhone 不開放 App 控制 Wi-Fi，無法支援） |
| 語言 | 繁體中文／English，預設跟隨手機語言，可在「設定」切換 |
| 費用 | 免費、無廣告 |
| 隱私 | 不連網、不收集資料；設定只存在你的手機裡 |
| 上架 | 沒有上架 Play 商店（原因見下方「為什麼 targetSdk 是 28」） |

## 安裝
1. 把 APK 傳到手機，點開安裝（需允許「安裝不明來源的應用程式」）。
2. 若出現「此應用程式是為舊版 Android 打造」，選「仍要安裝」——這是刻意的。
3. 若被 Play 安全防護攔下，選「更多詳細資料」→「仍要安裝」。

## 第一次使用
1. 打開 App，允許「位置」權限並選「精確位置」（Android 規定要有它才能看附近 Wi-Fi）。手機的「定位」也要開著。
2. 按「＋ 從附近／已儲存加入」把網路加進清單；手機裡沒存過的網路會請你輸入密碼。
3. 用 ▲ ▼ 排順序，最上面最優先。
4. 按畫面最上方的大按鈕啟用（綠色＝開啟中、灰色＝已停用，再按一下就停用）。
   也可以在「設定」→「檢查頻率」選多久檢查一次（10 秒～30 分鐘，預設 30 秒）。
5. 建議：位置權限改成「一律允許」，並按「讓系統不要限制本 App」，關螢幕後才會持續運作。

切換語言：「設定」→「語言 Language」→ 跟隨系統／中文／English。

## 切換規則
- 依你設定的「檢查頻率」檢查一次（預設每 30 秒，可選 10 秒～30 分鐘）。畫面會顯示上次檢查時間和下次檢查倒數。
- Android 限制主動掃描最多約 30 秒一次，頻率設得比 30 秒短時，中間幾次會沿用最近一次的掃描結果。
- Wi-Fi 斷線或剛打開時，會提前檢查一次，不用等到下一輪。
- 只有目標網路訊號強於「切換門檻」（預設 -75 dBm，可調）才會切。
- 目前網路順位較高時不會被換掉，除非它的訊號掉到門檻以下 5 dBm。
- 切換後 45 秒內不再切換；連不上的網路 5 分鐘內先跳過。
- 每次切換都會記在 App 裡的「切換紀錄」。

## 停用
- App 最上方的大按鈕再按一下，或通知列上的「停用自動切換」按鈕。
- 停用後背景服務完全停止、通知消失，開機也不會自動啟動，直到你再啟用。

## 權限用途
| 權限 | 用途 |
|---|---|
| 位置（精確／一律允許） | Android 規定要有位置權限才能讀取附近 Wi-Fi 名稱；App 不會讀取或記錄你的 GPS 位置 |
| Wi-Fi 狀態／變更 Wi-Fi | 讀取目前連線、切換網路、新增網路設定 |
| 前景服務、通知 | 在背景持續檢查，通知列顯示目前狀態 |
| 開機啟動 | 開機後自動恢復自動切換（若你有開啟） |
| 不受電池最佳化限制 | 選用，避免背景被系統關掉 |

## 已知限制
- 部分廠牌（小米、OPPO、vivo 等）可能額外限制背景執行，需要到系統設定允許「自動啟動」或關閉省電限制。
- 手機深度休眠時檢查會變慢，醒來後會補上。
- 手動新增只支援無密碼與 WPA/WPA2 個人版；WPA3 或企業網路請先在系統 Wi-Fi 設定連過一次，再回 App 加入清單。

## 為什麼 targetSdk 是 28
Android 10 起，Google 不讓新版 App 指定手機連到哪個已儲存的 Wi-Fi（`WifiManager.enableNetwork`）。
只有宣告 targetSdk ≤ 28 的 App 仍可使用，所以本 App 刻意這樣設定，也因此無法上架 Play 商店，只能自行安裝。

## 自行編譯
需要 JDK 17+ 與 Android SDK（platform 34、build-tools 34）：
```
./gradlew assembleDebug
```
輸出：`app/build/outputs/apk/debug/app-debug.apk`

開發細節（架構、設計決定、切換邏輯、翻譯方式）請看 [HANDOFF.md](HANDOFF.md)。

## 贊助
這個 App 完全免費。如果它幫上你的忙，歡迎到 **[Ko-fi](https://ko-fi.com/sagiriben)** 請作者喝杯咖啡 ☕
App 裡也有「贊助作者」頁面（主畫面最下方）。
