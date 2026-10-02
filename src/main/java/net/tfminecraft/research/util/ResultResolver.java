package net.tfminecraft.research.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.tfminecraft.research.loader.TemplateLoader;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResultTemplateDef;

public final class ResultResolver {

    private ResultResolver() {}

    public static String resolve(OutputDef.ResultSpec result) {
        if (result == null) {
            return null;
        }
        if (result.hasItem()) {
            String ref = result.getItemRef().trim();
            return ref.isEmpty() ? null : ref;
        }
        if (result.hasTemplate()) {
            return resolveToItemRef(result.getTemplateRef());
        }
        return null;
    }

    private static String resolveToItemRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        if (ResultRef.isTemplateRef(ref)) {
            ResultTemplateDef template = TemplateLoader.getById(ResultRef.templateId(ref));
            if (template == null || template.getOutputs().isEmpty()) {
                return null;
            }
            ResultTemplateDef.WeightedOutput chosen = pickWeightedTemplate(template.getOutputs());
            return resolveToItemRef(chosen.getRef());
        }
        if (ResultRef.isItemRef(ref)) {
            return ref;
        }
        return null;
    }

    private static ResultTemplateDef.WeightedOutput pickWeightedTemplate(
            List<ResultTemplateDef.WeightedOutput> outputs) {
        // Scale before summing so finite weights cannot overflow the random bound.
        double scale = 0.0;
        for (ResultTemplateDef.WeightedOutput output : outputs) {
            scale = Math.max(scale, output.getWeight());
        }
        if (scale <= 0.0) {
            return outputs.get(0);
        }
        double total = 0.0;
        for (ResultTemplateDef.WeightedOutput output : outputs) {
            total += output.getWeight() / scale;
        }
        if (total <= 0.0) {
            return outputs.get(0);
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0;
        for (int i = 0; i < outputs.size() - 1; i++) {
            ResultTemplateDef.WeightedOutput output = outputs.get(i);
            cumulative += output.getWeight() / scale;
            if (roll < cumulative) {
                return output;
            }
        }
        return outputs.get(outputs.size() - 1);
    }
}
