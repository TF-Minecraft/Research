package net.tfminecraft.research.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.google.gson.Gson;

import net.tfminecraft.research.Research;
import net.tfminecraft.research.ResearchTestState;
import net.tfminecraft.research.loader.InputLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;

class StationBackfillTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final CharacterRef HEIR = new CharacterRef(ACCOUNT, UUID.randomUUID());
    private static final UUID NO_HEIR = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @TempDir Path folder;
    private ResearchTestState globals;
    private World world;
    private Logger logger;
    private boolean ready = true;
    private final Map<UUID, CharacterRef> heirs = new HashMap<>();
    private int heirLookups;

    @BeforeEach
    void setUp() throws Exception {
        world = MockBukkit.mock().addSimpleWorld("world");
        globals = new ResearchTestState();
        Research.plugin = mock(Research.class);
        logger = mock(Logger.class);
        when(Research.plugin.getDataFolder()).thenReturn(folder.toFile());
        when(Research.plugin.getLogger()).thenReturn(logger);
        InputLoader.get().clear();
        OutputLoader.get().clear();
        InputLoader.get().put("input", new InputDef("input", new YamlConfiguration()));
        YamlConfiguration output = new YamlConfiguration();
        output.set("aspects.fire.required_points", 10);
        OutputLoader.get().put("output", new OutputDef("output", output));
        heirs.put(ACCOUNT, HEIR);
        // An heir on another account must never be used.
        heirs.put(STRANGER, new CharacterRef(UUID.randomUUID(), UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() throws Exception {
        globals.close();
        MockBukkit.unmock();
    }

    @Test
    void stationsWithoutACharacterGoToTheAccountsHeirWithABackup() throws Exception {
        save(ACCOUNT, null, 1);
        save(ACCOUNT, "kept", 2);
        String original = Files.readString(stationFile(1));

        assertTrue(backfill().run(StationStore.loadAll()));

        assertEquals(HEIR.characterId().toString(), loaded(1).getOwnerCharacterId());
        assertEquals("kept", loaded(2).getOwnerCharacterId());
        assertEquals(original, Files.readString(backfillFolder().resolve("backup/world_1_64_0.json")));
        assertFalse(Files.exists(backfillFolder().resolve("backup/world_2_64_0.json")));
        StationBackfill.Result result = result();
        assertEquals(1, result.filled());
        assertEquals(List.of(), result.unresolved());
    }

    @Test
    void stationsWhoseAccountHasNoHeirStayUnassignedAndAreListed() throws Exception {
        save(NO_HEIR, null, 1);
        save(STRANGER, null, 2);

        assertTrue(backfill().run(StationStore.loadAll()));

        assertNull(loaded(1).getOwnerCharacterId());
        assertNull(loaded(2).getOwnerCharacterId());
        assertEquals(List.of("world_1_64_0", "world_2_64_0"), result().unresolved().stream().sorted().toList());
        assertEquals(0, result().filled());
        verify(logger).warning(contains("world_1_64_0"));
    }

    @Test
    void notReadyChangesNothingAndTriesAgainNextBoot() throws Exception {
        save(ACCOUNT, null, 1);
        ready = false;

        assertFalse(backfill().run(StationStore.loadAll()));

        assertNull(loaded(1).getOwnerCharacterId());
        assertFalse(Files.exists(backfillFolder()));
        assertEquals(0, heirLookups);

        ready = true;
        assertTrue(backfill().run(StationStore.loadAll()));
        assertEquals(HEIR.characterId().toString(), loaded(1).getOwnerCharacterId());
    }

    @Test
    void aSecondBootDoesNothing() throws Exception {
        save(ACCOUNT, null, 1);
        assertTrue(backfill().run(StationStore.loadAll()));
        String marker = Files.readString(backfillFolder().resolve("done.json"));
        save(ACCOUNT, null, 2);
        heirLookups = 0;

        assertTrue(backfill().run(StationStore.loadAll()));

        assertEquals(0, heirLookups);
        assertNull(loaded(2).getOwnerCharacterId());
        assertEquals(marker, Files.readString(backfillFolder().resolve("done.json")));
    }

    @Test
    void aCrashBeforeTheMarkerFinishesFromTheSamePlan() throws Exception {
        save(ACCOUNT, null, 1);
        save(ACCOUNT, null, 2);
        save(ACCOUNT, null, 3);
        // The first run wrote its plan, backed up and filled station 1, then the server died.
        Map<String, String> planned = new java.util.LinkedHashMap<>();
        planned.put("world_1_64_0", HEIR.characterId().toString());
        planned.put("world_2_64_0", HEIR.characterId().toString());
        planned.put("world_3_64_0", null);
        Files.createDirectories(backfillFolder().resolve("backup"));
        Files.writeString(backfillFolder().resolve("plan.json"), new com.google.gson.GsonBuilder().serializeNulls().create().toJson(new StationBackfill.Plan(planned)));
        Files.writeString(backfillFolder().resolve("backup/world_1_64_0.json"), "original of station 1");
        save(ACCOUNT, HEIR.characterId().toString(), 1);

        assertTrue(backfill().run(StationStore.loadAll()));

        assertEquals(0, heirLookups);
        assertEquals(HEIR.characterId().toString(), loaded(2).getOwnerCharacterId());
        assertNull(loaded(3).getOwnerCharacterId());
        assertEquals("original of station 1", Files.readString(backfillFolder().resolve("backup/world_1_64_0.json")));
        assertEquals(2, result().filled());
        assertEquals(List.of("world_3_64_0"), result().unresolved());
    }

    @Test
    void aFailedSaveLeavesTheStationUnassignedAndWritesNoMarker() throws Exception {
        save(ACCOUNT, null, 1);
        List<ResearchStation> stations = StationStore.loadAll();
        Path file = stationFile(1);
        Files.delete(file);
        Files.createDirectories(file.resolve("blocked"));

        assertFalse(backfill().run(stations));

        assertNull(stations.getFirst().getOwnerCharacterId());
        assertFalse(Files.exists(backfillFolder().resolve("done.json")));
        Files.delete(file.resolve("blocked"));
        Files.delete(file);
        assertTrue(backfill().run(stations));
        assertEquals(HEIR.characterId().toString(), stations.getFirst().getOwnerCharacterId());
    }

    @Test
    void aStationThatCannotBeBackedUpIsLeftAlone() throws Exception {
        save(ACCOUNT, null, 1);
        Files.createDirectories(backfillFolder());
        Files.writeString(backfillFolder().resolve("backup"), "not a folder");

        assertFalse(backfill().run(StationStore.loadAll()));

        assertNull(loaded(1).getOwnerCharacterId());
        verify(logger).severe(contains("Could not back up station world_1_64_0"));
    }

    @Test
    void anUnreadablePlanStopsTheBackfill() throws Exception {
        save(ACCOUNT, null, 1);
        Files.createDirectories(backfillFolder());
        Files.writeString(backfillFolder().resolve("plan.json"), "{ broken");
        assertFalse(backfill().run(StationStore.loadAll()));
        Files.writeString(backfillFolder().resolve("plan.json"), "{}");
        assertFalse(backfill().run(StationStore.loadAll()));
        assertNull(loaded(1).getOwnerCharacterId());
        verify(logger, times(2)).severe(startsWith("[Research] Station heir backfill failed"));
    }

    @Test
    void plannedStationsThatAreGoneOrClaimedAreNotCounted() throws Exception {
        save(ACCOUNT, "claimed-by-another", 1);
        Files.createDirectories(backfillFolder());
        Files.writeString(backfillFolder().resolve("plan.json"), new Gson().toJson(new StationBackfill.Plan(
                Map.of("world_1_64_0", HEIR.characterId().toString(), "world_9_64_0", HEIR.characterId().toString()))));

        assertTrue(backfill().run(StationStore.loadAll()));

        assertEquals("claimed-by-another", loaded(1).getOwnerCharacterId());
        assertEquals(0, result().filled());
    }

    @Test
    void theHeirApiIsProbedSoAnOlderTLibsJustWaits() throws Exception {
        assertTrue(StationBackfill.heirsAvailable(Characters.class));
        assertFalse(StationBackfill.heirsAvailable(String.class));
        save(ACCOUNT, null, 1);
        // Without an identity provider the heirs are never ready.
        assertFalse(StationBackfill.forPlugin(folder.toFile(), logger).run(StationStore.loadAll()));
        // Without the heir API it waits the same way.
        assertFalse(StationBackfill.forPlugin(folder.toFile(), logger, false).run(StationStore.loadAll()));
        assertNull(loaded(1).getOwnerCharacterId());
    }

    private StationBackfill backfill() {
        Function<UUID, Optional<CharacterRef>> lookup = account -> {
            heirLookups++;
            return Optional.ofNullable(heirs.get(account));
        };
        return new StationBackfill(backfillFolder(), logger, () -> ready, lookup);
    }

    private StationBackfill.Result result() throws Exception {
        return new Gson().fromJson(Files.readString(backfillFolder().resolve("done.json")), StationBackfill.Result.class);
    }

    private void save(UUID account, String character, int x) {
        ResearchStation station = new ResearchStation(new Location(world, x, 64, 0), account,
                new ActiveProject("input", "output", "minecraft:diamond", false));
        station.setOwnerCharacterId(character);
        assertTrue(StationStore.saveStation(station));
    }

    private ResearchStation loaded(int x) {
        return StationStore.loadAll().stream().filter(station -> station.getLocation().getBlockX() == x)
                .findFirst().orElseThrow();
    }

    private Path stationFile(int x) {
        return folder.resolve("data/stations/world_" + x + "_64_0.json");
    }

    private Path backfillFolder() {
        return folder.resolve(StationBackfill.FOLDER);
    }
}
