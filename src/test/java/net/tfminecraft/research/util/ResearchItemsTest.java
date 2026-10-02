package net.tfminecraft.research.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.Indyuce.mmocore.api.player.PlayerData;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.loader.InputLoader;
import net.tfminecraft.research.manager.PlayerManager;
import net.tfminecraft.research.model.AspectDef;
import net.tfminecraft.research.model.ExperimentMatch;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.registry.AspectItemRegistry;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.focus.FocusService;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemChecker;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;

class ResearchItemsTest {
    private final List<AutoCloseable> mocks = new ArrayList<>();
    private Map<String, InputDef> originalInputs;
    private Map<String, String> originalPrimary;
    private Map<String, String> originalSecondary;
    private Object originalMatches;
    private Research originalPlugin;
    private PlayerManager originalPlayerManager;
    private Locale originalLocale;
    private Logger logger;
    private ItemCreator creator;
    private ItemChecker checker;
    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() throws Exception {
        originalLocale = Locale.getDefault();
        originalPlugin = Research.plugin;
        originalPlayerManager = PlayerManager.getInstance();
        originalInputs = new LinkedHashMap<>(InputLoader.get());
        originalPrimary = new LinkedHashMap<>(registryMap("primaryByRef"));
        originalSecondary = new LinkedHashMap<>(registryMap("secondaryByRef"));
        originalMatches = field(AspectItemRegistry.class, "matches").get(null);
        InputLoader.get().clear();
        AspectItemRegistry.rebuild(Map.of());
        Research.plugin = mock(Research.class);
        logger = mock(Logger.class);
        when(Research.plugin.getLogger()).thenReturn(logger);
        ItemAPI api = mock(ItemAPI.class);
        creator = mock(ItemCreator.class);
        checker = mock(ItemChecker.class);
        when(api.getCreator()).thenReturn(creator);
        when(api.getChecker()).thenReturn(checker);
        MockedStatic<TLibs> tlibs = scoped(mockStatic(TLibs.class));
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
    }

    @AfterEach
    void restoreState() throws Exception {
        for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
        InputLoader.get().clear();
        InputLoader.get().putAll(originalInputs);
        registryMap("primaryByRef").clear();
        registryMap("primaryByRef").putAll(originalPrimary);
        registryMap("secondaryByRef").clear();
        registryMap("secondaryByRef").putAll(originalSecondary);
        field(AspectItemRegistry.class, "matches").set(null, originalMatches);
        field(PlayerManager.class, "instance").set(null, originalPlayerManager);
        Research.plugin = originalPlugin;
        Locale.setDefault(originalLocale);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void emptyReferencesAreNeitherRecognizedNorBuilt(String ref) {
        assertEquals("", ItemRef.normalize(ref));
        assertFalse(ItemRef.hasKnownPrefix(ref));
        assertNull(ItemRef.build(ref));
        assertFalse(ItemRef.isValid(ref));
        verifyNoInteractions(creator);
    }

    @Test
    void knownPrefixesAreRecognizedAndVanillaAliasesAreNormalized() {
        assertEquals("v.DIAMOND", ItemRef.normalize(" VANILLA.DIAMOND "));
        for (String prefix : List.of("vanilla.", "v.", "m.", "ia.")) {
            assertTrue(ItemRef.hasKnownPrefix(prefix + "example"));
        }
        assertEquals("ia.namespace:item", ItemRef.normalize(" ia.namespace:item "));
        assertEquals("m.MATERIAL.GEM", ItemRef.normalize("m.MATERIAL.GEM"));
        assertFalse(ItemRef.hasKnownPrefix("unknown.ITEM"));
        assertNull(ItemRef.build("unknown.ITEM"));
        assertFalse(ItemRef.isValid("unknown.ITEM"));
        verifyNoInteractions(creator);
    }

    @Test
    void referenceParsingDoesNotDependOnTheMachineLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertEquals("v.DIAMOND", ItemRef.normalize("VANILLA.DIAMOND"));
        assertTrue(ItemRef.hasKnownPrefix("VANILLA.DIAMOND"));
        assertTrue(ItemRef.hasKnownPrefix("IA.namespace:item"));
    }

    @Test
    void buildingReturnsOneItemAndValidationUsesTheSameResolver() {
        ItemStack item = stack(Material.DIAMOND, 17);
        when(creator.getItemFromPath("v.DIAMOND")).thenReturn(item);

        assertSame(item, ItemRef.build(" vanilla.DIAMOND "));
        assertEquals(1, item.getAmount());
        assertTrue(ItemRef.isValid("vanilla.DIAMOND"));
        verify(creator, times(2)).getItemFromPath("v.DIAMOND");
    }

