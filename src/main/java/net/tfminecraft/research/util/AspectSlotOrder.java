package net.tfminecraft.research.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.tfminecraft.research.Research;
import net.tfminecraft.research.model.OutputDef;

public final class AspectSlotOrder {

    private AspectSlotOrder() {}

    public static List<String> buildRowAssignments(OutputDef def) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < GridLayout.ASPECT_ROW_COUNT; i++) {
            rows.add("");
        }
        if (def == null || def.getAspects().isEmpty()) {
            return rows;
        }

        List<String> aspectIds = new ArrayList<>(def.getAspects().keySet());
        Collections.shuffle(aspectIds, ThreadLocalRandom.current());

        List<Integer> rowIndices = new ArrayList<>();
        for (int i = 0; i < GridLayout.ASPECT_ROW_COUNT; i++) {
            rowIndices.add(i);
        }
        Collections.shuffle(rowIndices, ThreadLocalRandom.current());

        for (int i = 0; i < aspectIds.size() && i < rowIndices.size(); i++) {
            rows.set(rowIndices.get(i), aspectIds.get(i));
        }
        return rows;
    }

    /**
     * Returns six row assignments (index 0 = top row). Empty string = decoy row.
     */
    public static List<String> reconcileRowAssignments(OutputDef def, List<String> saved, String context) {
        if (def == null) {
            return emptyRows();
        }
        if (saved == null || saved.isEmpty()) {
            Research.plugin.getLogger().info("[Research] Assigned shuffled aspect rows for " + context
                    + " (no saved order).");
            return buildRowAssignments(def);
        }
        if (saved.size() != GridLayout.ASPECT_ROW_COUNT || !isValidRowAssignments(def, saved)) {
            Research.plugin.getLogger().warning("[Research] Aspect row assignments for " + context
                    + " were missing or stale; reshuffling.");
            return buildRowAssignments(def);
        }
        return new ArrayList<>(saved);
    }

    public static String aspectIdForRow(List<String> rowAssignments, int rowIndex) {
        if (rowAssignments == null || rowIndex < 0 || rowIndex >= rowAssignments.size()) {
            return "";
        }
        String aspectId = rowAssignments.get(rowIndex);
        return aspectId != null ? aspectId : "";
    }

    public static boolean isDecoyRow(List<String> rowAssignments, int rowIndex) {
        return aspectIdForRow(rowAssignments, rowIndex).isBlank();
    }

    private static boolean isValidRowAssignments(OutputDef def, List<String> saved) {
        List<String> expected = new ArrayList<>(def.getAspects().keySet());
        List<String> found = new ArrayList<>();
        for (String entry : saved) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (!def.getAspects().containsKey(entry) || found.contains(entry)) {
                return false;
            }
            found.add(entry);
        }
        if (found.size() != expected.size()) {
            return false;
        }
        for (String aspectId : expected) {
            if (!found.contains(aspectId)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> emptyRows() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < GridLayout.ASPECT_ROW_COUNT; i++) {
            rows.add("");
        }
        return rows;
    }
}
