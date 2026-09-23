package net.tfminecraft.research.model;

import org.bukkit.configuration.ConfigurationSection;

public final class AspectSounds {

    private final String input;
    private final String confirm;
    private final float volume;
    private final float pitch;

    public AspectSounds(ConfigurationSection config) {
        if (config != null) {
            this.input = config.getString("input", "entity.item.pickup");
            this.confirm = config.getString("confirm", "ENTITY_EXPERIENCE_ORB_PICKUP");
            this.volume = (float) config.getDouble("volume", 1.0);
            this.pitch = (float) config.getDouble("pitch", 1.0);
        } else {
            this.input = "entity.item.pickup";
            this.confirm = "ENTITY_EXPERIENCE_ORB_PICKUP";
            this.volume = 1.0f;
            this.pitch = 1.0f;
        }
    }

    public String getInput() {
        return input;
    }

    public String getConfirm() {
        return confirm;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }
}
