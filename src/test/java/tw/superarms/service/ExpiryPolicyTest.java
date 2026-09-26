package tw.superarms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import tw.superarms.data.TagVerdict;

/**
 * 決策矩陣：把「標籤沒被動 / 被動過 / 拔掉」對應到正確動作，
 * 對應規格中「無論怎麼改，只要標籤不被動就照規則走；標籤被動過就直接失效」。
 */
class ExpiryPolicyTest {

    private static final long NOW = 1_000_000L;

    @Test
    void untaggedIsSkipped() {
        assertEquals(
                ExpiryPolicy.Action.SKIP,
                ExpiryPolicy.decide(TagVerdict.UNTAGGED, true, false, NOW - 1, NOW)
        );
    }

    @Test
    void tamperedStripsEvenWhenNotYetExpired() {
        assertEquals(
                ExpiryPolicy.Action.STRIP,
                ExpiryPolicy.decide(TagVerdict.TAMPERED, true, false, NOW + 999_999L, NOW)
        );
    }

    @Test
    void tamperedStripsEvenWhenPermanent() {
        assertEquals(
                ExpiryPolicy.Action.STRIP,
                ExpiryPolicy.decide(TagVerdict.TAMPERED, true, false, 0L, NOW)
        );
    }

    @Test
    void legacyWithMissingTemplateStrips() {
        assertEquals(
                ExpiryPolicy.Action.STRIP,
                ExpiryPolicy.decide(TagVerdict.LEGACY, false, false, NOW + 999_999L, NOW)
        );
    }

    @Test
    void validExpiredExpires() {
        assertEquals(
                ExpiryPolicy.Action.EXPIRE,
                ExpiryPolicy.decide(TagVerdict.VALID, true, false, NOW - 1, NOW)
        );
    }

    @Test
    void validNotYetExpiredSkips() {
        assertEquals(
                ExpiryPolicy.Action.SKIP,
                ExpiryPolicy.decide(TagVerdict.VALID, true, false, NOW + 1, NOW)
        );
    }

    @Test
    void validPermanentSkips() {
        assertEquals(
                ExpiryPolicy.Action.SKIP,
                ExpiryPolicy.decide(TagVerdict.VALID, true, false, 0L, NOW)
        );
    }

    @Test
    void alreadyExpiredIsIdempotent() {
        assertEquals(
                ExpiryPolicy.Action.SKIP,
                ExpiryPolicy.decide(TagVerdict.VALID, true, true, NOW - 1, NOW)
        );
    }

    @Test
    void legacyWithTemplateBehavesLikeValid() {
        assertEquals(
                ExpiryPolicy.Action.EXPIRE,
                ExpiryPolicy.decide(TagVerdict.LEGACY, true, false, NOW - 1, NOW)
        );
        assertEquals(
                ExpiryPolicy.Action.SKIP,
                ExpiryPolicy.decide(TagVerdict.LEGACY, true, false, NOW + 1, NOW)
        );
    }
}
