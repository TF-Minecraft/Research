package net.tfminecraft.research.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class AspectDef {

    private static final Map<String, String> GRID_COLOR_PULSE_FALLBACK = Map.ofEntries(
            Map.entry("red", "vanilla.RED_STAINED_GLASS_PANE"),
            Map.entry("orange", "vanilla.ORANGE_STAINED_GLASS_PANE"),
            Map.entry("yellow", "vanilla.YELLOW_STAINED_GLASS_PANE"),
            Map.entry("green", "vanilla.LIME_STAINED_GLASS_PANE"),
            Map.entry("blue", "vanilla.LIGHT_BLUE_STAINED_GLASS_PANE"),
            Map.entry("cyan", "vanilla.CYAN_STAINED_GLASS_PANE"),
            Map.entry("purple", "vanilla.PURPLE_STAINED_GLASS_PANE"),
            Map.entry("magenta", "vanilla.MAGENTA_STAINED_GLASS_PANE"),
            Map.entry("pink", "vanilla.PINK_STAINED_GLASS_PANE"),
            Map.entry("gray", "vanilla.GRAY_STAINED_GLASS_PANE"),
            Map.entry("grey", "vanilla.GRAY_STAINED_GLASS_PANE"),
            Map.entry("white", "vanilla.WHITE_STAINED_GLASS_PANE"));

    private final String id;
    private final String name;
    private final List<String> lore;
    private final String displayItemRef;
    private final String gridColor;
    private final String pulseColorRef;
    private final AspectSounds sounds;
    private final List<String> primaryItems;
    private final List<String> secondaryItems;

    public AspectDef(String id, ConfigurationSection config) {
        this.id = id;
        this.name = config.getString("name", id);
        this.lore = config.getStringList("lore");
        this.displayItemRef = config.getConfigurationSection("display") != null
                ? config.getConfigurationSection("display").getString("item", "")
                : "";
        this.gridColor = config.getString("grid_color", "gray");
        this.pulseColorRef = resolvePulseColorRef(config);
        this.sounds = new AspectSounds(config.getConfigurationSection("sounds"));
        this.primaryItems = normalizeItemRefs(config.getStringList("primary_items"));
        this.secondaryItems = normalizeItemRefs(config.getStringList("secondary_items"));
    }

    private static List<String> normalizeItemRefs(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(refs.size());
        for (String ref : refs) {
            if (ref != null && !ref.isBlank()) {
                out.add(ref.trim());
            }
        }
        return List.copyOf(out);
    }

    private static String resolvePulseColorRef(ConfigurationSection config) {
        String pulseColor = config.getString("pulse_color", "");
        if (pulseColor != null && !pulseColor.isBlank()) {
            return pulseColor.trim();
        }
        String gridColor = config.getString("grid_color", "");
        if (gridColor == null || gridColor.isBlank()) {
            return "";
        }
        return GRID_COLOR_PULSE_FALLBACK.getOrDefault(gridColor.trim().toLowerCase(Locale.ROOT), "");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return new ArrayList<>(lore);
    }

    public String getDisplayItemRef() {
        return displayItemRef;
    }

    public String getGridColor() {
        return gridColor;
    }

    public String getPulseColorRef() {
        return pulseColorRef;
    }

    public AspectSounds getSounds() {
        return sounds;
    }

    public List<String> getPrimaryItems() {
        return primaryItems;
    }

    public List<String> getSecondaryItems() {
        return secondaryItems;
    }
}
