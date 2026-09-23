package net.tfminecraft.research.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.research.GuiCache;
import net.tfminecraft.research.Messages;

/**
 * GUI text styling via {@code gui.yml} hex tokens and TLibs {@link StringFormatter#formatHex}.
 * Chat messages use {@code messages.yml}; those strings are formatted in {@link Messages}.
 */
public final class GuiText {

    private static final String FALLBACK_COLOR = "#ffffff";

    private GuiText() {}

    public static String format(String template) {
        return StringFormatter.formatHex(template != null ? template : "");
    }

    public static String color(String key) {
        return GuiCache.color(key, FALLBACK_COLOR);
    }

    public static String text(String colorKey, String plain) {
        return format(color(colorKey) + (plain != null ? plain : ""));
    }

    public static String label(String configured) {
        return format(configured != null ? configured : "");
    }

    public static String guiMessage(String key, Map<String, String> placeholders) {
        return Messages.formatBody(key, placeholders);
    }

    public static List<String> formatLoreLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(format(line != null ? line : ""));
        }
        return out;
    }

    public static String undiscoveredAspectName() {
        if (GuiCache.undiscoveredAspectName != null && !GuiCache.undiscoveredAspectName.isBlank()) {
            return format(GuiCache.undiscoveredAspectName);
        }
        return text("progress_unknown", "???");
    }

    public static List<String> undiscoveredAspectLore() {
        return formatLoreLines(GuiCache.undiscoveredAspectLore);
    }

    public static String aspectName(AspectNameStyle style, String rawName) {
        String coloured = format(rawName != null ? rawName : "");
        String plain = ChatColor.stripColor(coloured);
        return switch (style) {
            case CONFIRMED -> text("aspect_confirmed", plain);
            case HIDDEN -> text("aspect_hidden", plain);
            case PLAIN -> coloured;
        };
    }

    public enum AspectNameStyle {
        CONFIRMED,
        HIDDEN,
        PLAIN
    }
}
