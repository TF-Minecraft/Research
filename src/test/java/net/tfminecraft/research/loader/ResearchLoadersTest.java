package net.tfminecraft.research.loader;

import net.tfminecraft.research.*;
import net.tfminecraft.research.model.*;
import net.tfminecraft.research.util.ItemRef;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchLoadersTest {
    @TempDir Path directory;
    private ResearchTestState snapshot;
    private Logger logger;
    private MockedStatic<ItemRef> items;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        snapshot = new ResearchTestState();
        Research.plugin = mock(Research.class);
        logger = mock(Logger.class);
        when(Research.plugin.getLogger()).thenReturn(logger);
        AspectLoader.get().clear();
        InputLoader.get().clear();
        OutputLoader.get().clear();
        TemplateLoader.get().clear();
        items = mockStatic(ItemRef.class, CALLS_REAL_METHODS);
        items.when(() -> ItemRef.isValid(anyString())).thenAnswer(c -> !((String)c.getArgument(0)).contains("missing"));
    }

    @AfterEach
    void tearDown() throws Exception {
        items.close();
        snapshot.close();
        MockBukkit.unmock();
    }

    private File yaml(String name, String content) throws Exception {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content).toFile();
    }

    private YamlConfiguration config(String content) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        return yaml;
    }

    @Test
    void globalLoadersReadBundledConfigsAndReportMalformedOrMissingFiles() throws Exception {
        ConfigLoader config = new ConfigLoader();
        GuiLoader gui = new GuiLoader();
        MessagesLoader messages = new MessagesLoader();
        for (String resource : List.of("config.yml", "gui.yml", "messages.yml")) {
            try (var stream = getClass().getResourceAsStream("/" + resource)) {
                Files.copy(stream, directory.resolve(resource));
            }
        }
        config.load(directory.resolve("config.yml").toFile());
        gui.load(directory.resolve("gui.yml").toFile());
        messages.load(directory.resolve("messages.yml").toFile());
        assertEquals(Material.LECTERN, Cache.stationBlock);
        assertFalse(GuiCache.confirmButton.isBlank());
        assertFalse(Messages.get("station.completed").isBlank());
        File empty = yaml("empty.yml", "{}");
        assertTrue(config.loadSafe(empty));
        assertTrue(gui.loadSafe(empty));
        assertTrue(messages.loadSafe(empty));
        File broken = yaml("broken.yml", "bad: [unterminated");
        config.load(broken);
        gui.load(broken);
        messages.load(broken);
        File missing = directory.resolve("missing.yml").toFile();
        assertFalse(config.loadSafe(missing));
        assertFalse(gui.loadSafe(missing));
        assertFalse(messages.loadSafe(missing));
        verify(logger).severe("[Research] config.yml load failed.");
        verify(logger).severe("[Research] gui.yml load failed.");
        verify(logger).severe("[Research] messages.yml load failed.");
    }

    @Test
    void configurationUsesConfiguredValuesClampsRejectPointsAndFallsBackForUnknownBlocks() throws Exception {
        ConfigLoader loader = new ConfigLoader();
        assertTrue(loader.loadSafe(yaml("full.yml", """
                station:
                  block: not_a_material
                  permission: research.use
                  start_effects: {sound: custom.start, particle_count: 7}
                  complete_effects: {sound: custom.complete, extra_sound_delay_ticks: 5}
                  result_spawn: {trail_ticks: 12, trail_interval_ticks: 3, burst_particles: false}
                mental_points: {experiment_cost: 4}
                experiment: {primary_points: 8, secondary_points: 3, reject_points: 0}
                attributes:
                  discovery:
                    mmocore_id: insight
                    product_reveal_bonus_per_point: 0.2
                    caps: {product_reveal_bonus: 0.7}
                aspect_discovery: {base_reveal_percent: 0.6}
                aspect_confirm: {base_confirm_percent: 0.8}
                external_modifiers: {enabled: true}
                """)));
        assertEquals(Material.LECTERN, Cache.stationBlock);
        assertEquals("research.use", Cache.stationPermission);
        assertEquals("custom.start", Cache.stationStartSound);
        assertEquals(7, Cache.stationStartParticleCount);
        assertEquals("custom.complete", Cache.stationCompleteSound);
        assertEquals(5, Cache.stationCompleteExtraSoundDelayTicks);
        assertEquals(12, Cache.resultSpawnTrailTicks);
        assertEquals(3, Cache.resultSpawnTrailIntervalTicks);
        assertFalse(Cache.resultSpawnBurstParticles);
        assertEquals(4, Cache.experimentCost);
        assertEquals(1, Cache.experimentRejectPoints);
        assertEquals("insight", Cache.discoveryMmocoreId);
        assertEquals(0.7, Cache.capProductRevealBonus);
        assertEquals(0.6, Cache.aspectBaseRevealPercent);
        assertEquals(0.8, Cache.aspectBaseConfirmPercent);
        assertTrue(Cache.externalModifiers.getBoolean("enabled"));
    }

    @Test
    void guiColorsLabelsAndMessagesSupportNestedValues() throws Exception {
        GuiLoader gui = new GuiLoader();
        assertTrue(gui.loadSafe(yaml("gui-test.yml", """
                items: {confirm_button: v.paper, filler: v.gray_stained_glass_pane}
                labels: {inventory_title: Lab, undiscovered_aspect_lore: [one, two]}
                colors: {accent: '&a', blank: ''}
                """)));
        assertEquals("v.paper", GuiCache.confirmButton);
        assertEquals("Lab", GuiCache.inventoryTitleLabel);
        assertEquals(List.of("one", "two"), GuiCache.undiscoveredAspectLore);
        gui.load(yaml("gui-no-lore.yml", "labels: {undiscovered_aspect_lore: invalid}"));
        assertTrue(GuiCache.undiscoveredAspectLore.isEmpty());
        new MessagesLoader().load(yaml("messages-test.yml", "prefix: '&aLab: '\nouter:\n  inner: 'Hello {who}'\n"));
        assertTrue(Messages.format("outer.inner", Map.of("who", "Player")).contains("Hello Player"));
    }

    @Test
    void aspectFilesCoverDefaultsValidationAndSortedDuplicateDetection() throws Exception {
        AspectLoader loader = new AspectLoader();
        File file = yaml("aspects/a.yml", """
                valid:
                  display: {item: v.paper}
                  pulse_color: v.red_stained_glass_pane
                  primary_items: [v.paper, v.missing]
                  secondary_items: [v.stone]
                invalid:
                  display: {item: unknown}
                  pulse_color: unknown
                  primary_items: [unknown]
                  sounds: {confirm: ''}
                missing-items:
                  display: {item: v.missing}
                  pulse_color: v.missing
                defaults: {}
                scalar: nope
                ' ': {}
                """);
        yaml("aspects/b.yml", "valid: {}\n");
        assertFalse(loader.loadFolder(file.getParentFile()));
        assertNotNull(AspectLoader.getById("valid"));
        assertNotNull(AspectLoader.getById("defaults"));
        assertNull(AspectLoader.getById("scalar"));
        loader.load(yaml("single-aspect.yml", "single: {}"));
        assertNotNull(AspectLoader.getById("single"));
    }

    @Test
    void outputValidationChecksDisplaysAspectsAndExclusiveResultKinds() throws Exception {
        AspectLoader.get().put("fire", new AspectDef("fire", config("{}")));
        TemplateLoader.get().put("loot", new ResultTemplateDef("loot", List.of(new ResultTemplateDef.WeightedOutput("v.paper", 1))));
        File file = yaml("outputs/a.yml", """
                item:
                  mystery_display: {item: v.paper}
                  product_display: {item: v.missing}
                  aspects: {fire: {required_points: 3}}
                  result: {item: v.paper}
                template:
                  mystery_display: {item: v.paper}
                  product_display: {item: v.paper}
                  result: {template: t.loot}
                unknown:
                  mystery_display: {item: bad}
                  result: {template: t.missing}
                both: {result: {item: v.paper, template: t.loot}}
                empty: {}
                invalid-aspects:
                  aspects:
                    absent: {required_points: 0}
                    a: {}
                    b: {}
                    c: {}
                    d: {}
                    e: {}
                    f: {}
                scalar: nope
                """);
        yaml("outputs/b.yml", "item: {}\n");
        OutputLoader loader = new OutputLoader();
        assertFalse(loader.loadFolder(file.getParentFile()));
        assertEquals(3, OutputLoader.getById("item").getAspects().get("fire").getRequiredPoints());
        assertTrue(OutputLoader.getById("template").getResult().hasTemplate());
        assertNull(OutputLoader.getById("scalar"));
        loader.load(yaml("single-output.yml", "single: {}"));
        assertNotNull(OutputLoader.getById("single"));
    }

    @Test
    void inputValidationChecksStartItemsWeightsTargetsAndDuplicateItems() throws Exception {
        OutputLoader.get().put("known", new OutputDef("known", config("{}")));
        File file = yaml("inputs/a.yml", """
                good: {start_item: {item: v.paper}, outputs: [{id: known, weight: 2}]}
                duplicate-item: {start_item: {item: v.paper}, outputs: [{id: known}]}
                empty: {}
                bad: {start_item: {item: bad, amount: 0}, outputs: [{id: missing, weight: 0}, {}]}
                unavailable: {start_item: {item: v.missing}, outputs: [{id: known}]}
                scalar: nope
                """);
        yaml("inputs/b.yml", "good: {}\n");
        InputLoader loader = new InputLoader();
        assertFalse(loader.loadFolder(file.getParentFile()));
        assertEquals("v.paper", InputLoader.getById("good").getStartItemRef());
        assertEquals(2, InputLoader.getById("good").getOutputs().getFirst().getWeight());
        assertNull(InputLoader.getById("scalar"));
        loader.load(yaml("single-input.yml", "single: {start_item: {item: v.stone}, outputs: [{id: known}]}"));
        assertNotNull(InputLoader.getById("single"));
    }

    @Test
    void templatesValidateNestedPathsCyclesDuplicatesAndWeights() throws Exception {
        File file = yaml("templates/a.yml", """
                leaf: {outputs: [{item: v.paper}, {item: v.missing}]}
                nested: {outputs: [{item: t.leaf}]}
                self: {outputs: [{item: t.self}]}
                missing: {outputs: [{item: t.nope}]}
                bad: {outputs: [{item: bad}, {}, {item: v.paper, weight: 0}]}
                dead-parent: {outputs: [{item: t.bad}]}
                empty-child: {}
                empty-parent: {outputs: [{item: t.empty-child}]}
                scalar: nope
                """);
        yaml("templates/b.yml", "leaf: {}\n");
        TemplateLoader loader = new TemplateLoader();
        assertFalse(loader.loadFolder(file.getParentFile()));
        assertEquals(2, TemplateLoader.getById("leaf").getOutputs().size());
        assertNotNull(TemplateLoader.getById("nested"));
        assertNull(TemplateLoader.getById("self"));
        assertNull(TemplateLoader.getById("dead-parent"), "zero-weight children cannot produce a result");
        loader.load(file);
        loader.load(new File("no-parent.yml"));
    }

    @Test
    void alternateLeafOutputsCannotHideMutuallyRecursiveTemplateEdges() throws Exception {
        File file = yaml("cyclic-templates/entries.yml", """
                A: {outputs: [{item: t.B}, {item: v.paper}]}
                B: {outputs: [{item: t.A}, {item: v.stone}]}
                """);

        assertFalse(new TemplateLoader().loadFolder(file.getParentFile()));
        assertEquals(List.of("v.paper"), TemplateLoader.getById("A").getOutputs().stream()
                .map(ResultTemplateDef.WeightedOutput::getRef).toList());
        assertEquals(List.of("v.stone"), TemplateLoader.getById("B").getOutputs().stream()
                .map(ResultTemplateDef.WeightedOutput::getRef).toList());
        verify(logger, atLeastOnce()).severe(contains("creates a cycle"));
    }

    @Test
    void nestedValidationSkipsUnselectableBranchesButStillRequiresAValidLeaf() throws Exception {
        File file = yaml("nested-weights/entries.yml", """
                dead: {outputs: [{item: v.paper, weight: 0}, {item: v.stone, weight: .NaN}]}
                dead-parent: {outputs: [{item: t.dead}]}
                healthy: {outputs: [{item: t.healthy, weight: 0}, {item: v.paper}]}
                healthy-parent: {outputs: [{item: t.healthy}]}
                """);

        assertFalse(new TemplateLoader().loadFolder(file.getParentFile()));
        assertNull(TemplateLoader.getById("dead"));
        assertNull(TemplateLoader.getById("dead-parent"));
        assertEquals(List.of("v.paper"), TemplateLoader.getById("healthy").getOutputs().stream()
                .map(ResultTemplateDef.WeightedOutput::getRef).toList());
        assertEquals(List.of("t.healthy"), TemplateLoader.getById("healthy-parent").getOutputs().stream()
                .map(ResultTemplateDef.WeightedOutput::getRef).toList());
    }

    @Test
    void folderLoadersReportUnreadableAndMalformedFilesWithoutThrowing() throws Exception {
        File broken = yaml("broken-folder/bad.yml", "bad: [unterminated");
        for (Function<File, Boolean> load : List.<Function<File, Boolean>>of(new AspectLoader()::loadFolder,
                new InputLoader()::loadFolder, new OutputLoader()::loadFolder, new TemplateLoader()::loadFolder)) {
            assertFalse(load.apply(broken.getParentFile()));
        }
        File missing = directory.resolve("missing.yml").toFile();
        new AspectLoader().load(missing);
        new InputLoader().load(missing);
        new OutputLoader().load(missing);
        new TemplateLoader().load(missing);
        verify(logger, atLeastOnce()).severe(contains("Failed to load"));
    }

    @Test
    void nonfiniteWeightsAreRejectedBeforeRandomSelection() throws Exception {
        OutputLoader.get().put("known", new OutputDef("known", config("{}")));
        for (String weight : List.of(".NaN", ".inf")) {
            File input = yaml("nonfinite-input/entry.yml", "entry: {start_item: {item: v.paper}, outputs: [{id: known, weight: " + weight + "}]}\n");
            assertFalse(new InputLoader().loadFolder(input.getParentFile()), "input " + weight);
            assertNull(InputLoader.getById("entry"), "invalid input must not remain selectable");
            File template = yaml("nonfinite-template/entry.yml", "entry: {outputs: [{item: v.paper, weight: " + weight + "}]}\n");
            assertFalse(new TemplateLoader().loadFolder(template.getParentFile()), "template " + weight);
        }
    }
}
