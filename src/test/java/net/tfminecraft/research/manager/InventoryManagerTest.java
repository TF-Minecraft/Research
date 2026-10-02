package net.tfminecraft.research.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.GuiCache;
import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.loader.AspectLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.model.*;
import net.tfminecraft.research.registry.AspectItemRegistry;
import net.tfminecraft.research.util.GridLayout;
import net.tfminecraft.research.util.ItemRef;
import net.tfminecraft.research.util.MmoAttributes;

@SuppressWarnings("deprecation")
class InventoryManagerTest {
    private final Map<Field, Object> savedFields = new LinkedHashMap<>();
    private final List<AutoCloseable> mocks = new ArrayList<>();
    private final Map<String, AspectDef> aspects = new HashMap<>();
    private final Set<String> missingItems = new HashSet<>();
    private ServerMock server;
    private PlayerMock player;
    private Research previousPlugin;
    private Research plugin;
    private PlayerManager playerManager;
    private InventoryManager manager;
    private ActiveProject project;
    private ResearchStation station;
    private OutputDef output;
    private MockedStatic<ItemRef> items;
    private MockedStatic<OutputLoader> outputs;
    private MockedStatic<AspectItemRegistry> registry;

    @BeforeEach void setup() throws Exception {
        for (Class<?> type : List.of(Cache.class, GuiCache.class)) {
            for (Field f : type.getFields()) {
                if (!java.lang.reflect.Modifier.isFinal(f.getModifiers())) savedFields.put(f, f.get(null));
            }
        }
        previousPlugin = Research.plugin;
        server = MockBukkit.mock();
        player = server.addPlayer();
        plugin = mock(Research.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getName()).thenReturn("Research");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        Research.plugin = plugin;
        playerManager = mock(PlayerManager.class);
        when(playerManager.getMentalPoints(player)).thenReturn(17);
        manager = new InventoryManager(playerManager);
        Cache.aspectBaseRevealPercent = .2; Cache.aspectMinRevealPercent = .1;
        Cache.aspectBaseConfirmPercent = .8; Cache.aspectMinConfirmPercent = .3;
        Cache.experimentPrimaryPoints = 4; Cache.experimentSecondaryPoints = 2;
        GuiCache.confirmButton = "vanilla.LIME_CONCRETE";
        GuiCache.cancelButton = "vanilla.BARRIER";
        GuiCache.filler = "vanilla.GRAY_STAINED_GLASS_PANE";
        GuiCache.gridFiller = "vanilla.GRAY_STAINED_GLASS_PANE";
        GuiCache.leftFiller = "vanilla.BLACK_STAINED_GLASS_PANE";
        GuiCache.aspectProgressInactive = "vanilla.GRAY_STAINED_GLASS_PANE";
        GuiCache.aspectProgressUnknown = "vanilla.YELLOW_STAINED_GLASS_PANE";
        GuiCache.aspectProgressRevealed = "vanilla.ORANGE_STAINED_GLASS_PANE";
        GuiCache.aspectRowDivider = "vanilla.BLACK_STAINED_GLASS_PANE";
        GuiCache.aspectTestedUnknown = "vanilla.PAPER";
        GuiCache.lockedAspect = "vanilla.IRON_NUGGET";
        GuiCache.mentalPointsIcon = "vanilla.LIGHT";
        GuiCache.wavePulseDefault = "vanilla.LIGHT_BLUE_STAINED_GLASS_PANE";
        GuiCache.inventoryTitleLabel = "Research Station"; GuiCache.scrapConfirmTitleLabel = "Confirm Scrap";
        GuiCache.scrapConfirmYesLabel = "Scrap it"; GuiCache.scrapConfirmNoLabel = "Keep it";
        GuiCache.scrapButtonLabel = "Scrap"; GuiCache.confirmExperimentLabel = "Confirm";
        GuiCache.experimentCompletedLabel = "Completed";
        GuiCache.undiscoveredAspectName = "Undiscovered"; GuiCache.undiscoveredAspectLore = List.of("Keep testing");
        GuiCache.colors = Map.of("aspect_confirmed", "#00ff00", "aspect_hidden", "#888888");
        scoped(mockStatic(MmoAttributes.class));
        MockedStatic<Messages> messages = scoped(mockStatic(Messages.class));
        messages.when(() -> Messages.formatBody(anyString(), anyMap())).thenAnswer(i -> {
            Map<String, String> values = i.getArgument(1);
            return i.<String>getArgument(0) + " " + new TreeMap<>(values);
        });
        items = scoped(mockStatic(ItemRef.class, CALLS_REAL_METHODS));
        items.when(() -> ItemRef.build(nullable(String.class))).thenAnswer(i -> {
            String ref = i.getArgument(0);
            if (ref == null || missingItems.contains(ref) || !ref.startsWith("vanilla.")) return null;
            Material material = Material.matchMaterial(ref.substring("vanilla.".length()));
            return material == null ? null : new ItemStack(material);
        });
        items.when(() -> ItemRef.matches(nullable(ItemStack.class), nullable(String.class))).thenAnswer(i -> {
            ItemStack item = i.getArgument(0); String ref = i.getArgument(1);
            return item != null && ref != null && ref.equals("vanilla." + item.getType().name());
        });
        outputs = scoped(mockStatic(OutputLoader.class));
        outputs.when(() -> OutputLoader.getById("result")).thenAnswer(i -> output);
        MockedStatic<AspectLoader> aspectLoader = scoped(mockStatic(AspectLoader.class));
        aspectLoader.when(() -> AspectLoader.getById(anyString())).thenAnswer(i -> aspects.get(i.getArgument(0)));
        registry = scoped(mockStatic(AspectItemRegistry.class));
        output = output(Map.of("fire", 10, "water", 10, "earth", 10, "air", 10, "cold", 10, "void", 0));
        for (String aspect : output.getAspects().keySet()) aspects.put(aspect, aspect(aspect, "vanilla.BLAZE_POWDER", "vanilla.RED_STAINED_GLASS_PANE"));
        project = new ActiveProject("input", "result", "vanilla.DIAMOND");
        project.setAspectSlotOrder(List.of("fire", "water", "earth", "air", "cold", "void"));
        station = new ResearchStation(new Location(server.addSimpleWorld("lab"), 1, 64, 2), player.getUniqueId(), project);
    }

