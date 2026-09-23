package net.tfminecraft.research.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StationSaveData {

    private String world;
    private int x;
    private int y;
    private int z;
    private String owner_uuid;
    private String input_id;
    private String resolved_output_id;
    private String resolved_result_ref;
    private boolean product_revealed;
    private boolean completed;
    private Map<String, Integer> aspect_points = new HashMap<>();
    private Map<String, Integer> aspect_test_counts = new HashMap<>();
    private Map<String, String> aspect_states = new HashMap<>();
    private List<String> tested_items = new ArrayList<>();
    private List<String> aspect_slot_order = new ArrayList<>();

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getOwnerUuid() {
        return owner_uuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.owner_uuid = ownerUuid;
    }

    public String getInputId() {
        return input_id;
    }

    public void setInputId(String inputId) {
        this.input_id = inputId;
    }

    public String getResolvedOutputId() {
        return resolved_output_id;
    }

    public void setResolvedOutputId(String resolvedOutputId) {
        this.resolved_output_id = resolvedOutputId;
    }

    public String getResolvedResultRef() {
        return resolved_result_ref;
    }

    public void setResolvedResultRef(String resolvedResultRef) {
        this.resolved_result_ref = resolvedResultRef;
    }

    /**
     * @deprecated Legacy field from project-centric saves; not read on load.
     */
    @Deprecated
    public String getProjectId() {
        return resolved_output_id;
    }

    /**
     * @deprecated Legacy field from project-centric saves; not written.
     */
    @Deprecated
    public void setProjectId(String projectId) {
        this.resolved_output_id = projectId;
    }

    public boolean isProductRevealed() {
        return product_revealed;
    }

    public void setProductRevealed(boolean productRevealed) {
        this.product_revealed = productRevealed;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Map<String, Integer> getAspectPoints() {
        return aspect_points;
    }

    public void setAspectPoints(Map<String, Integer> aspectPoints) {
        this.aspect_points = aspectPoints != null ? aspectPoints : new HashMap<>();
    }

    public Map<String, Integer> getAspectTestCounts() {
        return aspect_test_counts;
    }

    public void setAspectTestCounts(Map<String, Integer> aspectTestCounts) {
        this.aspect_test_counts = aspectTestCounts != null ? aspectTestCounts : new HashMap<>();
    }

    public Map<String, String> getAspectStates() {
        return aspect_states;
    }

    public void setAspectStates(Map<String, String> aspectStates) {
        this.aspect_states = aspectStates != null ? aspectStates : new HashMap<>();
    }

    public List<String> getTestedItems() {
        return tested_items;
    }

    public void setTestedItems(List<String> testedItems) {
        this.tested_items = testedItems != null ? testedItems : new ArrayList<>();
    }

    public List<String> getAspectSlotOrder() {
        return aspect_slot_order;
    }

    public void setAspectSlotOrder(List<String> aspectSlotOrder) {
        this.aspect_slot_order = aspectSlotOrder != null ? aspectSlotOrder : new ArrayList<>();
    }
}
