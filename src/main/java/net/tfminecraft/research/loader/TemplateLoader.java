package net.tfminecraft.research.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import net.tfminecraft.research.model.ResultTemplateDef;
import net.tfminecraft.research.util.ItemRef;
import net.tfminecraft.research.util.ResultRef;
import net.tfminecraft.research.util.YamlFolder;

public final class TemplateLoader implements LoaderInterface {

    private static final Map<String, ResultTemplateDef> map = new LinkedHashMap<>();

    public static Map<String, ResultTemplateDef> get() {
        return map;
    }

    public static ResultTemplateDef getById(String id) {
        return map.get(id);
    }

    @Override
    public void load(File configFile) {
        if (configFile.getParentFile() != null) {
            loadFolder(configFile.getParentFile());
        }
    }

    public boolean loadFolder(File folder) {
        map.clear();
        Map<String, ResultTemplateDef> raw = new LinkedHashMap<>();
        boolean ok = true;
        for (File file : YamlFolder.listYamlFiles(folder)) {
            ok &= loadFileInto(raw, file);
        }
        for (Map.Entry<String, ResultTemplateDef> entry : raw.entrySet()) {
            String id = entry.getKey();
            List<ResultTemplateDef.WeightedOutput> validated = new ArrayList<>();
            for (ResultTemplateDef.WeightedOutput output : entry.getValue().getOutputs()) {
                if (output.getWeight() <= 0) {
                    Research.plugin.getLogger().severe("[Research] Template '" + id + "' has output with weight <= 0");
                    ok = false;
                    continue;
                }
                if (isValidOutputRef(output.getRef(), Set.of(id), id, raw)) {
                    validated.add(output);
                } else {
                    ok = false;
                }
            }
            if (validated.isEmpty()) {
                Research.plugin.getLogger().severe("[Research] Template '" + id + "' has no valid outputs");
                ok = false;
            } else {
                map.put(id, new ResultTemplateDef(id, validated));
            }
        }
        return ok;
    }

    private boolean loadFileInto(Map<String, ResultTemplateDef> target, File configFile) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Research.plugin.getLogger().severe("[Research] Failed to load template file " + configFile.getName()
                    + ": " + ex.getMessage());
            return false;
        }

        boolean ok = true;
        for (String key : config.getKeys(false)) {
            if (target.containsKey(key)) {
                Research.plugin.getLogger().severe("[Research] Duplicate template id '" + key + "' in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                Research.plugin.getLogger().severe("[Research] Template '" + key + "' has no data in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            target.put(key, new ResultTemplateDef(key, section));
        }
        return ok;
    }

    private boolean isValidOutputRef(String ref, Set<String> ancestors, String templateId,
            Map<String, ResultTemplateDef> raw) {
        if (ref == null || ref.isBlank()) {
            Research.plugin.getLogger().severe("[Research] Template '" + templateId + "' has empty output ref");
            return false;
        }

        if (ResultRef.isTemplateRef(ref)) {
            String nestedId = ResultRef.templateId(ref);
            if (ancestors.contains(nestedId)) {
                Research.plugin.getLogger().severe("[Research] Template '" + templateId + "' output '" + ref
                        + "' creates a cycle");
                return false;
            }
            ResultTemplateDef nested = raw.get(nestedId);
            if (nested == null) {
                Research.plugin.getLogger().severe("[Research] Template '" + templateId + "' references missing template '"
                        + nestedId + "'");
                return false;
            }
            Set<String> nextAncestors = new HashSet<>(ancestors);
            nextAncestors.add(nestedId);
            boolean anyValid = false;
            for (ResultTemplateDef.WeightedOutput output : nested.getOutputs()) {
                if (isValidOutputRef(output.getRef(), nextAncestors, nestedId, raw)) {
                    anyValid = true;
                }
            }
            if (!anyValid) {
                Research.plugin.getLogger().severe("[Research] Template '" + templateId + "' output '" + ref
                        + "' has no valid nested path");
                return false;
            }
            return true;
        }

        if (!ResultRef.isItemRef(ref)) {
            Research.plugin.getLogger().severe("[Research] Template '" + templateId + "' has invalid output ref '"
                    + ref + "'");
            return false;
        }
        if (!ItemRef.isValid(ref)) {
            Research.plugin.getLogger().warning("[Research] Template '" + templateId + "' output could not be built: "
                    + ref);
        }
        return true;
    }
}
