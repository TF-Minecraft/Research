package net.tfminecraft.research.loader;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.research.GuiCache;
import net.tfminecraft.research.Research;

public final class GuiLoader implements LoaderInterface {

    @Override
    public void load(File configFile) {
        if (!loadInternal(configFile)) {
            Research.plugin.getLogger().severe("[Research] gui.yml load failed.");
        }
    }

    public boolean loadSafe(File configFile) {
        return loadInternal(configFile);
    }

    private boolean loadInternal(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Research.plugin.getLogger().severe("[Research] Failed to load gui.yml: " + ex.getMessage());
            return false;
        }

        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            GuiCache.confirmButton = items.getString("confirm_button", GuiCache.confirmButton);
            GuiCache.cancelButton = items.getString("cancel_button", GuiCache.cancelButton);
            GuiCache.filler = items.getString("filler", GuiCache.filler);
            GuiCache.gridFiller = items.getString("grid_filler", GuiCache.filler);
            GuiCache.aspectProgressInactive = items.getString(
                    "aspect_progress_inactive", GuiCache.filler);
            GuiCache.mentalPointsIcon = items.getString("mental_points_icon", GuiCache.mentalPointsIcon);
            GuiCache.lockedAspect = items.getString("locked_aspect", GuiCache.lockedAspect);
            GuiCache.aspectRowDivider = items.getString("aspect_row_divider", GuiCache.aspectRowDivider);
            GuiCache.aspectTestedUnknown = items.getString("aspect_tested_unknown", GuiCache.aspectTestedUnknown);
            GuiCache.aspectProgressUnknown = items.getString("aspect_progress_unknown", GuiCache.aspectProgressUnknown);
            GuiCache.aspectProgressRevealed = items.getString("aspect_progress_revealed", GuiCache.aspectProgressRevealed);
            GuiCache.wavePulseDefault = items.getString("wave_pulse_default", GuiCache.wavePulseDefault);
            GuiCache.leftFiller = items.getString("left_filler", GuiCache.leftFiller);
        }

        ConfigurationSection labels = config.getConfigurationSection("labels");
        if (labels != null) {
            GuiCache.inventoryTitleLabel = labels.getString("inventory_title", GuiCache.inventoryTitleLabel);
            GuiCache.scrapConfirmTitleLabel = labels.getString(
                    "scrap_confirm_title", GuiCache.scrapConfirmTitleLabel);
            GuiCache.undiscoveredAspectName = labels.getString(
                    "undiscovered_aspect_name", GuiCache.undiscoveredAspectName);
            if (labels.isList("undiscovered_aspect_lore")) {
                GuiCache.undiscoveredAspectLore = List.copyOf(labels.getStringList("undiscovered_aspect_lore"));
            } else {
                GuiCache.undiscoveredAspectLore = List.of();
            }
            GuiCache.scrapButtonLabel = labels.getString("scrap_button", GuiCache.scrapButtonLabel);
            GuiCache.confirmExperimentLabel = labels.getString(
                    "confirm_experiment", GuiCache.confirmExperimentLabel);
            GuiCache.experimentCompletedLabel = labels.getString(
                    "experiment_completed", GuiCache.experimentCompletedLabel);
            GuiCache.scrapConfirmYesLabel = labels.getString(
                    "scrap_confirm_yes", GuiCache.scrapConfirmYesLabel);
            GuiCache.scrapConfirmNoLabel = labels.getString(
                    "scrap_confirm_no", GuiCache.scrapConfirmNoLabel);
        }

        Map<String, String> colorTokens = new LinkedHashMap<>();
        ConfigurationSection colors = config.getConfigurationSection("colors");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                String value = colors.getString(key, "");
                if (value != null && !value.isBlank()) {
                    colorTokens.put(key, value);
                }
            }
        }
        GuiCache.resetColors(colorTokens);
        return true;
    }
}
