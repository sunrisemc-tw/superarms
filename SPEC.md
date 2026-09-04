# SuperArms 特武系統 — 設計規格 v0.1（討論中）

> 2026-09-04 與烤鴨於 Discord #人工智障/特武系統設計討論 逐步收斂。未決項見 §Open Questions。
> 平台：Canvas（Folia fork）/ MC 1.21.11 / Java 21 / folia-api `1.21.11-R0.1-SNAPSHOT`（沿 slab-breaker scaffold，`folia-supported: true`）

---

## 1. 已確認決策總表

| # | 決策 | 結論 |
|---|---|---|
| D1 | 武器可重複賣 | weapons.yml = **模板定義**，賣出複製實例。無限購買，價格高由服主自訂 |
| D2 | 附魔時限語意 | **時限長度**（duration），從「購買當下」起算。例：7d / 24h / 0=永久 |
| D3 | Last Buy time 語意 | = **最後可購買時間（販售截止）**，絕對時間 yyyy/MM/dd HH:mm；到點自動下架；管理員可改回重開 |
| D4 | 到期處理 | 單點 rewrite：拔定義附魔 + Lore 標「已失效」。**無續期**、不保留附魔 snapshot（rewrite 查定義即可） |
| D5 | 外觀 | **到期不改外觀**（不改 material、不用 RP/CMD 切換）；靠「無 glint + Lore 已失效標記」呈現 |
| D6 | 到期偵測 | **3 層閉環**：① region scheduler callback 精準排程 ② join 補查 ③ 使用時 lazy 檢查（堵箱子/潛影盒漏洞） |
| D7 | Threading | rewrite 一律走 entity thread（`player.getScheduler()`）；callback 用 region scheduler |
| D8 | 管理 UI | **MC 原生 Dialog（1.21.6+）**：按鈕＋文字輸入框＋item display，全 wizard 流程 |
| D9 | 玩家購買 UI | **Chest GUI**（顯示武器實體、點擊購買；Geyser/基岩也統一 chest GUI，不另做 fallback） |
| D10 | zMenu 整合 | 烤鴨在 zMenu /store 放按鈕執行 `/superarms shop` → superarms 自帶 GUI。softdepend，無 zMenu 也獨立可跑 |
| D11 | 輸入格式 | 名稱/Lore 支援 legacy `&` 與 MiniMessage。內部統一存 **MM 字串**，輸入時轉換 |
| D12 | 儲存 | **weapons.yml**（模板定義）；購買實例資料存 **PDC**（在物品上） |
| D13 | 經濟 | per-weapon 幣種：Vault / PlayerPoints（provider 抽象層，見 §12；「日幣」對應哪個 provider 未決） |
| D14 | 物品保護 | MVP 含：擋 grindstone 拆附魔、anvil 合併/改名、附魔台加附魔 |
| D15 | 配件 | config.yml、messages.yml、`/superarms list`、權限 superarms.admin/buy、滿包不扣款、購買 log、購買廣播（可關） |

---

## 2. 名詞定義

- **模板（WeaponDef）**：weapons.yml 中一把武器的定義。有自己 UUID（管理識別、Copy UUID 用）。
- **實例（WeaponInstance）**：購買後複製到玩家手上的物品。PDC 記 `definition-uuid + expiresAt`。
- **販售截止（sellUntil）**：模板的絕對時間，過期自動從購買 GUI 消失（不刪檔，可改回）。
- **附魔到期（expiresAt）**：單一實例的絕對時間 = 購買時刻 + duration。

---

## 3. 指令樹

```
/superarms                    → 無權限提示 / 管理首頁（admin）
/superarms shop               → 玩家購買 Chest GUI（列出仍在販售截止內的模板）
/superarms buy <uuid>         → 直接購買（指定 uuid；GUI 外快速通道）
/superarms list               → 純文字列出所有模板（uuid、名稱、價格、販售截止）→ 方便貼 Discord 叫賣
/superarms reload             → 重載 config/messages/weapons.yml（admin）
/superarms arms <uuid>        → 管理員取回模板本體（放進背包，檢查外觀用）
/superarms <uuid>             → 管理首頁直接跳到該武器 Manage（admin）
```

權限：
- `superarms.admin` — 管理頁/建立/編輯/刪除/reload/arms
- `superarms.buy` — 玩家購買（預設 true？走 config `permission-required` 決定）
- `superarms.bypass.limit`（若做每日限購）

---

