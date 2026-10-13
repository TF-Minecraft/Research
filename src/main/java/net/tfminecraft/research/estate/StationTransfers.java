package net.tfminecraft.research.estate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.tfminecraft.research.database.DurableFiles;
import net.tfminecraft.research.manager.ResearchManager;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Gives a dead character's research stations to the estate's recipient. Each death keeps a journal in
 * {@code data/estates/<deathId>.json}, written before the first station changes, so a crash between
 * stations is finished at the next settle and every moved station is still reported.
 */
final class StationTransfers {
    static final String PLANNED = "planned";
    static final String MOVED = "moved";
    // The station was scrapped, finished or given to someone else before this move reached it.
    static final String SUPERSEDED = "superseded";

    record Entry(String account, String character, String state) {
        CharacterRef recipient() {
            return new CharacterRef(UUID.fromString(account), UUID.fromString(character));
        }
    }

    record Journal(String deathId, String testator, Map<String, Entry> stations) {}

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final ResearchManager manager;
    private final Path directory;

    StationTransfers(ResearchManager manager, Path directory) {
        this.manager = manager;
        this.directory = directory;
    }

    /** Moves the testator's stations and any planned before a crash; returns every station key moved. */
    List<String> move(UUID deathId, UUID testator, CharacterRef to) throws IOException {
        Journal journal = read(deathId, testator);
        Map<String, Entry> stations = journal.stations();
        List<ResearchStation> owned = manager.stationsOwnedBy(testator.toString());
        Entry planned = new Entry(to.ownerId().toString(), to.characterId().toString(), PLANNED);
        for (ResearchStation station : owned) {
            stations.put(ResearchStation.locationKey(station.getLocation()), planned);
        }
        if (!owned.isEmpty()) {
            write(deathId, journal);
        }
        boolean changed = false;
        try {
            for (Map.Entry<String, Entry> entry : stations.entrySet()) {
                Entry plan = entry.getValue();
                if (!PLANNED.equals(plan.state())) {
                    continue;
                }
                ResearchStation station = manager.getStationByKey(entry.getKey());
                String owner = station == null ? null : station.getOwnerCharacterId();
                String state = SUPERSEDED;
                if (testator.toString().equalsIgnoreCase(owner)) {
                    if (!manager.transferStation(station, plan.recipient())) {
                        throw new IOException("Could not save research station " + entry.getKey());
                    }
                    state = MOVED;
                } else if (plan.character().equalsIgnoreCase(owner)) {
                    state = MOVED; // saved before a crash cut the journal update short
                }
                entry.setValue(new Entry(plan.account(), plan.character(), state));
                changed = true;
            }
        } finally {
            if (changed) {
                write(deathId, journal);
            }
        }
        return stations.entrySet().stream().filter(entry -> MOVED.equals(entry.getValue().state()))
                .map(Map.Entry::getKey).toList();
    }

    private Journal read(UUID deathId, UUID testator) throws IOException {
        Path file = file(deathId);
        if (!Files.exists(file)) {
            return new Journal(deathId.toString(), testator.toString(), new LinkedHashMap<>());
        }
        try {
            Journal journal = JSON.fromJson(Files.readString(file), Journal.class);
            if (journal == null || journal.stations() == null) {
                throw new IOException("Incomplete estate journal " + file);
            }
            // A journal copied from another death must not add its stations to this estate.
            if (!deathId.toString().equals(journal.deathId()) || !testator.toString().equals(journal.testator())) {
                throw new IOException("Estate journal " + file + " belongs to another death");
            }
            return new Journal(journal.deathId(), journal.testator(), new LinkedHashMap<>(journal.stations()));
        } catch (JsonParseException ex) {
            throw new IOException("Unreadable estate journal " + file, ex);
        }
    }

    private void write(UUID deathId, Journal journal) throws IOException {
        DurableFiles.write(file(deathId), JSON.toJson(journal));
    }

    private Path file(UUID deathId) {
        return directory.resolve(deathId + ".json");
    }
}
