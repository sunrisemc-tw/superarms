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
