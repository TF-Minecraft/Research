package net.tfminecraft.research.util;

import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.AspectState;
import net.tfminecraft.research.model.OutputDef;

public final class RevealHelper {

    private RevealHelper() {}

    public static int countConfirmedRecipeAspects(ActiveProject active, OutputDef def) {
        int count = 0;
        for (String aspectId : def.getAspects().keySet()) {
            if (active.getAspectState(aspectId) == AspectState.CONFIRMED) {
                count++;
            }
        }
        return count;
    }
}
