package net.tfminecraft.research.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.ResearchTestState;
import net.tfminecraft.research.database.StationStore;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.research.util.GridLayout;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;

class ResearchSessionTest {
    private ResearchTestState state;
    private ServerMock server;
    private PlayerMock player;
    private CharacterIdentity identity;
    private CharacterSession current;
    private ResearchManager manager;
    private InventoryManager menus;
    private ResearchStation station;
    private Block lectern;
    private MockedStatic<StationStore> store;
    private List<ResearchStation> saved;

    @BeforeEach
    void setUp() throws Exception {
        state = new ResearchTestState();
        server = MockBukkit.mock();
        player = server.addPlayer();
        player.openInventory(server.createInventory(null, 9, "Other menu"));
        Research.plugin = mock(Research.class);
        when(Research.plugin.getServer()).thenReturn(server);
        when(Research.plugin.isEnabled()).thenReturn(true);
        when(Research.plugin.getName()).thenReturn("Research");
        var lifecyclePlugin = MockBukkit.createMockPlugin();
        identity = mock(CharacterIdentity.class);
        current = new CharacterSession(new CharacterRef(player.getUniqueId(), UUID.randomUUID()), 1);
        when(identity.currentSession(player)).thenAnswer(call -> Optional.ofNullable(current));
        when(identity.isCurrent(any())).thenAnswer(call -> call.getArgument(0).equals(current));
        server.getServicesManager().register(CharacterIdentity.class, identity, Research.plugin, ServicePriority.Normal);
        Cache.stationBlock = Material.LECTERN;
        Cache.stationPermission = "";
        OutputLoader.get().clear();
        Messages.clear();
        Messages.put("station.character_required", "Character is loading.");
        lectern = server.addSimpleWorld("research").getBlockAt(1, 64, 1);
        lectern.setType(Material.LECTERN);
        ActiveProject project = new ActiveProject("input", "unknown-output", "vanilla.DIAMOND");
        project.addAspectPoints("fire", 3, 10);
        project.markTested("vanilla.COAL");
        station = new ResearchStation(lectern.getLocation(), player.getUniqueId(), project);
        station.setOwnerCharacterId(current.ref().characterId().toString().toUpperCase(Locale.ROOT));
        store = mockStatic(StationStore.class);
        store.when(StationStore::loadAll).thenReturn(List.of(station));
        PlayerManager points = new PlayerManager();
        manager = new ResearchManager(points);
        menus = new InventoryManager(points);
        manager.start();
        store.clearInvocations();
        store.when(() -> StationStore.saveAll(any())).thenAnswer(call -> {
            saved = List.copyOf(call.getArgument(0));
            return null;
        });
        server.getPluginManager().registerEvents(manager, lifecyclePlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        HandlerList.unregisterAll(manager);
        store.close();
        MockBukkit.unmock();
        state.close();
    }

    private void interact() {
        manager.onStationInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), lectern, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND));
    }

    private Inventory openExperiment() {
        interact();
        Inventory inventory = player.getOpenInventory().getTopInventory();
        assertInstanceOf(StationMenuHolder.class, inventory.getHolder());
        inventory.setItem(GridLayout.SLOT_EXPERIMENT, new ItemStack(Material.COAL));
        return inventory;
    }

    @ParameterizedTest
    @EnumSource(CharacterSessionEndEvent.Reason.class)
    void endReturnsExperimentOnceBeforeIncomingSessionAndKeepsProgress(CharacterSessionEndEvent.Reason reason) {
        Inventory inventory = openExperiment();
        InventoryView outgoing = player.getOpenInventory();
        CharacterSession ended = current;
        current = null;
        server.getPluginManager().callEvent(new CharacterSessionEndEvent(player, ended, reason));

        assertNull(inventory.getItem(GridLayout.SLOT_EXPERIMENT));
        assertEquals(1, player.getInventory().all(Material.COAL).values().stream().mapToInt(ItemStack::getAmount).sum());
        manager.onInventoryClose(new InventoryCloseEvent(outgoing));
        assertEquals(1, player.getInventory().all(Material.COAL).values().stream().mapToInt(ItemStack::getAmount).sum());
        assertSame(station, manager.getStationAt(lectern.getLocation()));
        assertEquals(3, station.getProject().getAspectPoints("fire"));
        assertTrue(station.getProject().hasTestedItem("vanilla.COAL"));
        store.verifyNoInteractions();
    }

    @Test
    void legacyOwnershipIsClaimedOnlyWhenReadyAndExistingCasingIsPreserved() {
        String original = station.getOwnerCharacterId();
        interact();
        assertEquals(original, station.getOwnerCharacterId());
        player.closeInventory();
        station.setOwnerCharacterId(null);
        CharacterSession ready = current;
        current = null;
        interact();
        assertNull(station.getOwnerCharacterId());
        assertEquals("Character is loading.", player.nextMessage());
        current = ready;
        interact();
        assertEquals(ready.ref().characterId().toString(), station.getOwnerCharacterId());
        store.verify(() -> StationStore.saveStation(station), times(1));
    }

    @Test
    void pendingOrMissingProviderCannotStartOrConsumePaper() {
        store.when(StationStore::loadAll).thenReturn(List.of());
        manager.start();
        player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER, 3));
        CharacterSession ready = current;
        current = null;
        interact();
        current = ready;
        server.getServicesManager().unregister(CharacterIdentity.class, identity);
        interact();
        assertNull(manager.getStationAt(lectern.getLocation()));
        assertEquals(3, player.getInventory().getItemInMainHand().getAmount());
        store.verify(() -> StationStore.saveStation(any()), never());
    }

    @Test
    void oldScrapMenuCannotActAfterSwitchingAwayAndBackOrProviderLoss() {
        menus.openScrapConfirm(player, station);
        InventoryView old = player.getOpenInventory();
        CharacterSession first = current;
        current = new CharacterSession(new CharacterRef(player.getUniqueId(), UUID.randomUUID()), 2);
        current = new CharacterSession(first.ref(), 3);
        clickScrap(old);
        assertSame(station, manager.getStationAt(lectern.getLocation()));

        menus.openScrapConfirm(player, station);
        InventoryView latest = player.getOpenInventory();
        server.getServicesManager().unregister(CharacterIdentity.class, identity);
        clickScrap(latest);
        assertSame(station, manager.getStationAt(lectern.getLocation()));
        store.verify(() -> StationStore.deleteStation(any()), never());
    }

    @Test
    void oldEndCannotCloseReplacementMenuAndUnboundMenusCannotAct() {
        CharacterSession ended = current;
        current = new CharacterSession(current.ref(), 2);
        menus.openScrapConfirm(player, station);
        Inventory replacement = player.getOpenInventory().getTopInventory();
        server.getPluginManager().callEvent(new CharacterSessionEndEvent(player, ended, CharacterSessionEndEvent.Reason.SWITCH));
        assertSame(replacement, player.getOpenInventory().getTopInventory());
        StationMenuHolder unbound = new StationMenuHolder(lectern.getLocation(), null);
        assertFalse(unbound.isCurrent(player));
        StationMenuHolder anotherOwner = new StationMenuHolder(lectern.getLocation(),
                new CharacterSession(new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), 3));
        assertFalse(anotherOwner.isCurrent(player));
    }

    @Test
    void disableReturnsHeldItemEvenAfterProviderRemovalAndSavesStations() {
        Inventory outgoing = openExperiment();
        server.getServicesManager().unregister(CharacterIdentity.class, identity);
        PlayerMock other = server.addPlayer();
        Inventory unrelated = server.createInventory(null, 9, "Unrelated");
        other.openInventory(unrelated);
        when(Research.plugin.isEnabled()).thenReturn(false);
        HandlerList.unregisterAll(manager);
        manager.unloadAll();
        assertNull(outgoing.getItem(GridLayout.SLOT_EXPERIMENT));
        assertTrue(player.getInventory().contains(Material.COAL, 1));
        assertSame(unrelated, other.getOpenInventory().getTopInventory());
        assertEquals(List.of(station), saved);
    }

    private void clickScrap(InventoryView view) {
        manager.onInventoryClick(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                InventoryManager.CONFIRM_SCRAP_YES, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }
}
