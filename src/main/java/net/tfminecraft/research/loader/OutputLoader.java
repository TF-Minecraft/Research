package net.tfminecraft.research.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.model.AspectRequirement;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.util.GridLayout;
import net.tfminecraft.research.util.ItemRef;
import net.tfminecraft.research.util.ResultRef;
import net.tfminecraft.research.util.YamlFolder;

public final class OutputLoader implements LoaderInterface {

    private static final Map<String, OutputDef> map = new LinkedHashMap<>();
    private static final Set<String> failedChecks = new HashSet<>();
    private static final Set<String> refusalsLogged = new HashSet<>();

    public static Map<String, OutputDef> get() {
        return map;
    }

    /** Ids of loaded outputs that failed a load check. They stay listed, but no project can start them. */
    public static Set<String> failedChecks() {
        return Collections.unmodifiableSet(failedChecks);
    }

    public static boolean passedChecks(String id) {
        return !failedChecks.contains(id);
    }

    /** True the first time a refused start is reported for this key since the last load, so the console is not flooded. */
    public static boolean firstRefusal(String key) {
        return refusalsLogged.add(key);
    }

    /** Test hook: marks an output as failed without loading a file. */
    static void markFailed(String id) {
        failedChecks.add(id);
    }

    /** Test hook: forgets failed outputs and logged refusals, as a load does. */
    static void resetChecks() {
        failedChecks.clear();
        refusalsLogged.clear();
    }

    public static OutputDef getById(String id) {
        return map.get(id);
    }

    @Override
    public void load(File configFile) {
        loadFile(configFile);
    }

    public boolean loadFolder(File folder) {
        map.clear();
        resetChecks();
        boolean ok = true;
        List<File> files = new ArrayList<>(YamlFolder.listYamlFiles(folder));
        files.sort(Comparator.comparing(file -> file.getName().toLowerCase()));
        for (File file : files) {
            ok &= loadFile(file);
        }
        return ok;
    }

