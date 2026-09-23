package net.tfminecraft.research.model;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.loader.AspectLoader;

public final class ExperimentMatch {

    private final String itemRef;
    private final String primaryAspectId;
    private final String secondaryAspectId;

    public ExperimentMatch(String itemRef, String primaryAspectId, String secondaryAspectId) {
        this.itemRef = itemRef;
        this.primaryAspectId = primaryAspectId;
        this.secondaryAspectId = secondaryAspectId;
    }

    public String getItemRef() {
        return itemRef;
    }

    public String getPrimaryAspect() {
        return primaryAspectId;
    }

    public int getPrimaryPoints() {
        return primaryAspectId != null && !primaryAspectId.isBlank() ? Cache.experimentPrimaryPoints : 0;
    }

    public String getSecondaryAspect() {
        return secondaryAspectId;
    }

    public int getSecondaryPoints() {
        return secondaryAspectId != null && !secondaryAspectId.isBlank() ? Cache.experimentSecondaryPoints : 0;
    }

    public AspectDef getSoundAspect() {
        if (primaryAspectId != null && !primaryAspectId.isBlank()) {
            return AspectLoader.getById(primaryAspectId);
        }
        if (secondaryAspectId != null && !secondaryAspectId.isBlank()) {
            return AspectLoader.getById(secondaryAspectId);
        }
        return null;
    }
}
