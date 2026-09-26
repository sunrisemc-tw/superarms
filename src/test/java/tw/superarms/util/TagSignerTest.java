package tw.superarms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TagSignerTest {

    private static final String SECRET = "test-secret-123";

    @Test
    void signIsDeterministic() {
        String payload = TagSigner.payload("abc", 1000L, "owner", 500L, 0);
        assertEquals(TagSigner.sign(SECRET, payload), TagSigner.sign(SECRET, payload));
    }

    @Test
    void verifyAcceptsValidSignature() {
        String payload = TagSigner.payload("abc", 1000L, "owner", 500L, 0);
        assertTrue(TagSigner.verify(SECRET, payload, TagSigner.sign(SECRET, payload)));
    }

    @Test
    void verifyRejectsTamperedExpiry() {
        String payload = TagSigner.payload("abc", 1000L, "owner", 500L, 0);
        String signature = TagSigner.sign(SECRET, payload);
        String tampered = TagSigner.payload("abc", 0L, "owner", 500L, 0);
        assertFalse(TagSigner.verify(SECRET, tampered, signature));
    }

    @Test
    void verifyRejectsTamperedDef() {
        String payload = TagSigner.payload("abc", 1000L, "owner", 500L, 0);
        String signature = TagSigner.sign(SECRET, payload);
        String tampered = TagSigner.payload("xyz", 1000L, "owner", 500L, 0);
        assertFalse(TagSigner.verify(SECRET, tampered, signature));
    }

    @Test
    void verifyRejectsTamperedExpiredFlag() {
        String payload = TagSigner.payload("abc", 1000L, "owner", 500L, 0);
        String signature = TagSigner.sign(SECRET, payload);
        String tampered = TagSigner.payload("abc", 1000L, "owner", 500L, 1);
        assertFalse(TagSigner.verify(SECRET, tampered, signature));
    }

    @Test
    void verifyRejectsForeignSecret() {
        String payload = TagSigner.payload("abc", 1000L, "owner", 500L, 0);
        assertFalse(TagSigner.verify("other-secret", payload, TagSigner.sign(SECRET, payload)));
    }

    @Test
    void verifyRejectsNullSignature() {
        String payload = TagSigner.payload("abc", 1000L, "owner", 500L, 0);
        assertFalse(TagSigner.verify(SECRET, payload, null));
    }

    @Test
    void newSecretIsUniqueAndNonBlank() {
        String first = TagSigner.newSecret();
        String second = TagSigner.newSecret();
        assertNotEquals(first, second);
        assertFalse(first.isBlank());
    }

    @Test
    void payloadTreatsNullAndEmptyAlike() {
        assertEquals(
                TagSigner.payload(null, 1L, null, 2L, 0),
                TagSigner.payload("", 1L, "", 2L, 0)
        );
    }
}
