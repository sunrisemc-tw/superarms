package tw.superarms.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 特武標籤簽章：以伺服器密鑰對特武的關鍵欄位做 HMAC-SHA256，
 * 讓玩家無法在不被察覺的情況下改寫 def／到期時間／擁有者等欄位。
 */
public final class TagSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private TagSigner() {
    }

    /** 產生一組新的隨機密鑰（Base64，32 bytes）。 */
    public static String newSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 產生 canonical payload。欄位以 '\n' 分隔，避免分隔符碰撞；
     * null 字串一律視為空字串。
     */
    public static String payload(
            String def,
            long expiresAt,
            String owner,
            long boughtAt,
            int expired
    ) {
        return String.join(
                "\n",
                nullToEmpty(def),
                Long.toString(expiresAt),
                nullToEmpty(owner),
                Long.toString(boughtAt),
                Integer.toString(expired)
        );
    }

    /** 以 HMAC-SHA256 計算簽章，回傳小寫十六進位字串。 */
    public static String sign(String secret, String payload) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("Tag secret is not configured");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign tag", exception);
        }
    }

    /** 以定時比較驗證簽章，避免時序側通道。 */
    public static boolean verify(String secret, String payload, String signature) {
        if (signature == null) {
            return false;
        }
        String expected = sign(secret, payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
