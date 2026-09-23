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

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.util.ItemRef;
import net.tfminecraft.research.util.YamlFolder;

public final class InputLoader implements LoaderInterface {

    private static final Map<String, InputDef> map = new LinkedHashMap<>();

    public static Map<String, InputDef> get() {
        return map;
    }

    public static InputDef getById(String id) {
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
        ok &= validateUniqueStartItems();
        return ok;
    }

    private boolean loadFile(File configFile) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Research.plugin.getLogger().severe("[Research] Failed to load input file " + configFile.getName()
                    + ": " + ex.getMessage());
            return false;
        }

        boolean ok = true;
        for (String key : config.getKeys(false)) {
            if (map.containsKey(key)) {
                Research.plugin.getLogger().severe("[Research] Duplicate input id '" + key + "' in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                Research.plugin.getLogger().severe("[Research] Input '" + key + "' has no data in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            InputDef def = new InputDef(key, section);
            ok &= validate(def, configFile);
            map.put(key, def);
        }
        return ok;
    }

    private boolean validateUniqueStartItems() {
        Map<String, String> seen = new LinkedHashMap<>();
        boolean ok = true;
        for (InputDef def : map.values()) {
            if (!def.requiresStartItem()) {
                continue;
            }
            String normalized = ItemRef.normalize(def.getStartItemRef());
            if (normalized.isBlank()) {
                continue;
            }
            String previous = seen.put(normalized, def.getId());
            if (previous != null && !previous.equals(def.getId())) {
                Research.plugin.getLogger().severe("[Research] Start item '" + normalized
                        + "' registered on inputs '" + previous + "' and '" + def.getId() + "'.");
                ok = false;
            }
        }
        return ok;
    }

    private boolean validate(InputDef def, File configFile) {
        boolean ok = true;
        if (!def.requiresStartItem()) {
            Research.plugin.getLogger().severe("[Research] Input '" + def.getId() + "' missing start_item in "
                    + configFile.getName());
            ok = false;
        } else {
            ok &= validateItemRef(def.getId(), "start_item", def.getStartItemRef(), configFile);
            if (def.getStartItemAmount() <= 0) {
                Research.plugin.getLogger().severe("[Research] Input '" + def.getId() + "' start_item amount must be > 0 in "
                        + configFile.getName());
                ok = false;
            }
        }

        if (def.getOutputs().isEmpty()) {
            Research.plugin.getLogger().severe("[Research] Input '" + def.getId() + "' has no outputs in "
                    + configFile.getName());
            ok = false;
        }

        for (InputDef.WeightedOutput output : def.getOutputs()) {
            if (output.getOutputId() == null || output.getOutputId().isBlank()) {
                Research.plugin.getLogger().severe("[Research] Input '" + def.getId() + "' has output with blank id in "
                        + configFile.getName());
                ok = false;
                continue;
            }
            if (output.getWeight() <= 0) {
                Research.plugin.getLogger().severe("[Research] Input '" + def.getId() + "' has output '"
                        + output.getOutputId() + "' with weight <= 0 in " + configFile.getName());
                ok = false;
            }
            if (OutputLoader.getById(output.getOutputId()) == null) {
                Research.plugin.getLogger().severe("[Research] Input '" + def.getId() + "' references unknown output '"
                        + output.getOutputId() + "' in " + configFile.getName());
                ok = false;
            }
        }
        return ok;
    }

    private boolean validateItemRef(String inputId, String field, String ref, File configFile) {
        if (ref == null || ref.isBlank()) {
            Research.plugin.getLogger().severe("[Research] Input '" + inputId + "' missing " + field
                    + " item in " + configFile.getName());
            return false;
        }
        if (!ItemRef.hasKnownPrefix(ref)) {
            Research.plugin.getLogger().severe("[Research] Input '" + inputId + "' has invalid " + field
                    + " item '" + ref + "' in " + configFile.getName());
            return false;
        }
        if (!ItemRef.isValid(ref)) {
            Research.plugin.getLogger().warning("[Research] Input '" + inputId + "' " + field
                    + " item could not be built: " + ref);
        }
        return true;
    }
}
