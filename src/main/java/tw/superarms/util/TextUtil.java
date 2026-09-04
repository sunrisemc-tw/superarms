package tw.superarms.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(
            "uuuu-MM-dd HH:mm"
    ).withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([dhms])$");

    private TextUtil() {
    }

    public static String mm(String source) {
        if (source == null) {
            return "";
        }
        String value = source;
        if (value.indexOf('&') >= 0) {
            value = value.replace('&', '§');
        }
        if (value.indexOf('§') >= 0) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '§' && index + 1 < value.length()) {
                    char code = Character.toLowerCase(value.charAt(++index));
                    builder.append(
                            switch (code) {
                                case '0' -> "<black>";
                                case '1' -> "<dark_blue>";
                                case '2' -> "<dark_green>";
                                case '3' -> "<dark_aqua>";
                                case '4' -> "<dark_red>";
                                case '5' -> "<dark_purple>";
                                case '6' -> "<gold>";
                                case '7' -> "<gray>";
                                case '8' -> "<dark_gray>";
                                case '9' -> "<blue>";
                                case 'a' -> "<green>";
                                case 'b' -> "<aqua>";
                                case 'c' -> "<red>";
                                case 'd' -> "<light_purple>";
                                case 'e' -> "<yellow>";
                                case 'f' -> "<white>";
                                case 'l' -> "<bold>";
                                case 'o' -> "<italic>";
                                case 'n' -> "<underlined>";
                                case 'm' -> "<strikethrough>";
                                case 'r' -> "<reset>";
                                default -> "";
                            }
                    );
                } else {
                    builder.append(character);
                }
            }
            value = builder.toString();
        }
        return value;
    }

    public static Component component(String source) {
        return MM.deserialize(mm(source));
    }

    public static long parseDuration(String source) {
        return parseDurationInput(source).orElse(0);
    }

    public static OptionalLong parseDurationInput(String source) {
        if (source == null) {
            return OptionalLong.empty();
        }
        String value = source.trim().toLowerCase();
        if (value.equals("0")) {
            return OptionalLong.of(0);
        }
        Matcher matcher = DURATION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        try {
            long amount = Long.parseLong(matcher.group(1));
            long multiplier = switch (matcher.group(2)) {
                case "d" -> 86_400_000L;
                case "h" -> 3_600_000L;
                case "m" -> 60_000L;
                case "s" -> 1_000L;
                default -> throw new IllegalStateException("Unexpected duration unit");
            };
            return OptionalLong.of(Math.multiplyExact(amount, multiplier));
        } catch (ArithmeticException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    public static long parseDate(String source) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        return parseDateInput(source).orElse(0);
    }

    public static OptionalLong parseDateInput(String source) {
        if (source == null || source.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long timestamp = LocalDateTime.parse(source.trim(), DATE_FORMAT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            return OptionalLong.of(timestamp);
        } catch (DateTimeParseException exception) {
            return OptionalLong.empty();
        }
    }

    public static String date(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        return DATE_FORMAT.format(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        );
    }

    public static String duration(long milliseconds) {
        if (milliseconds <= 0) {
            return "0";
        }
        if (milliseconds % 86_400_000L == 0) {
            return (milliseconds / 86_400_000L) + "d";
        }
        if (milliseconds % 3_600_000L == 0) {
            return (milliseconds / 3_600_000L) + "h";
        }
        if (milliseconds % 60_000L == 0) {
            return (milliseconds / 60_000L) + "m";
        }
        return (milliseconds / 1_000L) + "s";
    }
}