    @Test
    void unavailableAirOrFailingCreatorsProduceNoItem() {
        assertNull(ItemRef.build("vanilla.MISSING"));
        assertFalse(ItemRef.isValid("vanilla.MISSING"));
        ItemStack air = stack(Material.AIR, 0);
        when(creator.getItemFromPath("v.AIR")).thenReturn(air);
        assertNull(ItemRef.build("vanilla.AIR"));
        when(creator.getItemFromPath("v.BROKEN")).thenThrow(new IllegalStateException("integration unavailable"));
        assertNull(ItemRef.build("vanilla.BROKEN"));
    }

    @Test
    void matchesRejectMissingInputsAndDelegateNormalizedReferences() {
        ItemStack item = stack(Material.DIAMOND, 1);
        assertFalse(ItemRef.matches(null, "vanilla.DIAMOND"));
        assertFalse(ItemRef.matches(stack(Material.AIR, 0), "vanilla.DIAMOND"));
        assertFalse(ItemRef.matches(item, null));
        assertFalse(ItemRef.matches(item, " "));
        when(checker.checkItemWithPath(item, "v.DIAMOND")).thenReturn(true);
        assertTrue(ItemRef.matches(item, " vanilla.DIAMOND "));
        assertFalse(ItemRef.matches(item, "vanilla.STONE"));
        when(checker.checkItemWithPath(item, "v.BROKEN")).thenThrow(new IllegalArgumentException("bad path"));
        assertFalse(ItemRef.matches(item, "vanilla.BROKEN"));
    }

    @Test
    void blankDisplayRemovesNameLoreAttributesAndTooltip() {
        ItemRef.applyBlankDisplay(null);
        ItemStack noMeta = stack(Material.DIAMOND, 1);
        ItemRef.applyBlankDisplay(noMeta);
        verify(noMeta, never()).setItemMeta(any());
        ItemStack item = stack(Material.PAPER, 1);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getItemMeta()).thenReturn(meta);

        ItemRef.applyBlankDisplay(item);

