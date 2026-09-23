package net.tfminecraft.research.util;

import java.util.Collections;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.Plugins.TLibs.TLibs;

/**
 * Item references in yaml use vanilla.MATERIAL, m.TYPE.ID, or ia.namespace:id.
 * TLibs paths use v.MATERIAL internally for vanilla items.
 */
public final class ItemRef {

    private ItemRef() {}

    public static String normalize(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        String trimmed = ref.trim();
        if (trimmed.toLowerCase().startsWith("vanilla.")) {
            return "v." + trimmed.substring("vanilla.".length());
        }
        return trimmed;
    }

    public static boolean hasKnownPrefix(String ref) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        String lower = ref.trim().toLowerCase();
        return lower.startsWith("vanilla.")
                || lower.startsWith("v.")
                || lower.startsWith("m.")
                || lower.startsWith("ia.");
    }

    public static ItemStack build(String ref) {
        String normalized = normalize(ref);
        if (normalized.isBlank() || !hasKnownPrefix(ref)) {
            return null;
        }
        try {
            ItemStack item = TLibs.getItemAPI().getCreator().getItemFromPath(normalized);
            if (item == null || item.getType() == Material.AIR) {
                return null;
            }
            item.setAmount(1);
            return item;
        } catch (Exception ex) {
            return null;
        }
    }

    public static boolean isValid(String ref) {
        if (!hasKnownPrefix(ref)) {
            return false;
        }
        ItemStack item = build(ref);
        return item != null && item.getType() != Material.AIR;
    }

    public static boolean matches(ItemStack stack, String ref) {
        if (stack == null || stack.getType() == Material.AIR || ref == null || ref.isBlank()) {
            return false;
        }
        try {
            return TLibs.getItemAPI().getChecker().checkItemWithPath(stack, normalize(ref));
        } catch (Exception ex) {
            return false;
        }
    }

    /** Padding glass: no visible name, lore, or hover tooltip. */
    public static void applyBlankDisplay(ItemStack item) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName("");
        meta.setLore(Collections.emptyList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
    }
}
