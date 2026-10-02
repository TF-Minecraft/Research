package net.tfminecraft.research.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.database.StationStore;
import net.tfminecraft.research.event.ResearchCompleteEvent;
import net.tfminecraft.research.loader.AspectLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.AspectDef;
import net.tfminecraft.research.model.AspectState;
import net.tfminecraft.research.model.ExperimentMatch;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.research.registry.AspectItemRegistry;
import net.tfminecraft.research.util.GridLayout;
import net.tfminecraft.research.util.InputMatcher;
import net.tfminecraft.research.util.ItemRef;
import net.tfminecraft.research.util.MmoAttributes;
import net.tfminecraft.research.util.ResultSpawnEffects;
import net.tfminecraft.research.util.SoundKeys;
import net.tfminecraft.research.util.StationCompleteEffects;
import net.tfminecraft.research.util.StationStartEffects;

class ResearchManagerTest {
    private final List<AutoCloseable> mocks = new ArrayList<>();
    private final Map<Field, Object> originalCache = new LinkedHashMap<>();
    private Map<String, OutputDef> originalOutputs;
    private Map<String, AspectDef> originalAspects;
    private Research originalPlugin;
    private ResearchManager originalManager;
    private Research plugin;
    private Logger logger;
    private PluginManager pluginManager;
    private World world;
    private Player player;
    private PlayerInventory bottom;
    private Inventory top;
    private InventoryView view;
    private Block lectern;
    private Location location;
    private UUID owner;
    private AtomicReference<String> title;
    private Map<Integer, ItemStack> topItems;
    private Map<Integer, ItemStack> bottomItems;
    private ResearchStation station;
    private ActiveProject project;
    private PlayerManager points;
    private ResearchManager manager;
    private InventoryManager menus;
    private MockedStatic<StationStore> store;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<AspectItemRegistry> registry;
    private MockedStatic<InputMatcher> inputs;
    private MockedStatic<ItemRef> items;
    private MockedStatic<ResultSpawnEffects> rewards;
    private MockedStatic<StationStartEffects> startEffects;
    private MockedStatic<StationCompleteEffects> completeEffects;
    private MockedStatic<SoundKeys> sounds;
    private MockedStatic<Messages> messages;

