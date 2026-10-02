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
        if (outputs.isEmpty()) {
            return null;
        }
        // Scale before summing so finite weights cannot overflow the random bound.
        double scale = 0.0;
        for (InputDef.WeightedOutput output : outputs) {
            scale = Math.max(scale, output.getWeight());
        }
        if (scale <= 0.0) {
            return outputs.get(0);
        }
        double total = 0.0;
        for (InputDef.WeightedOutput output : outputs) {
            total += output.getWeight() / scale;
        }
        if (total <= 0.0) {
            return outputs.get(0);
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0;
        for (int i = 0; i < outputs.size() - 1; i++) {
            InputDef.WeightedOutput output = outputs.get(i);
            cumulative += output.getWeight() / scale;
            if (roll < cumulative) {
                return output;
            }
        }
        return outputs.get(outputs.size() - 1);
    }
}
