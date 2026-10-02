package net.tfminecraft.research.database;

import com.google.gson.Gson;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.ResearchTestState;
import net.tfminecraft.research.loader.InputLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.manager.StationMenuHolder;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResearchStation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class ResearchPersistenceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000021");
    @TempDir Path folder;
    private ResearchTestState globals;
    private World world;
    private Logger logger;

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
        YamlConfiguration config = new YamlConfiguration();
        config.set("aspects.fire.required_points", 10);
        OutputLoader.get().put("output", new OutputDef("output", config));
    }

    @AfterEach
    void tearDown() throws Exception {
        globals.close();
        MockBukkit.unmock();
    }

    private Path stationsFolder() { return folder.resolve("data/stations"); }

    private Path write(String name, String json) throws Exception {
        Files.createDirectories(stationsFolder());
        return Files.writeString(stationsFolder().resolve(name), json);
    }

    private StationSaveData data() {
        StationSaveData data = new StationSaveData();
        data.setWorld("world");
        data.setX(1);
        data.setY(64);
        data.setZ(2);
        data.setOwnerUuid(OWNER.toString());
        data.setInputId("input");
        data.setResolvedOutputId("output");
        data.setResolvedResultRef("minecraft:diamond");
        return data;
    }

    private ResearchStation station() {
        return new ResearchStation(new Location(world, 1, 64, 2), OWNER,
                new ActiveProject("input", "output", "minecraft:diamond", true));
    }

    @Test
    void missingOwnerDoesNotAbortLoadingOtherStations() throws Exception {
        StationSaveData broken = data();
        broken.setOwnerUuid(null);
        write("bad.json", new Gson().toJson(broken));
        write("good.json", new Gson().toJson(data()));
        List<ResearchStation> loaded = assertDoesNotThrow(StationStore::loadAll);
        assertEquals(1, loaded.size());
        assertEquals(OWNER, loaded.getFirst().getOwnerUuid());
        verify(logger).warning(contains("bad.json"));
    }

    @Test
    void roundTripPreservesOwnerProjectProgressAndSavedRows() throws Exception {
        ResearchStation original = station();
        ActiveProject project = original.getProject();
        project.getAspectPoints().put("fire", 7);
        project.getAspectTestCounts().put("fire", 2);
        project.getAspectStates().put("fire", "REVEALED");
        project.getTestedExperimentItems().add("minecraft:coal");
        project.setAspectSlotOrder(List.of("fire", "", "", "", "", ""));
        StationStore.saveAll(List.of(original));
        List<ResearchStation> loaded = StationStore.loadAll();
        assertEquals(1, loaded.size());
        ResearchStation actual = loaded.getFirst();
        assertEquals(original.getLocation(), actual.getLocation());
        assertEquals(OWNER, actual.getOwnerUuid());
        assertEquals("input", actual.getProject().getInputId());
        assertEquals("output", actual.getProject().getResolvedOutputId());
        assertEquals("minecraft:diamond", actual.getProject().getResolvedResultRef());
        assertTrue(actual.getProject().isProductRevealed());
        assertFalse(actual.getProject().isCompleted());
        assertEquals(Map.of("fire", 7), actual.getProject().getAspectPoints());
        assertEquals(Map.of("fire", 2), actual.getProject().getAspectTestCounts());
        assertEquals(Map.of("fire", "REVEALED"), actual.getProject().getAspectStates());
        assertTrue(actual.getProject().getTestedExperimentItems().contains("minecraft:coal"));
        assertEquals(project.getAspectSlotOrder(), actual.getProject().getAspectSlotOrder());
        StationStore.deleteStation(original.getLocation());
        assertFalse(Files.exists(stationsFolder().resolve("world_1_64_2.json")));
        StationStore.deleteStation(original.getLocation());
    }

    @Test
    void absentAndNullLegacyProgressGetsEmptyMapsAndACompleteRowAssignment() throws Exception {
        StationSaveData data = data();
        String json = new Gson().toJson(data)
                .replace("\"aspect_points\":{}", "\"aspect_points\":null")
                .replace("\"aspect_test_counts\":{}", "\"aspect_test_counts\":null")
                .replace("\"aspect_states\":{}", "\"aspect_states\":null")
                .replace("\"tested_items\":[]", "\"tested_items\":null")
                .replace("\"aspect_slot_order\":[]", "\"aspect_slot_order\":null");
        write("legacy-progress.json", json);
        ActiveProject project = StationStore.loadAll().getFirst().getProject();
        assertTrue(project.getAspectPoints().isEmpty());
        assertTrue(project.getAspectTestCounts().isEmpty());
        assertTrue(project.getAspectStates().isEmpty());
        assertTrue(project.getTestedExperimentItems().isEmpty());
        assertEquals(6, project.getAspectSlotOrder().size());
        assertEquals(1, project.getAspectSlotOrder().stream().filter("fire"::equals).count());
    }

    @Test
    void legacyInvalidMissingWorldAndUnknownDefinitionsAreSkipped() throws Exception {
        write("null.json", "null");
        write("no-world.json", "{}");
        write("syntax.json", "{broken");
        StationSaveData legacy = data();
        legacy.setInputId(" ");
        write("legacy.json", new Gson().toJson(legacy));
        StationSaveData unknownInput = data();
        unknownInput.setInputId("missing");
        write("unknown-input.json", new Gson().toJson(unknownInput));
        StationSaveData unknownOutput = data();
        unknownOutput.setResolvedOutputId("missing");
        write("unknown-output.json", new Gson().toJson(unknownOutput));
        StationSaveData noWorld = data();
        noWorld.setWorld("unloaded");
        write("missing-world.json", new Gson().toJson(noWorld));
        StationSaveData badOwner = data();
        badOwner.setOwnerUuid("invalid-uuid");
        write("invalid-owner.json", new Gson().toJson(badOwner));
        write("ignored.txt", "not a station");
        Files.createDirectory(stationsFolder().resolve("directory.json"));
        assertTrue(StationStore.loadAll().isEmpty());
        verify(logger).warning(contains("legacy project format"));
        verify(logger).warning(contains("unknown input"));
        verify(logger).warning(contains("unknown output"));
        verify(logger).warning(contains("missing world"));
        verify(logger).warning(contains("Failed to load station file syntax.json"));
        verify(logger).warning(contains("Failed to load station file invalid-owner.json"));
    }

    @Test
    void completedProjectsAreDeletedAndFailedDeletionIsLogged() throws Exception {
        StationSaveData data = data();
        data.setCompleted(true);
        Path completed = write("completed.json", new Gson().toJson(data));
        assertTrue(StationStore.loadAll().isEmpty());
        assertFalse(Files.exists(completed));
        Path undeletable = write("undeletable.json", new Gson().toJson(data));
        File file = spy(undeletable.toFile());
        doReturn(false).when(file).delete();
        Method load = StationStore.class.getDeclaredMethod("loadFile", File.class);
        load.setAccessible(true);
        assertNull(load.invoke(null, file));
        verify(logger).warning(contains("Failed to delete completed station file undeletable.json"));
    }

    @Test
    void missingOrBlockedStationFolderDoesNotThrow() throws Exception {
        assertTrue(StationStore.loadAll().isEmpty());
        assertTrue(Files.isDirectory(stationsFolder()));
        Files.delete(stationsFolder());
        Files.writeString(stationsFolder(), "blocked");
        assertTrue(StationStore.loadAll().isEmpty());
    }

    @Test
    void invalidSaveInputsAreIgnoredAndFilesystemFailuresAreLogged() throws Exception {
        StationStore.saveStation(null);
        StationStore.saveStation(new ResearchStation(null, OWNER, station().getProject()));
        StationStore.saveStation(new ResearchStation(new Location(world, 1, 64, 2), OWNER, null));
        assertFalse(Files.exists(stationsFolder()));
        Path file = stationsFolder().resolve("world_1_64_2.json");
        Files.createDirectories(file);
        Files.writeString(file.resolve("child"), "prevents deletion");
        assertDoesNotThrow(() -> StationStore.saveStation(station()));
        verify(logger).severe(contains("Failed to save station"));
        StationStore.deleteStation(station().getLocation());
        verify(logger).warning(contains("Failed to delete station file"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void saveDataNormalizesNullCollectionsAndPreservesLegacyOutputAlias() {
        StationSaveData data = data();
        assertEquals("world", data.getWorld());
        assertEquals(1, data.getX());
        assertEquals(64, data.getY());
        assertEquals(2, data.getZ());
        assertEquals(OWNER.toString(), data.getOwnerUuid());
        assertEquals("input", data.getInputId());
        assertEquals("minecraft:diamond", data.getResolvedResultRef());
        data.setProjectId("legacy-output");
        assertEquals("legacy-output", data.getProjectId());
        assertEquals("legacy-output", data.getResolvedOutputId());
        data.setProductRevealed(true);
        data.setCompleted(true);
        assertTrue(data.isProductRevealed());
        assertTrue(data.isCompleted());
        data.setAspectPoints(null);
        data.setAspectTestCounts(null);
        data.setAspectStates(null);
        data.setTestedItems(null);
        data.setAspectSlotOrder(null);
        assertTrue(data.getAspectPoints().isEmpty());
        assertTrue(data.getAspectTestCounts().isEmpty());
        assertTrue(data.getAspectStates().isEmpty());
        assertTrue(data.getTestedItems().isEmpty());
        assertTrue(data.getAspectSlotOrder().isEmpty());
    }

    @Test
    void stationIdentityUsesWorldAndBlockCoordinatesAndHolderKeepsItsStation() throws Exception {
        ResearchStation station = station();
        assertTrue(station.isAt(new Location(world, 1.9, 64.9, 2.9)));
        assertFalse(station.isAt(null));
        assertFalse(station.isAt(new Location(null, 1, 64, 2)));
        assertFalse(new ResearchStation(null, OWNER, null).isAt(station.getLocation()));
        assertFalse(new ResearchStation(new Location(null, 1, 64, 2), OWNER, null).isAt(station.getLocation()));
        assertFalse(station.isAt(new Location(world, 2, 64, 2)));
        assertFalse(station.isAt(new Location(world, 1, 65, 2)));
        assertFalse(station.isAt(new Location(world, 1, 64, 3)));
        World other = mock(World.class);
        when(other.getName()).thenReturn("a world/path");
        assertFalse(station.isAt(new Location(other, 1, 64, 2)));
        assertEquals("a_world_path_1_64_2", ResearchStation.locationKey(new Location(other, 1, 64, 2)));
        assertEquals("unknown", ResearchStation.locationKey(null));
        assertEquals("unknown", ResearchStation.locationKey(new Location(null, 1, 64, 2)));
        assertNull(ResearchStation.locationFromKey("unloaded", 1, 64, 2));
        assertEquals(station.getLocation(), ResearchStation.locationFromKey("world", 1, 64, 2));
        UUID replacement = UUID.fromString("00000000-0000-0000-0000-000000000022");
        station.setOwnerUuid(replacement);
        assertEquals(replacement, station.getOwnerUuid());
        ActiveProject project = new ActiveProject("other", "output", "minecraft:coal", false);
        station.setProject(project);
        assertSame(project, station.getProject());
        StationMenuHolder holder = new StationMenuHolder(station.getLocation());
        assertEquals(station.getLocation(), holder.getStationLocation());
        Inventory inventory = mock(Inventory.class);
        Method setter = StationMenuHolder.class.getDeclaredMethod("setInventory", Inventory.class);
        setter.setAccessible(true);
        setter.invoke(holder, inventory);
        assertSame(inventory, holder.getInventory());
    }
}
