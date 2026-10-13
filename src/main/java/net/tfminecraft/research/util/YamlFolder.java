package net.tfminecraft.research.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class YamlFolder {

    // Sorted so that duplicate ids resolve the same way on every filesystem: the first file by name wins.
    private static final Comparator<File> BY_NAME = Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(File::getName);

    private YamlFolder() {}

    public static List<File> listYamlFiles(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<File> yamlFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".yml")) {
                yamlFiles.add(file);
            }
        }
        yamlFiles.sort(BY_NAME);
        return yamlFiles;
    }
}
