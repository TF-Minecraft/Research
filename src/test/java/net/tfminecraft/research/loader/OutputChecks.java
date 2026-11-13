package net.tfminecraft.research.loader;

/** Lets tests in other packages set which outputs failed their load checks. */
public final class OutputChecks {

    private OutputChecks() {}

    public static void markFailed(String id) {
        OutputLoader.markFailed(id);
    }

    public static void reset() {
        OutputLoader.resetChecks();
    }
}
