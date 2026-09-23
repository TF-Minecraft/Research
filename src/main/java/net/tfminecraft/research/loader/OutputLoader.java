package net.tfminecraft.research.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public static Map<String, OutputDef> get() {
        return map;
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
            ok &= validate(def, configFile);
            map.put(key, def);
        }
        return ok;
    }

    private boolean validate(OutputDef def, File configFile) {
        boolean ok = true;
        ok &= validateItemRef(def.getId(), "mystery_display", def.getMysteryDisplayItemRef(), configFile);
        ok &= validateItemRef(def.getId(), "product_display", def.getProductDisplayItemRef(), configFile);

        if (def.getAspects().size() > GridLayout.ASPECT_ROW_COUNT) {
            Research.plugin.getLogger().severe("[Research] Output '" + def.getId() + "' has "
                    + def.getAspects().size() + " aspects but the station GUI supports "
                    + GridLayout.ASPECT_ROW_COUNT + " in " + configFile.getName());
            ok = false;
        }

        for (Map.Entry<String, AspectRequirement> entry : def.getAspects().entrySet()) {
            String aspectId = entry.getKey();
            if (AspectLoader.getById(aspectId) == null) {
                Research.plugin.getLogger().severe("[Research] Output '" + def.getId() + "' references unknown aspect '"
                        + aspectId + "' in " + configFile.getName());
                ok = false;
            }
            if (entry.getValue().getRequiredPoints() <= 0) {
                Research.plugin.getLogger().severe("[Research] Output '" + def.getId() + "' aspect '" + aspectId
                        + "' must have required_points > 0 in " + configFile.getName());
                ok = false;
            }
        }

        ok &= validateResult(def, configFile);
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
