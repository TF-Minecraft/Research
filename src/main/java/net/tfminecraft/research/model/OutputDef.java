package net.tfminecraft.research.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class OutputDef {

    private final String id;
    private final boolean enabled;
    private final String mysteryDisplayItemRef;
    private final String productDisplayItemRef;
    private final int productRevealAfterConfirmedAspects;
    private final Map<String, AspectRequirement> aspects;
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

        this.aspects = new LinkedHashMap<>();
        ConfigurationSection aspectsSection = config.getConfigurationSection("aspects");
        if (aspectsSection != null) {
            for (String aspectId : aspectsSection.getKeys(false)) {
                aspects.put(aspectId, new AspectRequirement(aspectsSection.getConfigurationSection(aspectId)));
            }
        }

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

    public ResultSpec getResult() {
        return result;
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
