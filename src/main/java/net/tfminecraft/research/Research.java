package net.tfminecraft.research;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.research.command.CommandManager;
import net.tfminecraft.research.loader.AspectLoader;
import net.tfminecraft.research.loader.ConfigLoader;
import net.tfminecraft.research.loader.GuiLoader;
import net.tfminecraft.research.loader.MessagesLoader;
import net.tfminecraft.research.loader.InputLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.loader.TemplateLoader;
import net.tfminecraft.research.manager.PlayerManager;
import net.tfminecraft.research.manager.ResearchManager;
import net.tfminecraft.research.util.ItemRef;

public class Research extends JavaPlugin {

    public static Research plugin;

    private final ConfigLoader configLoader = new ConfigLoader();
    private final MessagesLoader messagesLoader = new MessagesLoader();
    private final GuiLoader guiLoader = new GuiLoader();
    private final AspectLoader aspectLoader = new AspectLoader();
    private final OutputLoader outputLoader = new OutputLoader();
    private final InputLoader inputLoader = new InputLoader();
    private final TemplateLoader templateLoader = new TemplateLoader();
    private final CommandManager commandManager = new CommandManager();
    private final PlayerManager playerManager = new PlayerManager();
    private final ResearchManager researchManager = new ResearchManager(playerManager);

    @Override
    public void onEnable() {
        plugin = this;
        createFolders();
        createConfigs();
        if (!loadConfigs()) {
            getLogger().warning("Research loaded with config errors.");
        }
        getCommand("research").setExecutor(commandManager);
        getCommand("research").setTabCompleter(commandManager);
        getServer().getPluginManager().registerEvents(researchManager, this);
        researchManager.start();
        getLogger().info("Research enabled.");
    }

    @Override
    public void onDisable() {
        researchManager.unloadAll();
        getLogger().info("Research disabled.");
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ResearchManager getResearchManager() {
        return researchManager;
    }

    public boolean reloadAll() {
        return loadConfigs();
    }

    public boolean loadConfigs() {
        boolean ok = true;
        ok &= configLoader.loadSafe(new File(getDataFolder(), "config.yml"));
        ok &= messagesLoader.loadSafe(new File(getDataFolder(), "messages.yml"));
        ok &= guiLoader.loadSafe(new File(getDataFolder(), "gui.yml"));
        ok &= aspectLoader.loadFolder(new File(getDataFolder(), "aspects"));
        ok &= templateLoader.loadFolder(new File(getDataFolder(), "templates"));
        ok &= outputLoader.loadFolder(new File(getDataFolder(), "outputs"));
        ok &= inputLoader.loadFolder(new File(getDataFolder(), "inputs"));
        ok &= validateGuiItemRefs();
        if (ok) {
            getLogger().info("[Research] Loaded " + AspectLoader.get().size() + " aspects, "
                    + OutputLoader.get().size() + " outputs, "
                    + InputLoader.get().size() + " inputs, "
                    + TemplateLoader.get().size() + " templates.");
            if (net.tfminecraft.research.util.ExternalModifiers.isConfigured()) {
                getLogger().info("[Research] external_modifiers section loaded (experiment bonuses active).");
            }
        }
        return ok;
    }

    private boolean validateGuiItemRefs() {
        boolean ok = true;
        ok &= warnIfInvalidGuiItem("items.confirm_button", GuiCache.confirmButton);
        ok &= warnIfInvalidGuiItem("items.cancel_button", GuiCache.cancelButton);
        ok &= warnIfInvalidGuiItem("items.filler", GuiCache.filler);
        ok &= warnIfInvalidGuiItem("items.grid_filler", GuiCache.gridFiller);
        ok &= warnIfInvalidGuiItem("items.left_filler", GuiCache.leftFiller);
        ok &= warnIfInvalidGuiItem("items.aspect_progress_inactive", GuiCache.aspectProgressInactive);
        ok &= warnIfInvalidGuiItem("items.mental_points_icon", GuiCache.mentalPointsIcon);
        ok &= warnIfInvalidGuiItem("items.locked_aspect", GuiCache.lockedAspect);
        ok &= warnIfInvalidGuiItem("items.aspect_row_divider", GuiCache.aspectRowDivider);
        ok &= warnIfInvalidGuiItem("items.aspect_tested_unknown", GuiCache.aspectTestedUnknown);
        ok &= warnIfInvalidGuiItem("items.aspect_progress_unknown", GuiCache.aspectProgressUnknown);
        ok &= warnIfInvalidGuiItem("items.aspect_progress_revealed", GuiCache.aspectProgressRevealed);
        ok &= warnIfInvalidGuiItem("items.wave_pulse_default", GuiCache.wavePulseDefault);
        return ok;
    }

    private boolean warnIfInvalidGuiItem(String field, String ref) {
        if (ref == null || ref.isBlank() || !ItemRef.hasKnownPrefix(ref)) {
            getLogger().severe("[Research] Invalid gui.yml " + field + ": " + ref);
            return false;
        }
        if (!ItemRef.isValid(ref)) {
            getLogger().warning("[Research] gui.yml " + field + " could not be built: " + ref);
        }
        return true;
    }

    private void createFolders() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        mkdir("data");
        mkdir("data/players");
        mkdir("data/stations");
        mkdir("aspects");
        mkdir("templates");
        mkdir("outputs");
        mkdir("inputs");
    }

    private void mkdir(String relativePath) {
        File folder = new File(getDataFolder(), relativePath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private void createConfigs() {
        String[] defaultFiles = {
            "config.yml",
            "messages.yml",
            "gui.yml"
        };
        for (String path : defaultFiles) {
            ResourceBootstrap.copyIfMissing(this, path);
        }
    }
}
