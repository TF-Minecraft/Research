package net.tfminecraft.research.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.tfminecraft.research.Cache;

/**
 * Hook for profession or other external bonuses from {@code external_modifiers} in config.yml.
 * Empty config leaves all values unchanged.
 */
public final class ExternalModifiers {

    private ExternalModifiers() {}

    public static int adjustExperimentPoints(Player player, int basePoints) {
        if (basePoints <= 0 || player == null) {
            return basePoints;
        }
        ConfigurationSection section = Cache.externalModifiers;
        if (section == null || section.getKeys(false).isEmpty()) {
            return basePoints;
        }
        ConfigurationSection experiment = section.getConfigurationSection("experiment");
        if (experiment == null) {
            return basePoints;
        }
        double bonusPercent = experiment.getDouble("aspect_point_bonus_percent", 0.0);
        if (bonusPercent <= 0.0) {
            return basePoints;
        }
        return basePoints + (int) Math.floor(basePoints * bonusPercent);
    }

    public static boolean isConfigured() {
        ConfigurationSection section = Cache.externalModifiers;
        return section != null && !section.getKeys(false).isEmpty();
    }
}