    @AfterEach void cleanup() throws Exception {
        Collections.reverse(mocks);
        for (AutoCloseable mock : mocks) mock.close();
        MockBukkit.unmock();
        Research.plugin = previousPlugin;
        for (var entry : savedFields.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    private <T extends AutoCloseable> T scoped(T mock) { mocks.add(mock); return mock; }
    private OutputDef output(Map<String, Integer> requirements) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mystery_display.item", "vanilla.CHEST");
        yaml.set("product_display.item", "vanilla.DIAMOND");
        requirements.forEach((id, required) -> yaml.set("aspects." + id + ".required_points", required));
        return new OutputDef("result", yaml);
    }
    private AspectDef aspect(String id, String item, String pulse) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", id.toUpperCase(Locale.ROOT)); yaml.set("lore", List.of("About " + id));
        yaml.set("display.item", item); yaml.set("pulse_color", pulse);
        return new AspectDef(id, yaml);
    }
    private Inventory openMain() {
        manager.openMain(player, station);
        return player.getOpenInventory().getTopInventory();
    }
    private String name(ItemStack item) { return ChatColor.stripColor(item.getItemMeta().getDisplayName()); }
    private void assertProgress(Inventory inventory, int row, int filled, Material material) {
        List<Integer> slots = GridLayout.getAspectProgressSlots(row);
        for (int pane = 0; pane < slots.size(); pane++) {
            ItemStack item = inventory.getItem(slots.get(pane));
            assertEquals(pane < filled ? material : Material.GRAY_STAINED_GLASS_PANE, item.getType(), "row " + row + " pane " + pane);
        }
    }

    @Test void titlesUseConfiguredLabelsAndFallbackWhenUnset() {
        assertEquals("Research Station", ChatColor.stripColor(InventoryManager.mainInventoryTitle()));
        assertEquals("Confirm Scrap", ChatColor.stripColor(InventoryManager.scrapConfirmTitle()));
        GuiCache.inventoryTitleLabel = "Research Lab"; GuiCache.scrapConfirmTitleLabel = "Really scrap?";
        assertEquals("Research Lab", ChatColor.stripColor(InventoryManager.mainInventoryTitle()));
        assertEquals("Really scrap?", ChatColor.stripColor(InventoryManager.scrapConfirmTitle()));
        GuiCache.inventoryTitleLabel = null; GuiCache.scrapConfirmTitleLabel = null;
        assertEquals("Research Station", ChatColor.stripColor(InventoryManager.mainInventoryTitle()));
        assertEquals("Confirm Scrap", ChatColor.stripColor(InventoryManager.scrapConfirmTitle()));
    }

