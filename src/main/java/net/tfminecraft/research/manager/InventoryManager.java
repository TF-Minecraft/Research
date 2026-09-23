package net.tfminecraft.research.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.research.GuiCache;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.loader.AspectLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.AspectDef;
import net.tfminecraft.research.model.AspectRequirement;
import net.tfminecraft.research.model.AspectState;
import net.tfminecraft.research.model.ExperimentMatch;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.research.registry.AspectItemRegistry;
import net.tfminecraft.research.util.DiscoveryScaling;
import net.tfminecraft.research.util.AspectProgressLayout;
import net.tfminecraft.research.util.AspectSlotOrder;
import net.tfminecraft.research.util.GridLayout;
import net.tfminecraft.research.util.GuiText;
import net.tfminecraft.research.util.GuiText.AspectNameStyle;
import net.tfminecraft.research.util.ItemRef;

public final class InventoryManager {

    public static final int CONFIRM_SCRAP_YES = 3;
    public static final int CONFIRM_SCRAP_NO = 5;

    public static String mainInventoryTitle() {
        String hex = GuiCache.color("inventory_title", GuiCache.color("label_body", "#d4c9ae"));
        return GuiText.format(hex + (GuiCache.inventoryTitleLabel != null
                ? GuiCache.inventoryTitleLabel : "Research Station"));
    }

    public static String scrapConfirmTitle() {
        String hex = GuiCache.color("inventory_title", GuiCache.color("label_body", "#d4c9ae"));
        return GuiText.format(hex + (GuiCache.scrapConfirmTitleLabel != null
                ? GuiCache.scrapConfirmTitleLabel : "Confirm Scrap"));
    }

    private static final Set<Integer> UNFILLED_SLOTS = new HashSet<>(Arrays.asList(
            GridLayout.SLOT_PRIMARY_PREVIEW,
            GridLayout.SLOT_SECONDARY_PREVIEW,
            GridLayout.SLOT_EXPERIMENT,
            GridLayout.SLOT_CONFIRM_EXPERIMENT));

    private final PlayerManager playerManager;

    public InventoryManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public void openMain(Player player, ResearchStation station) {
        Inventory inventory = Research.plugin.getServer().createInventory(null, 54, mainInventoryTitle());
        populateMain(player, station, inventory);
        player.openInventory(inventory);
    }

    public void populateMain(Player player, ResearchStation station, Inventory inventory) {
        OutputDef output = OutputLoader.getById(station.getProject().getResolvedOutputId());
        if (output == null) {
            return;
        }

        ActiveProject activeProject = station.getProject();

        String centerRef = activeProject.isProductRevealed()
                ? output.getProductDisplayItemRef()
                : output.getMysteryDisplayItemRef();
        ItemStack productItem = ItemRef.build(centerRef);
        if (productItem != null) {
            inventory.setItem(GridLayout.SLOT_PRODUCT, productItem);
        }

        List<String> rowAssignments = activeProject.getAspectSlotOrder();
        if (!activeProject.hasAspectSlotOrder()) {
            rowAssignments = AspectSlotOrder.reconcileRowAssignments(output, null,
                    "station " + station.getProject().getResolvedOutputId());
            activeProject.setAspectSlotOrder(rowAssignments);
        }

        Set<Integer> aspectRowSlots = new HashSet<>(GridLayout.allAspectRowSlots());
        for (int row = 0; row < GridLayout.ASPECT_ROW_COUNT; row++) {
            String aspectId = AspectSlotOrder.aspectIdForRow(rowAssignments, row);
            if (AspectSlotOrder.isDecoyRow(rowAssignments, row)) {
                inventory.setItem(GridLayout.getAspectLockSlot(row), buildAspectRowDivider());
                renderInactiveProgressRow(inventory, row);
                continue;
            }

            AspectRequirement requirement = output.getAspects().get(aspectId);
            if (requirement == null) {
                inventory.setItem(GridLayout.getAspectLockSlot(row), buildAspectRowDivider());
                renderInactiveProgressRow(inventory, row);
                continue;
            }

            renderAspectRow(player, inventory, row, aspectId, activeProject, requirement);
        }

        inventory.setItem(GridLayout.SLOT_MENTAL_POINTS, buildMentalPointsItem(player));
        inventory.setItem(GridLayout.SLOT_SCRAP, buildScrapButton());
        inventory.setItem(GridLayout.SLOT_CONFIRM_EXPERIMENT, buildConfirmButton(activeProject));

        ItemStack experiment = inventory.getItem(GridLayout.SLOT_EXPERIMENT);
        updateExperimentPreview(inventory, experiment, activeProject);

        fillGridEmpty(inventory, aspectRowSlots);
    }

