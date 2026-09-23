package net.tfminecraft.research.loader;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;

public final class MessagesLoader implements LoaderInterface {

    @Override
    public void load(File configFile) {
        if (!loadInternal(configFile)) {
            Research.plugin.getLogger().severe("[Research] messages.yml load failed.");
        }
    }

    public boolean loadSafe(File configFile) {
        return loadInternal(configFile);
    }

    private boolean loadInternal(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Research.plugin.getLogger().severe("[Research] Failed to load messages.yml: " + ex.getMessage());
            return false;
        }

        Messages.clear();
        flatten("", config);
        return true;
    }

    private void flatten(String prefix, ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flatten(path, section.getConfigurationSection(key));
            } else {
                String value = section.getString(key, "");
                if ("prefix".equals(path)) {
                    Messages.setPrefix(value);
                } else {
                    Messages.put(path, value);
                }
            }
        }
    }
}