## 4. weapons.yml schema

```yaml
weapons:
  # key = 模板 UUID（管理用；`/superarms <uuid>` 與 Copy UUID 都指它）
  3f2a9c1e-8b4d-4e7f-9a2b-1c3d5e7f9a0b:
    # 建立時由管理員取名 → 名稱存這裡（MM 格式）
    name: "<gradient:#FFD700:#FF8C00>烈日之刃</gradient>"
    material: NETHERITE_SWORD
    custom-model-data: null          # 選用：RP 貼圖（本次不改外觀用，但保留欄位）
    unbreakable: true                # 預設 true？管理員可改
    # Lore 每行一筆，MM 格式（含「已失效」標記的生成規則見 §7）
    lore:
      - "<gray>傳說中的太陽鍛造之劍"
      - ""
      - "<green>購買後 7 天內附魔有效"
    # 定義附魔：賣出時套用，到期時全部拔除（查此清單 rewrite）
    enchantments:
      SHARPNESS: 10
      FIRE_ASPECT: 3
      UNBREAKING: 5
    # 附魔時限（D2）：長度字串；0 / absent = 永久
    timeout: "7d"
    # 販售截止（D3）：絕對時間；absent / 過去時間 = 下架（管理員可改回重開）
    sell-until: "2026-12-31 23:59"
    # 經濟（D13）：currency = VAULT | PLAYER_POINTS（provider 對應 config）
    price:
      currency: VAULT
      amount: 10000.0
    # 進階旗標
    flags:
      glow: true          # 購買實例加隱藏附魔 glint（到期拔除自然消失）
      announce: true      # 購買廣播（可被 config 全域關閉）
      protection: true    # 套用物品保護（§8）
    # 管理用註記
    created: "2026-09-04 14:00"
    updated: "2026-09-04 14:00"
```

- 實作提醒：`timeout` 解析支援 `Nd / Nh / Nm / Ns / 0 / absent`；`sell-until` 存 epoch millis 於記憶體，YAML 存 readable 字串供人讀。
- PDC key namespace：`superarms`（plugin 註冊 keyspace）。

---

## 5. 購買實例 PDC schema（物品上）

```
superarms:def        = STRING   模板 UUID（weapon 定義）
superarms:expiresAt  = LONG     附魔到期 epoch millis（0 = 永久）
superarms:owner      = STRING   購買者 UUID（log / 未來查詢）
superarms:boughtAt   = LONG     購買時刻
```

實例物品：material 照模板、name/lore 照模板 render、附魔照定義套、`glow=true` 時加隱藏附魔（如 `VANISHING_CURSE`？——會被保護擋掉，見 §8 選擇）。

**render 規則**（Lore 生成）：
- 永久（timeout=0）：lore 照模板原樣。
- 有時限：模板 lore 尾附加一行（管理員建檔時不需手打這行）：
  - 有效：`<green>附魔有效至 <time:yyyy/MM/dd HH:mm>`
  - 已失效：`<red>附魔已失效`（同 §7 標記）

---

## 6. 到期處理閉環（D6/D7）

3 層檢查，全部命中才 rewrite，冪等：

1. **Callback（精準）**：購買當下把 `(player, itemSlot, expiresAt)` 丟 region scheduler 排程到 expiresAt。到點 → 進 entity thread（`player.getScheduler().run`）→ 若玩家仍持有該實例 → rewrite + 訊息通知。
2. **Join 補查**：PlayerJoin → 掃背包/裝備欄 PDC `expiresAt` 過期者 → rewrite（補離線期間到期、或 callback 因離線沒跑的）。
3. **Lazy（堵漏洞）**：使用時檢查——攻擊實體（EntityDamageByEntity 出劍）、破壞方塊、右鍵互動（含弓/盾？特武主要是劍鎬斧）、開箱取出後？→ 對手上 item 做一次 PDC 檢查，過期當場 rewrite + 通知。成本 = 每次一發 PDC read。

**rewrite 動作**：clone item → 拔定義附魔（依 def.enchantments，非全拔——保留玩家自己後來加的？見 Open Q3）→ 去 glint → Lore 尾巴換成「已失效」行 → setItem。不改變 material/name。

**Threading 規則**（Folia）：
- 全部 item 讀寫發生在該 player/entity 的 scheduler 內。
- 不排 global task；region scheduler 只拿來做「到點叫醒」。
- join 掃描在 player join 的 entity context。

---

## 7. GUI 規格