    private void renderAspectRow(Player player, Inventory inventory, int rowIndex, String aspectId,
            ActiveProject activeProject, AspectRequirement requirement) {
        int points = activeProject.getAspectPoints(aspectId);
        int required = requirement.getRequiredPoints();
        AspectState state = activeProject.getAspectState(aspectId);
        AspectDef aspect = AspectLoader.getById(aspectId);

        if (state == AspectState.REJECTED) {
            AspectRowLabel label = buildAspectRowLabel(aspect, aspectId, state);
            inventory.setItem(GridLayout.getAspectLockSlot(rowIndex),
                    buildAspectDisplayWithLabel(aspect, label));
            renderRejectedProgressRow(inventory, rowIndex);
            return;
        }

        if (state == AspectState.CONFIRMED || DiscoveryScaling.isAspectIdentityRevealed(player, points, required)) {
            AspectRowLabel label = buildAspectRowLabel(aspect, aspectId, state);
            inventory.setItem(GridLayout.getAspectLockSlot(rowIndex),
                    buildAspectDisplayWithLabel(aspect, label));
            renderProgressRow(inventory, rowIndex, points, required, label.displayName, false);
            return;
        }

        if (DiscoveryScaling.isAspectRowRevealed(player, points, required)) {
            AspectRowLabel unknown = undiscoveredRowLabel();
            inventory.setItem(GridLayout.getAspectLockSlot(rowIndex),
                    applyLabel(buildTestedUnknownAspectItem(), unknown));
            renderProgressRow(inventory, rowIndex, points, required, unknown.displayName, true);
            return;
        }

        inventory.setItem(GridLayout.getAspectLockSlot(rowIndex), buildAspectRowDivider());
        renderInactiveProgressRow(inventory, rowIndex);
    }

    private void renderProgressRow(Inventory inventory, int rowIndex, int currentPoints, int requiredPoints,
            String displayName, boolean unknown) {
        List<String> pointsLore = List.of(pointsProgressLine(currentPoints, requiredPoints));
        List<Integer> progressSlots = GridLayout.getAspectProgressSlots(rowIndex);
        for (int pane = 0; pane < progressSlots.size(); pane++) {
            int slot = progressSlots.get(pane);
            if (!AspectProgressLayout.isPaneActive(pane, requiredPoints)) {
                inventory.setItem(slot, buildInactiveProgressPane());
                continue;
            }
            if (currentPoints > 0 && AspectProgressLayout.isPaneFilled(pane, currentPoints, requiredPoints)) {
                if (unknown) {
                    inventory.setItem(slot, buildUnknownProgressPane(displayName, List.of()));
                } else {
                    inventory.setItem(slot, buildRevealedProgressPane(displayName, pointsLore));
                }
            } else {
                inventory.setItem(slot, buildInactiveProgressPane());
            }
        }
    }

