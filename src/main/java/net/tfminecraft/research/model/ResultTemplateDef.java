package net.tfminecraft.research.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class ResultTemplateDef {

    private final String id;
    private final List<WeightedOutput> outputs;

    public ResultTemplateDef(String id, ConfigurationSection config) {
        this.id = id;
        this.outputs = new ArrayList<>();
        if (config != null) {
            for (Map<?, ?> entry : config.getMapList("outputs")) {
                String ref = entry.get("item") != null ? String.valueOf(entry.get("item")) : "";
                double weight = 1.0;
                if (entry.get("weight") instanceof Number number) {
                    weight = number.doubleValue();
                }
                outputs.add(new WeightedOutput(ref, weight));
            }
        }
    }

    public ResultTemplateDef(String id, List<WeightedOutput> outputs) {
        this.id = id;
        this.outputs = new ArrayList<>(outputs);
    }

    public String getId() {
        return id;
    }

    public List<WeightedOutput> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    public static final class WeightedOutput {
        private final String ref;
        private final double weight;

        public WeightedOutput(String ref, double weight) {
            this.ref = ref;
            this.weight = weight;
        }

        public String getRef() {
            return ref;
        }

        public double getWeight() {
            return weight;
        }
    }
}
