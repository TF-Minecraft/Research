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
            return result.getItemRef().trim();
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
            if (chosen == null) {
                return null;
            }
            return resolveToItemRef(chosen.getRef());
        }
        if (ResultRef.isItemRef(ref)) {
            return ref;
        }
        return null;
    }

    private static ResultTemplateDef.WeightedOutput pickWeightedTemplate(
            List<ResultTemplateDef.WeightedOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (ResultTemplateDef.WeightedOutput output : outputs) {
            total += output.getWeight();
        }
        if (total <= 0.0) {
            return outputs.get(0);
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0;
        for (ResultTemplateDef.WeightedOutput output : outputs) {
            cumulative += output.getWeight();
            if (roll < cumulative) {
                return output;
            }
        }
        return outputs.get(outputs.size() - 1);
    }
}
