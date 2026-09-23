package net.tfminecraft.research.util;

public final class ResultRef {

    private static final String TEMPLATE_PREFIX = "t.";

    private ResultRef() {}

    public static boolean isTemplateRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        return ref.trim().toLowerCase().startsWith(TEMPLATE_PREFIX);
    }

    public static String templateId(String ref) {
        if (!isTemplateRef(ref)) {
            return "";
        }
        return ref.trim().substring(TEMPLATE_PREFIX.length());
    }

    public static boolean isItemRef(String ref) {
        return ItemRef.hasKnownPrefix(ref);
    }
}
