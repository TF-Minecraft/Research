package net.tfminecraft.research;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI item refs and colour tokens from gui.yml. Layout slots stay in {@code GridLayout}.
 */
public final class GuiCache {

    private GuiCache() {}

    public static String confirmButton = "ia.mcicons:icon_confirm";
    public static String cancelButton = "ia.mcicons:icon_cancel";
    public static String filler = "vanilla.GRAY_STAINED_GLASS_PANE";
    public static String gridFiller = "vanilla.GRAY_STAINED_GLASS_PANE";
    public static String aspectProgressInactive = "vanilla.GRAY_STAINED_GLASS_PANE";
    public static String mentalPointsIcon = "vanilla.LIGHT";
    public static String lockedAspect = "ia.mcicons:icon_lock";
    public static String aspectRowDivider = "vanilla.BLACK_STAINED_GLASS_PANE";
    public static String aspectTestedUnknown = "ia.mcicons:icon_lock";
    public static String aspectProgressUnknown = "vanilla.YELLOW_STAINED_GLASS_PANE";
    public static String aspectProgressRevealed = "vanilla.ORANGE_STAINED_GLASS_PANE";
    public static String wavePulseDefault = "vanilla.LIGHT_BLUE_STAINED_GLASS_PANE";
    public static String leftFiller = "vanilla.BLACK_STAINED_GLASS_PANE";

    public static String inventoryTitleLabel = "Research Station";
    public static String scrapConfirmTitleLabel = "Confirm Scrap";
    public static String undiscoveredAspectName = "§8Undiscovered Aspect";
    public static List<String> undiscoveredAspectLore = List.of();
    public static String scrapButtonLabel = "#c45749Scrap";
    public static String confirmExperimentLabel = "#82d461Confirm Experiment";
    public static String experimentCompletedLabel = "#575150Completed";
    public static String scrapConfirmYesLabel = "#82d461Confirm Scrap";
    public static String scrapConfirmNoLabel = "#c45749Cancel";

    /** Hex colour tokens for GUI display names/lore (applied in batch 16). */
    public static Map<String, String> colors = Collections.emptyMap();

    public static void resetColors(Map<String, String> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            colors = Collections.emptyMap();
            return;
        }
        colors = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
    }

    public static String color(String key, String fallback) {
        String value = colors.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