    @Test void mainMenuBindsStationAndRendersEveryDiscoveryState() {
        project.setAspectState("fire", AspectState.CONFIRMED); project.addAspectPoints("fire", 6, 10);
        project.setAspectState("water", AspectState.REJECTED); project.addAspectPoints("water", 5, 10);
        project.setAspectState("earth", AspectState.TESTING); project.addAspectPoints("earth", 9, 10);
        project.setAspectState("air", AspectState.TESTING); project.addAspectPoints("air", 3, 10);
        project.setAspectState("void", AspectState.CONFIRMED);
        Inventory inv = openMain();
        assertEquals(54, inv.getSize());
        StationMenuHolder holder = assertInstanceOf(StationMenuHolder.class, inv.getHolder());
        assertSame(inv, holder.getInventory()); assertEquals(station.getLocation(), holder.getStationLocation());
        assertEquals(Material.CHEST, inv.getItem(GridLayout.SLOT_PRODUCT).getType());
        assertEquals("FIRE", name(inv.getItem(GridLayout.getAspectLockSlot(0))));
        assertEquals("WATER", name(inv.getItem(GridLayout.getAspectLockSlot(1))));
        assertEquals("EARTH", name(inv.getItem(GridLayout.getAspectLockSlot(2))));
        assertEquals("Undiscovered", name(inv.getItem(GridLayout.getAspectLockSlot(3))));
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(GridLayout.getAspectLockSlot(4)).getType());
        assertProgress(inv, 0, 3, Material.ORANGE_STAINED_GLASS_PANE);
        assertProgress(inv, 1, 0, Material.ORANGE_STAINED_GLASS_PANE);
        assertProgress(inv, 2, 4, Material.ORANGE_STAINED_GLASS_PANE);
        assertProgress(inv, 3, 1, Material.YELLOW_STAINED_GLASS_PANE);
        assertProgress(inv, 4, 0, Material.YELLOW_STAINED_GLASS_PANE);
        assertProgress(inv, 5, 0, Material.ORANGE_STAINED_GLASS_PANE);
        assertTrue(inv.getItem(GridLayout.getAspectProgressSlots(0).getFirst()).getItemMeta().getLore().getFirst().contains("current=6"));
        assertFalse(inv.getItem(GridLayout.getAspectProgressSlots(3).getFirst()).getItemMeta().hasLore());
        assertTrue(name(inv.getItem(GridLayout.SLOT_MENTAL_POINTS)).contains("amount=17"));
        assertEquals("Scrap", name(inv.getItem(GridLayout.SLOT_SCRAP)));
        assertEquals("Confirm", name(inv.getItem(GridLayout.SLOT_CONFIRM_EXPERIMENT)));
        assertNull(inv.getItem(GridLayout.SLOT_EXPERIMENT));
        assertTrue(inv.getItem(1).getItemMeta().isHideTooltip());
    }

    @Test void revealedCompletedProductUpdatesWithoutOverwritingExperimentOrExistingPadding() {
        Inventory inv = openMain(); ItemStack experiment = new ItemStack(Material.STONE);
        inv.setItem(GridLayout.SLOT_EXPERIMENT, experiment); inv.setItem(1, new ItemStack(Material.EMERALD));
        project.setProductRevealed(true); project.setCompleted(true);
        manager.populateMain(player, station, inv);
        assertEquals(Material.DIAMOND, inv.getItem(GridLayout.SLOT_PRODUCT).getType());
        assertEquals("Completed", name(inv.getItem(GridLayout.SLOT_CONFIRM_EXPERIMENT)));
        assertEquals(experiment, inv.getItem(GridLayout.SLOT_EXPERIMENT));
        assertEquals(Material.EMERALD, inv.getItem(1).getType());
    }

    @Test void missingOutputLeavesInventoryUntouchedAndMissingProductDoesNotInventOne() {
        Inventory inv = server.createInventory(null, 54); inv.setItem(1, new ItemStack(Material.EMERALD));
        output = null; manager.populateMain(player, station, inv);
        assertEquals(Material.EMERALD, inv.getItem(1).getType()); assertNull(inv.getItem(GridLayout.SLOT_PRODUCT));
        output = output(Map.of()); missingItems.add("vanilla.CHEST");
        manager.populateMain(player, station, inv); assertNull(inv.getItem(GridLayout.SLOT_PRODUCT));
    }

    @Test void missingSavedOrderIsAssignedAndDecoyOrStaleRowsAreBlank() {
        output = output(Map.of("fire", 10)); project.setAspectSlotOrder(List.of());
        Inventory inv = openMain();
        assertEquals(6, project.getAspectSlotOrder().size());
        assertEquals(1, project.getAspectSlotOrder().stream().filter("fire"::equals).count());
        project.setAspectSlotOrder(List.of("no-longer-configured", "", "", "", "", ""));
        manager.populateMain(player, station, inv);
        for (int row = 0; row < 6; row++) {
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(GridLayout.getAspectLockSlot(row)).getType());
            assertProgress(inv, row, 0, Material.ORANGE_STAINED_GLASS_PANE);
        }
    }

    @Test void unknownAspectDefinitionUsesStableIdAndPaperWhileConfirmedZeroProgressStaysEmpty() {
        project.setAspectState("fire", AspectState.CONFIRMED); aspects.remove("fire");
        Inventory inv = openMain();
        ItemStack label = inv.getItem(GridLayout.getAspectLockSlot(0));
        assertEquals(Material.PAPER, label.getType()); assertEquals("fire", name(label));
        assertProgress(inv, 0, 0, Material.ORANGE_STAINED_GLASS_PANE);
    }

    @Test void invalidItemReferencesUseUsableFallbackIconsAndProgressPanes() {
        missingItems.addAll(List.of(GuiCache.leftFiller, GuiCache.gridFiller, GuiCache.filler,
                GuiCache.aspectProgressInactive, GuiCache.aspectProgressRevealed, GuiCache.aspectProgressUnknown,
                GuiCache.aspectRowDivider, GuiCache.aspectTestedUnknown, GuiCache.lockedAspect,
                GuiCache.mentalPointsIcon, GuiCache.confirmButton, GuiCache.cancelButton, "vanilla.BLAZE_POWDER"));
        project.setAspectState("fire", AspectState.CONFIRMED); project.addAspectPoints("fire", 10, 10);
        project.addAspectPoints("air", 3, 10);
        Inventory inv = openMain();
        assertEquals(Material.PAPER, inv.getItem(GridLayout.getAspectLockSlot(0)).getType());
        assertEquals(Material.PAPER, inv.getItem(GridLayout.getAspectLockSlot(3)).getType());
        assertEquals(Material.LIGHT, inv.getItem(GridLayout.SLOT_MENTAL_POINTS).getType());
        assertEquals(Material.BARRIER, inv.getItem(GridLayout.SLOT_SCRAP).getType());
        assertEquals(Material.LIME_CONCRETE, inv.getItem(GridLayout.SLOT_CONFIRM_EXPERIMENT).getType());
        assertProgress(inv, 0, 5, Material.ORANGE_STAINED_GLASS_PANE);
        assertProgress(inv, 3, 1, Material.YELLOW_STAINED_GLASS_PANE);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(1).getType());
    }

    @Test void secondaryFallbackReferencesAreUsedBeforeHardcodedMaterials() {
        GuiCache.aspectProgressInactive = "missing"; GuiCache.gridFiller = "missing";
        GuiCache.filler = "vanilla.WHITE_STAINED_GLASS_PANE"; GuiCache.aspectTestedUnknown = "missing";
        project.addAspectPoints("air", 3, 10);
        Inventory inv = openMain();
        assertEquals(Material.IRON_NUGGET, inv.getItem(GridLayout.getAspectLockSlot(3)).getType());
        assertEquals(Material.WHITE_STAINED_GLASS_PANE, inv.getItem(GridLayout.getAspectProgressSlots(4).getFirst()).getType());
    }

    @Test void previewShowsBothAspectsPointsAndTheirIndependentStatuses() {
        Inventory inv = openMain(); ItemStack experiment = new ItemStack(Material.STONE);
        registry.when(() -> AspectItemRegistry.findByItemStack(experiment)).thenReturn(new ExperimentMatch("stone", "fire", "water"));
        project.setAspectState("fire", AspectState.CONFIRMED); project.setAspectState("water", AspectState.REJECTED);
        manager.updateExperimentPreview(inv, experiment, project);
        ItemStack primary = inv.getItem(GridLayout.SLOT_PRIMARY_PREVIEW), secondary = inv.getItem(GridLayout.SLOT_SECONDARY_PREVIEW);
        assertEquals(4, primary.getAmount()); assertEquals(2, secondary.getAmount());
        assertEquals("FIRE", name(primary)); assertEquals("WATER", name(secondary));
        assertTrue(primary.getItemMeta().getLore().stream().anyMatch(s -> s.contains("status_confirmed")));
        assertTrue(secondary.getItemMeta().getLore().stream().anyMatch(s -> s.contains("status_rejected")));
        assertTrue(primary.getItemMeta().getLore().stream().anyMatch(s -> s.contains("points=4")));
        project.setAspectState("fire", AspectState.TESTING); Cache.experimentPrimaryPoints = 100;
        missingItems.add("vanilla.BLAZE_POWDER"); manager.updateExperimentPreview(inv, experiment, project);
        primary = inv.getItem(GridLayout.SLOT_PRIMARY_PREVIEW);
        assertEquals(Material.PAPER, primary.getType()); assertEquals(64, primary.getAmount());
        assertTrue(primary.getItemMeta().getLore().stream().anyMatch(s -> s.contains("status_testing")));
    }

    @Test void absentUnknownOrEmptyExperimentsClearBothPreviewSlots() {
        Inventory inv = openMain(); ItemStack stone = new ItemStack(Material.STONE);
        for (ItemStack empty : Arrays.asList(null, new ItemStack(Material.AIR), stone)) {
            inv.setItem(GridLayout.SLOT_PRIMARY_PREVIEW, stone); inv.setItem(GridLayout.SLOT_SECONDARY_PREVIEW, stone);
            manager.updateExperimentPreview(inv, empty, project);
            assertNull(inv.getItem(GridLayout.SLOT_PRIMARY_PREVIEW)); assertNull(inv.getItem(GridLayout.SLOT_SECONDARY_PREVIEW));
        }
    }

    @Test void previewOmitsBlankMissingAndNonpositiveAspects() {
        Inventory inv = openMain(); ItemStack stone = new ItemStack(Material.STONE);
        for (String id : Arrays.asList(null, "", "missing", "fire")) {
            Cache.experimentPrimaryPoints = id != null && id.equals("fire") ? 0 : 2;
            registry.when(() -> AspectItemRegistry.findByItemStack(stone)).thenReturn(new ExperimentMatch("stone", id, null));
            manager.updateExperimentPreview(inv, stone, project);
            assertNull(inv.getItem(GridLayout.SLOT_PRIMARY_PREVIEW)); assertNull(inv.getItem(GridLayout.SLOT_SECONDARY_PREVIEW));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void scrapMenuUsesStationHolderAndBothActionsEvenWhenConfiguredIconsAreMissing(boolean missing) {
        if (missing) missingItems.addAll(List.of(GuiCache.confirmButton, GuiCache.cancelButton));
        manager.openScrapConfirm(player, station);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(9, inv.getSize()); assertEquals(InventoryManager.scrapConfirmTitle(), player.getOpenInventory().getTitle());
        assertEquals(station.getLocation(), ((StationMenuHolder) inv.getHolder()).getStationLocation());
        assertEquals("Scrap it", name(inv.getItem(InventoryManager.CONFIRM_SCRAP_YES)));
        assertEquals("Keep it", name(inv.getItem(InventoryManager.CONFIRM_SCRAP_NO)));
        assertEquals(missing ? Material.PAPER : Material.LIME_CONCRETE, inv.getItem(InventoryManager.CONFIRM_SCRAP_YES).getType());
        assertEquals(missing ? Material.PAPER : Material.BARRIER, inv.getItem(InventoryManager.CONFIRM_SCRAP_NO).getType());
    }

    @Test void confirmationWavePulsesOutwardPreservesContentsAndRepopulatesUpdatedProject() {
        Inventory inv = openMain(); ItemStack experiment = new ItemStack(Material.STONE);
        inv.setItem(GridLayout.SLOT_EXPERIMENT, experiment);
        inv.setItem(2, new ItemStack(Material.EMERALD)); inv.setItem(9, null);
        ItemStack legacy = new ItemStack(Material.GRAY_STAINED_GLASS_PANE); inv.setItem(11, legacy);
        ItemStack hiddenGlass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE); ItemRef.applyBlankDisplay(hiddenGlass); inv.setItem(18, hiddenGlass);
        ItemStack visibleGlass = new ItemStack(Material.PINK_STAINED_GLASS_PANE); inv.setItem(19, visibleGlass);
        ItemStack originalAspect = inv.getItem(GridLayout.getAspectLockSlot(0)).clone();
        manager.playConfirmRefreshWave(player, station, inv, "fire");
        server.getScheduler().performOneTick();
        assertEquals(Material.RED_STAINED_GLASS_PANE, inv.getItem(1).getType());
        assertEquals(Material.RED_STAINED_GLASS_PANE, inv.getItem(9).getType());
        assertEquals(Material.RED_STAINED_GLASS_PANE, inv.getItem(11).getType());
        assertEquals(Material.RED_STAINED_GLASS_PANE, inv.getItem(18).getType());
        assertEquals(Material.EMERALD, inv.getItem(2).getType()); assertEquals(visibleGlass, inv.getItem(19));
        assertEquals(originalAspect, inv.getItem(GridLayout.getAspectLockSlot(0)));
        assertEquals(experiment, inv.getItem(GridLayout.SLOT_EXPERIMENT));
        project.setProductRevealed(true);
        server.getScheduler().performTicks(12);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(1).getType());
        assertEquals(Material.DIAMOND, inv.getItem(GridLayout.SLOT_PRODUCT).getType());
        assertEquals(Material.EMERALD, inv.getItem(2).getType()); assertEquals(experiment, inv.getItem(GridLayout.SLOT_EXPERIMENT));
        server.getScheduler().performTicks(12);
    }

    @ParameterizedTest @ValueSource(strings = {"", "unknown", "no-color", "bad-color", "no-default", "null"})
    void pulseUsesDefaultOrBuiltInFallbackForMissingAspectColors(String kind) {
        if (kind.equals("no-color")) aspects.put(kind, aspect(kind, "vanilla.PAPER", ""));
        if (kind.equals("bad-color")) aspects.put(kind, aspect(kind, "vanilla.PAPER", "invalid"));
        if (kind.equals("no-default")) GuiCache.wavePulseDefault = "invalid";
        Inventory inv = openMain();
        manager.playConfirmRefreshWave(player, station, inv, kind.equals("null") ? null : kind);
        server.getScheduler().performOneTick();
        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS_PANE, inv.getItem(1).getType());
        assertTrue(inv.getItem(1).getItemMeta().isHideTooltip());
        server.getScheduler().performTicks(12);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(1).getType());
    }

    @Test void waveStopsWhenViewerLeavesOrChangesInventoryOrTitle() {
        Inventory inv = openMain(); Player offline = mock(Player.class);
        manager.playConfirmRefreshWave(offline, station, inv, null);
        server.getScheduler().performOneTick(); server.getScheduler().performTicks(12);
        manager.playConfirmRefreshWave(player, station, inv, null);
        player.openInventory(server.createInventory(null, 9, "Other menu"));
        server.getScheduler().performOneTick(); server.getScheduler().performTicks(12);
        Player renamedView = mock(Player.class); InventoryView view = mock(InventoryView.class);
        when(renamedView.isOnline()).thenReturn(true); when(renamedView.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inv); when(view.getTitle()).thenReturn("Other title");
        manager.playConfirmRefreshWave(renamedView, station, inv, null);
        server.getScheduler().performOneTick(); server.getScheduler().performTicks(12);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(1).getType());
        verify(offline, times(1)).isOnline();
        verify(renamedView, times(1)).isOnline();
    }
}
