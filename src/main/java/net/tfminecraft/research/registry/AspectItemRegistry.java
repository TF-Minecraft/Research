package net.tfminecraft.research.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.research.Research;
import net.tfminecraft.research.model.AspectDef;
import net.tfminecraft.research.model.ExperimentMatch;
import net.tfminecraft.research.util.ItemRef;

public final class AspectItemRegistry {

    private static final Map<String, String> primaryByRef = new LinkedHashMap<>();
    private static final Map<String, String> secondaryByRef = new LinkedHashMap<>();
    private static List<ExperimentMatch> matches = Collections.emptyList();

    private AspectItemRegistry() {}

    public static void rebuild(Map<String, AspectDef> aspects) {
        primaryByRef.clear();
        secondaryByRef.clear();

        if (aspects == null || aspects.isEmpty()) {
            matches = Collections.emptyList();
            return;
        }

        for (AspectDef aspect : aspects.values()) {
            registerPrimaryItems(aspect);
            registerSecondaryItems(aspect);
        }

        Map<String, ExperimentMatch> merged = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : primaryByRef.entrySet()) {
            merged.put(entry.getKey(), new ExperimentMatch(entry.getKey(), entry.getValue(), null));
        }
        for (Map.Entry<String, String> entry : secondaryByRef.entrySet()) {
            String ref = entry.getKey();
            ExperimentMatch existing = merged.get(ref);
            if (existing != null) {
                merged.put(ref, new ExperimentMatch(ref, existing.getPrimaryAspect(), entry.getValue()));
            } else {
                merged.put(ref, new ExperimentMatch(ref, null, entry.getValue()));
            }
        }
        matches = List.copyOf(merged.values());
    }

    public static ExperimentMatch findByItemStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        for (ExperimentMatch match : matches) {
            if (ItemRef.matches(stack, match.getItemRef())) {
                return match;
            }
        }
        return null;
    }

    private static void registerPrimaryItems(AspectDef aspect) {
        for (String ref : aspect.getPrimaryItems()) {
            String normalized = ItemRef.normalize(ref);
            if (normalized.isBlank()) {
                continue;
            }
            String previous = primaryByRef.get(normalized);
            if (previous != null && !previous.equals(aspect.getId())) {
                Research.plugin.getLogger().severe("[Research] Item '" + normalized
                        + "' registered as primary on aspects '" + previous + "' and '" + aspect.getId()
                        + "'; '" + aspect.getId() + "' wins.");
            }
            primaryByRef.put(normalized, aspect.getId());
        }
    }

    private static void registerSecondaryItems(AspectDef aspect) {
        for (String ref : aspect.getSecondaryItems()) {
            String normalized = ItemRef.normalize(ref);
            if (normalized.isBlank()) {
                continue;
            }
            if (primaryByRef.containsKey(normalized)
                    && aspect.getId().equals(primaryByRef.get(normalized))) {
                Research.plugin.getLogger().warning("[Research] Item '" + normalized
                        + "' listed as primary and secondary on aspect '" + aspect.getId()
                        + "'; primary wins.");
                continue;
            }
            String previous = secondaryByRef.get(normalized);
            if (previous != null && !previous.equals(aspect.getId())) {
                Research.plugin.getLogger().severe("[Research] Item '" + normalized
                        + "' registered as secondary on aspects '" + previous + "' and '" + aspect.getId()
                        + "'; '" + aspect.getId() + "' wins.");
            }
            secondaryByRef.put(normalized, aspect.getId());
        }
    }
}
