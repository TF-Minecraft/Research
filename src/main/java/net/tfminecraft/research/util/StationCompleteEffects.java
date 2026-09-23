package net.tfminecraft.research.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Research;

public final class StationCompleteEffects {

    private StationCompleteEffects() {}

    public static void play(Player player, Location blockLocation) {
        if (player == null || blockLocation == null || blockLocation.getWorld() == null) {
            return;
        }

        Location center = blockLocation.clone().add(0.5, 1.0, 0.5);
        SoundKeys.play(player, Cache.stationCompleteSound, Cache.stationCompleteSoundVolume,
                Cache.stationCompleteSoundPitch);
        spawnBurst(center, Cache.stationCompleteParticle, Cache.stationCompleteParticleCount,
                Cache.stationCompleteParticleRadius);

        if (Cache.stationCompleteExtraSoundDelayTicks <= 0) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                SoundKeys.play(player, Cache.stationCompleteExtraSound,
                        Cache.stationCompleteExtraSoundVolume, Cache.stationCompleteExtraSoundPitch);
                spawnBurst(center, Cache.stationCompleteExtraParticle, Cache.stationCompleteExtraParticleCount,
                        Cache.stationCompleteExtraParticleRadius);
            }
        }.runTaskLater(Research.plugin, Cache.stationCompleteExtraSoundDelayTicks);
    }

    private static void spawnBurst(Location center, String particleName, int count, double radius) {
        Particle particle = parseParticle(particleName);
        if (particle == null || center.getWorld() == null) {
            return;
        }
        center.getWorld().spawnParticle(
                particle,
                center,
                count,
                radius,
                0.7,
                radius,
                0.02);
    }

    private static Particle parseParticle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            Research.plugin.getLogger().warning("[Research] Unknown station complete particle: " + name);
            return null;
        }
    }
}
