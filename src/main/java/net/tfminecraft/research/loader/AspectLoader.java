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
import net.tfminecraft.research.model.AspectDef;
import net.tfminecraft.research.registry.AspectItemRegistry;
import net.tfminecraft.research.util.ItemRef;
import net.tfminecraft.research.util.YamlFolder;

public final class AspectLoader implements LoaderInterface {

    private static final Map<String, AspectDef> map = new LinkedHashMap<>();

    public static Map<String, AspectDef> get() {
        return map;
    }

    public static AspectDef getById(String id) {
        return map.get(id);
    }

    @Override
    public void load(File configFile) {
        loadFile(configFile);
        AspectItemRegistry.rebuild(map);
    }

    public boolean loadFolder(File folder) {
        map.clear();
        boolean ok = true;
        List<File> files = new ArrayList<>(YamlFolder.listYamlFiles(folder));
        files.sort(Comparator.comparing(file -> file.getName().toLowerCase()));
        for (File file : files) {
            ok &= loadFile(file);
        }
        AspectItemRegistry.rebuild(map);
        return ok;
    }

    private boolean loadFile(File configFile) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Research.plugin.getLogger().severe("[Research] Failed to load aspect file " + configFile.getName()
                    + ": " + ex.getMessage());
            return false;
        }

        boolean ok = true;
        for (String key : config.getKeys(false)) {
            if (key.isBlank()) {
                Research.plugin.getLogger().severe("[Research] Empty aspect id in " + configFile.getName());
                ok = false;
                continue;
            }
            if (map.containsKey(key)) {
                Research.plugin.getLogger().severe("[Research] Duplicate aspect id '" + key + "' in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                Research.plugin.getLogger().severe("[Research] Aspect '" + key + "' has no data in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            AspectDef def = new AspectDef(key, section);
            ok &= validateDisplay(def, configFile);
            ok &= validatePulse(def, configFile);
            ok &= validateItemRefs(def, configFile);
            ok &= validateSounds(def, configFile);
            map.put(key, def);
        }
        return ok;
    }

    private boolean validateDisplay(AspectDef def, File configFile) {
        String displayRef = def.getDisplayItemRef();
        if (displayRef.isBlank()) {
            return true;
        }
        if (!ItemRef.hasKnownPrefix(displayRef)) {
            Research.plugin.getLogger().severe("[Research] Aspect '" + def.getId() + "' has invalid display item in "
                    + configFile.getName());
            return false;
        }
        if (!ItemRef.isValid(displayRef)) {
            Research.plugin.getLogger().warning("[Research] Aspect '" + def.getId() + "' display item could not be built: "
                    + displayRef);
        }
        return true;
    }

    private boolean validatePulse(AspectDef def, File configFile) {
        String pulseRef = def.getPulseColorRef();
        if (pulseRef.isBlank()) {
            return true;
        }
        if (!ItemRef.hasKnownPrefix(pulseRef)) {
            Research.plugin.getLogger().severe("[Research] Aspect '" + def.getId() + "' has invalid pulse_color in "
                    + configFile.getName());
            return false;
        }
        if (!ItemRef.isValid(pulseRef)) {
            Research.plugin.getLogger().warning("[Research] Aspect '" + def.getId() + "' pulse_color could not be built: "
                    + pulseRef);
        }
        return true;
    }

    private boolean validateItemRefs(AspectDef def, File configFile) {
        boolean ok = true;
        ok &= validateItemRefList(def.getId(), "primary_items", def.getPrimaryItems(), configFile);
        ok &= validateItemRefList(def.getId(), "secondary_items", def.getSecondaryItems(), configFile);
        return ok;
    }

    private boolean validateItemRefList(String aspectId, String field, List<String> refs, File configFile) {
        boolean ok = true;
        for (String ref : refs) {
            if (!ItemRef.hasKnownPrefix(ref)) {
                Research.plugin.getLogger().severe("[Research] Aspect '" + aspectId + "' has invalid " + field
                        + " entry '" + ref + "' in " + configFile.getName());
                ok = false;
            } else if (!ItemRef.isValid(ref)) {
                Research.plugin.getLogger().warning("[Research] Aspect '" + aspectId + "' " + field
                        + " item could not be built: " + ref);
            }
        }
        return ok;
    }

    private boolean validateSounds(AspectDef def, File configFile) {
        if (def.getSounds().getConfirm() == null || def.getSounds().getConfirm().isBlank()) {
            Research.plugin.getLogger().severe("[Research] Aspect '" + def.getId()
                    + "' has blank sounds.confirm in " + configFile.getName());
            return false;
        }
        return true;
    }
}
