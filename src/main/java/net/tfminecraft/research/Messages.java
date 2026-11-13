package net.tfminecraft.research;

import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public final class Messages {

    private static String prefix = "";
    private static final Map<String, String> entries = new HashMap<>();

    private Messages() {}

    public static void clear() {
        entries.clear();
        prefix = "";
    }

    public static void setPrefix(String value) {
        prefix = value != null ? value : "";
    }

    public static void put(String key, String value) {
        entries.put(key, value);
    }

    public static boolean has(String key) {
        return key != null && entries.containsKey(key);
    }

    public static String get(String key) {
        return format(key, Map.of());
    }

    /** The message for a key that older messages.yml files may lack: the fallback, with the prefix, until it is added. */
    public static String getOrDefault(String key, String fallback) {
        return has(key) ? get(key) : StringFormatter.formatHex(prefix + fallback);
    }

    public static String format(String key, Map<String, String> placeholders) {
        return StringFormatter.formatHex(prefix + formatBodyRaw(key, placeholders));
    }

    public static String formatBody(String key, Map<String, String> placeholders) {
        return StringFormatter.formatHex(formatBodyRaw(key, placeholders));
    }

    private static String formatBodyRaw(String key, Map<String, String> placeholders) {
        String raw = entries.getOrDefault(key, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return raw != null ? raw : "";
    }
}
