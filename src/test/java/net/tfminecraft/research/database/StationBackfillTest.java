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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    private static final String CLAIMED = UUID.randomUUID().toString();

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
    void unloadedStationsGetTheirHeirWithoutChangingRecipeOrExtensionFields() throws Exception {
        save(ACCOUNT, null, 1);
        save(ACCOUNT, null, 2);
        JsonObject unknownRecipe = JsonParser.parseString(Files.readString(stationFile(1))).getAsJsonObject();
        unknownRecipe.addProperty("input_id", "retired-input");
        unknownRecipe.add("extension", JsonParser.parseString("{\"nested\":[1,null,\"kept\"],\"flag\":true}"));
        Files.writeString(stationFile(1), unknownRecipe.toString());
        JsonObject missingWorld = JsonParser.parseString(Files.readString(stationFile(2))).getAsJsonObject();
        missingWorld.addProperty("world", "unloaded_world");
        Path missingWorldFile = folder.resolve("data/stations/unloaded_world_2_64_0.json");
        Files.delete(stationFile(2));
        Files.writeString(missingWorldFile, missingWorld.toString());
        String original = Files.readString(stationFile(1));
        assertTrue(StationStore.loadAll().isEmpty());

        assertTrue(backfill().run(StationStore.loadAll()));

        JsonObject updated = JsonParser.parseString(Files.readString(stationFile(1))).getAsJsonObject();
        assertEquals(HEIR.characterId().toString(), updated.remove("owner_character_id").getAsString());
        assertEquals(unknownRecipe, updated);
        JsonObject updatedWorld = JsonParser.parseString(Files.readString(missingWorldFile)).getAsJsonObject();
        assertEquals(HEIR.characterId().toString(), updatedWorld.remove("owner_character_id").getAsString());
        assertEquals(missingWorld, updatedWorld);
        assertEquals(original, Files.readString(backfillFolder().resolve("backup/world_1_64_0.json")));
        assertEquals(2, result().filled());
        assertTrue(StationStore.loadAll().isEmpty());
    }

    @Test
    void stationsWithoutACharacterGoToTheAccountsHeirWithABackup() throws Exception {
        save(ACCOUNT, null, 1);
        save(ACCOUNT, CLAIMED, 2);
        String original = Files.readString(stationFile(1));

        List<ResearchStation> stations = StationStore.loadAll();
        assertTrue(backfill().run(stations));

        assertEquals(HEIR.characterId().toString(), stations.stream()
                .filter(station -> station.getLocation().getBlockX() == 1).findFirst().orElseThrow().getOwnerCharacterId());
        assertEquals(HEIR.characterId().toString(), loaded(1).getOwnerCharacterId());
        assertEquals(CLAIMED, loaded(2).getOwnerCharacterId());
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

        List<ResearchStation> stations = StationStore.loadAll();
        assertTrue(backfill().run(stations));

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
        Map<String, StationBackfill.Destination> planned = new java.util.LinkedHashMap<>();
        planned.put("world_1_64_0", destination(HEIR.characterId().toString()));
        planned.put("world_2_64_0", destination(HEIR.characterId().toString()));
        planned.put("world_3_64_0", destination(null));
        Files.createDirectories(backfillFolder().resolve("backup"));
        writePlan(planned);
        String original = Files.readString(stationFile(1));
        Files.writeString(backfillFolder().resolve("backup/world_1_64_0.json"), original);
        List<ResearchStation> stations = StationStore.loadAll();
        save(ACCOUNT, HEIR.characterId().toString(), 1);
        heirs.put(ACCOUNT, new CharacterRef(ACCOUNT, UUID.randomUUID()));

        assertTrue(backfill().run(stations));

        assertEquals(0, heirLookups);
        assertEquals(HEIR.characterId().toString(), stations.stream()
                .filter(station -> station.getLocation().getBlockX() == 1).findFirst().orElseThrow().getOwnerCharacterId());
        assertEquals(HEIR.characterId().toString(), loaded(2).getOwnerCharacterId());
        assertNull(loaded(3).getOwnerCharacterId());
        assertEquals(original, Files.readString(backfillFolder().resolve("backup/world_1_64_0.json")));
        assertEquals(2, result().filled());
        assertEquals(List.of("world_3_64_0"), result().unresolved());
    }

    @Test
    void aFailedSaveLeavesTheStationUnassignedAndWritesNoMarker() throws Exception {
        save(ACCOUNT, null, 1);
        List<ResearchStation> stations = StationStore.loadAll();
        Path file = stationFile(1);
        String original = Files.readString(file);
        try (var writes = mockStatic(DurableFiles.class, CALLS_REAL_METHODS)) {
            writes.when(() -> DurableFiles.write(eq(file), anyString())).thenThrow(new java.io.IOException("write failed"));
            assertFalse(backfill().run(stations));
        }

        assertNull(stations.getFirst().getOwnerCharacterId());
        assertEquals(original, Files.readString(file));
        assertEquals(original, Files.readString(backfillFolder().resolve("backup/world_1_64_0.json")));
        assertFalse(Files.exists(backfillFolder().resolve("done.json")));
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
        verify(logger).severe(startsWith("[Research] Station heir backfill failed"));
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
        save(ACCOUNT, CLAIMED, 1);
        writePlan(Map.of("world_1_64_0", destination(HEIR.characterId().toString()),
                "world_9_64_0", destination(HEIR.characterId().toString())));

        assertTrue(backfill().run(StationStore.loadAll()));

        assertEquals(CLAIMED, loaded(1).getOwnerCharacterId());
        assertEquals(0, result().filled());
        assertEquals(List.of("world_1_64_0", "world_9_64_0"), result().skipped().stream().sorted().toList());
    }

    @Test
    void corruptOwnershipMetadataIsPreservedAndListedForStaff() throws Exception {
        save(ACCOUNT, null, 1);
        String valid = Files.readString(stationFile(1));
        Map<String, String> corrupt = new java.util.LinkedHashMap<>();
        corrupt.put("broken", "{ broken");
        corrupt.put("null", "null");
        corrupt.put("array", "[]");
        corrupt.put("no-owner", valid.replace("owner_uuid", "former_owner_uuid"));
        corrupt.put("invalid-owner", valid.replace(ACCOUNT.toString(), "not-a-uuid"));
        corrupt.put("short-owner", valid.replace(ACCOUNT.toString(), "1-1-1-1-1"));
        corrupt.put("wrong-location", valid.replace("\"x\": 1", "\"x\": 2"));
        corrupt.put("missing-coordinate", valid.replace("\"x\": 1,", ""));
        corrupt.put("invalid-coordinate", valid.replace("\"x\": 1", "\"x\": \"1\""));
        corrupt.put("fractional-coordinate", valid.replace("\"x\": 1", "\"x\": 1.5"));
        corrupt.put("invalid-world", valid.replace("\"world\": \"world\"", "\"world\": {}"));
        corrupt.put("blank-world", valid.replace("\"world\": \"world\"", "\"world\": \"\""));
        // These match their filename so the corrupt character field is the reason they are skipped.
        JsonObject badCharacter = JsonParser.parseString(valid).getAsJsonObject();
        badCharacter.addProperty("x", 2);
        badCharacter.addProperty("owner_character_id", "invalid-character");
        corrupt.put("world_2_64_0", badCharacter.toString());
        JsonObject wrongOwnerType = JsonParser.parseString(valid).getAsJsonObject();
        wrongOwnerType.addProperty("x", 3);
        wrongOwnerType.addProperty("owner_uuid", 123);
        corrupt.put("world_3_64_0", wrongOwnerType.toString());
        Map<String, String> matchingFiles = new java.util.LinkedHashMap<>();
        for (var entry : corrupt.entrySet()) {
            String key = entry.getKey().startsWith("world_") ? entry.getKey() : entry.getKey() + "_1_64_0";
            String contents = entry.getKey().startsWith("world_") ? entry.getValue() : entry.getValue()
                    .replace("\"world\": \"world\"", "\"world\": \"" + entry.getKey() + "\"");
            matchingFiles.put(key, contents);
        }
        corrupt = matchingFiles;
        for (var entry : corrupt.entrySet()) {
            Files.writeString(folder.resolve("data/stations/" + entry.getKey() + ".json"), entry.getValue());
        }
        Files.createDirectories(folder.resolve("data/stations/unreadable.json"));
        Files.writeString(folder.resolve("data/stations/ignored.txt"), "ignored");

        assertTrue(backfill().run(StationStore.loadAll()));

        assertEquals(HEIR.characterId().toString(), loaded(1).getOwnerCharacterId());
        assertEquals(1, result().filled());
        List<String> skipped = new java.util.ArrayList<>(corrupt.keySet());
        skipped.add("unreadable");
        assertEquals(skipped.stream().sorted().toList(), result().skipped().stream().sorted().toList());
        for (var entry : corrupt.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(folder.resolve("data/stations/" + entry.getKey() + ".json")));
        }
        verify(logger).warning(contains("staff review:"));
        assertEquals(List.of(), result().unresolved());
    }

    @Test
    void changedAccountsAndInMemoryClaimsAreNotOverwrittenOnReplay() throws Exception {
        save(NO_HEIR, null, 1);
        save(ACCOUNT, null, 2);
        save(ACCOUNT, null, 3);
        List<ResearchStation> stations = StationStore.loadAll();
        stations.stream().filter(station -> station.getLocation().getBlockX() == 2)
                .findFirst().orElseThrow().setOwnerCharacterId(CLAIMED);
        stations.stream().filter(station -> station.getLocation().getBlockX() == 3)
                .findFirst().orElseThrow().setOwnerUuid(NO_HEIR);
        writePlan(Map.of("world_1_64_0", destination(HEIR.characterId().toString()),
                "world_2_64_0", destination(HEIR.characterId().toString()),
                "world_3_64_0", destination(HEIR.characterId().toString())));

        assertTrue(backfill().run(stations));

        assertEquals(0, result().filled());
        assertEquals(List.of("world_1_64_0", "world_2_64_0", "world_3_64_0"), result().skipped().stream().sorted().toList());
        assertNull(loaded(1).getOwnerCharacterId());
        assertNull(loaded(2).getOwnerCharacterId());
        assertNull(loaded(3).getOwnerCharacterId());
        assertFalse(Files.exists(backfillFolder().resolve("backup")));
    }

    @Test
    void aMarkerFailureReplaysTheSameHeirAndKeepsTheOriginalBackup() throws Exception {
        save(ACCOUNT, null, 1);
        JsonObject data = JsonParser.parseString(Files.readString(stationFile(1))).getAsJsonObject();
        data.addProperty("resolved_output_id", "unknown-output");
        data.add("extension", JsonParser.parseString("[1,2,3]"));
        data.add("owner_character_id", com.google.gson.JsonNull.INSTANCE);
        Files.writeString(stationFile(1), data.toString());
        String original = Files.readString(stationFile(1));
        Path marker = backfillFolder().resolve("done.json");
        try (var writes = mockStatic(DurableFiles.class, CALLS_REAL_METHODS)) {
            writes.when(() -> DurableFiles.write(eq(marker), anyString())).thenThrow(new java.io.IOException("marker failed"));
            assertFalse(backfill().run(StationStore.loadAll()));
        }
        assertFalse(Files.exists(marker));
        String assigned = Files.readString(stationFile(1));
        assertEquals(HEIR.characterId().toString(), JsonParser.parseString(assigned).getAsJsonObject()
                .get("owner_character_id").getAsString());
        heirs.put(ACCOUNT, new CharacterRef(ACCOUNT, UUID.randomUUID()));
        heirLookups = 0;

        assertTrue(backfill().run(StationStore.loadAll()));

        assertEquals(0, heirLookups);
        assertEquals(assigned, Files.readString(stationFile(1)));
        assertEquals(original, Files.readString(backfillFolder().resolve("backup/world_1_64_0.json")));
        assertEquals(1, result().filled());
    }

    @Test
    void anInterruptedPlanWhoseStationBecomesUnreadableWaitsForRepair() throws Exception {
        save(ACCOUNT, null, 1);
        String original = Files.readString(stationFile(1));
        writePlan(Map.of("world_1_64_0", destination(HEIR.characterId().toString())));
        Files.writeString(stationFile(1), "{ broken");

        assertFalse(backfill().run(List.of()));

        assertFalse(Files.exists(backfillFolder().resolve("done.json")));
        verify(logger).severe(contains("Could not read planned station world_1_64_0"));
        Files.writeString(stationFile(1), original);
        assertTrue(backfill().run(StationStore.loadAll()));
        assertEquals(0, heirLookups);
        assertEquals(1, result().filled());
    }

    @Test
    void incompleteUnsafeOrLegacyPlansAreRefusedWithoutReplanning() throws Exception {
        save(ACCOUNT, null, 1);
        Files.createDirectories(backfillFolder());
        List<String> invalidPlans = List.of("null", "{\"stations\":{}}", "{\"stations\":{\"world_1_64_0\":null},\"skipped\":[]}",
                "{\"stations\":{\"world_1_64_0\":{\"characterId\":\"" + HEIR.characterId() + "\"}},\"skipped\":[]}",
                "{\"stations\":{\"world_1_64_0\":\"" + HEIR.characterId() + "\"}}",
                "{\"stations\":{\"../outside\":{\"ownerUuid\":\"" + ACCOUNT + "\"}},\"skipped\":[]}");
        for (String plan : invalidPlans) {
            Files.writeString(backfillFolder().resolve("plan.json"), plan);
            assertFalse(backfill().run(StationStore.loadAll()));
            assertEquals(plan, Files.readString(backfillFolder().resolve("plan.json")));
        }
        assertEquals(0, heirLookups);
        assertNull(loaded(1).getOwnerCharacterId());
        assertFalse(Files.exists(backfillFolder().resolve("done.json")));
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

    private StationBackfill.Destination destination(String character) {
        return new StationBackfill.Destination(ACCOUNT, character);
    }

    private void writePlan(Map<String, StationBackfill.Destination> destinations) throws Exception {
        Files.createDirectories(backfillFolder());
        Files.writeString(backfillFolder().resolve("plan.json"), new com.google.gson.GsonBuilder().serializeNulls()
                .create().toJson(new StationBackfill.Plan(destinations, List.of())));
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
