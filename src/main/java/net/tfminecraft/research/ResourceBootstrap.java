package net.tfminecraft.research;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Copies required default resource files into the plugin data folder when missing.
 * Content folders (aspects, inputs, outputs, templates) are created empty and are not shipped.
 */
public final class ResourceBootstrap {

  private ResourceBootstrap() {}

  public static void copyIfMissing(JavaPlugin plugin, String relativePath) {
    File target = new File(plugin.getDataFolder(), relativePath);
    if (!target.exists()) {
      File parent = target.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }
      plugin.saveResource(relativePath, false);
    }
  }
}
