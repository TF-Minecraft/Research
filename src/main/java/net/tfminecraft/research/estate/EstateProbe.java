package net.tfminecraft.research.estate;

/** Whether TLibs has the estate API (2.6.0). On older TLibs stations stay with dead owners. */
public final class EstateProbe {

    public static final String[] CLASSES = {
        "net.tfminecraft.tlibs.character.estate.Estates",
        "net.tfminecraft.tlibs.character.estate.EstateParticipant",
        "net.tfminecraft.tlibs.character.estate.EstateDecision",
        "net.tfminecraft.tlibs.character.estate.Outcome",
        "net.tfminecraft.tlibs.character.estate.Holdings",
    };

    private EstateProbe() {}

    public static boolean available(String... classes) {
        try {
            for (String name : classes) {
                Class.forName(name);
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }
}