### 7.1 管理面：原生 Dialog wizard（D8）

流程樹（每步一張 dialog，callback 回 superarms 再送下一張；中途關閉 = wizard 中斷無殘留）：

```
/superarms
└─ [Home] 標題列武器數量
   ├─ Add new ──▶ [輸入框] 武器名稱 ──▶ 建檔（預設 material=DIAMOND_SWORD, timeout=0,
   │                                        price=Vault 0, 進 Manage）
   ├─ Manage ──▶ [武器選擇頁：列出全部模板，item display 預覽] ──▶ [Manage 單一武器]
   │                Manage:
   │                ├─ Rename ──▶ [輸入框] 新名稱（MM/& 皆可）
   │                ├─ Lore
   │                │   ├─ Add line ──▶ [輸入框] 一行 lore
   │                │   ├─ Remove line ──▶ [選擇列] 點掉哪一行
   │                │   └─ (Clear all?)
   │                ├─ Add Enchantment ──▶ [選擇附魔（registry，可過濾該 material 可附）] ──▶ [輸入框] level
   │                ├─ Remove Enchantment ──▶ [選擇現有附魔]
   │                ├─ Set Price ──▶ [按鈕] 幣種 Vault/PlayerPoints toggle ──▶ [輸入框] 金額
   │                ├─ Set Last Buy Time ──▶ [輸入框] yyyy/MM/dd HH:mm（空=不限；過去=立即下架）
   │                ├─ Set Arms Timeout ──▶ [輸入框] 長度字串（7d / 24h / 0=永久）
   │                ├─ Set Material / Unbreakable / Glow (dialog toggle)
   │                ├─ Copy UUID ──▶ 複製到剪貼簿（玩家 clipboard 發 `minecraft:copy`？→ 實作時驗證；否則顯示在 chat 供複製）
   │                └─ Remove ──▶ [確認 dialog 按鈕]
   └─ Reload / List（文字）
```

- 名稱/Lore 輸入框即原生 text field（支援 IME 中文輸入）。
- Dialog body 用 item display 預覽目前武器外觀。

### 7.2 玩家面：Chest GUI（D9）

```
/superarms shop（或 zMenu /store 按鈕觸發）
└─ [Shop] 3x9 或 6x9（config）
   ├─ 每格一把武器：實體 item 顯示（glint、真外觀）、lore 含價錢與時限
   ├─ 只列 sell-until 未過的模板（自動下架）
   ├─ 分頁（武器多）
   └─ 點擊 ──▶ [確認 GUI]（顯示武器 + 價格 + 「確認購買/取消」按鈕）
         ──▶ 扣款成功：滿包→不扣款+訊息（D15）；成功→給 item + log + 廣播（flags.announce & config）
```

- 買到的就是已 render 的實例（含 expiresAt PDC、時限 lore）。

---

## 8. 物品保護（D14）

目標：特武是「帶附魔的普通 item」，防止玩家拆掉附魔價值 / 複製。
事件攔截（都先查 PDC `def` 是否存在）：
- `PrepareGrindstoneEvent`：特武放磨石 → 取消（不給拆附魔/退材料）。
- `PrepareAnvilEvent`：特武當材料被合併、或被改名/加書 → 取消該操作（結果設 null 或原樣）。
- `PrepareAnvilEvent`（斧頭改名例外？見 Open Q4）
- `EnchantItemEvent`：附魔台對特武 → 取消。
- （後補）鐵砧組合書（enchanted book 合成到特武）。
- 預設啟用（flags.protection 可關）。只擋「改變/拆解」；使用、丟棄、交易展示不受限。

---

## 9. zMenu 整合（D10）

烤鴨在 /store 的 zMenu YAML 放一顆按鈕：
```yaml
# store.yml（示意，實際語法以 zMenu docs 為準）
button:
  material: NETHERITE_SWORD
  slot: 22
  name: "⚔ 特武商城"
  actions:
    - "[command] superarms shop"   # 或 left_click 等
```
→ superarms 開自己的購買 Chest GUI。superarms 完全不讀 zMenu store 配置（雙向解耦，softdepend 只留：偵測 zMenu 存在與否以決定 GUI 開啟方式不用，因為玩家面統一 chest GUI）。

---

## 10. config.yml / messages.yml

