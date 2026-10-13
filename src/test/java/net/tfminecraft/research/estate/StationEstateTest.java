package net.tfminecraft.research.estate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.google.gson.Gson;

import net.tfminecraft.research.Research;
import net.tfminecraft.research.ResearchTestState;
import net.tfminecraft.research.database.StationStore;
import net.tfminecraft.research.loader.InputLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.manager.PlayerManager;
import net.tfminecraft.research.manager.ResearchManager;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.EstateParticipant;
import net.tfminecraft.tlibs.character.estate.Holdings;
import net.tfminecraft.tlibs.character.estate.Outcome;
import net.tfminecraft.tlibs.character.estate.Recipient;

class StationEstateTest {
    private static final CharacterRef TESTATOR = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private static final CharacterRef HEIR = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private static final CharacterRef ALT = new CharacterRef(TESTATOR.ownerId(), UUID.randomUUID());
    private static final CharacterRef OTHER = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private static final UUID DEATH = UUID.randomUUID();

    @TempDir Path folder;
    private ResearchTestState globals;
    private World world;
    private ResearchManager manager;
    private StationEstate estate;
    private boolean guard = true;

    @BeforeEach
    void setUp() throws Exception {
        world = MockBukkit.mock().addSimpleWorld("world");
        globals = new ResearchTestState();
        Research.plugin = mock(Research.class);
        when(Research.plugin.getDataFolder()).thenReturn(folder.toFile());
        when(Research.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        InputLoader.get().clear();
        OutputLoader.get().clear();
        InputLoader.get().put("input", new InputDef("input", new YamlConfiguration()));
        YamlConfiguration output = new YamlConfiguration();
        output.set("aspects.fire.required_points", 10);
        OutputLoader.get().put("output", new OutputDef("output", output));
        manager = new ResearchManager(mock(PlayerManager.class));
        estate = new StationEstate(manager, new StationTransfers(manager, journals()), Logger.getAnonymousLogger());
        estate.guard = decision -> guard;
    }

    @AfterEach
    void tearDown() throws Exception {
        globals.close();
        MockBukkit.unmock();
    }

    @Test
    void decidedEstateGivesTheTestatorsStationsToTheRecipientOnDisk() throws Exception {
        save(TESTATOR, 1);
        save(TESTATOR, 2);
        save(OTHER, 3);
        manager.start();

        Outcome outcome = estate.settle(decision(Recipient.character(HEIR), 0));

        assertEquals(Outcome.done(Map.of("stations", 2), List.of("world_1_64_0", "world_2_64_0")),
                sortedIds(outcome));
        manager.start(); // reload from disk
        assertOwner("world_1_64_0", HEIR);
        assertOwner("world_2_64_0", HEIR);
        assertOwner("world_3_64_0", OTHER);
        assertEquals("input", manager.getStationByKey("world_1_64_0").getProject().getInputId());
    }

    @Test
    void theManagerListsLoadedStationsReadOnly() {
        save(TESTATOR, 1);
        save(OTHER, 2);
        manager.start();

        assertEquals(2, manager.stations().size());
        assertThrows(UnsupportedOperationException.class, () -> manager.stations().clear());
    }

    @Test
    void sameAccountRecipientKeepsTheAccountAndTakesTheCharacter() throws Exception {
        save(TESTATOR, 1);
        manager.start();

        estate.settle(decision(Recipient.character(ALT), 0));

        manager.start();
        assertOwner("world_1_64_0", ALT);
        assertEquals(TESTATOR.ownerId(), manager.getStationByKey("world_1_64_0").getOwnerUuid());
    }

    @Test
    void settlingAgainMovesNothingTwiceAndStillReportsEveryStation() throws Exception {
        save(TESTATOR, 1);
        manager.start();
        Outcome first = estate.settle(decision(Recipient.character(HEIR), 0));
        Path file = folder.resolve("data/stations/world_1_64_0.json");
        String saved = Files.readString(file);

        Outcome again = estate.settle(decision(Recipient.character(HEIR), 1));

        assertEquals(first, again);
        assertEquals(saved, Files.readString(file));
    }

    @Test
    void crashAfterThePlanFinishesTheMoveAndCountsAWriteThatAlreadyLanded() throws Exception {
        save(TESTATOR, 1);
        save(HEIR, 2);
        save(OTHER, 3);
        manager.start();
        journal(Map.of("world_1_64_0", planned(), "world_2_64_0", planned(), "world_3_64_0", planned(),
                "world_9_64_0", planned()));
        ResearchManager spy = spy(manager);
        StationEstate replay = new StationEstate(spy, new StationTransfers(spy, journals()), Logger.getAnonymousLogger());
        replay.guard = decision -> true;

        Outcome outcome = replay.settle(decision(Recipient.character(HEIR), 0));

        assertEquals(Outcome.done(Map.of("stations", 2), List.of("world_1_64_0", "world_2_64_0")), sortedIds(outcome));
        verify(spy, times(1)).transferStation(any(), eq(HEIR));
        assertOwner("world_3_64_0", OTHER);
        String journal = Files.readString(journals().resolve(DEATH + ".json"));
        assertFalse(journal.contains(StationTransfers.PLANNED));
        assertTrue(journal.contains(StationTransfers.SUPERSEDED));
    }

    @Test
    void waitingAndFrozenPropertyMovesNothing() throws Exception {
        save(TESTATOR, 1);
        manager.start();
        assertEquals(Outcome.done(), estate.settle(decision(Recipient.WAITING, 0)));
        assertEquals(Outcome.done(), estate.settle(decision(Recipient.FROZEN, 1)));
        assertOwner("world_1_64_0", TESTATOR);
        assertFalse(Files.exists(journals()));
    }

    @Test
    void failingGuardMovesNothing() throws Exception {
        save(TESTATOR, 1);
        manager.start();
        guard = false;
        assertEquals(Outcome.Kind.RETRY, estate.settle(decision(Recipient.character(HEIR), 0)).kind());
        assertOwner("world_1_64_0", TESTATOR);
    }

    @Test
    void failedSaveKeepsTheTestatorAsOwnerAndRetries() throws Exception {
        save(TESTATOR, 1);
        manager.start();
        Path file = folder.resolve("data/stations/world_1_64_0.json");
        Files.delete(file);
        Files.createDirectories(file.resolve("blocked"));

        Outcome.Retry retry = assertInstanceOf(Outcome.Retry.class, estate.settle(decision(Recipient.character(HEIR), 0)));

        assertTrue(retry.reason().contains("world_1_64_0"));
        assertOwner("world_1_64_0", TESTATOR);
        assertTrue(Files.readString(journals().resolve(DEATH + ".json")).contains(StationTransfers.PLANNED));

        Files.delete(file.resolve("blocked"));
        Files.delete(file);
        assertEquals(Outcome.done(Map.of("stations", 1), List.of("world_1_64_0")),
                estate.settle(decision(Recipient.character(HEIR), 0)));
    }

    @Test
    void unreadableJournalRetries() throws Exception {
        save(TESTATOR, 1);
        manager.start();
        Files.createDirectories(journals());
        Files.writeString(journals().resolve(DEATH + ".json"), "{ broken");
        assertEquals(Outcome.Kind.RETRY, estate.settle(decision(Recipient.character(HEIR), 0)).kind());
        Files.writeString(journals().resolve(DEATH + ".json"), "{}");
        assertEquals(Outcome.Kind.RETRY, estate.settle(decision(Recipient.character(HEIR), 0)).kind());
        assertOwner("world_1_64_0", TESTATOR);
    }

    @Test
    void describeListsTheCharactersStations() throws Exception {
        save(TESTATOR, 2);
        save(TESTATOR, 1);
        save(OTHER, 3);
        manager.start();

        Holdings holdings = estate.describe(TESTATOR.characterId());

        assertEquals(Map.of("stations", 2), holdings.counts());
        assertEquals(List.of("Research station at world 1, 64, 0 (input)", "Research station at world 2, 64, 0 (input)"),
                holdings.labels());
        assertSame(Holdings.NONE, estate.describe(HEIR.characterId()));
    }

    @Test
    void describeCopesWithAStationWithoutItsWorldOrProject() {
        ResearchManager stations = mock(ResearchManager.class);
        Location unloaded = mock(Location.class);
        when(unloaded.isWorldLoaded()).thenReturn(false);
        ResearchStation noWorld = new ResearchStation(unloaded, TESTATOR.ownerId(),
                new ActiveProject("input", "output", "minecraft:diamond", false));
        ResearchStation noProject = new ResearchStation(new Location(world, 4, 64, 0), TESTATOR.ownerId(), null);
        ResearchStation nowhere = new ResearchStation(null, TESTATOR.ownerId(), null);
        when(stations.stationsOwnedBy(TESTATOR.characterId().toString())).thenReturn(List.of(noWorld, noProject, nowhere));
        StationEstate described = new StationEstate(stations, new StationTransfers(stations, journals()),
                Logger.getAnonymousLogger());

        assertEquals(List.of("Research station at an unloaded world", "Research station at an unloaded world (input)",
                "Research station at world 4, 64, 0"), described.describe(TESTATOR.characterId()).labels());
    }

    @Test
    void aJournalFromAnotherDeathIsRefused() throws Exception {
        save(TESTATOR, 1);
        save(OTHER, 2);
        manager.start();
        StationTransfers.Entry planned = planned();
        Files.createDirectories(journals());
        Files.writeString(journals().resolve(DEATH + ".json"), new Gson().toJson(new StationTransfers.Journal(
                UUID.randomUUID().toString(), TESTATOR.characterId().toString(), Map.of("world_2_64_0", planned))));
        assertEquals(Outcome.Kind.RETRY, estate.settle(decision(Recipient.character(HEIR), 0)).kind());
        Files.writeString(journals().resolve(DEATH + ".json"), new Gson().toJson(new StationTransfers.Journal(
                DEATH.toString(), OTHER.characterId().toString(), Map.of("world_2_64_0", planned))));
        assertEquals(Outcome.Kind.RETRY, estate.settle(decision(Recipient.character(HEIR), 0)).kind());
        assertOwner("world_1_64_0", TESTATOR);
        assertOwner("world_2_64_0", OTHER);
    }

    @Test
    void oneFailingEstateDoesNotStopTheReconcileOfTheOthers() throws Exception {
        save(TESTATOR, 1);
        manager.start();
        EstateDecision broken = new EstateDecision(UUID.randomUUID(), OTHER, Optional.empty(), Instant.EPOCH,
                Optional.empty(), Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.FROZEN,
                Recipient.FROZEN, Recipient.SENDER, 0);
        estate.guard = decision -> {
            if (decision == broken) {
                throw new IllegalStateException("service failed");
            }
            return true;
        };
        estate.decided = () -> List.of(broken, decision(Recipient.character(HEIR), 0));
        List<UUID> reported = new ArrayList<>();
        estate.reporter = (death, participant, revision, outcome) -> reported.add(death);

        estate.reconcile();

        assertEquals(List.of(DEATH), reported);
        assertOwner("world_1_64_0", HEIR);
    }

    @Test
    void reconcileSettlesDecidedEstatesAndReportsOnlyChanges() throws Exception {
        save(TESTATOR, 1);
        save(OTHER, 2);
        manager.start();
        EstateDecision blocked = new EstateDecision(UUID.randomUUID(), OTHER, Optional.empty(), Instant.EPOCH,
                Optional.empty(), Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.character(HEIR),
                Recipient.FROZEN, Recipient.SENDER, 0);
        estate.guard = decision -> decision != blocked;
        estate.decided = () -> List.of(blocked, decision(Recipient.character(HEIR), 0));
        List<Object[]> reports = new ArrayList<>();
        estate.reporter = (death, participant, revision, outcome) -> reports.add(new Object[] {death, participant, revision, outcome});

        estate.reconcile();
        estate.reconcile();

        assertEquals(1, reports.size());
        assertArrayEquals(new Object[] {DEATH, "research", 0L,
                Outcome.done(Map.of("stations", 1), List.of("world_1_64_0"))}, reports.get(0));
        assertOwner("world_2_64_0", OTHER);

        estate.decided = () -> List.of(decision(Recipient.character(HEIR), 1));
        estate.reconcile();
        assertEquals(2, reports.size());
    }

    @Test
    void reconcileReportsAgainWhenNoServiceTookTheReport() {
        manager.start();
        estate.decided = () -> List.of(decision(Recipient.FROZEN, 0));
        List<Outcome> reports = new ArrayList<>();
        estate.reporter = (death, participant, revision, outcome) -> reports.add(outcome) && false;

        estate.reconcile();
        estate.reconcile();

        assertEquals(List.of(Outcome.done(), Outcome.done()), reports);
    }

    @Test
    void startRegistersTheParticipantAndReconcilesOnTheFirstTickThenHourly() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        ServicesManager services = mock(ServicesManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(server.getServicesManager()).thenReturn(services);
        when(server.getScheduler()).thenReturn(scheduler);

        StationEstate.start(plugin, manager);

        var participant = org.mockito.ArgumentCaptor.forClass(EstateParticipant.class);
        verify(services).register(eq(EstateParticipant.class), participant.capture(), eq(plugin), eq(ServicePriority.Normal));
        assertEquals("research", participant.getValue().id());
        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(72_000L));
    }

