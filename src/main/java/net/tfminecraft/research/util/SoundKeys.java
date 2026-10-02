package net.tfminecraft.research.util;

import java.util.Locale;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.tfminecraft.research.Research;

/**
 * Normalizes Bukkit enum-style sound names and custom namespace keys for Paper 1.21+.
 */
public final class SoundKeys {

    private SoundKeys() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        String namespace;
        String path;
        if (colon >= 0) {
            namespace = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            path = trimmed.substring(colon + 1).trim();
        } else {
            namespace = "minecraft";
            path = trimmed;
        }
        if (looksLikeBukkitEnum(path)) {
            try {
                path = Sound.valueOf(path).getKey().getKey();
            } catch (IllegalArgumentException ex) {
                path = path.toLowerCase(Locale.ROOT).replace('_', '.');
            }
        } else {
            path = path.toLowerCase(Locale.ROOT);
        }
        return namespace + ":" + path;
    }

    private static boolean looksLikeBukkitEnum(String path) {
        return path.equals(path.toUpperCase(Locale.ROOT)) && path.contains("_");
    }

    public static void play(Player player, String soundKey, float volume, float pitch) {
        if (player == null || soundKey == null || soundKey.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), normalize(soundKey), volume, pitch);
        } catch (RuntimeException ex) {
            Research.plugin.getLogger().warning("[Research] Failed to play sound '" + soundKey + "': "
                    + ex.getMessage());
        }
    }
}
