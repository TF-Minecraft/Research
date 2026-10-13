package net.tfminecraft.research.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Once per server: stations saved before characters were recorded go to their account's reviewed heir,
 * as 6a did for locks and vehicles. The plan ({@code plan.json}) is written before any station changes and
 * reused after a crash; each changed station's original file is kept in {@code backup/}; {@code done.json}
 * records the result, after which later boots do nothing. Accounts without a heir keep their stations
 * unassigned (the next character on the account claims them) and are listed for staff.
 */
public final class StationBackfill {
    static final String FOLDER = "data/backfill-character-v1";

    record Plan(Map<String, String> stations) {
    }

    record Result(int filled, List<String> unresolved, String completedAt) {
    }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Path folder;
    private final Logger logger;
    private final BooleanSupplier ready;
    private final Function<UUID, Optional<CharacterRef>> heirs;

    StationBackfill(Path folder, Logger logger, BooleanSupplier ready, Function<UUID, Optional<CharacterRef>> heirs) {
        this.folder = folder;
        this.logger = logger;
        this.ready = ready;
        this.heirs = heirs;
    }

    /** The backfill for this data folder; it waits forever on a TLibs without reviewed heirs. */
    public static StationBackfill forPlugin(File dataFolder, Logger logger) {
        return forPlugin(dataFolder, logger, heirsAvailable(Characters.class));
    }

    // The heir methods are only linked when the probe found them (TLibs 2.4.0 or later).
    static StationBackfill forPlugin(File dataFolder, Logger logger, boolean heirApi) {
        Path folder = dataFolder.toPath().resolve(FOLDER);
        if (!heirApi) {
            return new StationBackfill(folder, logger, () -> false, owner -> Optional.empty());
        }
        return new StationBackfill(folder, logger, Characters::legacyHeirsReady, Characters::legacyHeir);
    }

    static boolean heirsAvailable(Class<?> characters) {
        try {
            characters.getMethod("legacyHeirsReady");
            characters.getMethod("legacyHeir", UUID.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    /** Runs the backfill unless it already ran; false when it must try again at the next boot. */
    public boolean run(List<ResearchStation> stations) {
        Path marker = folder.resolve("done.json");
        if (Files.exists(marker)) {
            return true;
        }
        if (!ready.getAsBoolean()) {
            logger.warning("[Research] Reviewed heirs are not ready; stations without a character wait for the next boot.");
            return false;
        }
        try {
            Map<String, String> plan = plan(stations);
            int failed = 0;
            for (Map.Entry<String, String> entry : plan.entrySet()) {
                ResearchStation station = find(stations, entry.getKey());
                if (entry.getValue() == null || station == null || station.getOwnerCharacterId() != null) {
                    continue;
                }
                if (!fill(station, entry.getKey(), entry.getValue())) {
                    failed++;
                }
            }
            if (failed > 0) {
                logger.severe("[Research] " + failed + " station(s) could not be given to their heir; retrying next boot.");
                return false;
            }
            List<String> unresolved = new ArrayList<>();
            int filled = 0;
            for (Map.Entry<String, String> entry : plan.entrySet()) {
                ResearchStation station = find(stations, entry.getKey());
                if (entry.getValue() == null) {
                    unresolved.add(entry.getKey());
                } else if (station != null && entry.getValue().equalsIgnoreCase(station.getOwnerCharacterId())) {
                    filled++;
                }
            }
            if (!unresolved.isEmpty()) {
                logger.warning("[Research] Stations whose account has no reviewed heir stay unassigned: "
                        + String.join(", ", unresolved));
            }
            DurableFiles.write(marker, JSON.toJson(new Result(filled, unresolved, Instant.now().toString())));
            logger.info("[Research] Gave " + filled + " station(s) to their account's heir; " + unresolved.size()
                    + " without a heir.");
            return true;
        } catch (IOException | RuntimeException ex) {
            logger.severe("[Research] Station heir backfill failed; retrying next boot: " + ex);
            return false;
        }
    }

    // The plan from an interrupted run is reused, so a crash never changes who a station goes to.
    private Map<String, String> plan(List<ResearchStation> stations) throws IOException {
        Path file = folder.resolve("plan.json");
        if (Files.exists(file)) {
            try {
                Plan plan = JSON.fromJson(Files.readString(file), Plan.class);
                if (plan == null || plan.stations() == null) {
                    throw new IOException("Incomplete backfill plan " + file);
                }
                return plan.stations();
            } catch (JsonParseException ex) {
                throw new IOException("Unreadable backfill plan " + file, ex);
            }
        }
        Map<String, String> plan = new LinkedHashMap<>();
        for (ResearchStation station : stations) {
            if (station.getOwnerCharacterId() != null) {
                continue;
            }
            UUID account = station.getOwnerUuid();
            // An heir on another account would hand the station to someone else's character.
            plan.put(ResearchStation.locationKey(station.getLocation()), heirs.apply(account)
                    .filter(heir -> heir.ownerId().equals(account))
                    .map(heir -> heir.characterId().toString())
                    .orElse(null));
        }
        DurableFiles.write(file, JSON.toJson(new Plan(plan)));
        return plan;
    }

    private boolean fill(ResearchStation station, String key, String character) {
        try {
            Path original = StationStore.fileFor(station.getLocation()).toPath();
            Path backup = folder.resolve("backup").resolve(key + ".json");
            Files.createDirectories(backup.getParent());
            if (Files.exists(original) && !Files.exists(backup)) {
                Files.copy(original, backup);
            }
        } catch (IOException ex) {
            logger.severe("[Research] Could not back up station " + key + ": " + ex.getMessage());
            return false;
        }
        station.setOwnerCharacterId(character);
        if (StationStore.saveStation(station)) {
            return true;
        }
        station.setOwnerCharacterId(null);
        return false;
    }

    private static ResearchStation find(List<ResearchStation> stations, String key) {
        for (ResearchStation station : stations) {
            if (ResearchStation.locationKey(station.getLocation()).equals(key)) {
                return station;
            }
        }
        return null;
    }
}