    private boolean loadFile(File configFile) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Research.plugin.getLogger().severe("[Research] Failed to load output file " + configFile.getName()
                    + ": " + ex.getMessage());
            return false;
        }

        boolean ok = true;
        for (String key : config.getKeys(false)) {
            if (map.containsKey(key)) {
                Research.plugin.getLogger().severe("[Research] Duplicate output id '" + key + "' in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                Research.plugin.getLogger().severe("[Research] Output '" + key + "' has no data in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            OutputDef def = new OutputDef(key, section);
            if (!validate(def, configFile)) {
                failedChecks.add(key);
                ok = false;
            }
            map.put(key, def);
        }
        return ok;
    }

    private boolean validate(OutputDef def, File configFile) {
        boolean ok = true;
        ok &= validateItemRef(def.getId(), "mystery_display", def.getMysteryDisplayItemRef(), configFile);
        ok &= validateItemRef(def.getId(), "product_display", def.getProductDisplayItemRef(), configFile);

        int recipeSize = def.getAspects().size() + def.getAspectPoolPick();
        if (recipeSize > GridLayout.ASPECT_ROW_COUNT) {
            Research.plugin.getLogger().severe("[Research] Output '" + def.getId() + "' has "
                    + recipeSize + " aspects but the station GUI supports "
                    + GridLayout.ASPECT_ROW_COUNT + " in " + configFile.getName());
            ok = false;
        }
        if (recipeSize <= 0) {
            // A recipe with nothing to research would be complete the moment it starts.
            Research.plugin.getLogger().severe("[Research] Output '" + def.getId()
                    + "' lists no aspects and no aspect_pool.pick in " + configFile.getName());
            ok = false;
        }

        ok &= validateAspects(def.getId(), "", def.getAspects(), configFile);
        ok &= validateAspectPool(def, configFile);
        ok &= validateResult(def, configFile);
        return ok;
    }

    private boolean validateAspects(String outputId, String field, Map<String, AspectRequirement> aspects,
            File configFile) {
        boolean ok = true;
        for (Map.Entry<String, AspectRequirement> entry : aspects.entrySet()) {
            String aspectId = entry.getKey();
            if (AspectLoader.getById(aspectId) == null) {
                Research.plugin.getLogger().severe("[Research] Output '" + outputId + "' references unknown "
                        + field + "aspect '" + aspectId + "' in " + configFile.getName());
                ok = false;
            }
            if (entry.getValue().getRequiredPoints() <= 0) {
                Research.plugin.getLogger().severe("[Research] Output '" + outputId + "' " + field + "aspect '"
                        + aspectId + "' must have required_points > 0 in " + configFile.getName());
                ok = false;
            }
        }
        return ok;
    }

    private boolean validateAspectPool(OutputDef def, File configFile) {
        int pick = def.getAspectPoolPick();
        Map<String, AspectRequirement> pool = def.getAspectPool();
        if (pool.isEmpty()) {
            if (pick != 0) {
                Research.plugin.getLogger().severe("[Research] Output '" + def.getId()
                        + "' sets aspect_pool.pick but lists no aspect_pool.aspects in " + configFile.getName());
                return false;
            }
            return true;
        }

        boolean ok = validateAspects(def.getId(), "pool ", pool, configFile);
        if (pick < 1 || pick > pool.size()) {
            Research.plugin.getLogger().severe("[Research] Output '" + def.getId() + "' aspect_pool.pick must be"
                    + " between 1 and " + pool.size() + " (the pool size), got " + pick + " in "
                    + configFile.getName());
            ok = false;
        }
        for (String aspectId : pool.keySet()) {
            if (def.getAspects().containsKey(aspectId)) {
                Research.plugin.getLogger().severe("[Research] Output '" + def.getId() + "' lists aspect '"
                        + aspectId + "' in both aspects and aspect_pool in " + configFile.getName());
                ok = false;
            }
        }
        return ok;
    }

    private boolean validateResult(OutputDef def, File configFile) {
        OutputDef.ResultSpec result = def.getResult();
        if (result.hasItem() && result.hasTemplate()) {
            Research.plugin.getLogger().severe("[Research] Output '" + def.getId()
                    + "' result must specify item or template, not both in " + configFile.getName());
            return false;
        }
        if (!result.hasItem() && !result.hasTemplate()) {
            Research.plugin.getLogger().severe("[Research] Output '" + def.getId()
                    + "' missing result.item or result.template in " + configFile.getName());
            return false;
        }
        if (result.isTemplateInItemKey()) {
            Research.plugin.getLogger().warning("[Research] Output '" + def.getId() + "' sets result.item: "
                    + result.getTemplateRef().trim() + ", which is a template. It is read as result.template;"
                    + " write it as result.template in " + configFile.getName());
        }
        if (result.hasTemplate()) {
            String templateId = ResultRef.templateId(result.getTemplateRef());
            if (TemplateLoader.getById(templateId) == null) {
                Research.plugin.getLogger().severe("[Research] Output '" + def.getId() + "' references unknown template '"
                        + templateId + "' in " + configFile.getName());
                return false;
            }
            return true;
        }
        return validateItemRef(def.getId(), "result", result.getItemRef(), configFile);
    }

    private boolean validateItemRef(String outputId, String field, String ref, File configFile) {
        if (ref == null || ref.isBlank()) {
            Research.plugin.getLogger().severe("[Research] Output '" + outputId + "' missing " + field
                    + " item in " + configFile.getName());
            return false;
        }
        if (!ItemRef.hasKnownPrefix(ref)) {
            Research.plugin.getLogger().severe("[Research] Output '" + outputId + "' has invalid " + field
                    + " item '" + ref + "' in " + configFile.getName());
            return false;
        }
        if (!ItemRef.isValid(ref)) {
            Research.plugin.getLogger().warning("[Research] Output '" + outputId + "' " + field
                    + " item could not be built: " + ref);
        }
        return true;
    }
}
