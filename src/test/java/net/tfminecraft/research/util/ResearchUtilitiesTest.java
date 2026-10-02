package net.tfminecraft.research.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.loader.TemplateLoader;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.AspectState;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResultTemplateDef;

class ResearchUtilitiesTest {
    @TempDir Path directory;
    private final Map<Field, Object> originalCache = new LinkedHashMap<>();
    private Map<String, ResultTemplateDef> originalTemplates;
    private Research originalPlugin;
    private Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        for (Field field : Cache.class.getFields()) originalCache.put(field, field.get(null));
        originalTemplates = new LinkedHashMap<>(TemplateLoader.get());
        TemplateLoader.get().clear();
        originalPlugin = Research.plugin;
        Research.plugin = mock(Research.class);
        logger = mock(Logger.class);
        when(Research.plugin.getLogger()).thenReturn(logger);
        Cache.externalModifiers = null;
        Cache.discoveryMmocoreId = "intelligence";
        Cache.aspectBaseRevealPercent = 0.2;
        Cache.aspectRevealPercentReductionPerPoint = 0.01;
        Cache.aspectMinRevealPercent = 0.1;
        Cache.aspectBaseConfirmPercent = 0.8;
        Cache.aspectConfirmPercentReductionPerPoint = 0.01;
        Cache.aspectMinConfirmPercent = 0.3;
        Cache.productRevealBonusPerPoint = 0.05;
        Cache.capProductRevealBonus = 0.5;
    }

    @AfterEach
    void restoreState() throws Exception {
        for (Map.Entry<Field, Object> entry : originalCache.entrySet()) entry.getKey().set(null, entry.getValue());
        TemplateLoader.get().clear();
        TemplateLoader.get().putAll(originalTemplates);
        Research.plugin = originalPlugin;
    }

    @Test
    void gridCoordinatesAndReservedSlotsDescribeTheSixRowMenu() {
        Set<Integer> controls = Set.of(0, 45, 27, 47, 28, 37);
        Set<Integer> aspectSlots = new HashSet<>();
        for (int row = 0; row < 6; row++) {
            List<Integer> expectedProgress = new ArrayList<>();
            for (int col = 0; col < 9; col++) {
                int slot = GridLayout.slot(row, col);
                assertEquals(row, GridLayout.slotToRow(slot));
                assertEquals(col, GridLayout.slotToCol(slot));
                assertEquals(col < 3, GridLayout.isLeftPanelSlot(slot));
                assertEquals(controls.contains(slot), GridLayout.isControlSlot(slot));
                assertEquals(slot == 10, GridLayout.isProductSlot(slot));
                assertEquals(controls.contains(slot) || slot == 10, GridLayout.isReservedStationSlot(slot));
                assertEquals(col == 3, GridLayout.isAspectColumnSlot(slot));
                assertEquals(col >= 3, GridLayout.isAspectRowSlot(slot));
                if (col >= 3) aspectSlots.add(slot);
                if (col >= 4) expectedProgress.add(slot);
            }
            assertEquals(row * 9 + 3, GridLayout.getAspectLockSlot(row));
            assertEquals(expectedProgress, GridLayout.getAspectProgressSlots(row));
            List<Integer> expectedRow = new ArrayList<>(expectedProgress);
            expectedRow.addFirst(row * 9 + 3);
            assertEquals(expectedRow, GridLayout.getAspectRowSlots(row));
        }
        assertEquals(aspectSlots, new HashSet<>(GridLayout.allAspectRowSlots()));
        assertEquals(36, aspectSlots.size());
        List<Integer> copy = GridLayout.allAspectRowSlots();
        copy.clear();
        assertEquals(36, GridLayout.allAspectRowSlots().size());
        assertThrows(UnsupportedOperationException.class, () -> GridLayout.ASPECT_COLUMN_SLOTS.add(0));
    }

    @ParameterizedTest
    @CsvSource({"10, 0, 0", "10, 1, 0", "10, 2, 1", "10, 8, 4", "10, 10, 5", "3, 1, 1", "3, 2, 3", "3, 3, 5", "0, 10, 0", "-1, 10, 0"})
    void progressFillsFiveEvenlySpacedThresholds(int required, int current, int panes) {
        assertEquals(panes, AspectProgressLayout.filledPaneCount(current, required));
        assertEquals(required > 0 ? 5 : 0, AspectProgressLayout.activePaneCount(required));
    }

    @Test
    void progressRejectsInvalidPanesAndRoundsThresholdsUp() {
        assertFalse(AspectProgressLayout.isPaneActive(-1, 10));
        assertFalse(AspectProgressLayout.isPaneActive(5, 10));
        assertFalse(AspectProgressLayout.isPaneActive(0, 0));
        assertEquals(0, AspectProgressLayout.paneThreshold(-1, 10));
        assertEquals(0, AspectProgressLayout.paneThreshold(0, 0));
        assertFalse(AspectProgressLayout.isPaneFilled(5, 100, 10));
        assertEquals(List.of(1, 2, 2, 3, 3), java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> AspectProgressLayout.paneThreshold(index, 3)).toList());
    }

    @Test
    void progressThresholdsDoNotOverflowForLargeConfiguredRequirements() {
        assertEquals(Integer.MAX_VALUE, AspectProgressLayout.paneThreshold(4, Integer.MAX_VALUE));
        assertEquals(429496730, AspectProgressLayout.paneThreshold(0, Integer.MAX_VALUE));
        assertEquals(0, AspectProgressLayout.filledPaneCount(0, Integer.MAX_VALUE));
        assertEquals(5, AspectProgressLayout.filledPaneCount(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void rowAssignmentsContainEveryAspectOnceAndKeepSixRows() {
        assertEquals(List.of("", "", "", "", "", ""), AspectSlotOrder.buildRowAssignments(null));
        assertEquals(List.of("", "", "", "", "", ""), AspectSlotOrder.buildRowAssignments(output()));
        List<String> rows = AspectSlotOrder.buildRowAssignments(output("fire", "water"));
        assertEquals(6, rows.size());
        assertEquals(1, rows.stream().filter("fire"::equals).count());
        assertEquals(1, rows.stream().filter("water"::equals).count());
        assertEquals(4, rows.stream().filter(String::isEmpty).count());
        List<String> truncated = AspectSlotOrder.buildRowAssignments(output("a", "b", "c", "d", "e", "f", "g"));
        assertEquals(6, truncated.size());
        assertEquals(6, new HashSet<>(truncated).size());
    }

    @Test
    void reconcilesAbsentStaleDuplicateAndUnknownSavedRows() {
        OutputDef output = output("fire", "water");
        assertEquals(List.of("", "", "", "", "", ""),
                AspectSlotOrder.reconcileRowAssignments(null, null, "missing"));
        List<List<String>> invalid = Arrays.asList(null, List.of(), List.of("fire"),
                List.of("fire", "fire", "", "", "", ""),
                List.of("fire", "unknown", "", "", "", ""),
                List.of("fire", "", "", "", "", ""));
        for (List<String> saved : invalid) {
            List<String> rows = AspectSlotOrder.reconcileRowAssignments(output, saved, "station");
            assertEquals(6, rows.size());
            assertEquals(Set.of("fire", "water"), new HashSet<>(rows.stream().filter(s -> !s.isBlank()).toList()));
        }
        verify(logger, times(2)).info(contains("no saved order"));
        verify(logger, times(4)).warning(contains("missing or stale"));
    }

    @Test
    void validSavedRowsAreCopiedAndNullsAreDecoys() {
        List<String> saved = new ArrayList<>(Arrays.asList("fire", "", null, "water", " ", ""));
        List<String> result = AspectSlotOrder.reconcileRowAssignments(output("fire", "water"), saved, "station");
        assertEquals(saved, result);
        assertNotSame(saved, result);
        saved.set(0, "changed");
        assertEquals("fire", result.getFirst());
        assertEquals("", AspectSlotOrder.aspectIdForRow(null, 0));
        assertEquals("", AspectSlotOrder.aspectIdForRow(result, -1));
        assertEquals("", AspectSlotOrder.aspectIdForRow(result, 6));
        assertEquals("", AspectSlotOrder.aspectIdForRow(result, 2));
        assertEquals("water", AspectSlotOrder.aspectIdForRow(result, 3));
        assertTrue(AspectSlotOrder.isDecoyRow(result, 4));
        assertFalse(AspectSlotOrder.isDecoyRow(result, 3));
    }

    @Test
    void discoveryReducesRevealAndConfirmThresholdsButHonorsConfiguredMinima() {
        Player player = mock(Player.class);
        try (MockedStatic<MmoAttributes> attributes = mockStatic(MmoAttributes.class)) {
            attributes.when(() -> MmoAttributes.getTotal(player, "intelligence")).thenReturn(5d);
            assertEquals(0.15, DiscoveryScaling.effectiveRevealPercent(player), 0.00001);
            assertEquals(0.75, DiscoveryScaling.effectiveConfirmPercent(player), 0.00001);
            attributes.when(() -> MmoAttributes.getTotal(player, "intelligence")).thenReturn(100d);
            assertEquals(0.1, DiscoveryScaling.effectiveRevealPercent(player), 0.00001);
            assertEquals(0.3, DiscoveryScaling.effectiveConfirmPercent(player), 0.00001);
        }
    }

    @Test
    void aspectIdentityRequiresBothRevealAndConfirmThresholds() {
        try (MockedStatic<MmoAttributes> ignored = mockStatic(MmoAttributes.class)) {
            assertFalse(DiscoveryScaling.isAspectRowRevealed(null, 10, 0));
            assertFalse(DiscoveryScaling.isAspectRowRevealed(null, 1, 10));
            assertTrue(DiscoveryScaling.isAspectRowRevealed(null, 2, 10));
            assertFalse(DiscoveryScaling.isAspectIdentityRevealed(null, 1, 10));
            assertFalse(DiscoveryScaling.isAspectIdentityRevealed(null, 7, 10));
            assertTrue(DiscoveryScaling.meetsConfirmThreshold(null, 8, 10));
            Cache.aspectBaseRevealPercent = 0.9;
            assertFalse(DiscoveryScaling.meetsConfirmThreshold(null, 8, 10));
            assertTrue(DiscoveryScaling.meetsConfirmThreshold(null, 9, 10));
        }
    }

    @Test
    void productRevealBonusIsCappedAndCannotRequireNegativeConfirmations() {
        Player player = mock(Player.class);
        Cache.productRevealBonusPerPoint = 0.5;
        Cache.capProductRevealBonus = 2.0;
        YamlConfiguration config = new YamlConfiguration();
        config.set("product_reveal.base_after_confirmed_aspects", 3);
        OutputDef output = new OutputDef("output", config);
        try (MockedStatic<MmoAttributes> attributes = mockStatic(MmoAttributes.class)) {
            attributes.when(() -> MmoAttributes.getTotal(player, "intelligence")).thenReturn(10d);
            assertEquals(1, DiscoveryScaling.effectiveProductRevealAfterConfirmed(player, output));
            Cache.capProductRevealBonus = 10;
            assertEquals(0, DiscoveryScaling.effectiveProductRevealAfterConfirmed(player, output));
        }
    }

    @Test
    void onlyConfirmedRecipeAspectsCountTowardProductReveal() {
        ActiveProject active = new ActiveProject("input", "output", "vanilla.PAPER");
        active.setAspectState("fire", AspectState.CONFIRMED);
        active.setAspectState("water", AspectState.TESTING);
        active.setAspectState("unrelated", AspectState.CONFIRMED);
        assertEquals(1, RevealHelper.countConfirmedRecipeAspects(active, output("fire", "water")));
        assertEquals(0, RevealHelper.countConfirmedRecipeAspects(active, output()));
    }

    @Test
    void modifiersPreserveBasePointsWithoutAConfiguredPositiveBonus() {
        Player player = mock(Player.class);
        assertFalse(ExternalModifiers.isConfigured());
        assertEquals(2, ExternalModifiers.adjustExperimentPoints(player, 2));
        assertEquals(2, ExternalModifiers.adjustExperimentPoints(null, 2));
        assertEquals(0, ExternalModifiers.adjustExperimentPoints(player, 0));
        assertEquals(-2, ExternalModifiers.adjustExperimentPoints(player, -2));
        YamlConfiguration config = new YamlConfiguration();
        Cache.externalModifiers = config;
        assertFalse(ExternalModifiers.isConfigured());
        assertEquals(2, ExternalModifiers.adjustExperimentPoints(player, 2));
        config.set("other", true);
        assertTrue(ExternalModifiers.isConfigured());
        assertEquals(2, ExternalModifiers.adjustExperimentPoints(player, 2));
        config.set("experiment.aspect_point_bonus_percent", -1.0);
        assertEquals(2, ExternalModifiers.adjustExperimentPoints(player, 2));
        config.set("experiment.aspect_point_bonus_percent", 0.0);
        assertEquals(2, ExternalModifiers.adjustExperimentPoints(player, 2));
        config.set("experiment.aspect_point_bonus_percent", 0.5);
        assertEquals(4, ExternalModifiers.adjustExperimentPoints(player, 3));
    }

    @Test
    void experimentBonusCannotOverflowIntoNegativePoints() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("experiment.aspect_point_bonus_percent", 0.5);
        Cache.externalModifiers = config;
        assertEquals(Integer.MAX_VALUE, ExternalModifiers.adjustExperimentPoints(mock(Player.class), Integer.MAX_VALUE));
    }

    @Test
    void nonfiniteExperimentBonusesLeaveBasePointsUnchanged() {
        YamlConfiguration config = new YamlConfiguration();
        Cache.externalModifiers = config;
        Player player = mock(Player.class);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            config.set("experiment.aspect_point_bonus_percent", invalid);
            assertEquals(3, ExternalModifiers.adjustExperimentPoints(player, 3));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "vanilla.PAPER", "unknown"})
    void nonTemplateReferencesHaveNoTemplateId(String ref) {
        assertFalse(ResultRef.isTemplateRef(ref));
        assertEquals("", ResultRef.templateId(ref));
    }

    @Test
    void templateReferencesIgnorePrefixCaseAndOuterWhitespace() {
        assertTrue(ResultRef.isTemplateRef(" T.Alchemy "));
        assertEquals("Alchemy", ResultRef.templateId(" T.Alchemy "));
        assertTrue(ResultRef.isItemRef("vanilla.PAPER"));
        assertFalse(ResultRef.isItemRef("t.Alchemy"));
    }

    @Test
    void outputPickerHandlesMissingEmptyAndNonpositivePools() {
        assertNull(OutputPicker.roll(null));
        assertNull(OutputPicker.roll(input(List.of())));
        assertEquals("first", OutputPicker.roll(input(List.of(Map.of("id", "first", "weight", 0)))));
        assertEquals("first", OutputPicker.roll(input(List.of(Map.of("id", "first", "weight", -1)))));
        assertEquals("first", OutputPicker.roll(input(List.of(
                Map.of("id", "first", "weight", -2), Map.of("id", "second", "weight", 1)))));
    }

    @Test
    void outputPickerRespectsWeightedIntervalBoundaries() {
        InputDef input = input(List.of(Map.of("id", "first", "weight", 1), Map.of("id", "last", "weight", 3)));
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        AtomicInteger step = new AtomicInteger();
        when(random.nextDouble(anyDouble())).thenAnswer(call -> {
            double bound = call.getArgument(0);
            return switch (step.getAndIncrement()) {
                case 0 -> 0d;
                case 1 -> bound / 4;
                default -> Math.nextDown(bound);
            };
        });
        try (MockedStatic<ThreadLocalRandom> source = mockStatic(ThreadLocalRandom.class)) {
            source.when(ThreadLocalRandom::current).thenReturn(random);
            assertEquals("first", OutputPicker.roll(input));
            assertEquals("last", OutputPicker.roll(input));
            assertEquals("last", OutputPicker.roll(input));
        }
        verify(random, times(3)).nextDouble(anyDouble());
    }

    @Test
    void finiteLargeWeightsAreNormalizedWithoutOverflowingTheirSum() {
        InputDef input = input(List.of(Map.of("id", "first", "weight", Double.MAX_VALUE),
                Map.of("id", "last", "weight", Double.MAX_VALUE)));
        TemplateLoader.get().put("large", new ResultTemplateDef("large", List.of(
                new ResultTemplateDef.WeightedOutput("vanilla.PAPER", Double.MAX_VALUE),
                new ResultTemplateDef.WeightedOutput("vanilla.DIAMOND", Double.MAX_VALUE))));
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextDouble(anyDouble())).thenAnswer(call -> {
            double bound = call.getArgument(0);
            assertEquals(2.0, bound, "Equal finite weights should produce two normalized unit weights");
            return 1.0;
        });
        try (MockedStatic<ThreadLocalRandom> source = mockStatic(ThreadLocalRandom.class)) {
            source.when(ThreadLocalRandom::current).thenReturn(random);
            assertEquals("last", OutputPicker.roll(input));
            assertEquals("vanilla.DIAMOND", resolveTemplate("t.large"));
        }
        verify(random, times(2)).nextDouble(2d);
    }

    @Test
    void directResultsAreTrimmedAndMissingResultsReturnNull() {
        assertNull(ResultResolver.resolve(null));
        assertNull(ResultResolver.resolve(new OutputDef.ResultSpec(null)));
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", " vanilla.PAPER ");
        assertEquals("vanilla.PAPER", ResultResolver.resolve(new OutputDef.ResultSpec(config)));
    }

    @Test
    void controlOnlyDirectResultsAreRejectedAfterTrimming() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("item: \"\\0\"\n");
        assertNull(ResultResolver.resolve(new OutputDef.ResultSpec(config)));
    }

    @Test
    void templatesResolveNestedWeightedResultsAtBoundaries() {
        TemplateLoader.get().put("nested", new ResultTemplateDef("nested", List.of(
                new ResultTemplateDef.WeightedOutput("vanilla.DIAMOND", 1))));
        TemplateLoader.get().put("root", new ResultTemplateDef("root", List.of(
                new ResultTemplateDef.WeightedOutput("vanilla.PAPER", 1),
                new ResultTemplateDef.WeightedOutput("t.nested", 3))));
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        AtomicInteger step = new AtomicInteger();
        when(random.nextDouble(anyDouble())).thenAnswer(call -> {
            double bound = call.getArgument(0);
            return step.getAndIncrement() == 1 ? bound / 4 : 0d;
        });
        try (MockedStatic<ThreadLocalRandom> source = mockStatic(ThreadLocalRandom.class)) {
            source.when(ThreadLocalRandom::current).thenReturn(random);
            assertEquals("vanilla.PAPER", resolveTemplate("t.root"));
            assertEquals("vanilla.DIAMOND", resolveTemplate("t.root"));
        }
    }

    @Test
    void missingEmptyAndInvalidTemplatePathsReturnNull() {
        assertNull(resolveTemplate("t.missing"));
        TemplateLoader.get().put("empty", new ResultTemplateDef("empty", List.of()));
        assertNull(resolveTemplate("t.empty"));
        assertNull(resolveTemplate("unknown"));
        for (String invalid : Arrays.asList(null, "", " ", "bad.ref")) {
            TemplateLoader.get().put("invalid", new ResultTemplateDef("invalid", List.of(
                    new ResultTemplateDef.WeightedOutput(invalid, 0))));
            assertNull(resolveTemplate("t.invalid"));
        }
    }

    @Test
    void nonpositiveTemplateWeightsUseTheFirstAvailableResult() {
        TemplateLoader.get().put("fallback", new ResultTemplateDef("fallback", List.of(
                new ResultTemplateDef.WeightedOutput("vanilla.PAPER", 0),
                new ResultTemplateDef.WeightedOutput("vanilla.DIAMOND", -1))));
        assertEquals("vanilla.PAPER", resolveTemplate("t.fallback"));
        TemplateLoader.get().put("mixed", new ResultTemplateDef("mixed", List.of(
                new ResultTemplateDef.WeightedOutput("vanilla.PAPER", -2),
                new ResultTemplateDef.WeightedOutput("vanilla.DIAMOND", 1))));
        assertEquals("vanilla.PAPER", resolveTemplate("t.mixed"));
    }

    @Test
    void yamlFilesAreSelectedOnlyFromReadableDirectories() throws Exception {
        assertEquals(List.of(), YamlFolder.listYamlFiles(null));
        assertEquals(List.of(), YamlFolder.listYamlFiles(directory.resolve("missing").toFile()));
        Path lower = Files.writeString(directory.resolve("a.yml"), "a: 1");
        Path upper = Files.writeString(directory.resolve("B.YML"), "b: 2");
        Files.writeString(directory.resolve("other.txt"), "text");
        Files.createDirectory(directory.resolve("folder.yml"));
        assertEquals(List.of(), YamlFolder.listYamlFiles(lower.toFile()));
        assertEquals(Set.of(lower.toFile(), upper.toFile()), new HashSet<>(YamlFolder.listYamlFiles(directory.toFile())));
        File unreadable = mock(File.class);
        when(unreadable.exists()).thenReturn(true);
        when(unreadable.isDirectory()).thenReturn(true);
        when(unreadable.listFiles()).thenReturn(null);
        assertEquals(List.of(), YamlFolder.listYamlFiles(unreadable));
    }

    @Test
    void utilityClassesExposeNoPublicConstructors() throws Exception {
        for (Class<?> type : List.of(GridLayout.class, AspectProgressLayout.class, AspectSlotOrder.class,
                DiscoveryScaling.class, RevealHelper.class, ExternalModifiers.class, ResultRef.class,
                OutputPicker.class, ResultResolver.class, YamlFolder.class)) {
            Constructor<?> constructor = type.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()), type.getName());
            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance());
        }
    }

    private OutputDef output(String... aspectIds) {
        YamlConfiguration config = new YamlConfiguration();
        for (String id : aspectIds) config.set("aspects." + id + ".required_points", 10);
        return new OutputDef("output", config);
    }

    private InputDef input(List<Map<String, Object>> outputs) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("outputs", outputs);
        return new InputDef("input", config);
    }

    private String resolveTemplate(String ref) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("template", ref);
        return ResultResolver.resolve(new OutputDef.ResultSpec(config));
    }
}
