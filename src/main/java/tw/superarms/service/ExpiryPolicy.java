package tw.superarms.service;

import tw.superarms.data.TagVerdict;

/**
 * 特武到期決策：把「物品標籤狀態 + 到期時間」映成要採取的動作。
 * 抽成純函式以便離線測試各種被竄改的情境。
 */
public final class ExpiryPolicy {

    public enum Action {
        /** 不動作。 */
        SKIP,
        /** 到期：拔除全部附魔並標記失效。 */
        EXPIRE,
        /** 標籤遭竄改／無效：fail-closed，直接拔除全部附魔。 */
        STRIP
    }

    private ExpiryPolicy() {
    }

    /**
     * @param verdict        標籤驗證結果
     * @param templateExists def 指向的模板是否仍存在
     * @param alreadyExpired 是否已標記失效
     * @param expiresAt      到期時間（epoch millis，0 = 永久）
     * @param now            現在時間（epoch millis）
     */
    public static Action decide(
            TagVerdict verdict,
            boolean templateExists,
            boolean alreadyExpired,
            long expiresAt,
            long now
    ) {
        if (verdict == TagVerdict.UNTAGGED) {
            return Action.SKIP;
        }
        if (verdict == TagVerdict.TAMPERED) {
            return Action.STRIP;
        }
        if (verdict == TagVerdict.LEGACY && !templateExists) {
            return Action.STRIP;
        }
        if (alreadyExpired) {
            return Action.SKIP;
        }
        if (expiresAt <= 0 || expiresAt > now) {
            return Action.SKIP;
        }
        return Action.EXPIRE;
    }
}
