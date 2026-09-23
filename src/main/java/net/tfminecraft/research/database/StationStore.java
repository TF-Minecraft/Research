package net.tfminecraft.research.database;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.tfminecraft.research.Research;
import net.tfminecraft.research.loader.InputLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.research.util.AspectSlotOrder;

public final class StationStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private StationStore() {}

    public static List<ResearchStation> loadAll() {
        List<ResearchStation> stations = new ArrayList<>();
        File folder = folder();
        if (!folder.exists()) {
            folder.mkdirs();
            return stations;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return stations;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".json")) {
                continue;
            }
            ResearchStation station = loadFile(file);
            if (station != null) {
                stations.add(station);
            }
        }
        return stations;
    }

    public static void saveAll(List<ResearchStation> stations) {
        File folder = folder();
        folder.mkdirs();
        for (ResearchStation station : stations) {
            saveStation(station);
        }
    }

    public static void saveStation(ResearchStation station) {
        if (station == null || station.getLocation() == null || station.getProject() == null) {
            return;
        }
        File file = fileFor(station.getLocation());
        file.getParentFile().mkdirs();

        ActiveProject project = station.getProject();
        StationSaveData data = new StationSaveData();
        Location loc = station.getLocation();
        data.setWorld(loc.getWorld().getName());
        data.setX(loc.getBlockX());
        data.setY(loc.getBlockY());
        data.setZ(loc.getBlockZ());
        data.setOwnerUuid(station.getOwnerUuid().toString());
        data.setInputId(project.getInputId());
        data.setResolvedOutputId(project.getResolvedOutputId());
        data.setResolvedResultRef(project.getResolvedResultRef());
        data.setProductRevealed(project.isProductRevealed());
        data.setCompleted(project.isCompleted());
        data.setAspectPoints(new java.util.HashMap<>(project.getAspectPoints()));
        data.setAspectTestCounts(new java.util.HashMap<>(project.getAspectTestCounts()));
        data.setAspectStates(new java.util.HashMap<>(project.getAspectStates()));
        data.setTestedItems(new java.util.ArrayList<>(project.getTestedExperimentItems()));
        data.setAspectSlotOrder(new java.util.ArrayList<>(project.getAspectSlotOrder()));

        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException ex) {
            Research.plugin.getLogger().severe("[Research] Failed to save station " + file.getName() + ": "
                    + ex.getMessage());
        }
    }

    public static void deleteStation(Location location) {
        File file = fileFor(location);
        if (file.exists() && !file.delete()) {
            Research.plugin.getLogger().warning("[Research] Failed to delete station file " + file.getName());
        }
    }

    private static ResearchStation loadFile(File file) {
        try (Reader reader = new FileReader(file)) {
            StationSaveData data = GSON.fromJson(reader, StationSaveData.class);
            if (data == null || data.getWorld() == null) {
                Research.plugin.getLogger().warning("[Research] Invalid station file " + file.getName());
                return null;
            }
            if (data.getInputId() == null || data.getInputId().isBlank()
                    || data.getResolvedOutputId() == null || data.getResolvedOutputId().isBlank()
                    || data.getResolvedResultRef() == null || data.getResolvedResultRef().isBlank()) {
                Research.plugin.getLogger().warning("[Research] Station file " + file.getName()
                        + " uses legacy project format; skipping (no migration).");
                return null;
            }
            if (InputLoader.getById(data.getInputId()) == null) {
                Research.plugin.getLogger().warning("[Research] Station file " + file.getName()
                        + " references unknown input '" + data.getInputId() + "', skipping.");
                return null;
            }
            if (OutputLoader.getById(data.getResolvedOutputId()) == null) {
                Research.plugin.getLogger().warning("[Research] Station file " + file.getName()
                        + " references unknown output '" + data.getResolvedOutputId() + "', skipping.");
                return null;
            }
            if (data.isCompleted()) {
                if (!file.delete()) {
                    Research.plugin.getLogger().warning("[Research] Failed to delete completed station file "
                            + file.getName());
                }
                return null;
            }
            Location location = ResearchStation.locationFromKey(data.getWorld(), data.getX(), data.getY(), data.getZ());
            if (location == null) {
                Research.plugin.getLogger().warning("[Research] Station file " + file.getName()
                        + " references missing world '" + data.getWorld() + "', skipping.");
                return null;
            }
            UUID owner = UUID.fromString(data.getOwnerUuid());
            ActiveProject project = new ActiveProject(
                    data.getInputId(),
                    data.getResolvedOutputId(),
                    data.getResolvedResultRef(),
                    data.isProductRevealed());
            project.setCompleted(data.isCompleted());
            if (data.getAspectPoints() != null) {
                project.getAspectPoints().putAll(data.getAspectPoints());
            }
            if (data.getAspectTestCounts() != null) {
                project.getAspectTestCounts().putAll(data.getAspectTestCounts());
            }
            if (data.getAspectStates() != null) {
                project.getAspectStates().putAll(data.getAspectStates());
            }
            if (data.getTestedItems() != null) {
                project.getTestedExperimentItems().addAll(data.getTestedItems());
            }
            OutputDef outputDef = OutputLoader.getById(data.getResolvedOutputId());
            String context = "station " + file.getName();
            List<String> savedOrder = data.getAspectSlotOrder();
            if (savedOrder == null || savedOrder.isEmpty()) {
                project.setAspectSlotOrder(AspectSlotOrder.reconcileRowAssignments(outputDef, null, context));
            } else {
                project.setAspectSlotOrder(AspectSlotOrder.reconcileRowAssignments(outputDef, savedOrder, context));
            }
            return new ResearchStation(location, owner, project);
        } catch (IOException | com.google.gson.JsonSyntaxException | IllegalArgumentException ex) {
            Research.plugin.getLogger().warning("[Research] Failed to load station file " + file.getName() + ": "
                    + ex.getMessage());
            return null;
        }
    }

    private static File folder() {
        return new File(Research.plugin.getDataFolder(), "data/stations");
    }

    private static File fileFor(Location location) {
        return new File(folder(), ResearchStation.locationKey(location) + ".json");
    }
}
