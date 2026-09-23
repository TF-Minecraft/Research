package net.tfminecraft.research.loader;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Research;

public final class ConfigLoader implements LoaderInterface {

    @Override
    public void load(File configFile) {
        if (!loadInternal(configFile)) {
            Research.plugin.getLogger().severe("[Research] config.yml load failed.");
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
            Research.plugin.getLogger().severe("[Research] Failed to load config.yml: " + ex.getMessage());
            return false;
        }

        String blockName = config.getString("station.block", "LECTERN");
        try {
            Cache.stationBlock = Material.valueOf(blockName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            Bukkit.getLogger().warning("[Research] Unknown station.block '" + blockName + "', using LECTERN.");
            Cache.stationBlock = Material.LECTERN;
        }
        Cache.stationPermission = config.getString("station.permission", "");

        ConfigurationSection startEffects = config.getConfigurationSection("station.start_effects");
        if (startEffects != null) {
            Cache.stationStartSound = startEffects.getString("sound", Cache.stationStartSound);
            Cache.stationStartSoundVolume = (float) startEffects.getDouble("sound_volume", Cache.stationStartSoundVolume);
            Cache.stationStartSoundPitch = (float) startEffects.getDouble("sound_pitch", Cache.stationStartSoundPitch);
            Cache.stationStartParticle = startEffects.getString("particle", Cache.stationStartParticle);
            Cache.stationStartParticleCount = startEffects.getInt("particle_count", Cache.stationStartParticleCount);
            Cache.stationStartParticleRadius = startEffects.getDouble("particle_radius", Cache.stationStartParticleRadius);
        }

        ConfigurationSection completeEffects = config.getConfigurationSection("station.complete_effects");
        if (completeEffects != null) {
            Cache.stationCompleteSound = completeEffects.getString("sound", Cache.stationCompleteSound);
            Cache.stationCompleteSoundVolume = (float) completeEffects.getDouble("sound_volume",
                    Cache.stationCompleteSoundVolume);
            Cache.stationCompleteSoundPitch = (float) completeEffects.getDouble("sound_pitch",
                    Cache.stationCompleteSoundPitch);
            Cache.stationCompleteExtraSound = completeEffects.getString("extra_sound", Cache.stationCompleteExtraSound);
            Cache.stationCompleteExtraSoundVolume = (float) completeEffects.getDouble("extra_sound_volume",
                    Cache.stationCompleteExtraSoundVolume);
            Cache.stationCompleteExtraSoundPitch = (float) completeEffects.getDouble("extra_sound_pitch",
                    Cache.stationCompleteExtraSoundPitch);
            Cache.stationCompleteExtraSoundDelayTicks = completeEffects.getLong("extra_sound_delay_ticks",
                    Cache.stationCompleteExtraSoundDelayTicks);
            Cache.stationCompleteParticle = completeEffects.getString("particle", Cache.stationCompleteParticle);
            Cache.stationCompleteParticleCount = completeEffects.getInt("particle_count",
                    Cache.stationCompleteParticleCount);
            Cache.stationCompleteParticleRadius = completeEffects.getDouble("particle_radius",
                    Cache.stationCompleteParticleRadius);
            Cache.stationCompleteExtraParticle = completeEffects.getString("extra_particle",
                    Cache.stationCompleteExtraParticle);
            Cache.stationCompleteExtraParticleCount = completeEffects.getInt("extra_particle_count",
                    Cache.stationCompleteExtraParticleCount);
            Cache.stationCompleteExtraParticleRadius = completeEffects.getDouble("extra_particle_radius",
                    Cache.stationCompleteExtraParticleRadius);
        }

        ConfigurationSection resultSpawn = config.getConfigurationSection("station.result_spawn");
        if (resultSpawn != null) {
            Cache.resultSpawnKickVelocityMin = resultSpawn.getDouble("kick_velocity_min",
                    Cache.resultSpawnKickVelocityMin);
            Cache.resultSpawnKickVelocityMax = resultSpawn.getDouble("kick_velocity_max",
                    Cache.resultSpawnKickVelocityMax);
            Cache.resultSpawnKickHorizontalMin = resultSpawn.getDouble("kick_horizontal_min",
                    Cache.resultSpawnKickHorizontalMin);
            Cache.resultSpawnKickHorizontalMax = resultSpawn.getDouble("kick_horizontal_max",
                    Cache.resultSpawnKickHorizontalMax);
            Cache.resultSpawnTrailTicks = resultSpawn.getInt("trail_ticks", Cache.resultSpawnTrailTicks);
            Cache.resultSpawnTrailIntervalTicks = resultSpawn.getInt("trail_interval_ticks",
                    Cache.resultSpawnTrailIntervalTicks);
            Cache.resultSpawnBurstParticles = resultSpawn.getBoolean("burst_particles",
                    Cache.resultSpawnBurstParticles);
        }

        Cache.experimentCost = config.getInt("mental_points.experiment_cost", 1);

        ConfigurationSection experiment = config.getConfigurationSection("experiment");
        if (experiment != null) {
            Cache.experimentPrimaryPoints = experiment.getInt("primary_points", Cache.experimentPrimaryPoints);
            Cache.experimentSecondaryPoints = experiment.getInt("secondary_points", Cache.experimentSecondaryPoints);
            Cache.experimentRejectPoints = Math.max(1, experiment.getInt("reject_points", Cache.experimentRejectPoints));
        }

        ConfigurationSection discovery = config.getConfigurationSection("attributes.discovery");
        if (discovery != null) {
            Cache.discoveryMmocoreId = discovery.getString("mmocore_id", "intelligence");
            Cache.productRevealBonusPerPoint = discovery.getDouble("product_reveal_bonus_per_point", 0.05);

            ConfigurationSection caps = discovery.getConfigurationSection("caps");
            if (caps != null) {
                Cache.capProductRevealBonus = caps.getDouble("product_reveal_bonus", 0.5);
            }
        }

        ConfigurationSection aspectDiscovery = config.getConfigurationSection("aspect_discovery");
        if (aspectDiscovery != null) {
            Cache.aspectBaseRevealPercent = aspectDiscovery.getDouble("base_reveal_percent",
                    Cache.aspectBaseRevealPercent);
            Cache.aspectRevealPercentReductionPerPoint = aspectDiscovery.getDouble(
                    "reveal_percent_reduction_per_point", Cache.aspectRevealPercentReductionPerPoint);
            Cache.aspectMinRevealPercent = aspectDiscovery.getDouble("min_reveal_percent",
                    Cache.aspectMinRevealPercent);
        }

        ConfigurationSection aspectConfirm = config.getConfigurationSection("aspect_confirm");
        if (aspectConfirm != null) {
            Cache.aspectBaseConfirmPercent = aspectConfirm.getDouble("base_confirm_percent",
                    Cache.aspectBaseConfirmPercent);
            Cache.aspectConfirmPercentReductionPerPoint = aspectConfirm.getDouble(
                    "confirm_percent_reduction_per_point", Cache.aspectConfirmPercentReductionPerPoint);
            Cache.aspectMinConfirmPercent = aspectConfirm.getDouble("min_confirm_percent",
                    Cache.aspectMinConfirmPercent);
        }

        Cache.externalModifiers = config.getConfigurationSection("external_modifiers");
        return true;
    }
}
