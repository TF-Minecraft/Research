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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Once per server: stations saved before characters were recorded go to their account's reviewed heir,
 * as 6a did for locks and vehicles. The plan ({@code plan.json}) is written before any station changes and
 * reused after a crash; each changed station's original file is kept in {@code backup/}; {@code done.json}
 * records the result, after which later boots do nothing. Accounts without a heir keep their stations
 * unassigned and unusable, and are listed for staff.
 */
public final class StationBackfill {
    static final String FOLDER = "data/backfill-character-v1";

    record Destination(UUID ownerUuid, String characterId) {
    }

    record Plan(Map<String, Destination> stations, List<String> skipped) {
    }

    record Result(int filled, List<String> unresolved, List<String> skipped, String completedAt) {
    }

    private record Metadata(String original, JsonObject data, UUID ownerUuid, String characterId) {
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
            Plan plan = plan();
            List<String> unresolved = new ArrayList<>();
            List<String> skipped = new ArrayList<>(plan.skipped());
            int filled = 0;
            for (Map.Entry<String, Destination> entry : plan.stations().entrySet()) {
                String key = entry.getKey();
                Destination destination = entry.getValue();
                Path file = stationFile(key);
                if (!Files.exists(file)) {
                    skipped.add(key);
                    continue;
                }
                Metadata metadata;
                try {
                    metadata = read(file, key);
                } catch (IOException | RuntimeException ex) {
                    throw new IOException("Could not read planned station " + key, ex);
                }
                ResearchStation station = find(stations, key);
                // A replacement or a claim since the plan was written must never be overwritten.
                if (!metadata.ownerUuid().equals(destination.ownerUuid())
                        || (station != null && (!station.getOwnerUuid().equals(metadata.ownerUuid())
                        || (station.getOwnerCharacterId() != null
                        && !station.getOwnerCharacterId().equalsIgnoreCase(metadata.characterId()))))) {
                    skipped.add(key);
                    continue;
                }
                if (metadata.characterId() != null) {
                    if (!metadata.characterId().equalsIgnoreCase(destination.characterId())) {
                        skipped.add(key);
                        continue;
                    }
                } else if (destination.characterId() == null) {
                    unresolved.add(key);
                    continue;
                } else {
                    fill(file, metadata, key, destination.characterId());
                }
                if (station != null) {
                    station.setOwnerCharacterId(destination.characterId());
                }
                filled++;
            }
            if (!unresolved.isEmpty()) {
                logger.warning("[Research] Stations whose account has no reviewed heir stay unassigned: "
                        + String.join(", ", unresolved));
            }
            if (!skipped.isEmpty()) {
                logger.warning("[Research] Station heir backfill skipped invalid, missing or changed stations; staff review: "
                        + String.join(", ", skipped));
            }
            DurableFiles.write(marker, JSON.toJson(new Result(filled, unresolved, skipped, Instant.now().toString())));
            logger.info("[Research] Gave " + filled + " station(s) to their account's heir; " + unresolved.size()
                    + " without a heir; " + skipped.size() + " skipped for staff review.");
            return true;
        } catch (IOException | RuntimeException ex) {
            logger.severe("[Research] Station heir backfill failed; retrying next boot: " + ex);
            return false;
        }
    }

    // The plan from an interrupted run is reused, so a crash never changes who a station goes to.
    private Plan plan() throws IOException {
        Path file = folder.resolve("plan.json");
        if (Files.exists(file)) {
            try {
                Plan plan = JSON.fromJson(Files.readString(file), Plan.class);
                if (plan == null || plan.stations() == null || plan.skipped() == null) {
                    throw new IOException("Incomplete backfill plan " + file);
                }
                for (Map.Entry<String, Destination> entry : plan.stations().entrySet()) {
                    stationFile(entry.getKey());
                    if (entry.getValue() == null || entry.getValue().ownerUuid() == null) {
                        throw new IOException("Missing planned station account " + entry.getKey());
                    }
                    if (entry.getValue().characterId() != null) {
                        uuid(entry.getValue().characterId());
                    }
                }
                return plan;
            } catch (JsonParseException ex) {
                throw new IOException("Unreadable backfill plan " + file, ex);
            }
        }
        Map<String, Destination> stations = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        Path stationFolder = folder.getParent().resolve("stations");
        Files.createDirectories(stationFolder);
        try (var files = Files.list(stationFolder)) {
            for (Path stationFile : files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String name = stationFile.getFileName().toString();
                String key = name.substring(0, name.length() - 5);
                Metadata metadata;
                try {
                    metadata = read(stationFile, key);
                } catch (IOException | RuntimeException ex) {
                    skipped.add(key);
                    logger.warning("[Research] Invalid ownership metadata in station " + name + ": " + ex.getMessage());
                    continue;
                }
                if (metadata.characterId() == null) {
                    UUID account = metadata.ownerUuid();
                    // An heir on another account would hand the station to someone else's character.
                    stations.put(key, new Destination(account, heirs.apply(account)
                            .filter(heir -> heir.ownerId().equals(account))
                            .map(heir -> heir.characterId().toString()).orElse(null)));
                }
            }
        }
        Plan plan = new Plan(stations, skipped);
        DurableFiles.write(file, JSON.toJson(plan));
        return plan;
    }

    private void fill(Path file, Metadata metadata, String key, String character) throws IOException {
        Path backup = folder.resolve("backup").resolve(key + ".json");
        if (!Files.exists(backup)) {
            DurableFiles.write(backup, metadata.original());
        }
        metadata.data().addProperty("owner_character_id", character);
        DurableFiles.write(file, JSON.toJson(metadata.data()));
    }

    private Path stationFile(String key) throws IOException {
        if (key.isBlank() || !Path.of(key).getFileName().toString().equals(key)) {
            throw new IOException("Invalid planned station filename");
        }
        return folder.getParent().resolve("stations").resolve(key + ".json");
    }

    // Ownership does not depend on a recipe, an output, or a world being loadable today.
    private static Metadata read(Path file, String key) throws IOException {
        String original = Files.readString(file);
        JsonObject data = JSON.fromJson(original, JsonObject.class);
        String world = string(data, "world");
        String savedKey = world + "_" + coordinate(data, "x") + "_" + coordinate(data, "y") + "_" + coordinate(data, "z");
        if (!savedKey.equals(key)) {
            throw new IOException("Station location does not match its filename");
        }
        UUID owner = uuid(string(data, "owner_uuid"));
        JsonElement character = data.get("owner_character_id");
        String characterId = character == null || character.isJsonNull() ? null : string(data, "owner_character_id");
        if (characterId != null) {
            uuid(characterId);
        }
        return new Metadata(original, data, owner, characterId);
    }

    private static String string(JsonObject data, String field) throws IOException {
        JsonElement value = data == null ? null : data.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IOException("Missing or invalid " + field);
        }
        return value.getAsString();
    }

    private static int coordinate(JsonObject data, String field) throws IOException {
        JsonElement value = data.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Missing or invalid " + field);
        }
        return value.getAsBigDecimal().intValueExact();
    }

    private static UUID uuid(String value) throws IOException {
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equalsIgnoreCase(value)) {
            throw new IOException("Invalid ownership UUID");
        }
        return uuid;
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
