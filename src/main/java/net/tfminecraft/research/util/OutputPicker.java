package net.tfminecraft.research.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.tfminecraft.research.model.InputDef;

public final class OutputPicker {

    private OutputPicker() {}

    public static String roll(InputDef input) {
        if (input == null) {
            return null;
        }
        InputDef.WeightedOutput chosen = pickWeighted(input.getOutputs());
        return chosen != null ? chosen.getOutputId() : null;
    }

    private static InputDef.WeightedOutput pickWeighted(List<InputDef.WeightedOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (InputDef.WeightedOutput output : outputs) {
            total += output.getWeight();
        }
        if (total <= 0.0) {
            return outputs.get(0);
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0;
        for (InputDef.WeightedOutput output : outputs) {
            cumulative += output.getWeight();
            if (roll < cumulative) {
                return output;
            }
        }
        return outputs.get(outputs.size() - 1);
    }
}