    private static String pointsProgressLine(int current, int required) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current", String.valueOf(current));
        placeholders.put("required", String.valueOf(required));
        return GuiText.guiMessage("aspect.points_progress", placeholders);
    }

    private void renderRejectedProgressRow(Inventory inventory, int rowIndex) {
        for (int slot : GridLayout.getAspectProgressSlots(rowIndex)) {
            inventory.setItem(slot, buildInactiveProgressPane());
        }
    }

    private static final class AspectRowLabel {
        private final String displayName;
        private final List<String> lore;

        private AspectRowLabel(String displayName, List<String> lore) {
            this.displayName = displayName;
            this.lore = lore;
        }
    }

    private AspectRowLabel undiscoveredRowLabel() {
        return new AspectRowLabel(GuiText.undiscoveredAspectName(), GuiText.undiscoveredAspectLore());
    }

    private AspectRowLabel buildAspectRowLabel(AspectDef aspect, String aspectId, AspectState state) {
        if (aspect == null) {
            return new AspectRowLabel(aspectId, List.of());
        }
        String displayName;
        if (state == AspectState.CONFIRMED) {
            displayName = GuiText.aspectName(AspectNameStyle.CONFIRMED, aspect.getName());
        } else if (state == AspectState.REJECTED) {
            displayName = GuiText.aspectName(AspectNameStyle.HIDDEN, aspect.getName());
        } else {
            displayName = GuiText.aspectName(AspectNameStyle.PLAIN, aspect.getName());
        }
        return new AspectRowLabel(displayName, GuiText.formatLoreLines(aspect.getLore()));
    }

    private void renderInactiveProgressRow(Inventory inventory, int rowIndex) {
        for (int slot : GridLayout.getAspectProgressSlots(rowIndex)) {
            inventory.setItem(slot, buildInactiveProgressPane());
        }
    }

    /**
     * Brief outward pulse across the grid, then repopulates with updated project state.
     */
    public void playConfirmRefreshWave(Player player, ResearchStation station, Inventory inventory,
            String pulseAspectId) {
        List<List<Integer>> rings = buildWaveRings();
        if (rings.isEmpty()) {
            populateMain(player, station, inventory);
            return;
        }
        ItemStack pulse = buildWavePulseFiller(pulseAspectId);
        ItemStack leftFiller = buildLeftFiller();

        new BukkitRunnable() {
            int ringIndex = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Inventory openTop = player.getOpenInventory().getTopInventory();
                if (!openTop.equals(inventory)
                        || !player.getOpenInventory().getTitle().equals(mainInventoryTitle())) {
                    cancel();
                    return;
                }
                if (ringIndex > 0) {
                    revertRing(openTop, rings.get(ringIndex - 1), leftFiller);
                }
                if (ringIndex >= rings.size()) {
                    populateMain(player, station, inventory);
                    cancel();
                    return;
                }
                paintRing(openTop, rings.get(ringIndex), pulse);
                ringIndex++;
            }
        }.runTaskTimer(Research.plugin, 0L, 2L);
    }

    private void paintRing(Inventory inventory, List<Integer> slots, ItemStack pulse) {
        for (int slot : slots) {
            if (isGridFillerOrPulseSlot(inventory, slot)) {
                inventory.setItem(slot, pulse.clone());
            }
        }
    }

    private void revertRing(Inventory inventory, List<Integer> slots, ItemStack grayFiller) {
        for (int slot : slots) {
            if (isGridFillerOrPulseSlot(inventory, slot)) {
                inventory.setItem(slot, grayFiller.clone());
            }
        }
    }

    private List<List<Integer>> buildWaveRings() {
        Map<Integer, List<Integer>> rings = new TreeMap<>();
        for (int slot = 0; slot < 54; slot++) {
            if (!GridLayout.isLeftPanelSlot(slot)) {
                continue;
            }
            if (GridLayout.isReservedStationSlot(slot)) {
                continue;
            }
            if (UNFILLED_SLOTS.contains(slot)) {
                continue;
            }
            int row = GridLayout.slotToRow(slot);
            int col = GridLayout.slotToCol(slot);
            int dist = Math.max(Math.abs(row - GridLayout.PRODUCT_ROW), Math.abs(col - GridLayout.PRODUCT_COL));
            rings.computeIfAbsent(dist, key -> new ArrayList<>()).add(slot);
        }
        return new ArrayList<>(rings.values());
    }

    /**
     * Wave only paints left-of-divider padding (and an earlier pulse on those slots).
     * Aspect bars sit on the right of the divider and are never part of the wave.
     */
    private boolean isGridFillerOrPulseSlot(Inventory inventory, int slot) {
        if (!GridLayout.isLeftPanelSlot(slot) || GridLayout.isReservedStationSlot(slot)
                || UNFILLED_SLOTS.contains(slot)) {
            return false;
        }
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }
        if (ItemRef.matches(item, GuiCache.leftFiller)
                || ItemRef.matches(item, GuiCache.gridFiller)
                || ItemRef.matches(item, GuiCache.filler)) {
            return true;
        }
        ItemMeta paneMeta = item.getItemMeta();
        return paneMeta != null && paneMeta.isHideTooltip()
                && item.getType().name().endsWith("STAINED_GLASS_PANE");
    }

    private ItemStack buildWavePulseFiller(String pulseAspectId) {
        String ref = GuiCache.wavePulseDefault;
        if (pulseAspectId != null && !pulseAspectId.isBlank()) {
            AspectDef aspect = AspectLoader.getById(pulseAspectId);
            if (aspect != null && aspect.getPulseColorRef() != null && !aspect.getPulseColorRef().isBlank()) {
                ref = aspect.getPulseColorRef();
            }
        }
        ItemStack pulse = ItemRef.build(ref);
        if (pulse == null) {
            pulse = ItemRef.build(GuiCache.wavePulseDefault);
        }
        if (pulse == null) {
            pulse = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        }
        ItemRef.applyBlankDisplay(pulse);
        return pulse;
    }

    public void updateExperimentPreview(Inventory inventory, ItemStack experimentStack, ActiveProject project) {
        if (experimentStack == null || experimentStack.getType() == Material.AIR) {
            inventory.setItem(GridLayout.SLOT_PRIMARY_PREVIEW, null);
            inventory.setItem(GridLayout.SLOT_SECONDARY_PREVIEW, null);
            return;
        }

        ExperimentMatch match = AspectItemRegistry.findByItemStack(experimentStack);
        if (match == null) {
            inventory.setItem(GridLayout.SLOT_PRIMARY_PREVIEW, null);
            inventory.setItem(GridLayout.SLOT_SECONDARY_PREVIEW, null);
            return;
        }

        inventory.setItem(GridLayout.SLOT_PRIMARY_PREVIEW,
                buildPreviewAspectItem(match.getPrimaryAspect(), match.getPrimaryPoints(), project));
        inventory.setItem(GridLayout.SLOT_SECONDARY_PREVIEW,
                buildPreviewAspectItem(match.getSecondaryAspect(), match.getSecondaryPoints(), project));
    }

    public void openScrapConfirm(Player player) {
        Inventory inventory = Research.plugin.getServer().createInventory(null, 9, scrapConfirmTitle());
        inventory.setItem(CONFIRM_SCRAP_YES, buildButton(GuiCache.confirmButton, GuiText.label(GuiCache.scrapConfirmYesLabel)));
        inventory.setItem(CONFIRM_SCRAP_NO, buildButton(GuiCache.cancelButton, GuiText.label(GuiCache.scrapConfirmNoLabel)));
        player.openInventory(inventory);
    }

    private ItemStack buildRevealedProgressPane(String displayName, List<String> lore) {
        ItemStack item = ItemRef.build(GuiCache.aspectProgressRevealed);
        if (item == null) {
            item = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildUnknownProgressPane(String displayName, List<String> lore) {
        ItemStack item = ItemRef.build(GuiCache.aspectProgressUnknown);
        if (item == null) {
            item = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildInactiveProgressPane() {
        ItemStack item = ItemRef.build(GuiCache.aspectProgressInactive);
        if (item == null) {
            item = ItemRef.build(GuiCache.filler);
        }
        if (item == null) {
            item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        }
        ItemRef.applyBlankDisplay(item);
        return item;
    }

    private ItemStack buildGridFiller() {
        ItemStack item = ItemRef.build(GuiCache.gridFiller);
        if (item == null) {
            item = ItemRef.build(GuiCache.filler);
        }
        if (item == null) {
            item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        }
        ItemRef.applyBlankDisplay(item);
        return item;
    }

    private ItemStack buildLeftFiller() {
        ItemStack item = ItemRef.build(GuiCache.leftFiller);
        if (item == null) {
            item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        }
        ItemRef.applyBlankDisplay(item);
        return item;
    }

    private ItemStack buildAspectDisplayWithLabel(AspectDef aspect, AspectRowLabel label) {
        ItemStack item = null;
        if (aspect != null) {
            item = ItemRef.build(aspect.getDisplayItemRef());
        }
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        }
        return applyLabel(item, label);
    }

    private ItemStack applyLabel(ItemStack item, AspectRowLabel label) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(label.displayName);
            meta.setLore(new ArrayList<>(label.lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildAspectRowDivider() {
        ItemStack item = ItemRef.build(GuiCache.aspectRowDivider);
        if (item == null) {
            item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        }
        ItemRef.applyBlankDisplay(item);
        return item;
    }

    private ItemStack buildTestedUnknownAspectItem() {
        ItemStack item = ItemRef.build(GuiCache.aspectTestedUnknown);
        if (item == null) {
            item = ItemRef.build(GuiCache.lockedAspect);
        }
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GuiText.undiscoveredAspectName());
            meta.setLore(new ArrayList<>(GuiText.undiscoveredAspectLore()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildPreviewAspectItem(String aspectId, int grantPoints, ActiveProject project) {
        if (aspectId == null || aspectId.isBlank() || grantPoints <= 0) {
            return null;
        }
        AspectDef aspect = AspectLoader.getById(aspectId);
        if (aspect == null) {
            return null;
        }
        ItemStack item = ItemRef.build(aspect.getDisplayItemRef());
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            AspectState state = project.getAspectState(aspectId);
            List<String> lore = new ArrayList<>(GuiText.formatLoreLines(aspect.getLore()));
            if (state == AspectState.CONFIRMED) {
                meta.setDisplayName(GuiText.aspectName(AspectNameStyle.CONFIRMED, aspect.getName()));
                lore.add(GuiText.guiMessage("aspect.status_confirmed", Map.of()));
            } else if (state == AspectState.REJECTED) {
                meta.setDisplayName(GuiText.aspectName(AspectNameStyle.PLAIN, aspect.getName()));
                lore.add(GuiText.guiMessage("aspect.status_rejected", Map.of()));
            } else {
                meta.setDisplayName(GuiText.aspectName(AspectNameStyle.PLAIN, aspect.getName()));
                lore.add(GuiText.guiMessage("aspect.status_testing", Map.of()));
            }
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("points", String.valueOf(grantPoints));
            lore.add(GuiText.guiMessage("experiment.preview_points", placeholders));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, Math.min(grantPoints, 64)));
        return item;
    }

    private ItemStack buildMentalPointsItem(Player player) {
        ItemStack item = ItemRef.build(GuiCache.mentalPointsIcon);
        if (item == null) {
            item = new ItemStack(Material.LIGHT);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(playerManager.getMentalPoints(player)));
            meta.setDisplayName(GuiText.guiMessage("mental_points.display", placeholders));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildScrapButton() {
        ItemStack item = ItemRef.build(GuiCache.cancelButton);
        if (item == null) {
            item = new ItemStack(Material.BARRIER);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GuiText.label(GuiCache.scrapButtonLabel));
            meta.setLore(List.of(GuiText.guiMessage("station.scrap_warning", Map.of())));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildConfirmButton(ActiveProject project) {
        ItemStack item = ItemRef.build(GuiCache.confirmButton);
        if (item == null) {
            item = new ItemStack(Material.LIME_CONCRETE);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (project.isCompleted()) {
                meta.setDisplayName(GuiText.label(GuiCache.experimentCompletedLabel));
            } else {
                meta.setDisplayName(GuiText.label(GuiCache.confirmExperimentLabel));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildButton(String itemRef, String name) {
        ItemStack item = ItemRef.build(itemRef);
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillGridEmpty(Inventory inventory, Set<Integer> reservedSlots) {
        ItemStack gridFiller = buildGridFiller();
        ItemStack leftFiller = buildLeftFiller();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (UNFILLED_SLOTS.contains(slot)) {
                continue;
            }
            if (GridLayout.isReservedStationSlot(slot)) {
                continue;
            }
            if (reservedSlots.contains(slot)) {
                continue;
            }
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, GridLayout.isLeftPanelSlot(slot) ? leftFiller.clone() : gridFiller.clone());
            }
        }
    }
}
