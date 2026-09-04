package tw.superarms.util;

import java.time.*;
import java.time.format.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextUtil {
  private static final MiniMessage MM = MiniMessage.miniMessage();
  private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private TextUtil() {}

  public static String mm(String s) {
    if (s == null) return "";
    if (s.indexOf('&') >= 0) s = s.replace('&', '§');
    if (s.indexOf('§') >= 0) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '§' && i + 1 < s.length()) {
          char x = s.charAt(++i);
          b.append(
              switch (x) {
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
              });
        } else b.append(c);
      }
      s = b.toString();
    }
    return s;
  }

  public static Component component(String s) {
    return MM.deserialize(mm(s));
  }

  public static long parseDuration(String s) {
    if (s == null || s.isBlank() || s.equals("0")) return 0;
    try {
      long n = Long.parseLong(s.substring(0, s.length() - 1));
      return switch (s.substring(s.length() - 1)) {
        case "d" -> n * 86400000L;
        case "h" -> n * 3600000L;
        case "m" -> n * 60000L;
        case "s" -> n * 1000L;
        default -> 0;
      };
    } catch (Exception e) {
      return 0;
    }
  }

  public static long parseDate(String s) {
    if (s == null || s.isBlank()) return 0;
    try {
      return LocalDateTime.parse(s, F).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    } catch (Exception e) {
      return 0;
    }
  }

  public static String date(long t) {
    return t <= 0
        ? ""
        : F.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault()));
  }

  public static String duration(long ms) {
    if (ms <= 0) return "0";
    if (ms % 86400000L == 0) return (ms / 86400000L) + "d";
    if (ms % 3600000L == 0) return (ms / 3600000L) + "h";
    if (ms % 60000L == 0) return (ms / 60000L) + "m";
    return (ms / 1000L) + "s";
  }
}
