package net.tfminecraft.research.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class InputDef {

    private final String id;
    private final boolean enabled;
    private final String startItemRef;
    private final int startItemAmount;
    private final List<WeightedOutput> outputs;

    public InputDef(String id, ConfigurationSection config) {
        this.id = id;
        this.enabled = config.getBoolean("enabled", true);

        ConfigurationSection startItem = config.getConfigurationSection("start_item");
        if (startItem != null) {
            this.startItemRef = startItem.getString("item", "");
            this.startItemAmount = startItem.getInt("amount", 1);
        } else {
            this.startItemRef = "";
            this.startItemAmount = 1;
        }

        this.outputs = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList("outputs")) {
            String outputId = entry.get("id") != null ? String.valueOf(entry.get("id")) : "";
            double weight = 1.0;
            if (entry.get("weight") instanceof Number number) {
                weight = number.doubleValue();
            }
            outputs.add(new WeightedOutput(outputId, weight));
        }
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getStartItemRef() {
        return startItemRef;
    }

    public int getStartItemAmount() {
        return startItemAmount;
    }

    public boolean requiresStartItem() {
        return startItemRef != null && !startItemRef.isBlank();
    }

    public List<WeightedOutput> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    public static final class WeightedOutput {
        private final String outputId;
        private final double weight;

        public WeightedOutput(String outputId, double weight) {
            this.outputId = outputId;
            this.weight = weight;
        }

        public String getOutputId() {
            return outputId;
        }

        public double getWeight() {
            return weight;
        }
    }
}