    @BeforeEach
    void setUp() throws Exception {
        originalPlugin = Research.plugin;
        originalManager = ResearchManager.getInstance();
        for (Field field : Cache.class.getFields()) {
            originalCache.put(field, field.get(null));
        }
        originalOutputs = new LinkedHashMap<>(OutputLoader.get());
        originalAspects = new LinkedHashMap<>(AspectLoader.get());
        OutputLoader.get().clear();
        AspectLoader.get().clear();
        Cache.stationBlock = Material.LECTERN;
        Cache.stationPermission = "";
        Cache.experimentCost = 1;
        Cache.experimentPrimaryPoints = 2;
        Cache.experimentSecondaryPoints = 1;
        Cache.experimentRejectPoints = 6;
        Cache.aspectBaseRevealPercent = 0.2;
        Cache.aspectMinRevealPercent = 0.1;
        Cache.aspectBaseConfirmPercent = 0.8;
        Cache.aspectMinConfirmPercent = 0.3;
        Cache.externalModifiers = null;

        plugin = mock(Research.class);
        logger = mock(Logger.class);
        Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        Research.plugin = plugin;
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        location = new Location(world, 10, 64, 20);
        owner = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getName()).thenReturn("Researcher");
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        bottom = mock(PlayerInventory.class);
        top = mock(Inventory.class);
        view = mock(InventoryView.class);
        title = new AtomicReference<>("Research Station");
        topItems = new HashMap<>();
        bottomItems = new HashMap<>();
        when(player.getInventory()).thenReturn(bottom);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTitle()).thenAnswer(call -> title.get());
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(top.getSize()).thenReturn(54);
        when(top.getHolder()).thenReturn(new StationMenuHolder(location));
        when(top.getItem(anyInt())).thenAnswer(call -> topItems.get(call.getArgument(0)));
        doAnswer(call -> { topItems.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(top).setItem(anyInt(), nullable(ItemStack.class));
        doAnswer(call -> { topItems.clear(); return null; }).when(top).clear();
        when(bottom.getItem(anyInt())).thenAnswer(call -> bottomItems.get(call.getArgument(0)));
        doAnswer(call -> { bottomItems.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(bottom).setItem(anyInt(), nullable(ItemStack.class));
        when(bottom.addItem(any(ItemStack.class))).thenAnswer(call -> new HashMap<>());
        when(view.getInventory(anyInt())).thenAnswer(call -> {
            int slot = call.getArgument(0);
            return slot < 0 ? null : slot < top.getSize() ? top : bottom;
        });
        when(view.convertSlot(anyInt())).thenAnswer(call -> {
            int slot = call.getArgument(0);
            return slot < top.getSize() ? slot : slot - top.getSize();
        });
        when(view.getItem(anyInt())).thenAnswer(call -> {
            int slot = call.getArgument(0);
            return slot < 0 ? null : slot < top.getSize() ? topItems.get(slot)
                    : bottomItems.get(slot - top.getSize());
        });
        lectern = mock(Block.class);
        when(lectern.getType()).thenReturn(Material.LECTERN);
        when(lectern.getLocation()).thenReturn(location);
        project = new ActiveProject("input", "output", "vanilla.DIAMOND");
        station = new ResearchStation(location, owner, project);
        points = mock(PlayerManager.class);
        when(points.trySpendMentalPoints(player, 1)).thenReturn(true);

        store = scoped(mockStatic(StationStore.class));
        store.when(StationStore::loadAll).thenReturn(List.of(station));
        bukkit = scoped(mockStatic(Bukkit.class));
        bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(player);
        registry = scoped(mockStatic(AspectItemRegistry.class));
        inputs = scoped(mockStatic(InputMatcher.class));
        items = scoped(mockStatic(ItemRef.class));
        rewards = scoped(mockStatic(ResultSpawnEffects.class));
        startEffects = scoped(mockStatic(StationStartEffects.class));
        completeEffects = scoped(mockStatic(StationCompleteEffects.class));
        sounds = scoped(mockStatic(SoundKeys.class));
        messages = scoped(mockStatic(Messages.class));
        messages.when(() -> Messages.get(anyString())).thenAnswer(call -> call.getArgument(0));
        messages.when(() -> Messages.format(anyString(), anyMap()))
                .thenAnswer(call -> call.getArgument(0) + " " + call.getArgument(1));
        scoped(mockStatic(MmoAttributes.class));
        MockedStatic<InventoryManager> menuTitles = scoped(mockStatic(InventoryManager.class));
        menuTitles.when(InventoryManager::mainInventoryTitle).thenReturn("Research Station");
        menuTitles.when(InventoryManager::scrapConfirmTitle).thenReturn("Confirm Scrap");
        MockedConstruction<InventoryManager> constructors = scoped(mockConstruction(InventoryManager.class));
        manager = new ResearchManager(points);
        menus = constructors.constructed().getFirst();
        manager.start();
    }

    @AfterEach
    void restoreState() throws Exception {
        for (int i = mocks.size() - 1; i >= 0; i--) {
            mocks.get(i).close();
        }
        for (Map.Entry<Field, Object> entry : originalCache.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
        OutputLoader.get().clear();
        OutputLoader.get().putAll(originalOutputs);
        AspectLoader.get().clear();
        AspectLoader.get().putAll(originalAspects);
        Research.plugin = originalPlugin;
        Field singleton = ResearchManager.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        singleton.set(null, originalManager);
    }

    @ParameterizedTest
    @ValueSource(ints = {InventoryManager.CONFIRM_SCRAP_YES, InventoryManager.CONFIRM_SCRAP_NO})
    void bottomInventoryCannotActivateScrapButtons(int slot) {
        scrapMenu();
        bottomItems.put(slot, stack(Material.DIRT, 1));

        manager.onInventoryClick(click(top.getSize() + slot));

        assertSame(station, manager.getStationAt(location));
        verifyNoInteractions(menus);
        verify(player, never()).closeInventory();
        store.verify(() -> StationStore.deleteStation(location), never());
    }

    @Test
    void cancelledLecternBreakPreservesProject() {
        BlockBreakEvent event = new BlockBreakEvent(lectern, player);
        event.setCancelled(true);

        manager.onStationBreak(event);

        assertSame(station, manager.getStationAt(location));
        store.verify(() -> StationStore.deleteStation(location), never());
        verify(player, never()).closeInventory();
    }

    @Test
    void unrelatedInventoryWithMatchingTitleKeepsItsItems() {
        ItemStack item = stack(Material.DIAMOND, 3);
        topItems.put(GridLayout.SLOT_EXPERIMENT, item);
        when(top.getHolder()).thenReturn(null);

        manager.onInventoryClose(new InventoryCloseEvent(view));

        assertSame(item, topItems.get(GridLayout.SLOT_EXPERIMENT));
        verify(bottom, never()).addItem(any(ItemStack.class));
    }

    @Test
    void lifecycleLoadsReplacesAndPersistsTheCurrentStations() {
        assertSame(manager, ResearchManager.getInstance());
        assertSame(station, manager.getStationAt(location.clone().add(0.9, 0.5, 0.8)));
        assertNull(manager.getStationAt(null));
        assertNull(manager.getStationAt(location.clone().add(1, 0, 0)));
        List<List<ResearchStation>> saved = new ArrayList<>();
        store.when(() -> StationStore.saveAll(anyList())).thenAnswer(call -> {
            saved.add(List.copyOf(call.getArgument(0))); return null;
        });

        manager.unloadAll();

        assertEquals(List.of(List.of(station)), saved);
        assertNull(manager.getStationAt(location));
        manager.start();
        assertSame(station, manager.getStationAt(location));
        store.when(StationStore::loadAll).thenReturn(List.of());
        manager.start();
        assertNull(manager.getStationAt(location));
    }

    @Test
    void irrelevantInteractionsAreIgnored() {
        PlayerInteractEvent leftClick = interact(Action.LEFT_CLICK_BLOCK, lectern, EquipmentSlot.HAND);
        manager.onStationInteract(leftClick);
        PlayerInteractEvent noBlock = interact(Action.RIGHT_CLICK_BLOCK, null, EquipmentSlot.HAND);
        manager.onStationInteract(noBlock);
        when(lectern.getType()).thenReturn(Material.STONE);
        PlayerInteractEvent otherBlock = interact(Action.RIGHT_CLICK_BLOCK, lectern, EquipmentSlot.HAND);
        manager.onStationInteract(otherBlock);

        assertFalse(leftClick.isCancelled());
        assertFalse(noBlock.isCancelled());
        assertFalse(otherBlock.isCancelled());
        verifyNoInteractions(menus);
    }

    @Test
    void configuredPermissionIsCheckedBeforeOpeningStation() {
        Cache.stationPermission = "research.use";
        PlayerInteractEvent denied = interact(Action.RIGHT_CLICK_BLOCK, lectern, EquipmentSlot.HAND);
        manager.onStationInteract(denied);
        assertFalse(denied.isCancelled());
        verifyNoInteractions(menus);

        when(player.hasPermission("research.use")).thenReturn(true);
        PlayerInteractEvent allowed = interact(Action.RIGHT_CLICK_BLOCK, lectern, EquipmentSlot.HAND);
        manager.onStationInteract(allowed);
        assertTrue(allowed.isCancelled());
        verify(menus).openMain(player, station);
    }

    @Test
    void stationOwnerCanReopenButOtherPlayersCannot() {
        manager.onStationInteract(interact(Action.RIGHT_CLICK_BLOCK, lectern, EquipmentSlot.HAND));
        verify(menus).openMain(player, station);
        clearInvocations(menus);
        station.setOwnerUuid(UUID.randomUUID());

        manager.onStationInteract(interact(Action.RIGHT_CLICK_BLOCK, lectern, EquipmentSlot.HAND));

        verify(player).sendMessage("station.in_use");
        verifyNoInteractions(menus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "AIR", "DIRT"})
    void startingWithoutAMatchingInputExplainsTheProblem(String material) {
        noStations();
        ItemStack held = material.equals("null") ? null : stack(Material.valueOf(material), 1);
        when(bottom.getItemInMainHand()).thenReturn(held);

        PlayerInteractEvent event = interact(Action.RIGHT_CLICK_BLOCK, lectern, null);
        manager.onStationInteract(event);

        assertTrue(event.isCancelled());
        verify(player).sendMessage(material.equals("DIRT") ? "station.invalid_start_item" : "station.hold_start_item");
        assertNull(manager.getStationAt(location));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void startingConsumesOnlyTheOpeningHandAndSavesResolvedProject(boolean offHand) {
        noStations();
        ItemStack held = stack(Material.PAPER, 3);
        EquipmentSlot hand = offHand ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        when(bottom.getItemInMainHand()).thenReturn(offHand ? null : held);
        when(bottom.getItemInOffHand()).thenReturn(offHand ? held : null);
        InputDef input = input("vanilla.PAPER", 2, "output");
        inputs.when(() -> InputMatcher.findByStartItem(held)).thenReturn(input);
        items.when(() -> ItemRef.matches(held, "vanilla.PAPER")).thenReturn(true);
        output(Map.of("fire", 8));

        manager.onStationInteract(interact(Action.RIGHT_CLICK_BLOCK, lectern, hand));

        ResearchStation created = manager.getStationAt(location);
        assertNotNull(created);
        assertEquals(owner, created.getOwnerUuid());
        assertEquals("input", created.getProject().getInputId());
        assertEquals("output", created.getProject().getResolvedOutputId());
        assertEquals("vanilla.DIAMOND", created.getProject().getResolvedResultRef());
        assertEquals(6, created.getProject().getAspectSlotOrder().size());
        assertTrue(created.getProject().getAspectSlotOrder().contains("fire"));
        assertEquals(1, held.getAmount());
        store.verify(() -> StationStore.saveStation(created));
        startEffects.verify(() -> StationStartEffects.play(player, location));
        verify(player).sendMessage("station.started");
        verify(menus).openMain(player, created);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-output", "disabled", "unresolved", "missing-item"})
    void startFailuresDoNotCreateOrConsumeAProject(String failure) {
        noStations();
        ItemStack held = stack(Material.PAPER, 1);
        when(bottom.getItemInMainHand()).thenReturn(held);
        InputDef input = input("vanilla.PAPER", 2, "output");
        inputs.when(() -> InputMatcher.findByStartItem(held)).thenReturn(input);
        items.when(() -> ItemRef.matches(held, "vanilla.PAPER")).thenReturn(true);
        YamlConfiguration config = outputConfig(Map.of("fire", 2));
        if (failure.equals("disabled")) config.set("enabled", false);
        if (failure.equals("unresolved")) config.set("result", null);
        if (!failure.equals("unknown-output")) OutputLoader.get().put("output", new OutputDef("output", config));

        manager.onStationInteract(interact(Action.RIGHT_CLICK_BLOCK, lectern, EquipmentSlot.HAND));

        assertNull(manager.getStationAt(location));
        assertEquals(1, held.getAmount());
        verify(player).sendMessage(failure.equals("missing-item") ? "station.missing_start_item" : "reload.failed");
        store.verify(() -> StationStore.saveStation(any()), never());
        startEffects.verifyNoInteractions();
    }

    @Test
    void breaksIgnoreOtherBlocksAndUntrackedLecterns() {
        when(lectern.getType()).thenReturn(Material.STONE);
        manager.onStationBreak(new BlockBreakEvent(lectern, player));
        when(lectern.getType()).thenReturn(Material.LECTERN);
        when(lectern.getLocation()).thenReturn(location.clone().add(1, 0, 0));
        manager.onStationBreak(new BlockBreakEvent(lectern, player));
        assertSame(station, manager.getStationAt(location));
    }

    @ParameterizedTest
    @ValueSource(strings = {"online-menu", "online-other-menu", "offline", "absent"})
    void breakingResearchScrapsPersistedStateAndNotifiesAvailableOwner(String availability) {
        if (availability.equals("absent")) bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(null);
        if (availability.equals("offline")) when(player.isOnline()).thenReturn(false);
        if (availability.equals("online-other-menu")) title.set("Another menu");

        manager.onStationBreak(new BlockBreakEvent(lectern, player));

        assertNull(manager.getStationAt(location));
        store.verify(() -> StationStore.deleteStation(location));
        verify(player, times(availability.startsWith("online") ? 1 : 0)).sendMessage("station.broken");
        verify(player, times(availability.equals("online-menu") ? 1 : 0)).closeInventory();
    }

    @Test
    void breakingOneStationDoesNotCloseAnotherStationsMenu() {
        when(top.getHolder()).thenReturn(new StationMenuHolder(location.clone().add(10, 0, 0)));
        manager.onStationBreak(new BlockBreakEvent(lectern, player));
        verify(player, never()).closeInventory();
        assertNull(manager.getStationAt(location));
    }

    @Test
    void scrapTopButtonsConfirmCancelAndIgnoreOtherSlots() {
        scrapMenu();
        topItems.put(InventoryManager.CONFIRM_SCRAP_NO, stack(Material.PAPER, 1));
        manager.onInventoryClick(click(InventoryManager.CONFIRM_SCRAP_NO));
        verify(menus).openMain(player, station);
        topItems.put(4, stack(Material.PAPER, 1));
        manager.onInventoryClick(click(4));
        manager.onInventoryClick(click(2));
        assertSame(station, manager.getStationAt(location));
        topItems.put(InventoryManager.CONFIRM_SCRAP_YES, stack(Material.PAPER, 1));
        manager.onInventoryClick(click(InventoryManager.CONFIRM_SCRAP_YES));
        assertNull(manager.getStationAt(location));
        verify(player).sendMessage("station.scrapped");
        store.verify(() -> StationStore.deleteStation(location));
    }

    @Test
    void mainControlOpensScrapAndIgnoresEmptyAndNonControlSlots() {
        topItems.put(GridLayout.SLOT_SCRAP, stack(Material.PAPER, 1));
        manager.onInventoryClick(click(GridLayout.SLOT_SCRAP));
        verify(menus).openScrapConfirm(player, station);
        topItems.put(10, stack(Material.PAPER, 1));
        manager.onInventoryClick(click(10));
        manager.onInventoryClick(click(11));
        verifyNoMoreInteractions(menus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-title", "not-player", "no-holder", "missing-station", "other-owner"})
    void invalidMenuClicksCannotActOnStations(String problem) {
        if (problem.equals("other-title")) title.set("Other");
        if (problem.equals("not-player")) when(view.getPlayer()).thenReturn(mock(HumanEntity.class));
        if (problem.equals("no-holder")) when(top.getHolder()).thenReturn(null);
        if (problem.equals("missing-station")) when(top.getHolder()).thenReturn(new StationMenuHolder(location.clone().add(1, 0, 0)));
        if (problem.equals("other-owner")) station.setOwnerUuid(UUID.randomUUID());
        topItems.put(GridLayout.SLOT_SCRAP, stack(Material.PAPER, 1));

        manager.onInventoryClick(click(GridLayout.SLOT_SCRAP));

        verifyNoInteractions(menus);
        assertSame(station, manager.getStationAt(location));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Research Station", "Confirm Scrap", "Other"})
    void dragsAreCancelledForResearchMenuTitles(String inventoryTitle) {
        title.set(inventoryTitle);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);

        manager.onInventoryDrag(event);

        verify(event, times(inventoryTitle.equals("Other") ? 0 : 1)).setCancelled(true);
        when(event.getWhoClicked()).thenReturn(mock(HumanEntity.class));
        clearInvocations(event);
        manager.onInventoryDrag(event);
        verify(event, never()).setCancelled(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-title", "not-player", "empty", "air"})
    void closeIgnoresNonResearchOrEmptyInventories(String problem) {
        if (problem.equals("other-title")) title.set("Other");
        if (problem.equals("not-player")) when(view.getPlayer()).thenReturn(mock(HumanEntity.class));
        if (problem.equals("air")) topItems.put(GridLayout.SLOT_EXPERIMENT, stack(Material.AIR, 0));
        manager.onInventoryClose(new InventoryCloseEvent(view));
        verify(bottom, never()).addItem(any(ItemStack.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void takingExperimentReturnsItOrDropsOverflow(boolean full) {
        ItemStack experiment = stack(Material.DIAMOND, 1);
        topItems.put(GridLayout.SLOT_EXPERIMENT, experiment);
        if (full) when(bottom.addItem(experiment)).thenReturn(new HashMap<>(Map.of(0, experiment)));

        manager.onInventoryClick(click(GridLayout.SLOT_EXPERIMENT));

        verify(bottom).addItem(experiment);
        verify(world, times(full ? 1 : 0)).dropItemNaturally(location, experiment);
        assertNull(topItems.get(GridLayout.SLOT_EXPERIMENT));
        verify(menus).updateExperimentPreview(top, null, project);
        manager.onInventoryClick(click(GridLayout.SLOT_EXPERIMENT));
        topItems.put(GridLayout.SLOT_EXPERIMENT, stack(Material.AIR, 0));
        manager.onInventoryClick(click(GridLayout.SLOT_EXPERIMENT));
        verify(bottom).addItem(experiment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "air", "shift", "outside"})
    void bottomInventoryIgnoresUnsupportedClicks(String kind) {
        int slot = 2;
        if (kind.equals("air")) bottomItems.put(slot, stack(Material.AIR, 0));
        if (kind.equals("shift")) bottomItems.put(slot, stack(Material.DIRT, 3));
        InventoryClickEvent event = kind.equals("shift")
                ? new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 54 + slot,
                        ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                : click(kind.equals("outside") ? -999 : 54 + slot);

        manager.onInventoryClick(event);

        assertTrue(event.isCancelled());
        registry.verifyNoInteractions();
        verifyNoInteractions(menus);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clicksWithoutThePlayersBottomInventoryCannotMoveItems(boolean missing) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(56);
        when(event.getClickedInventory()).thenReturn(missing ? null : mock(Inventory.class));

        manager.onInventoryClick(event);

        verify(event).setCancelled(true);
        registry.verifyNoInteractions();
        verifyNoInteractions(menus);
    }

    @Test
    void breakingResearchDoesNotCloseASameTitleForeignMenu() {
        when(top.getHolder()).thenReturn(null);

        manager.onStationBreak(new BlockBreakEvent(lectern, player));

        assertNull(manager.getStationAt(location));
        verify(player, never()).closeInventory();
        verify(player).sendMessage("station.broken");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void configuredEmptyScrapMessageDoesNotSendAnEmptyChatLine(String message) {
        messages.when(() -> Messages.get("station.broken")).thenReturn(message);

        manager.onStationBreak(new BlockBreakEvent(lectern, player));

        verify(player, never()).sendMessage(anyString());
        assertNull(manager.getStationAt(location));
    }

    @Test
    void absentScrapMessageStillDeletesTheStation() {
        messages.when(() -> Messages.get("station.broken")).thenReturn(null);

        manager.onStationBreak(new BlockBreakEvent(lectern, player));

        verify(player, never()).sendMessage(anyString());
        assertNull(manager.getStationAt(location));
    }

    @Test
    void inputWithoutAnyOutputsFailsBeforeConsumingTheStartItem() {
        noStations();
        ItemStack held = stack(Material.PAPER, 1);
        when(bottom.getItemInMainHand()).thenReturn(held);
        YamlConfiguration config = new YamlConfiguration();
        config.set("start_item.item", "vanilla.PAPER");
        inputs.when(() -> InputMatcher.findByStartItem(held)).thenReturn(new InputDef("input", config));

        manager.onStationInteract(interact(Action.RIGHT_CLICK_BLOCK, lectern, EquipmentSlot.HAND));

        assertNull(manager.getStationAt(location));
        verify(player).sendMessage("reload.failed");
        assertEquals(1, held.getAmount());
    }

    @Test
    void unknownAndAlreadyTestedInputLeaveInventoriesUnchanged() {
        ItemStack input = stack(Material.DIRT, 3);
        bottomItems.put(2, input);

        manager.onInventoryClick(click(56));

        verify(player).sendMessage("experiment.unknown_item");
        assertSame(input, bottomItems.get(2));
        ExperimentMatch match = new ExperimentMatch("vanilla.DIRT", "earth", null);
        registry.when(() -> AspectItemRegistry.findByItemStack(input)).thenReturn(match);
        project.markTested(match.getItemRef());
        ItemStack previous = stack(Material.PAPER, 1);
        topItems.put(GridLayout.SLOT_EXPERIMENT, previous);

        manager.onInventoryClick(click(56));

        verify(player).sendMessage("experiment.item_already_tested");
        assertSame(input, bottomItems.get(2));
        assertSame(previous, topItems.get(GridLayout.SLOT_EXPERIMENT));
        verify(menus).updateExperimentPreview(top, previous, project);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void selectingInputMovesOneItemAndRefundsPreviousExperiment(int amount) {
        ItemStack input = stack(Material.DIRT, amount);
        ItemStack previous = stack(Material.PAPER, 1);
        bottomItems.put(2, input);
        topItems.put(GridLayout.SLOT_EXPERIMENT, previous);
        ExperimentMatch match = new ExperimentMatch("vanilla.DIRT", "earth", null);
        registry.when(() -> AspectItemRegistry.findByItemStack(input)).thenReturn(match);
        aspect("earth", "block.note_block.pling");

        manager.onInventoryClick(click(56));

        ItemStack inserted = topItems.get(GridLayout.SLOT_EXPERIMENT);
        assertNotSame(input, inserted);
        assertEquals(Material.DIRT, inserted.getType());
        assertEquals(1, inserted.getAmount());
        if (amount == 1) assertNull(bottomItems.get(2));
        else assertEquals(2, bottomItems.get(2).getAmount());
        verify(bottom).addItem(previous);
        verify(menus).updateExperimentPreview(top, inserted, project);
        sounds.verify(() -> SoundKeys.play(player, "block.note_block.pling", 1f, 1f));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-aspect", "blank-sound", "empty-slot", "air-slot"})
    void selectingInputSupportsSilentAspectsAndAnEmptyExperimentSlot(String kind) {
        ItemStack input = stack(Material.DIRT, 1);
        bottomItems.put(2, input);
        registry.when(() -> AspectItemRegistry.findByItemStack(input))
                .thenReturn(new ExperimentMatch("vanilla.DIRT", "earth", null));
        if (!kind.equals("missing-aspect")) aspect("earth", "");
        if (kind.equals("air-slot")) topItems.put(GridLayout.SLOT_EXPERIMENT, stack(Material.AIR, 0));

        manager.onInventoryClick(click(56));

        assertNull(bottomItems.get(2));
        assertEquals(Material.DIRT, topItems.get(GridLayout.SLOT_EXPERIMENT).getType());
        sounds.verifyNoInteractions();
        verify(bottom, never()).addItem(any(ItemStack.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "empty", "air", "unknown", "tested", "rejected", "missing-output", "exhausted"})
    void invalidConfirmationDoesNotChargeOrChangeTheExperiment(String problem) {
        ItemStack item = prepareExperiment("fire", null, Map.of("fire", 10));
        if (problem.equals("completed")) project.setCompleted(true);
        if (problem.equals("empty")) topItems.remove(GridLayout.SLOT_EXPERIMENT);
        if (problem.equals("air")) topItems.put(GridLayout.SLOT_EXPERIMENT, stack(Material.AIR, 0));
        if (problem.equals("unknown")) registry.when(() -> AspectItemRegistry.findByItemStack(item)).thenReturn(null);
        if (problem.equals("tested")) project.markTested("vanilla.COAL");
        if (problem.equals("rejected")) project.setAspectState("fire", AspectState.REJECTED);
        if (problem.equals("missing-output")) OutputLoader.get().clear();
        if (problem.equals("exhausted")) when(points.trySpendMentalPoints(player, 1)).thenReturn(false);
        ItemStack before = topItems.get(GridLayout.SLOT_EXPERIMENT);

        confirm();

        assertSame(before, topItems.get(GridLayout.SLOT_EXPERIMENT));
        assertEquals(0, project.getAspectPoints("fire"));
        verify(points, times(problem.equals("exhausted") ? 1 : 0)).trySpendMentalPoints(player, 1);
        if (problem.equals("unknown")) verify(player).sendMessage("experiment.unknown_item");
        if (problem.equals("tested")) verify(player).sendMessage("experiment.item_already_tested");
        if (problem.equals("rejected")) verify(player).sendMessage("experiment.only_rejected");
        if (problem.equals("exhausted")) verify(player).sendMessage("mental_points.exhausted");
        store.verify(() -> StationStore.saveStation(any()), never());
    }

    @Test
    void experimentAwardsPrimaryAndSecondaryPointsReturnsInputAndPersists() {
        prepareExperiment("fire", "water", Map.of("fire", 10, "water", 10));
        aspect("fire", "input.sound");

        confirm();

        assertEquals(2, project.getAspectPoints("fire"));
        assertEquals(1, project.getAspectPoints("water"));
        assertEquals(AspectState.TESTING, project.getAspectState("fire"));
        assertEquals(AspectState.TESTING, project.getAspectState("water"));
        assertTrue(project.hasTestedItem("vanilla.COAL"));
        assertFalse(project.isProductRevealed());
        verify(points).trySpendMentalPoints(player, 1);
        assertNull(topItems.get(GridLayout.SLOT_EXPERIMENT));
        ArgumentCaptor<ItemStack> returned = ArgumentCaptor.forClass(ItemStack.class);
        verify(bottom).addItem(returned.capture());
        assertEquals(Material.COAL, returned.getValue().getType());
        store.verify(() -> StationStore.saveStation(station));
        verify(menus).populateMain(player, station, top);
        verify(menus).playConfirmRefreshWave(player, station, top, "fire");
        sounds.verify(() -> SoundKeys.play(player, "confirm.sound", 1f, 1f));
    }

    @Test
    void rejectedPrimaryStillAllowsScoringSecondary() {
        prepareExperiment("fire", "water", Map.of("fire", 10, "water", 10));
        project.setAspectState("fire", AspectState.REJECTED);
        project.setAspectState("water", AspectState.TESTING);

        confirm();

        assertEquals(0, project.getAspectPoints("fire"));
        assertEquals(1, project.getAspectPoints("water"));
        assertEquals(AspectState.REJECTED, project.getAspectState("fire"));
        verify(menus).playConfirmRefreshWave(player, station, top, "water");
    }

    @Test
    void secondaryOnlyExperimentUsesItsAspectForPointsAndSound() {
        prepareExperiment(null, "water", Map.of("water", 10));
        aspect("water", "water.input");

        confirm();

        assertEquals(1, project.getAspectPoints("water"));
        assertNull(topItems.get(GridLayout.SLOT_EXPERIMENT));
        verify(menus).playConfirmRefreshWave(player, station, top, "water");
        sounds.verify(() -> SoundKeys.play(player, "confirm.sound", 1f, 1f));
    }

    @Test
    void absentAspectIdentifiersCannotAwardProgress() {
        prepareExperiment(" ", "", Map.of("fire", 10));

        confirm();

        assertTrue(project.getAspectPoints().isEmpty());
        assertFalse(project.isCompleted());
        verify(menus).playConfirmRefreshWave(player, station, top, null);
        assertSame(station, manager.getStationAt(location));
    }

    @Test
    void bothRejectedAspectsPreventChargingForUselessExperiment() {
        prepareExperiment("fire", "water", Map.of("fire", 10));
        project.setAspectState("fire", AspectState.REJECTED);
        project.setAspectState("water", AspectState.REJECTED);

        confirm();

        verify(player).sendMessage("experiment.only_rejected");
        verifyNoInteractions(points);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -2})
    void nonpositiveConfiguredPointsDoNotAdvanceAspects(int configuredPoints) {
        Cache.experimentPrimaryPoints = configuredPoints;
        prepareExperiment("fire", null, Map.of("fire", 10));

        confirm();

        assertEquals(0, project.getAspectPoints("fire"));
        assertTrue(project.hasTestedItem("vanilla.COAL"));
        verify(menus).playConfirmRefreshWave(player, station, top, null);
    }

    @Test
    void fullyScoredAspectsDoNotAdvanceFurther() {
        prepareExperiment("fire", null, Map.of("fire", 5, "water", 5));
        project.getAspectPoints().put("fire", 5);
        project.setAspectState("fire", AspectState.CONFIRMED);

        confirm();

        assertEquals(5, project.getAspectPoints("fire"));
        verify(menus).playConfirmRefreshWave(player, station, top, null);
        assertSame(station, manager.getStationAt(location));
    }

    @Test
    void previouslyConfirmedAspectKeepsItsStateWhileFinishingPoints() {
        prepareExperiment("fire", null, Map.of("fire", 10));
        project.setAspectState("fire", AspectState.CONFIRMED);
        project.getAspectPoints().put("fire", 3);

        confirm();

        assertEquals(5, project.getAspectPoints("fire"));
        assertEquals(AspectState.CONFIRMED, project.getAspectState("fire"));
        rewards.verifyNoInteractions();
        verify(player, never()).sendMessage(startsWith("experiment.confirmed"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 6})
    void offRecipeAspectProgressesThenRejectsAtTheConfiguredCap(int before) {
        prepareExperiment("earth", null, Map.of("fire", 10));
        project.getAspectPoints().put("earth", before);
        if (before == 4) project.setAspectState("earth", AspectState.TESTING);

        confirm();

        assertEquals(Math.min(6, before + 2), project.getAspectPoints("earth"));
        assertEquals(before == 0 ? AspectState.TESTING : AspectState.REJECTED, project.getAspectState("earth"));
        verify(menus).playConfirmRefreshWave(player, station, top, before == 6 ? null : "earth");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void largePositiveAwardsReachTheCapWithoutWrapping(boolean offRecipe) {
        Cache.experimentPrimaryPoints = Integer.MAX_VALUE;
        Cache.experimentRejectPoints = Integer.MAX_VALUE;
        String aspectId = offRecipe ? "earth" : "fire";
        prepareExperiment(aspectId, null, Map.of("fire", Integer.MAX_VALUE, "water", 10));
        project.getAspectPoints().put(aspectId, 1);

        confirm();

        assertEquals(Integer.MAX_VALUE, project.getAspectPoints(aspectId));
        assertEquals(offRecipe ? AspectState.REJECTED : AspectState.CONFIRMED, project.getAspectState(aspectId));
        verify(points).trySpendMentalPoints(player, 1);
        store.verify(() -> StationStore.saveStation(station));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void confirmingAspectRevealsProductAtThresholdOnlyOnce(boolean alreadyRevealed) {
        prepareExperiment("fire", null, Map.of("fire", 2, "water", 10));
        aspect("fire", "input.sound");
        project.setProductRevealed(alreadyRevealed);

        confirm();

        assertEquals(AspectState.CONFIRMED, project.getAspectState("fire"));
        assertTrue(project.isProductRevealed());
        verify(player).sendMessage("experiment.confirmed {aspect=Fire}");
        verify(player, times(alreadyRevealed ? 0 : 1)).sendMessage("product.revealed");
        assertSame(station, manager.getStationAt(location));
    }

    @Test
    void confirmingBelowProductThresholdKeepsProductHiddenAndFallsBackToAspectId() {
        prepareExperiment("fire", null, Map.of("fire", 2, "water", 10));
        YamlConfiguration config = outputConfig(Map.of("fire", 2, "water", 10));
        config.set("product_reveal.base_after_confirmed_aspects", 2);
        OutputLoader.get().put("output", new OutputDef("output", config));

        confirm();

        assertEquals(AspectState.CONFIRMED, project.getAspectState("fire"));
        assertFalse(project.isProductRevealed());
        verify(player).sendMessage("experiment.confirmed {aspect=fire}");
        verify(player, never()).sendMessage("product.revealed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-result", "blank-result", "unbuildable", "spawn-failed"})
    void rewardFailureKeepsTheCompletedRecipeAvailable(String failure) {
        prepareExperiment("fire", null, Map.of("fire", 2));
        if (failure.equals("missing-result") || failure.equals("blank-result")) {
            project = new ActiveProject("input", "output", failure.equals("missing-result") ? null : " ");
            station.setProject(project);
        }
        if (failure.equals("spawn-failed")) {
            ItemStack reward = stack(Material.DIAMOND, 1);
            items.when(() -> ItemRef.build("vanilla.DIAMOND")).thenReturn(reward);
        }

        confirm();

        assertSame(station, manager.getStationAt(location));
        assertFalse(project.isCompleted());
        assertEquals(2, project.getAspectPoints("fire"));
        store.verify(() -> StationStore.saveStation(station));
        verify(logger).warning(contains(failure.endsWith("result") ? "no resolved result"
                : failure.equals("unbuildable") ? "Could not build" : "Could not spawn"));
        verifyNoInteractions(pluginManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-meta", "unnamed-meta", "named-meta"})
    void completingRecipeSpawnsRewardPublishesEventAndFreesLectern(String metadata) {
        prepareExperiment("fire", "water", Map.of("fire", 2, "water", 1));
        ItemStack reward = stack(Material.DIAMOND, 1);
        if (!metadata.equals("no-meta")) {
            ItemMeta meta = mock(ItemMeta.class);
            when(reward.getItemMeta()).thenReturn(meta);
            when(meta.hasDisplayName()).thenReturn(metadata.equals("named-meta"));
            when(meta.getDisplayName()).thenReturn("Special discovery");
        }
        items.when(() -> ItemRef.build("vanilla.DIAMOND")).thenReturn(reward);
        rewards.when(() -> ResultSpawnEffects.spawnAtStation(location, reward)).thenReturn(true);

        confirm();

        assertTrue(project.isCompleted());
        assertNull(manager.getStationAt(location));
        assertTrue(topItems.isEmpty());
        verify(player).closeInventory();
        verify(player).sendMessage("station.completed");
        verify(player).sendMessage("result.granted {item="
                + (metadata.equals("named-meta") ? "Special discovery" : "DIAMOND") + "}");
        ArgumentCaptor<ResearchCompleteEvent> event = ArgumentCaptor.forClass(ResearchCompleteEvent.class);
        verify(pluginManager).callEvent(event.capture());
        assertSame(player, event.getValue().getPlayer());
        assertEquals("input", event.getValue().getInputId());
        assertEquals("output", event.getValue().getOutputId());
        assertEquals("vanilla.DIAMOND", event.getValue().getResultItemRef());
        rewards.verify(() -> ResultSpawnEffects.spawnAtStation(location, reward));
        completeEffects.verify(() -> StationCompleteEffects.play(player, location));
        store.verify(() -> StationStore.deleteStation(location));
        store.verify(() -> StationStore.saveStation(any()), never());
        verify(menus, never()).populateMain(any(), any(), any());
    }

    private ItemStack prepareExperiment(String primary, String secondary, Map<String, Integer> recipe) {
        output(recipe);
        ItemStack item = stack(Material.COAL, 1);
        topItems.put(GridLayout.SLOT_EXPERIMENT, item);
        registry.when(() -> AspectItemRegistry.findByItemStack(item))
                .thenReturn(new ExperimentMatch("vanilla.COAL", primary, secondary));
        return item;
    }

    private void confirm() {
        topItems.put(GridLayout.SLOT_CONFIRM_EXPERIMENT, stack(Material.PAPER, 1));
        manager.onInventoryClick(click(GridLayout.SLOT_CONFIRM_EXPERIMENT));
    }

    private void aspect(String id, String inputSound) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", "Fire");
        config.set("sounds.input", inputSound);
        config.set("sounds.confirm", "confirm.sound");
        config.set("sounds.volume", 1.0);
        config.set("sounds.pitch", 1.0);
        AspectLoader.get().put(id, new AspectDef(id, config));
    }

    private <T extends AutoCloseable> T scoped(T value) {
        mocks.add(value);
        return value;
    }

    private void noStations() {
        store.when(StationStore::loadAll).thenReturn(List.of());
        manager.start();
    }

    private void scrapMenu() {
        title.set("Confirm Scrap");
        when(top.getSize()).thenReturn(9);
    }

    private InventoryClickEvent click(int rawSlot) {
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private PlayerInteractEvent interact(Action action, Block block, EquipmentSlot hand) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
        when(event.getAction()).thenReturn(action);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(hand);
        when(event.isCancelled()).thenAnswer(call -> cancelled.get());
        doAnswer(call -> { cancelled.set(call.getArgument(0)); return null; }).when(event).setCancelled(anyBoolean());
        return event;
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack stack = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenAnswer(call -> count.get());
        doAnswer(call -> { count.set(call.getArgument(0)); return null; }).when(stack).setAmount(anyInt());
        when(stack.clone()).thenAnswer(call -> stack(material, count.get()));
        return stack;
    }

    private InputDef input(String startItem, int amount, String outputId) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("start_item.item", startItem);
        config.set("start_item.amount", amount);
        config.set("outputs", List.of(Map.of("id", outputId, "weight", 1)));
        return new InputDef("input", config);
    }

    private YamlConfiguration outputConfig(Map<String, Integer> aspects) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("result.item", "vanilla.DIAMOND");
        config.set("product_reveal.base_after_confirmed_aspects", 1);
        for (Map.Entry<String, Integer> entry : aspects.entrySet()) {
            config.set("aspects." + entry.getKey() + ".required_points", entry.getValue());
        }
        return config;
    }

    private OutputDef output(Map<String, Integer> aspects) {
        OutputDef result = new OutputDef("output", outputConfig(aspects));
        OutputLoader.get().put("output", result);
        return result;
    }
}
