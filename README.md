# SuperArms

日出伺服器特武系統（Canvas / Folia 1.21.11，Java 21）。

- 功能規格：`SPEC.md`（設計討論已定案；未決項以 SPEC 內預設實作，review 時確認）
- 建置：`mvn package` → `target/SuperArms-<ver>.jar`

## 里程碑

- M0 scaffold（本 repo 初始狀態）
- M1 資料層（weapons.yml / WeaponDef / PDC 實例 / MM 轉換）
- M2 指令骨架 + Dialog wizard（管理面）
- M3 購買流（Chest GUI / buy / 金流 / log / 廣播 / 滿包）
- M4 時限閉環（callback + join + lazy rewrite）
- M5 物品保護
- M6 打磨（config/messages 抽離、音效、list 格式）

## 管理面：匯入背包物品（v0.2.0）

管理 GUI 支援直接用現成物品當模板，不必手動一項項填材質/Lore/附魔。

- 管理首頁 `匯入背包武器`（slot 51）→ 列出自己背包 0-35 + 副手 40 的非空物品（每頁 28），點一個就建新模板。
  - 帶入：材質、自訂名稱、Lore、附魔、自訂模型資料、不可破壞、發光。
  - 商業欄位預設：價格 0、時限 0（永久）、販售截止不限 → 進管理頁再設定。
  - 本系統自己的 Lore 標記（「附魔有效至 / 附魔已失效」）會略過；假光澤（LURE 1 + HIDE_ENCHANTS）視為「發光」而非附魔。
  - 不消耗來源物品（只是複製外觀成模板）。
- 管理頁 `從手上匯入覆蓋`（slot 34）→ 把主手物品的外觀覆蓋到既有模板，**價格/幣種/時限/販售截止/開關不變**；物品沒有自訂名稱時保留原本名稱。
- 指令版：`/superarms import` = 主手物品 → 新模板（不開 GUI，適合 console / 基岩版 / 腳本）。
- 名稱/Lore 以 MiniMessage 字串存回 `weapons.yml`（`TextUtil.serialize`，會逸出尖括號、去掉 `<italic>`）。
- 附魔名稱顯示：管理 GUI（新增/移除附魔、管理頁 hover）一律顯示繁體中文，來源 = MC 官方語言檔 `zh_tw.json` 的 `enchantment.minecraft.*`（見 `util/EnchantNames`）；第三方插件自訂附魔顯示原始 id。
