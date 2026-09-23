package net.tfminecraft.research.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Research;

public final class StationStartEffects {

    private StationStartEffects() {}

    public static void play(Player player, Location blockLocation) {
        if (player == null || blockLocation == null || blockLocation.getWorld() == null) {
            return;
        }
        SoundKeys.play(player, Cache.stationStartSound, Cache.stationStartSoundVolume, Cache.stationStartSoundPitch);

        Particle particle = parseParticle(Cache.stationStartParticle);
        if (particle == null) {
            return;
        }
        Location center = blockLocation.clone().add(0.5, 1.0, 0.5);
        blockLocation.getWorld().spawnParticle(
                particle,
                center,
                Cache.stationStartParticleCount,
                Cache.stationStartParticleRadius,
                0.6,
                Cache.stationStartParticleRadius,
                0.02);
    }

    private static Particle parseParticle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            Research.plugin.getLogger().warning("[Research] Unknown station start particle: " + name);
            return null;
        }
    }
}
