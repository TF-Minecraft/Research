package net.tfminecraft.research;

import net.tfminecraft.research.command.CommandManager;
import net.tfminecraft.research.loader.ConfigLoader;
import net.tfminecraft.research.manager.*;
import net.tfminecraft.research.util.ItemRef;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchLifecycleTest {
    @TempDir Path directory;
    private ResearchTestState state;
    private MockedStatic<ItemRef> items;
    private MockedConstruction<ResearchManager> managers;
    @BeforeEach void setup() throws Exception {
        MockBukkit.mock(); state = new ResearchTestState();
        for (String name : List.of("MMOItems", "MythicLib", "ItemsAdder", "TLibs", "RPCharacters")) MockBukkit.createMockPlugin(name);
        items = mockStatic(ItemRef.class, CALLS_REAL_METHODS);
        items.when(() -> ItemRef.isValid(anyString())).thenReturn(true);
        managers = mockConstruction(ResearchManager.class);
    }
    @AfterEach void teardown() throws Exception {
        try { MockBukkit.unmock(); } finally { managers.close(); items.close(); state.close(); }
    }
    @Test void enableCreatesEmptyContentFoldersRegistersCommandsStartsManagerAndDisablesCleanly() throws Exception {
        Research plugin = MockBukkit.load(Research.class);
        assertSame(plugin, Research.plugin); assertTrue(plugin.isEnabled());
        for (String folder : List.of("data/players", "data/stations", "aspects", "templates", "outputs", "inputs")) {
            assertTrue(Files.isDirectory(plugin.getDataFolder().toPath().resolve(folder)));
        }
        assertNotNull(plugin.getPlayerManager()); assertSame(managers.constructed().getFirst(), plugin.getResearchManager());
        assertInstanceOf(CommandManager.class, plugin.getCommand("research").getExecutor());
        assertSame(plugin.getCommand("research").getExecutor(), plugin.getCommand("research").getTabCompleter());
        verify(plugin.getResearchManager()).start();
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "external_modifiers: {experiment: {aspect_point_bonus_percent: 0.1}}\n");
        assertTrue(plugin.reloadAll());
        plugin.onEnable(); // Existing resources and folders survive a later enable.
        assertTrue(Files.readString(plugin.getDataFolder().toPath().resolve("config.yml")).contains("external_modifiers"));
        plugin.onDisable(); verify(plugin.getResearchManager()).unloadAll();
    }
    @Test void firstInstallCreatesTheDataRootWhenItDoesNotExist() throws Exception {
        Research plugin = MockBukkit.load(Research.class);
        Path fresh = plugin.getDataFolder().toPath();
        // MockBukkit precreates the data root; remove only this test-owned temporary tree.
        try (var paths = Files.walk(fresh)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        assertFalse(Files.exists(fresh));
        plugin.onEnable();
        assertTrue(Files.isDirectory(fresh.resolve("data/stations")));
        assertTrue(Files.isDirectory(fresh.resolve("aspects")));
        assertTrue(Files.isRegularFile(fresh.resolve("config.yml")));
    }
    @Test void startupAndReloadReportConfigurationErrorsAndUnavailableGuiItems() throws Exception {
        try (var configs = mockConstruction(ConfigLoader.class, (loader, context) -> when(loader.loadSafe(any())).thenReturn(false))) {
            Research plugin = MockBukkit.load(Research.class);
            assertTrue(plugin.isEnabled()); assertFalse(plugin.reloadAll());
        }
    }
    @Test void guiValidationRejectsEmptyAndUnknownReferencesButAllowsUnavailableOptionalItems() throws Exception {
        Research plugin = MockBukkit.load(Research.class);
        Path gui = plugin.getDataFolder().toPath().resolve("gui.yml");
        for (String ref : List.of("''", "unknown")) {
            Files.writeString(gui, "items: {confirm_button: " + ref + "}\n");
            assertFalse(plugin.loadConfigs());
        }
        Files.writeString(gui, "items: {confirm_button: v.paper}\n");
        items.when(() -> ItemRef.isValid(anyString())).thenReturn(false);
        assertTrue(plugin.loadConfigs());
    }
    @Test void resourceBootstrapCreatesParentsCopiesMissingResourcesAndPreservesExistingFiles() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        ResourceBootstrap.copyIfMissing(plugin,"nested/default.yml");
        assertTrue(Files.isDirectory(directory.resolve("nested"))); verify(plugin).saveResource("nested/default.yml",false);
        Files.writeString(directory.resolve("existing.yml"),"keep");
        ResourceBootstrap.copyIfMissing(plugin,"existing.yml"); verify(plugin,never()).saveResource("existing.yml",false);
    }
    @Test void adminCommandUsesPermissionsReportsReloadOutcomeAndCompletesOnlyFirstArgument() {
        Research.plugin = mock(Research.class);
        CommandManager commands = new CommandManager(); CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class); Messages.clear();
        assertTrue(commands.onCommand(sender,command,"research",new String[0])); verify(sender).sendMessage("command.no_permission");
        assertEquals(List.of(),commands.onTabComplete(sender,command,"research",new String[]{""}));
        when(sender.hasPermission("research.admin")).thenReturn(true);
        assertTrue(commands.onCommand(sender,command,"research",new String[0])); verify(sender).sendMessage("command.usage");
        commands.onCommand(sender,command,"research",new String[]{"reload"}); verify(sender).sendMessage("reload.failed");
        when(Research.plugin.reloadAll()).thenReturn(true);
        commands.onCommand(sender,command,"research",new String[]{"RELOAD"}); verify(sender).sendMessage("reload.success");
        commands.onCommand(sender,command,"research",new String[]{"bad"}); verify(sender).sendMessage("command.unknown_subcommand");
        assertEquals(List.of("reload"),commands.onTabComplete(sender,command,"research",new String[]{""}));
        assertEquals(List.of(),commands.onTabComplete(sender,command,"research",new String[]{"reload",""}));
    }
}