        verify(meta).setDisplayName("");
        verify(meta).setLore(List.of());
        verify(meta).addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        verify(meta).setHideTooltip(true);
        verify(item).setItemMeta(meta);
    }

    @Test
    void inputMatcherSkipsDisabledMissingAndNonmatchingStartItems() {
        ItemStack item = stack(Material.PAPER, 3);
        assertNull(InputMatcher.findByStartItem(null));
        assertNull(InputMatcher.findByStartItem(stack(Material.AIR, 0)));
        assertNull(InputMatcher.findByStartItem(item));
        InputLoader.get().put("disabled", input("disabled", false, "vanilla.PAPER"));
        InputLoader.get().put("missing", input("missing", true, ""));
        InputLoader.get().put("other", input("other", true, "vanilla.STONE"));
        InputDef matching = input("match", true, "vanilla.PAPER");
        InputLoader.get().put("match", matching);
        when(checker.checkItemWithPath(item, "v.PAPER")).thenReturn(true);

        assertSame(matching, InputMatcher.findByStartItem(item));

        verify(checker, times(1)).checkItemWithPath(item, "v.PAPER");
        verify(checker).checkItemWithPath(item, "v.STONE");
    }

    @Test
    void registryMergesPrimarySecondaryAndReportsConflictingDefinitions() {
        Map<String, AspectDef> aspects = new LinkedHashMap<>();
        aspects.put("fire", aspect("fire", List.of("vanilla.COAL", "v.COAL", "vanilla.PAPER"),
                List.of("vanilla.COAL", "vanilla.DIAMOND")));
        aspects.put("water", aspect("water", List.of("v.COAL"),
                List.of("vanilla.DIAMOND", "v.DIAMOND", "v.PAPER")));
        materialMatching();

        AspectItemRegistry.rebuild(aspects);

        ExperimentMatch coal = AspectItemRegistry.findByItemStack(stack(Material.COAL, 1));
        assertEquals("v.COAL", coal.getItemRef());
        assertEquals("water", coal.getPrimaryAspect());
        assertNull(coal.getSecondaryAspect());
        ExperimentMatch paper = AspectItemRegistry.findByItemStack(stack(Material.PAPER, 1));
        assertEquals("fire", paper.getPrimaryAspect());
        assertEquals("water", paper.getSecondaryAspect());
        ExperimentMatch diamond = AspectItemRegistry.findByItemStack(stack(Material.DIAMOND, 1));
        assertNull(diamond.getPrimaryAspect());
        assertEquals("water", diamond.getSecondaryAspect());
        assertNull(AspectItemRegistry.findByItemStack(stack(Material.STONE, 1)));
        assertNull(AspectItemRegistry.findByItemStack(null));
        assertNull(AspectItemRegistry.findByItemStack(stack(Material.AIR, 0)));
        verify(logger).warning(contains("primary and secondary"));
        verify(logger).severe(contains("registered as primary"));
        verify(logger).severe(contains("registered as secondary"));
    }

    @Test
    void registryRebuildClearsOldDefinitionsAndAcceptsAnEmptyCatalog() {
        materialMatching();
        AspectItemRegistry.rebuild(Map.of("fire", aspect("fire", List.of("v.COAL"), List.of())));
        assertNotNull(AspectItemRegistry.findByItemStack(stack(Material.COAL, 1)));
        AspectItemRegistry.rebuild(null);
        assertNull(AspectItemRegistry.findByItemStack(stack(Material.COAL, 1)));
        AspectItemRegistry.rebuild(Map.of());
        assertNull(AspectItemRegistry.findByItemStack(stack(Material.COAL, 1)));
    }

    @Test
    void registrySkipsReferencesThatBecomeEmptyAfterTrimming() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("primary_items: [\"\\0\", 'vanilla.COAL']\nsecondary_items: [\"\\0\", 'vanilla.PAPER']\n");
        AspectDef aspect = new AspectDef("fire", config);
        materialMatching();

        AspectItemRegistry.rebuild(Map.of("fire", aspect));

        assertEquals("fire", AspectItemRegistry.findByItemStack(stack(Material.COAL, 1)).getPrimaryAspect());
        assertEquals("fire", AspectItemRegistry.findByItemStack(stack(Material.PAPER, 1)).getSecondaryAspect());
        assertFalse(registryMap("primaryByRef").containsKey(""));
        assertFalse(registryMap("secondaryByRef").containsKey(""));
        verifyNoInteractions(logger);
    }

    @Test
    void handAccessUsesOffHandOnlyWhenRequestedAndToleratesNoPlayer() {
        ItemStack main = stack(Material.PAPER, 2);
        ItemStack off = stack(Material.DIAMOND, 3);
        when(inventory.getItemInMainHand()).thenReturn(main);
        when(inventory.getItemInOffHand()).thenReturn(off);
        assertNull(PlayerInventoryUtil.getStackInHand(null, EquipmentSlot.HAND));
        assertSame(main, PlayerInventoryUtil.getStackInHand(player, EquipmentSlot.HAND));
        assertSame(main, PlayerInventoryUtil.getStackInHand(player, null));
        assertSame(off, PlayerInventoryUtil.getStackInHand(player, EquipmentSlot.OFF_HAND));
        PlayerInventoryUtil.setStackInHand(null, EquipmentSlot.HAND, main);
        PlayerInventoryUtil.setStackInHand(player, EquipmentSlot.HAND, off);
        PlayerInventoryUtil.setStackInHand(player, EquipmentSlot.OFF_HAND, main);
        verify(inventory).setItemInMainHand(off);
        verify(inventory).setItemInOffHand(main);
    }

    @Test
    void consumingRejectsInvalidOrInsufficientItemsWithoutChangingThem() {
        assertFalse(PlayerInventoryUtil.consumeFromHand(null, EquipmentSlot.HAND, "v.PAPER", 1));
        assertTrue(PlayerInventoryUtil.consumeFromHand(player, EquipmentSlot.HAND, "v.PAPER", 0));
        assertFalse(PlayerInventoryUtil.consumeFromHand(player, EquipmentSlot.HAND, null, 1));
        assertFalse(PlayerInventoryUtil.consumeFromHand(player, EquipmentSlot.HAND, " ", 1));
        assertFalse(PlayerInventoryUtil.consumeFromHand(player, EquipmentSlot.HAND, "v.PAPER", 1));
        ItemStack air = stack(Material.AIR, 0);
        when(inventory.getItemInMainHand()).thenReturn(air);
        assertFalse(PlayerInventoryUtil.consumeFromHand(player, EquipmentSlot.HAND, "v.PAPER", 1));
        ItemStack wrong = stack(Material.DIRT, 2);
        when(inventory.getItemInMainHand()).thenReturn(wrong);
        assertFalse(PlayerInventoryUtil.consumeFromHand(player, EquipmentSlot.HAND, "v.PAPER", 1));
        ItemStack few = stack(Material.PAPER, 1);
        when(inventory.getItemInMainHand()).thenReturn(few);
        when(checker.checkItemWithPath(few, "v.PAPER")).thenReturn(true);
        assertFalse(PlayerInventoryUtil.consumeFromHand(player, EquipmentSlot.HAND, "v.PAPER", 2));
        assertEquals(1, few.getAmount());
        verify(inventory, never()).setItemInMainHand(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consumingUsesTheSpecifiedHandAndClearsItOnlyWhenExhausted(boolean offHand) {
        ItemStack held = stack(Material.PAPER, 3);
        EquipmentSlot hand = offHand ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        when(inventory.getItemInMainHand()).thenReturn(offHand ? null : held);
        when(inventory.getItemInOffHand()).thenReturn(offHand ? held : null);
        when(checker.checkItemWithPath(held, "v.PAPER")).thenReturn(true);
        assertTrue(PlayerInventoryUtil.consumeFromHand(player, hand, "vanilla.PAPER", 2));
        assertEquals(1, held.getAmount());
        if (offHand) verify(inventory).setItemInOffHand(held);
        else verify(inventory).setItemInMainHand(held);
        assertTrue(PlayerInventoryUtil.consumeFromHand(player, hand, "vanilla.PAPER", 1));
        assertEquals(0, held.getAmount());
        if (offHand) {
            verify(inventory).setItemInOffHand(null);
            verify(inventory, never()).setItemInMainHand(any());
        } else {
            verify(inventory).setItemInMainHand(null);
            verify(inventory, never()).setItemInOffHand(any());
        }
    }

    @Test
    void mmocoreAttributesReadTotalsAndFallBackWhenUnavailable() {
        assertEquals(0, MmoAttributes.getTotal(null, "intelligence"));
        assertEquals(0, MmoAttributes.getTotal(player, null));
        assertEquals(0, MmoAttributes.getTotal(player, " "));
        PlayerData data = mock(PlayerData.class, RETURNS_DEEP_STUBS);
        when(data.getAttributes().getInstance("intelligence").getTotal()).thenReturn(12);
        try (MockedStatic<PlayerData> api = mockStatic(PlayerData.class)) {
            api.when(() -> PlayerData.get(player)).thenReturn(data);
            assertEquals(12, MmoAttributes.getTotal(player, "intelligence"));
            when(data.getAttributes().getInstance("absent")).thenReturn(null);
            assertEquals(0, MmoAttributes.getTotal(player, "absent"));
            api.when(() -> PlayerData.get(player)).thenThrow(new IllegalStateException("not loaded"));
            assertEquals(0, MmoAttributes.getTotal(player, "intelligence"));
        }
    }

    @Test
    void mentalPointsDelegateToTheCurrentFocusService() {
        PlayerManager manager = new PlayerManager();
        assertSame(manager, PlayerManager.getInstance());
        FocusService focus = mock(FocusService.class);
        when(focus.getPoints(player)).thenReturn(7);
        when(focus.trySpend(player, 2)).thenReturn(true);
        try (MockedStatic<RPCharacters> api = mockStatic(RPCharacters.class)) {
            assertEquals(0, manager.getMentalPoints(player));
            assertFalse(manager.trySpendMentalPoints(player, 2));
            manager.grantMentalPoints(player, 4);
            api.when(RPCharacters::getFocusService).thenReturn(focus);
            assertEquals(7, manager.getMentalPoints(player));
            assertTrue(manager.trySpendMentalPoints(player, 2));
            assertFalse(manager.trySpendMentalPoints(player, 20));
            manager.grantMentalPoints(player, 4);
            verify(focus).grant(player, 4);
        }
    }

    @Test
    void staticUtilitiesHavePrivateConstructors() throws Exception {
        for (Class<?> type : List.of(ItemRef.class, InputMatcher.class, AspectItemRegistry.class,
                PlayerInventoryUtil.class, MmoAttributes.class)) {
            Constructor<?> constructor = type.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance());
        }
    }

    private <T extends AutoCloseable> T scoped(T value) {
        mocks.add(value);
        return value;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> registryMap(String name) throws Exception {
        return (Map<String, String>) field(AspectItemRegistry.class, name).get(null);
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack result = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(result.getType()).thenReturn(material);
        when(result.getAmount()).thenAnswer(call -> count.get());
        doAnswer(call -> { count.set(call.getArgument(0)); return null; }).when(result).setAmount(anyInt());
        return result;
    }

    private InputDef input(String id, boolean enabled, String startItem) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", enabled);
        config.set("start_item.item", startItem);
        return new InputDef(id, config);
    }

    private AspectDef aspect(String id, List<String> primary, List<String> secondary) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("primary_items", primary);
        config.set("secondary_items", secondary);
        return new AspectDef(id, config);
    }

    private void materialMatching() {
        when(checker.checkItemWithPath(any(ItemStack.class), anyString())).thenAnswer(call -> {
            ItemStack stack = call.getArgument(0);
            return ("v." + stack.getType().name()).equals(call.getArgument(1));
        });
    }
}
