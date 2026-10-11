package net.tfminecraft.research.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.ConfigurationSection;

public final class OutputDef {

    private final String id;
    private final boolean enabled;
    private final String mysteryDisplayItemRef;
    private final String productDisplayItemRef;
    private final int productRevealAfterConfirmedAspects;
    private final Map<String, AspectRequirement> aspects;
    private final Map<String, AspectRequirement> aspectPool;
    private final int aspectPoolPick;
    private final ResultSpec result;

    public OutputDef(String id, ConfigurationSection config) {
        this.id = id;
        this.enabled = config.getBoolean("enabled", true);

        ConfigurationSection mystery = config.getConfigurationSection("mystery_display");
        this.mysteryDisplayItemRef = mystery != null ? mystery.getString("item", "") : "";

        ConfigurationSection product = config.getConfigurationSection("product_display");
        this.productDisplayItemRef = product != null ? product.getString("item", "") : "";

        ConfigurationSection reveal = config.getConfigurationSection("product_reveal");
        this.productRevealAfterConfirmedAspects = reveal != null
                ? reveal.getInt("base_after_confirmed_aspects", 0)
                : 0;

        this.aspects = readAspects(config.getConfigurationSection("aspects"));

        ConfigurationSection pool = config.getConfigurationSection("aspect_pool");
        this.aspectPool = readAspects(pool != null ? pool.getConfigurationSection("aspects") : null);
        this.aspectPoolPick = pool != null ? pool.getInt("pick", 0) : 0;

        ConfigurationSection resultSection = config.getConfigurationSection("result");
        this.result = resultSection != null ? new ResultSpec(resultSection) : new ResultSpec(null);
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMysteryDisplayItemRef() {
        return mysteryDisplayItemRef;
    }

    public String getProductDisplayItemRef() {
        return productDisplayItemRef;
    }

    public int getProductRevealAfterConfirmedAspects() {
        return productRevealAfterConfirmedAspects;
    }

    public Map<String, AspectRequirement> getAspects() {
        return Collections.unmodifiableMap(aspects);
    }

    public Map<String, AspectRequirement> getAspectPool() {
        return Collections.unmodifiableMap(aspectPool);
    }

    public int getAspectPoolPick() {
        return aspectPoolPick;
    }

    /**
     * Draws {@code aspect_pool.pick} distinct aspects from the pool for a new project.
     */
    public List<String> rollPoolAspects() {
        List<String> ids = new ArrayList<>(aspectPool.keySet());
        Collections.shuffle(ids, ThreadLocalRandom.current());
        return new ArrayList<>(ids.subList(0, Math.max(0, Math.min(aspectPoolPick, ids.size()))));
    }

    /**
     * Keeps the saved draws still in the pool (up to {@code pick}) and draws again to fill any gap,
     * so a station survives pool edits without losing aspects it already researched.
     */
    public List<String> reconcilePoolAspects(List<String> saved) {
        List<String> kept = new ArrayList<>();
        if (saved != null) {
            for (String aspectId : saved) {
                if (kept.size() < aspectPoolPick && aspectPool.containsKey(aspectId) && !kept.contains(aspectId)) {
                    kept.add(aspectId);
                }
            }
        }
        List<String> remaining = new ArrayList<>(aspectPool.keySet());
        remaining.removeAll(kept);
        Collections.shuffle(remaining, ThreadLocalRandom.current());
        for (String aspectId : remaining) {
            if (kept.size() >= aspectPoolPick) {
                break;
            }
            kept.add(aspectId);
        }
        return kept;
    }

    /**
     * The aspects a project must complete: every fixed aspect, then the pool aspects it drew.
     */
    public Map<String, AspectRequirement> getRecipeAspects(ActiveProject project) {
        Map<String, AspectRequirement> recipe = new LinkedHashMap<>(aspects);
        if (project != null) {
            for (String aspectId : project.getPoolAspects()) {
                AspectRequirement requirement = aspectPool.get(aspectId);
                if (requirement != null) {
                    recipe.putIfAbsent(aspectId, requirement);
                }
            }
        }
        return recipe;
    }

    public ResultSpec getResult() {
        return result;
    }

    private static Map<String, AspectRequirement> readAspects(ConfigurationSection section) {
        Map<String, AspectRequirement> read = new LinkedHashMap<>();
        if (section != null) {
            for (String aspectId : section.getKeys(false)) {
                read.put(aspectId, new AspectRequirement(section.getConfigurationSection(aspectId)));
            }
        }
        return read;
    }

    public static final class ResultSpec {
        private final String itemRef;
        private final String templateRef;

        public ResultSpec(ConfigurationSection config) {
            if (config != null) {
                this.itemRef = config.getString("item", "");
                this.templateRef = config.getString("template", "");
            } else {
                this.itemRef = "";
                this.templateRef = "";
            }
        }

        public String getItemRef() {
            return itemRef;
        }

        public String getTemplateRef() {
            return templateRef;
        }

        public boolean hasItem() {
            return itemRef != null && !itemRef.isBlank();
        }

        public boolean hasTemplate() {
            return templateRef != null && !templateRef.isBlank();
        }
    }
}