```yaml
# config.yml
prefix: "<gradient:#FFD700:#FF8C00>[特武]</gradient> "
gui:
  shop-title: "⚔ 特武商城"
  shop-rows: 6
  confirm-title: "確認購買"
  previous-page-item: ...
  next-page-item: ...
economy:
  vault: true
  player-points: true
  # 未決：哪個 provider = 「日幣」→ §12
buy:
  permission-required: false
  give-on-full-inventory: false   # false = 滿包不扣款並提示（D15）
  sound-success: ENTITY_PLAYER_LEVELUP
  sound-fail: BLOCK_NOTE_BLOCK_BASS
  broadcast:
    enabled: true
    format: "<yellow>%player% 購買了 %weapon%！"
protection:
  grindstone: true
  anvil: true
  enchant-table: true
```

```yaml
# messages.yml（全 MiniMessage）
no-permission: "<red>你沒有權限"
not-found: "<red>找不到該武器"
buy-success: "<green>購買成功！已放入背包"
buy-full-inventory: "<red>背包已滿，購買取消"
expired-notify: "<yellow>你持有的 %weapon% 附魔已失效"
weapon-expired-sell: "<gray>該武器已停止販售"
```

---

## 11. 權限總表

| 權限 | 範圍 |
|---|---|
| `superarms.admin` | 管理全功能（GUI、建立、編輯、刪除、reload、arms） |
| `superarms.buy` | 玩家購買（若 permission-required） |
| `superarms.bypass.limit` | （保留）每日限購 bypass |

---

## 12. 經濟抽象層

介面 `CurrencyProvider`：`has(player, amount)` / `withdraw(player, amount)` / `deposit(player, amount)` / `format(amount)`。
- 實作 `VaultCurrency`（Vault 經濟）、`PlayerPointsCurrency`。
- weapons.yml 的 `price.currency` 指定用哪個。
- **「日幣」= 哪個 provider？未決（Open Q1）**。若是 Vault 裡叫「日幣」的貨幣，靠 Vault 直接通；若是自訂（CoinsEngine 等）→ 加 provider 實作。

---

## 13. Open Questions

- **Q1（經濟）**：/store 日幣商城 = Vault？PlayerPoints？還是 CoinsEngine 類自訂貨幣？決定 provider 實作範圍。
- **Q2（glint）**：glow=true 用哪種隱藏附魔當 glint 來源？VANISHING_CURSE 會被保護擋、MENDING 可能誤導。乾淨做法：發送隱藏附魔需要 NMS 或 1.20.5+ 無法純 API → 改用 item flags + 真的放一個低調附魔？需 spike 驗證純 Bukkit 可達性。折衷：glint 用 ENCHANTED 顯示但 lore 說明「外觀特效」。spike 決定。
- **Q3（rewrite 範圍）**：到期 rewrite 只拔「定義附魔」還是全拔（連玩家自己加的）？建議只拔定義的（玩家加的保留）→ 但這樣「失效」不完全……烤鴨拍板。
- **Q4（改名保護）**：Anvil 對特武「改名」要擋嗎？特武名稱是賣點，但改名是玩家自由……預設擋，可 flag 關。
- **Q5（Copy UUID）**：Clipboard 用 client 指令（`/trigger`? 原生 copy 按鈕 1.21.6+ dialog 內建 copy？）驗證 API 再定。fallback：chat 顯示 UUID。
- **Q6**：`/superarms list` 輸出格式（Discord 叫賣用：一行一把含價格？）。
- **Q7**：reload 後進行中 GUI / 已購買實例的 expiresAt 不受影響（PDC 已固化）——確認無需處理。

---

## 14. 里程碑（開發順序建議）

1. **M0 scaffold**：Maven 專案（folia-api 1.21.11-SNAPSHOT、Java 21、plugin.yml api-version 1.21.11 + folia-supported、.gitignore）＋空 plugin 能載入。
2. **M1 資料層**：WeaponDef 讀寫 weapons.yml、UUID 產生、PDC 實例建立/讀取（含 legacy `&`→MM 轉換 util）。
3. **M2 指令骨架 + Dialog wizard**：Home/Add/Manage/List/Reload（Dialog API 可行性 spike 先行）。
4. **M3 購買流**：Chest GUI shop（分頁、確認）、buy 指令、金流（Vault/PP）、log、廣播、滿包。
5. **M4 時限閉環**：callback + join + lazy 三層 + rewrite。
6. **M5 物品保護**。
7. **M6 打磨**：config/messages 抽離、音效、list 格式、（bStats/update checker 視需要）。

---
*End of spec v0.1*
