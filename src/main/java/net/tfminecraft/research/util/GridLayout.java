package net.tfminecraft.research.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Slot map for the 54-slot (9x6) vertical research station GUI.
 */
public final class GridLayout {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;

    public static final int ASPECT_ROW_COUNT = 6;
    public static final int ASPECT_PROGRESS_PANES = 5;
    public static final int ASPECT_LOCK_COL = 3;
    public static final int ASPECT_PROGRESS_COL_START = 4;

    public static final int SLOT_SCRAP = 0;
    public static final int SLOT_MENTAL_POINTS = 45;
    public static final int SLOT_PRODUCT = 10;
    public static final int SLOT_EXPERIMENT = 27;
    public static final int SLOT_CONFIRM_EXPERIMENT = 47;
    public static final int SLOT_PRIMARY_PREVIEW = 28;
    public static final int SLOT_SECONDARY_PREVIEW = 37;

    public static final int PRODUCT_ROW = 1;
    public static final int PRODUCT_COL = 1;

  /**
     * Vertical aspect lock column (col 3), top to bottom.
     */
    public static final List<Integer> ASPECT_COLUMN_SLOTS = Collections.unmodifiableList(Arrays.asList(
            3, 12, 21, 30, 39, 48));

    private static final Set<Integer> CONTROL_SLOTS = new HashSet<>(Arrays.asList(
            SLOT_SCRAP,
            SLOT_MENTAL_POINTS,
            SLOT_EXPERIMENT,
            SLOT_CONFIRM_EXPERIMENT,
            SLOT_PRIMARY_PREVIEW,
            SLOT_SECONDARY_PREVIEW));

    private static final Set<Integer> ASPECT_ROW_SLOT_SET = new HashSet<>();

    private GridLayout() {}

    static {
        for (int row = 0; row < ASPECT_ROW_COUNT; row++) {
            ASPECT_ROW_SLOT_SET.addAll(getAspectRowSlots(row));
        }
    }

    public static int slot(int row, int col) {
        return row * COLUMNS + col;
    }

    public static int slotToRow(int slot) {
        return slot / COLUMNS;
    }

    public static int slotToCol(int slot) {
        return slot % COLUMNS;
    }

    public static int getAspectLockSlot(int rowIndex) {
        return slot(rowIndex, ASPECT_LOCK_COL);
    }

    public static List<Integer> getAspectProgressSlots(int rowIndex) {
        List<Integer> slots = new ArrayList<>(ASPECT_PROGRESS_PANES);
        for (int pane = 0; pane < ASPECT_PROGRESS_PANES; pane++) {
            slots.add(slot(rowIndex, ASPECT_PROGRESS_COL_START + pane));
        }
        return slots;
    }

    public static List<Integer> getAspectRowSlots(int rowIndex) {
        List<Integer> slots = new ArrayList<>(ASPECT_PROGRESS_PANES + 1);
        slots.add(getAspectLockSlot(rowIndex));
        slots.addAll(getAspectProgressSlots(rowIndex));
        return slots;
    }

    public static List<Integer> allAspectRowSlots() {
        return new ArrayList<>(ASPECT_ROW_SLOT_SET);
    }

    public static boolean isLeftPanelSlot(int slot) {
        return slotToCol(slot) < 3;
    }

    public static boolean isControlSlot(int slot) {
        return CONTROL_SLOTS.contains(slot);
    }

    public static boolean isProductSlot(int slot) {
        return slot == SLOT_PRODUCT;
    }

    public static boolean isAspectColumnSlot(int slot) {
        return ASPECT_COLUMN_SLOTS.contains(slot);
    }

    public static boolean isAspectRowSlot(int slot) {
        return ASPECT_ROW_SLOT_SET.contains(slot);
    }

    public static boolean isReservedStationSlot(int slot) {
        return isControlSlot(slot) || isProductSlot(slot);
    }
}