    @Test
    void probeFindsTheEstateApiAndMissesAbsentClasses() {
        assertTrue(EstateProbe.available(EstateProbe.CLASSES));
        assertFalse(EstateProbe.available("net.tfminecraft.tlibs.character.estate.Missing"));
    }

    private static Outcome sortedIds(Outcome outcome) {
        Outcome.Done done = assertInstanceOf(Outcome.Done.class, outcome);
        return Outcome.done(done.counts(), done.movedIds().stream().sorted().toList());
    }

    private EstateDecision decision(Recipient property, long revision) {
        return new EstateDecision(DEATH, TESTATOR, Optional.of(Instant.EPOCH), Instant.EPOCH, Optional.empty(),
                Optional.empty(), Optional.empty(), Recipient.FROZEN, property, Recipient.FROZEN, Recipient.SENDER, revision);
    }

    private void save(CharacterRef owner, int x) {
        ResearchStation station = new ResearchStation(new Location(world, x, 64, 0), owner.ownerId(),
                new ActiveProject("input", "output", "minecraft:diamond", false));
        station.setOwnerCharacterId(owner.characterId().toString());
        assertTrue(StationStore.saveStation(station));
    }

    private void assertOwner(String key, CharacterRef owner) {
        ResearchStation station = manager.getStationByKey(key);
        assertEquals(owner.ownerId(), station.getOwnerUuid());
        assertEquals(owner.characterId().toString(), station.getOwnerCharacterId());
    }

    private StationTransfers.Entry planned() {
        return new StationTransfers.Entry(HEIR.ownerId().toString(), HEIR.characterId().toString(), StationTransfers.PLANNED);
    }

    private void journal(Map<String, StationTransfers.Entry> stations) throws Exception {
        Files.createDirectories(journals());
        Files.writeString(journals().resolve(DEATH + ".json"), new Gson().toJson(
                new StationTransfers.Journal(DEATH.toString(), TESTATOR.characterId().toString(), stations)));
    }

    private Path journals() {
        return folder.resolve("data/estates");
    }
}
