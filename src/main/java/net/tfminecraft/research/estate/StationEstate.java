package net.tfminecraft.research.estate;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import net.tfminecraft.research.manager.ResearchManager;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.EstateParticipant;
import net.tfminecraft.tlibs.character.estate.Estates;
import net.tfminecraft.tlibs.character.estate.Holdings;
import net.tfminecraft.tlibs.character.estate.Outcome;

/**
 * The {@code research} estate participant: a dead character's research stations go to the estate's
 * property recipient. Loaded only after {@link EstateProbe} finds the TLibs 2.6.0 estate API.
 */
public final class StationEstate implements EstateParticipant {
    static final String ID = "research";
    static final long HOUR_TICKS = 20L * 60 * 60;

    interface Reporter {
        boolean report(UUID deathId, String participantId, long revision, Outcome outcome);
    }

    private final ResearchManager manager;
    private final StationTransfers transfers;
    private final Logger logger;
    Predicate<EstateDecision> guard = Estates::guardHolds;
    Supplier<List<EstateDecision>> decided = Estates::decided;
    Reporter reporter = Estates::report;
    // The last outcome reported per death, so the hourly pass reports only changes.
    private final Map<UUID, Map.Entry<Long, Outcome>> reported = new HashMap<>();

    StationEstate(ResearchManager manager, StationTransfers transfers, Logger logger) {
        this.manager = manager;
        this.transfers = transfers;
        this.logger = logger;
    }

    /** Registers the participant once stations are loaded; reconciles on the first tick, then hourly. */
    public static void start(Plugin plugin, ResearchManager manager) {
        StationEstate estate = new StationEstate(manager,
                new StationTransfers(manager, plugin.getDataFolder().toPath().resolve("data").resolve("estates")),
                plugin.getLogger());
        plugin.getServer().getServicesManager().register(EstateParticipant.class, estate, plugin, ServicePriority.Normal);
        plugin.getServer().getScheduler().runTaskTimer(plugin, estate::reconcile, 1L, HOUR_TICKS);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Outcome settle(EstateDecision decision) {
        if (!guard.test(decision)) {
            return Outcome.retry("The estate guard does not hold");
        }
        Optional<CharacterRef> recipient = decision.property().character();
        // Waiting and frozen property stays with the testator; there is nothing to move at this revision.
        if (recipient.isEmpty()) {
            return Outcome.done();
        }
        try {
            List<String> moved = transfers.move(decision.deathId(), decision.testator().characterId(), recipient.get());
            return Outcome.done(Map.of("stations", moved.size()), moved);
        } catch (IOException | RuntimeException ex) {
            logger.warning("Could not move the research stations of estate " + decision.deathId() + ": " + ex);
            return Outcome.retry("Could not save a research station: " + ex.getMessage());
        }
    }

    @Override
    public Holdings describe(UUID characterId) {
        List<String> labels = manager.stationsOwnedBy(characterId.toString()).stream().map(StationEstate::label)
                .sorted().toList();
        return labels.isEmpty() ? Holdings.NONE : new Holdings(Map.of("stations", labels.size()), labels);
    }

    /** Settles every decided estate whose guard holds and reports outcomes that changed. */
    void reconcile() {
        for (EstateDecision decision : decided.get()) {
            // One estate failing must not stop the others until the next hour.
            try {
                if (!guard.test(decision)) {
                    continue;
                }
                Outcome outcome = settle(decision);
                Map.Entry<Long, Outcome> last = Map.entry(decision.revision(), outcome);
                if (!last.equals(reported.get(decision.deathId()))
                        && reporter.report(decision.deathId(), ID, decision.revision(), outcome)) {
                    reported.put(decision.deathId(), last);
                }
            } catch (RuntimeException ex) {
                logger.warning("Could not reconcile the research stations of estate " + decision.deathId() + ": " + ex);
            }
        }
    }

    private static String label(ResearchStation station) {
        // Describe feeds the will screen, so a station without its world or project must not throw.
        Location location = station.getLocation();
        String place = location != null && location.isWorldLoaded()
                ? location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", "
                        + location.getBlockZ()
                : "an unloaded world";
        return "Research station at " + place
                + (station.getProject() == null ? "" : " (" + station.getProject().getInputId() + ")");
    }
}
