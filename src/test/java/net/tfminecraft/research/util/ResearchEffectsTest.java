package net.tfminecraft.research.util;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.ResearchTestState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class ResearchEffectsTest {
    private ResearchTestState globals;
    private ServerMock server;
    private World world;
    private Player player;
    private Item entity;
    private Location block;
    private Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        globals = new ResearchTestState();
        Research.plugin = mock(Research.class);
        logger = mock(Logger.class);
        when(Research.plugin.getLogger()).thenReturn(logger);
        when(Research.plugin.getServer()).thenReturn(server);
        when(Research.plugin.isEnabled()).thenReturn(true);
        when(Research.plugin.getName()).thenReturn("Research");
        world = mock(World.class);
        block = new Location(world, 1, 64, 2);
        player = mock(Player.class);
        when(player.getLocation()).thenReturn(block);
        when(player.isOnline()).thenReturn(true);
        entity = mock(Item.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.getLocation()).thenAnswer(call -> block.clone().add(0.5, 1.0, 0.5));
        when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(entity);
        Messages.put("result.entity_name", "{amount} x {item}");
        Cache.resultSpawnBurstParticles = true;
        Cache.resultSpawnKickHorizontalMin = 0.05;
        Cache.resultSpawnKickHorizontalMax = 0.15;
        Cache.resultSpawnKickVelocityMin = 0.4;
        Cache.resultSpawnKickVelocityMax = 0.8;
        Cache.resultSpawnTrailTicks = 4;
        Cache.resultSpawnTrailIntervalTicks = 2;
    }

    @AfterEach
    void tearDown() throws Exception {
        server.getScheduler().cancelTasks(Research.plugin);
        globals.close();
        MockBukkit.unmock();
    }

    @Test
    void configuredTrailDurationCountsTicksRatherThanScheduledExecutions() {
        assertTrue(ResultSpawnEffects.spawnAtStation(block, new ItemStack(Material.DIAMOND)));
        server.getScheduler().performTicks(6);
        verify(world, times(2)).spawnParticle(eq(Particle.CRIT), any(Location.class), eq(4),
                eq(0.05), eq(0.05), eq(0.05), eq(0.0));
        server.getScheduler().performTicks(10);
        verify(world, times(2)).spawnParticle(eq(Particle.CRIT), any(Location.class), eq(4),
                eq(0.05), eq(0.05), eq(0.05), eq(0.0));
    }

    @Test
    void soundNormalizationUsesRootLocaleForEnumNamesAndNamespaces() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("minecraft:item.shield.break", SoundKeys.normalize("ITEM_SHIELD_BREAK"));
            assertEquals("ideal:item.shield.break", SoundKeys.normalize(" IDEAL: ITEM_SHIELD_BREAK "));
            assertEquals("minecraft:item.shield.break", SoundKeys.normalize("item.shield.break"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void bukkitSoundEnumsPreserveUnderscoresInsideRegistryKeySegments() {
        assertEquals(Sound.ENTITY_EXPERIENCE_ORB_PICKUP.getKey().toString(),
                SoundKeys.normalize("ENTITY_EXPERIENCE_ORB_PICKUP"));
        assertEquals(Sound.BLOCK_NOTE_BLOCK_CHIME.getKey().toString(),
                SoundKeys.normalize("BLOCK_NOTE_BLOCK_CHIME"));
    }

    @Test
    void configuredParticleNamesUseRootLocaleInBothStationEffects() {
        Cache.stationStartParticle = "crit";
        Cache.stationCompleteParticle = "crit";
        Cache.stationCompleteExtraSoundDelayTicks = 0;
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            StationStartEffects.play(player, block);
            StationCompleteEffects.play(player, block);
            verify(world, times(2)).spawnParticle(eq(Particle.CRIT), any(Location.class), anyInt(),
                    anyDouble(), anyDouble(), anyDouble(), anyDouble());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void resultSpawnCopiesTheRewardSetsItsNameAndAppliesConfiguredKickAndBurst() {
        ItemStack reward = new ItemStack(Material.DIAMOND, 3);
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextDouble(anyDouble(), anyDouble())).thenAnswer(call ->
                (call.getArgument(0, Double.class) + call.getArgument(1, Double.class)) / 2);
        when(random.nextBoolean()).thenReturn(true, false);
        try (MockedStatic<ThreadLocalRandom> current = mockStatic(ThreadLocalRandom.class)) {
            current.when(ThreadLocalRandom::current).thenReturn(random);
            assertTrue(ResultSpawnEffects.spawnAtStation(block, reward));
        }
        ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(world).dropItem(eq(block.clone().add(0.5, 0.95, 0.5)), dropped.capture());
        assertEquals(reward, dropped.getValue());
        assertNotSame(reward, dropped.getValue());
        assertEquals(3, reward.getAmount());
        assertEquals(new Location(world, 1, 64, 2), block);
        verify(entity).setPickupDelay(0);
        verify(entity).setCustomName("3 x Diamond");
        verify(entity).setCustomNameVisible(true);
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(entity).setVelocity(velocity.capture());
        assertEquals(0.1, velocity.getValue().getX(), 0.0001);
        assertEquals(-0.1, velocity.getValue().getZ(), 0.0001);
        assertEquals(0.6, velocity.getValue().getY(), 0.0001);
        verify(world).playSound(block.clone().add(0.5, 0.8, 0.5), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
        verify(world).spawnParticle(Particle.CLOUD, block.clone().add(0.5, 0.8, 0.5), 18, 0.35, 0.25, 0.35, 0.0);
        verify(world).spawnParticle(Particle.ENCHANTED_HIT, block.clone().add(0.5, 0.8, 0.5), 12, 0.25, 0.2, 0.25, 0.0);
    }

    @Test
    void customNamesArePreservedAndBurstCanBeDisabled() {
        Cache.resultSpawnBurstParticles = false;
        ItemStack reward = new ItemStack(Material.PAPER);
        var meta = reward.getItemMeta();
        meta.setDisplayName("Research notes");
        reward.setItemMeta(meta);
        assertTrue(ResultSpawnEffects.spawnAtStation(block, reward));
        verify(entity).setCustomName("1 x Research notes");
        verify(world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void invalidRewardAndStationArgumentsDoNotProduceEffects() {
        ItemStack reward = new ItemStack(Material.PAPER);
        assertFalse(ResultSpawnEffects.spawnAtStation(null, reward));
        assertFalse(ResultSpawnEffects.spawnAtStation(new Location(null, 0, 0, 0), reward));
        assertFalse(ResultSpawnEffects.spawnAtStation(block, null));
        assertFalse(ResultSpawnEffects.spawnAtStation(block, new ItemStack(Material.AIR)));
        verifyNoInteractions(entity);
        verify(world, never()).dropItem(any(), any());
    }

    @Test
    void trailStopsForRemovedOrDeadEntitiesAndClampsItsSchedulingInterval() {
        Cache.resultSpawnTrailIntervalTicks = 0;
        when(entity.isValid()).thenReturn(false);
        ResultSpawnEffects.spawnAtStation(block, new ItemStack(Material.PAPER));
        server.getScheduler().performOneTick();
        when(entity.isValid()).thenReturn(true);
        when(entity.isDead()).thenReturn(true);
        ResultSpawnEffects.spawnAtStation(block, new ItemStack(Material.PAPER));
        server.getScheduler().performOneTick();
        when(entity.isDead()).thenReturn(false);
        Cache.resultSpawnTrailTicks = 0;
        ResultSpawnEffects.spawnAtStation(block, new ItemStack(Material.PAPER));
        server.getScheduler().performOneTick();
        clearInvocations(entity);
        server.getScheduler().performTicks(10);
        verify(entity, never()).isValid();
        verify(world, never()).spawnParticle(eq(Particle.CRIT), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void startEffectsUseConfiguredSoundAndCenteredParticles() {
        Cache.stationStartSound = "BLOCK_NOTE_BLOCK_CHIME";
        Cache.stationStartSoundVolume = 0.6f;
        Cache.stationStartSoundPitch = 1.4f;
        Cache.stationStartParticle = " cloud ";
        Cache.stationStartParticleCount = 7;
        Cache.stationStartParticleRadius = 0.2;
        StationStartEffects.play(player, block);
        verify(player).playSound(block, "minecraft:block.note_block.chime", 0.6f, 1.4f);
        verify(world).spawnParticle(Particle.CLOUD, block.clone().add(0.5, 1, 0.5), 7, 0.2, 0.6, 0.2, 0.02);
        assertEquals(new Location(world, 1, 64, 2), block);
    }

    @Test
    void startEffectsIgnoreInvalidContextAndMissingParticlesAndWarnForUnknownParticles() {
        StationStartEffects.play(null, block);
        StationStartEffects.play(player, null);
        StationStartEffects.play(player, new Location(null, 0, 0, 0));
        Cache.stationStartParticle = null;
        StationStartEffects.play(player, block);
        Cache.stationStartParticle = " ";
        StationStartEffects.play(player, block);
        Cache.stationStartParticle = "not-a-particle";
        StationStartEffects.play(player, block);
        verify(logger).warning(contains("Unknown station start particle"));
        verify(world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void completionSchedulesTheExtraEffectAtItsConfiguredDelay() {
        Cache.stationCompleteSound = "test.primary";
        Cache.stationCompleteParticle = "CLOUD";
        Cache.stationCompleteParticleCount = 4;
        Cache.stationCompleteParticleRadius = 0.4;
        Cache.stationCompleteExtraSoundDelayTicks = 8;
        Cache.stationCompleteExtraSound = "test.extra";
        Cache.stationCompleteExtraParticle = "CRIT";
        Cache.stationCompleteExtraParticleCount = 2;
        Cache.stationCompleteExtraParticleRadius = 0.2;
        StationCompleteEffects.play(player, block);
        verify(player).playSound(block, "minecraft:test.primary", Cache.stationCompleteSoundVolume, Cache.stationCompleteSoundPitch);
        verify(world).spawnParticle(Particle.CLOUD, block.clone().add(0.5, 1, 0.5), 4, 0.4, 0.7, 0.4, 0.02);
        server.getScheduler().performTicks(7);
        verify(player, never()).playSound(any(Location.class), eq("minecraft:test.extra"), anyFloat(), anyFloat());
        server.getScheduler().performTicks(2);
        verify(player).playSound(block, "minecraft:test.extra", Cache.stationCompleteExtraSoundVolume, Cache.stationCompleteExtraSoundPitch);
        verify(world).spawnParticle(Particle.CRIT, block.clone().add(0.5, 1, 0.5), 2, 0.2, 0.7, 0.2, 0.02);
    }

    @Test
    void completionDoesNotPlayDelayedEffectsForPlayersWhoLoggedOut() {
        Cache.stationCompleteExtraSoundDelayTicks = 1;
        Cache.stationCompleteExtraSound = "test.extra";
        StationCompleteEffects.play(player, block);
        when(player.isOnline()).thenReturn(false);
        server.getScheduler().performTicks(2);
        verify(player, never()).playSound(any(Location.class), eq("minecraft:test.extra"), anyFloat(), anyFloat());
    }

    @Test
    void completionHandlesInvalidContextAndParticleSettingsAndDisabledExtraEffects() {
        StationCompleteEffects.play(null, block);
        StationCompleteEffects.play(player, null);
        StationCompleteEffects.play(player, new Location(null, 0, 0, 0));
        Cache.stationCompleteExtraSoundDelayTicks = 0;
        Cache.stationCompleteParticle = null;
        StationCompleteEffects.play(player, block);
        Cache.stationCompleteParticle = " ";
        StationCompleteEffects.play(player, block);
        Cache.stationCompleteParticle = "not-a-particle";
        StationCompleteEffects.play(player, block);
        verify(logger).warning(contains("Unknown station complete particle"));
        server.getScheduler().performTicks(20);
        verify(world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void soundKeysNormalizeNamespaceFormatsAndIgnoreMissingKeys() {
        assertNull(SoundKeys.normalize(null));
        assertEquals("  ", SoundKeys.normalize("  "));
        assertEquals("minecraft:entity.player.levelup", SoundKeys.normalize(" ENTITY_PLAYER_LEVELUP "));
        assertEquals("custom:my_sound", SoundKeys.normalize(" Custom:my_sound "));
        assertEquals("custom:my.unknown.sound", SoundKeys.normalize("Custom:MY_UNKNOWN_SOUND"));
        assertEquals("minecraft:simple", SoundKeys.normalize("simple"));
        SoundKeys.play(null, "key", 1, 1);
        SoundKeys.play(player, null, 1, 1);
        SoundKeys.play(player, " ", 1, 1);
        verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
        SoundKeys.play(player, "Custom:my_sound", 0.4f, 1.2f);
        verify(player).playSound(block, "custom:my_sound", 0.4f, 1.2f);
    }

    @Test
    void failedSoundPlaybackIsLoggedWithoutBreakingGameplay() {
        doThrow(new IllegalArgumentException("unavailable")).when(player)
                .playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
        assertDoesNotThrow(() -> SoundKeys.play(player, "broken.sound", 1, 1));
        verify(logger).warning(contains("Failed to play sound 'broken.sound'"));
    }
}
