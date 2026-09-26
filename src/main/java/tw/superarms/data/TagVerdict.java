package tw.superarms.data;

/** 特武標籤的驗證結果。 */
public enum TagVerdict {
    /** 沒有任何特武標籤（不是特武，或標籤被整個移除）。 */
    UNTAGGED,
    /** 標籤存在且簽章正確。 */
    VALID,
    /** 舊版（v1）標籤：有 def 但沒有簽章，需要一次性遷移。 */
    LEGACY,
    /** 標籤存在但簽章不符／欄位缺失 → 判定遭竄改。 */
    TAMPERED
}
