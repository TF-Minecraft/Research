package net.tfminecraft.research.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.research.util.GridLayout;

/**
 * Runtime state for an active research project on a station.
 */
public final class ActiveProject {

    private final String inputId;
    private final String resolvedOutputId;
    private final String resolvedResultRef;
    private boolean productRevealed;
    private boolean completed;
    private final Map<String, String> aspectStates = new HashMap<>();
    private final Map<String, Integer> aspectPoints = new HashMap<>();
    private final Map<String, Integer> aspectTestCounts = new HashMap<>();
    private final Set<String> testedExperimentItems = new HashSet<>();
    private final List<String> aspectSlotOrder = new ArrayList<>();

    public ActiveProject(String inputId, String resolvedOutputId, String resolvedResultRef) {
        this.inputId = inputId;
        this.resolvedOutputId = resolvedOutputId;
        this.resolvedResultRef = resolvedResultRef;
        this.productRevealed = false;
        this.completed = false;
    }

    public ActiveProject(String inputId, String resolvedOutputId, String resolvedResultRef, boolean productRevealed) {
        this.inputId = inputId;
        this.resolvedOutputId = resolvedOutputId;
        this.resolvedResultRef = resolvedResultRef;
        this.productRevealed = productRevealed;
        this.completed = false;
    }

    public String getInputId() {
        return inputId;
    }

    public String getResolvedOutputId() {
        return resolvedOutputId;
    }

    public String getResolvedResultRef() {
        return resolvedResultRef;
    }

    /**
     * @deprecated Use {@link #getResolvedOutputId()} for batch 18+ output-centric model.
     */
    @Deprecated
    public String getProjectId() {
        return resolvedOutputId;
    }

    public boolean isProductRevealed() {
        return productRevealed;
    }

    public void setProductRevealed(boolean productRevealed) {
        this.productRevealed = productRevealed;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Map<String, String> getAspectStates() {
        return aspectStates;
    }

    public Map<String, Integer> getAspectPoints() {
        return aspectPoints;
    }

    public Map<String, Integer> getAspectTestCounts() {
        return aspectTestCounts;
    }

    public Set<String> getTestedExperimentItems() {
        return testedExperimentItems;
    }

    public List<String> getAspectSlotOrder() {
        return aspectSlotOrder;
    }

    public void setAspectSlotOrder(List<String> order) {
        aspectSlotOrder.clear();
        if (order == null) {
            return;
        }
        for (String aspectId : order) {
            aspectSlotOrder.add(aspectId != null ? aspectId : "");
        }
    }

    public boolean hasAspectSlotOrder() {
        return aspectSlotOrder.size() == GridLayout.ASPECT_ROW_COUNT;
    }

    public AspectState getAspectState(String aspectId) {
        String raw = aspectStates.get(aspectId);
        if (raw == null || raw.isBlank()) {
            return AspectState.UNKNOWN;
        }
        try {
            return AspectState.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return AspectState.UNKNOWN;
        }
    }

    public void setAspectState(String aspectId, AspectState state) {
        if (aspectId != null && !aspectId.isBlank() && state != null) {
            aspectStates.put(aspectId, state.name());
        }
    }

    public int getAspectPoints(String aspectId) {
        return aspectPoints.getOrDefault(aspectId, 0);
    }

    public int getAspectTestCount(String aspectId) {
        return aspectTestCounts.getOrDefault(aspectId, 0);
    }

    public void incrementAspectTestCount(String aspectId) {
        if (aspectId != null && !aspectId.isBlank()) {
            aspectTestCounts.merge(aspectId, 1, Integer::sum);
        }
    }

    public void addAspectPoints(String aspectId, int points, int maxRequired) {
        if (aspectId == null || aspectId.isBlank() || points <= 0 || maxRequired <= 0) {
            return;
        }
        if (getAspectState(aspectId) == AspectState.REJECTED) {
            return;
        }
        int current = getAspectPoints(aspectId);
        if (current >= maxRequired) {
            return;
        }
        aspectPoints.put(aspectId, Math.min(maxRequired, current + points));
    }

    public boolean hasTestedItem(String experimentItemId) {
        return testedExperimentItems.contains(experimentItemId);
    }

    public void markTested(String experimentItemId) {
        if (experimentItemId != null && !experimentItemId.isBlank()) {
            testedExperimentItems.add(experimentItemId);
        }
    }
}
