package net.tfminecraft.research.model;

import org.bukkit.configuration.ConfigurationSection;

public final class AspectRequirement {

    private final int requiredPoints;

    public AspectRequirement(ConfigurationSection config) {
        this.requiredPoints = config != null ? config.getInt("required_points", 0) : 0;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }
}
