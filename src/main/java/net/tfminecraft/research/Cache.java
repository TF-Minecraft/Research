package net.tfminecraft.research;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;

/**
 * Runtime scalars from config.yml. Definition registries live in loaders.
 */
public final class Cache {

    private Cache() {}

    public static Material stationBlock = Material.LECTERN;
    public static String stationPermission = "";
    public static String stationStartSound = "entity.player.levelup";
    public static float stationStartSoundVolume = 0.8f;
    public static float stationStartSoundPitch = 1.1f;
    public static String stationStartParticle = "HAPPY_VILLAGER";
    public static int stationStartParticleCount = 30;
    public static double stationStartParticleRadius = 0.8;

    public static String stationCompleteSound = "ui.toast.challenge_complete";
    public static float stationCompleteSoundVolume = 1.0f;
    public static float stationCompleteSoundPitch = 1.15f;
    public static String stationCompleteExtraSound = "entity.firework_rocket.twinkle";
    public static float stationCompleteExtraSoundVolume = 0.9f;
    public static float stationCompleteExtraSoundPitch = 1.3f;
    public static long stationCompleteExtraSoundDelayTicks = 8L;
    public static String stationCompleteParticle = "TOTEM_OF_UNDYING";
    public static int stationCompleteParticleCount = 50;
    public static double stationCompleteParticleRadius = 1.0;
    public static String stationCompleteExtraParticle = "FIREWORK";
    public static int stationCompleteExtraParticleCount = 30;
    public static double stationCompleteExtraParticleRadius = 0.9;

    public static double resultSpawnKickVelocityMin = 0.35;
    public static double resultSpawnKickVelocityMax = 0.75;
    public static double resultSpawnKickHorizontalMin = 0.05;
    public static double resultSpawnKickHorizontalMax = 0.12;
    public static int resultSpawnTrailTicks = 140;
    public static int resultSpawnTrailIntervalTicks = 2;
    public static boolean resultSpawnBurstParticles = true;

    public static int experimentCost = 1;
    public static int experimentPrimaryPoints = 2;
    public static int experimentSecondaryPoints = 1;
    public static int experimentRejectPoints = 6;

    public static String discoveryMmocoreId = "intelligence";
    public static double productRevealBonusPerPoint = 0.05;
    public static double capProductRevealBonus = 0.5;

    public static double aspectBaseRevealPercent = 0.2;
    public static double aspectRevealPercentReductionPerPoint = 0.01;
    public static double aspectMinRevealPercent = 0.1;

    public static double aspectBaseConfirmPercent = 0.8;
    public static double aspectConfirmPercentReductionPerPoint = 0.01;
    public static double aspectMinConfirmPercent = 0.3;

    public static ConfigurationSection externalModifiers = null;
}

