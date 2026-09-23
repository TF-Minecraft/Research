package net.tfminecraft.research.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;
import net.tfminecraft.research.database.StationStore;
import net.tfminecraft.research.event.ResearchCompleteEvent;
import net.tfminecraft.research.loader.AspectLoader;
import net.tfminecraft.research.loader.OutputLoader;
import net.tfminecraft.research.model.ActiveProject;
import net.tfminecraft.research.model.AspectDef;
import net.tfminecraft.research.model.AspectRequirement;
import net.tfminecraft.research.model.AspectState;
import net.tfminecraft.research.model.ExperimentMatch;
import net.tfminecraft.research.model.InputDef;
import net.tfminecraft.research.model.OutputDef;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.research.registry.AspectItemRegistry;
import net.tfminecraft.research.util.AspectSlotOrder;
import net.tfminecraft.research.util.GridLayout;
import net.tfminecraft.research.util.DiscoveryScaling;
import net.tfminecraft.research.util.ExternalModifiers;
import net.tfminecraft.research.util.InputMatcher;
import net.tfminecraft.research.util.ItemRef;
import net.tfminecraft.research.util.OutputPicker;
import net.tfminecraft.research.util.PlayerInventoryUtil;
import net.tfminecraft.research.util.ResultResolver;
import net.tfminecraft.research.util.ResultSpawnEffects;
import net.tfminecraft.research.util.RevealHelper;
import net.tfminecraft.research.util.SoundKeys;
import net.tfminecraft.research.util.StationCompleteEffects;
import net.tfminecraft.research.util.StationStartEffects;

public final class ResearchManager implements Listener {

    private static ResearchManager instance;

    private final PlayerManager playerManager;
    private final InventoryManager inventoryManager;

    private final List<ResearchStation> stations = new ArrayList<>();
    private final Map<UUID, Location> openGui = new HashMap<>();

    public ResearchManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
        this.inventoryManager = new InventoryManager(playerManager);
        instance = this;
    }

    public static ResearchManager getInstance() {
        return instance;
    }

    public void start() {
        stations.clear();
        openGui.clear();
        stations.addAll(StationStore.loadAll());
    }

    public void unloadAll() {
        StationStore.saveAll(stations);
        stations.clear();
        openGui.clear();
    }

    public ResearchStation getStationAt(Location location) {
        Location blockLoc = blockLocation(location);
        for (ResearchStation station : stations) {
            if (station.isAt(blockLoc)) {
                return station;
            }
        }
        return null;
    }

    @EventHandler
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Cache.stationBlock) {
            return;
        }

        Player player = event.getPlayer();
        if (!Cache.stationPermission.isBlank() && !player.hasPermission(Cache.stationPermission)) {
            return;
        }

        event.setCancelled(true);
        Location stationLoc = block.getLocation();
        ResearchStation station = getStationAt(stationLoc);

        if (station != null) {
            if (!station.getOwnerUuid().equals(player.getUniqueId())) {
                player.sendMessage(Messages.get("station.in_use"));
                return;
            }
            openMainGui(player, station);
            return;
        }

        EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
        ItemStack held = PlayerInventoryUtil.getStackInHand(player, hand);
        InputDef input = InputMatcher.findByStartItem(held);
        if (input == null) {
            if (held == null || held.getType().isAir()) {
                player.sendMessage(Messages.get("station.hold_start_item"));
            } else {
                player.sendMessage(Messages.get("station.invalid_start_item"));
            }
            return;
        }

        ResearchStation newStation = startProject(player, stationLoc, hand, input);
        if (newStation != null) {
            player.sendMessage(Messages.get("station.started"));
            openMainGui(player, newStation);
        }
    }

    @EventHandler
    public void onStationBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Cache.stationBlock) {
            return;
        }
        ResearchStation station = getStationAt(block.getLocation());
        if (station == null) {
            return;
        }
        scrapStation(station, Messages.get("station.broken"));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (title.equals(InventoryManager.mainInventoryTitle())
                || title.equals(InventoryManager.scrapConfirmTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (!title.equals(InventoryManager.mainInventoryTitle())
                && !title.equals(InventoryManager.scrapConfirmTitle())) {
            return;
        }

        event.setCancelled(true);

        ResearchStation station = resolveStationForPlayerGui(player);
        if (station == null) {
            return;
        }

        if (title.equals(InventoryManager.scrapConfirmTitle())) {
            if (event.getCurrentItem() == null) {
                return;
            }
            handleScrapConfirmClick(player, station, event.getSlot());
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        Inventory clickedInventory = event.getClickedInventory();

        if (rawSlot < topSize) {
            if (rawSlot == GridLayout.SLOT_EXPERIMENT) {
                handleExperimentSlotTakeOut(player, station, event.getView().getTopInventory());
                return;
            }
            if (event.getCurrentItem() == null) {
                return;
            }
            handleMainClick(player, station, rawSlot, event.getView().getTopInventory());
            return;
        }

        if (clickedInventory == null || !clickedInventory.equals(event.getView().getBottomInventory())) {
            return;
        }
        if (event.isShiftClick()) {
            return;
        }
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }
        handlePlayerInventoryToExperiment(event, player, station);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!event.getView().getTitle().equals(InventoryManager.mainInventoryTitle())) {
            return;
        }

        ItemStack experiment = event.getView().getTopInventory().getItem(GridLayout.SLOT_EXPERIMENT);
        if (experiment != null && experiment.getType() != Material.AIR) {
            returnItemToPlayer(player, experiment);
        }
        openGui.remove(player.getUniqueId());
    }

    private void handleMainClick(Player player, ResearchStation station, int slot, Inventory inventory) {
        if (slot == GridLayout.SLOT_SCRAP) {
            openGui.put(player.getUniqueId(), blockLocation(station.getLocation()));
            inventoryManager.openScrapConfirm(player);
        } else if (slot == GridLayout.SLOT_CONFIRM_EXPERIMENT) {
            confirmExperiment(player, station, inventory);
        }
    }

    private void handleExperimentSlotTakeOut(Player player, ResearchStation station, Inventory top) {
        ItemStack inSlot = top.getItem(GridLayout.SLOT_EXPERIMENT);
        if (inSlot == null || inSlot.getType() == Material.AIR) {
            return;
        }
        returnItemToPlayer(player, inSlot);
        top.setItem(GridLayout.SLOT_EXPERIMENT, null);
        inventoryManager.updateExperimentPreview(top, null, station.getProject());
    }

    private void handlePlayerInventoryToExperiment(InventoryClickEvent event, Player player, ResearchStation station) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        ExperimentMatch match = AspectItemRegistry.findByItemStack(stack);
        if (match == null) {
            player.sendMessage(Messages.get("experiment.unknown_item"));
            return;
        }

        ActiveProject active = station.getProject();
        Inventory top = event.getView().getTopInventory();
        if (active.hasTestedItem(match.getItemRef())) {
            player.sendMessage(Messages.get("experiment.item_already_tested"));
            inventoryManager.updateExperimentPreview(
                    top, top.getItem(GridLayout.SLOT_EXPERIMENT), active);
            return;
        }

        ItemStack previous = top.getItem(GridLayout.SLOT_EXPERIMENT);

        ItemStack one = stack.clone();
        one.setAmount(1);

        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            clickedInventory.setItem(event.getSlot(), stack);
        } else {
            clickedInventory.setItem(event.getSlot(), null);
        }

        top.setItem(GridLayout.SLOT_EXPERIMENT, one);
        if (previous != null && previous.getType() != Material.AIR) {
            returnItemToPlayer(player, previous);
        }

        playExperimentInputSound(player, match);
        inventoryManager.updateExperimentPreview(top, one, active);
    }

    private void playExperimentInputSound(Player player, ExperimentMatch match) {
        AspectDef soundAspect = match.getSoundAspect();
        if (soundAspect == null) {
            return;
        }
        String inputSound = soundAspect.getSounds().getInput();
        if (inputSound == null || inputSound.isBlank()) {
            return;
        }
        SoundKeys.play(player, inputSound, soundAspect.getSounds().getVolume(), soundAspect.getSounds().getPitch());
    }

    private void confirmExperiment(Player player, ResearchStation station, Inventory inventory) {
        ActiveProject project = station.getProject();
        if (project.isCompleted()) {
            return;
        }

        ItemStack experimentItem = inventory.getItem(GridLayout.SLOT_EXPERIMENT);
        if (experimentItem == null || experimentItem.getType() == Material.AIR) {
            return;
        }

        ExperimentMatch match = AspectItemRegistry.findByItemStack(experimentItem);
        if (match == null) {
            player.sendMessage(Messages.get("experiment.unknown_item"));
            return;
        }

        if (project.hasTestedItem(match.getItemRef())) {
            player.sendMessage(Messages.get("experiment.item_already_tested"));
            return;
        }

        if (allMatchAspectsRejected(project, match)) {
            player.sendMessage(Messages.get("experiment.only_rejected"));
            return;
        }

        OutputDef outputDef = OutputLoader.getById(project.getResolvedOutputId());
        if (outputDef == null) {
            return;
        }

        if (!playerManager.trySpendMentalPoints(player, Cache.experimentCost)) {
            player.sendMessage(Messages.get("mental_points.exhausted"));
            return;
        }

        project.markTested(match.getItemRef());
        boolean primaryScored = applyExperimentAspect(player, project, outputDef, match.getPrimaryAspect(),
                ExternalModifiers.adjustExperimentPoints(player, match.getPrimaryPoints()));
        boolean secondaryScored = applyExperimentAspect(player, project, outputDef, match.getSecondaryAspect(),
                ExternalModifiers.adjustExperimentPoints(player, match.getSecondaryPoints()));

        playExperimentConfirmSound(player, match);
        inventoryManager.updateExperimentPreview(inventory, experimentItem, project);
        returnItemToPlayer(player, experimentItem.clone());
        inventory.setItem(GridLayout.SLOT_EXPERIMENT, null);
        inventoryManager.updateExperimentPreview(inventory, null, project);

        if (checkProjectCompletion(player, station, outputDef, inventory)) {
            return;
        }

        StationStore.saveStation(station);
        inventoryManager.populateMain(player, station, inventory);
        String pulseAspectId = primaryScored ? match.getPrimaryAspect()
                : (secondaryScored ? match.getSecondaryAspect() : null);
        inventoryManager.playConfirmRefreshWave(player, station, inventory, pulseAspectId);
    }

    private void playExperimentConfirmSound(Player player, ExperimentMatch match) {
        AspectDef soundAspect = match.getSoundAspect();
        if (soundAspect == null) {
            return;
        }
        SoundKeys.play(player, soundAspect.getSounds().getConfirm(), soundAspect.getSounds().getVolume(),
                soundAspect.getSounds().getPitch());
    }

    private boolean applyExperimentAspect(Player player, ActiveProject active, OutputDef outputDef,
            String aspectId, int points) {
        if (aspectId == null || aspectId.isBlank() || points <= 0) {
            return false;
        }
        if (active.getAspectState(aspectId) == AspectState.REJECTED) {
            return false;
        }
        AspectRequirement requirement = outputDef.getAspects().get(aspectId);
        if (requirement != null) {
            int required = requirement.getRequiredPoints();
            int before = active.getAspectPoints(aspectId);
            if (before >= required) {
                return false;
            }
            active.addAspectPoints(aspectId, points, required);
            if (active.getAspectPoints(aspectId) > before) {
                reconcileAspectState(player, active, outputDef, aspectId);
                return true;
            }
            return false;
        }
        return applyOffRecipeAspect(active, aspectId, points);
    }

    private boolean applyOffRecipeAspect(ActiveProject active, String aspectId, int points) {
        int cap = Cache.experimentRejectPoints;
        int before = active.getAspectPoints(aspectId);
        if (before >= cap) {
            active.setAspectState(aspectId, AspectState.REJECTED);
            return false;
        }
        active.addAspectPoints(aspectId, points, cap);
        int after = active.getAspectPoints(aspectId);
        if (after <= before) {
            return false;
        }
        if (active.getAspectState(aspectId) == AspectState.UNKNOWN) {
            active.setAspectState(aspectId, AspectState.TESTING);
        }
        if (after >= cap) {
            active.setAspectState(aspectId, AspectState.REJECTED);
        }
        return true;
    }

    private boolean allMatchAspectsRejected(ActiveProject project, ExperimentMatch match) {
        List<String> ids = new ArrayList<>();
        if (match.getPrimaryAspect() != null && !match.getPrimaryAspect().isBlank()) {
            ids.add(match.getPrimaryAspect());
        }
        if (match.getSecondaryAspect() != null && !match.getSecondaryAspect().isBlank()) {
            ids.add(match.getSecondaryAspect());
        }
        if (ids.isEmpty()) {
            return false;
        }
        for (String aspectId : ids) {
            if (project.getAspectState(aspectId) != AspectState.REJECTED) {
                return false;
            }
        }
        return true;
    }

    private void reconcileAspectState(Player player, ActiveProject active, OutputDef outputDef, String aspectId) {
        AspectState state = active.getAspectState(aspectId);
        if (state == AspectState.CONFIRMED || state == AspectState.REJECTED) {
            return;
        }

        if (state == AspectState.UNKNOWN) {
            active.setAspectState(aspectId, AspectState.TESTING);
        }

        AspectRequirement requirement = outputDef.getAspects().get(aspectId);
        if (requirement == null) {
            return;
        }

        int points = active.getAspectPoints(aspectId);
        int required = requirement.getRequiredPoints();
        if (DiscoveryScaling.meetsConfirmThreshold(player, points, required)) {
            active.setAspectState(aspectId, AspectState.CONFIRMED);
            player.sendMessage(Messages.format("experiment.confirmed",
                    Map.of("aspect", aspectDisplayName(aspectId))));
            checkReveals(player, active, outputDef);
        }
    }

    private void checkReveals(Player player, ActiveProject active, OutputDef outputDef) {
        if (!active.isProductRevealed()) {
            int threshold = DiscoveryScaling.effectiveProductRevealAfterConfirmed(player, outputDef);
            if (RevealHelper.countConfirmedRecipeAspects(active, outputDef) >= threshold) {
                active.setProductRevealed(true);
                player.sendMessage(Messages.get("product.revealed"));
            }
        }
    }

    /**
     * @return true if the project was completed and the station was cleared for reuse
     */
    private boolean checkProjectCompletion(Player player, ResearchStation station, OutputDef outputDef,
            Inventory inventory) {
        ActiveProject active = station.getProject();
        if (active.isCompleted() || !isRecipeComplete(player, outputDef, active)) {
            return false;
        }

        String resultRef = active.getResolvedResultRef();
        if (resultRef == null || resultRef.isBlank()) {
            Research.plugin.getLogger().warning("[Research] Output '" + outputDef.getId()
                    + "' has no resolved result on active project.");
            return false;
        }

        ItemStack reward = ItemRef.build(resultRef);
        if (reward == null) {
            Research.plugin.getLogger().warning("[Research] Could not build result item for output '"
                    + outputDef.getId() + "': " + resultRef);
            return false;
        }

        Location stationLoc = blockLocation(station.getLocation());
        if (!ResultSpawnEffects.spawnAtStation(stationLoc, reward)) {
            Research.plugin.getLogger().warning("[Research] Could not spawn result item for output '"
                    + outputDef.getId() + "' at lectern.");
            return false;
        }

        active.setCompleted(true);
        player.sendMessage(Messages.get("station.completed"));
        sendResultGrantedMessage(player, reward);

        ResearchCompleteEvent event = new ResearchCompleteEvent(
                player, active.getInputId(), active.getResolvedOutputId(), resultRef);
        Research.plugin.getServer().getPluginManager().callEvent(event);
        Research.plugin.getLogger().info("[Research] Codex stub: player " + player.getName()
                + " completed output " + outputDef.getId() + " result " + resultRef);

        finishCompletedStation(player, station, inventory);
        return true;
    }

    /**
     * Removes completed research from the lectern, closes the GUI, and deletes persisted station data.
     */
    private void finishCompletedStation(Player player, ResearchStation station, Inventory inventory) {
        Location loc = blockLocation(station.getLocation());
        StationCompleteEffects.play(player, loc);
        stations.remove(station);
        openGui.remove(player.getUniqueId());
        StationStore.deleteStation(loc);
        inventory.clear();
        player.closeInventory();
    }

    private void sendResultGrantedMessage(Player player, ItemStack item) {
        String itemName = item.getType().name();
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            itemName = item.getItemMeta().getDisplayName();
        }
        player.sendMessage(Messages.format("result.granted", Map.of("item", itemName)));
    }

    private boolean isRecipeComplete(Player player, OutputDef outputDef, ActiveProject active) {
        for (Map.Entry<String, AspectRequirement> entry : outputDef.getAspects().entrySet()) {
            String aspectId = entry.getKey();
            if (active.getAspectState(aspectId) != AspectState.CONFIRMED) {
                return false;
            }
            if (active.getAspectPoints(aspectId) < entry.getValue().getRequiredPoints()) {
                return false;
            }
        }
        return true;
    }

    private String aspectDisplayName(String aspectId) {
        AspectDef aspect = AspectLoader.getById(aspectId);
        return aspect != null ? aspect.getName() : aspectId;
    }

    private void handleScrapConfirmClick(Player player, ResearchStation station, int slot) {
        if (slot == InventoryManager.CONFIRM_SCRAP_YES) {
            scrapStation(station, Messages.get("station.scrapped"));
            player.closeInventory();
        } else if (slot == InventoryManager.CONFIRM_SCRAP_NO) {
            openMainGui(player, station);
        }
    }

    private void returnItemToPlayer(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private ResearchStation startProject(Player player, Location location, EquipmentSlot hand, InputDef input) {
        if (input == null) {
            player.sendMessage(Messages.get("station.invalid_start_item"));
            return null;
        }

        String outputId = OutputPicker.roll(input);
        OutputDef output = outputId != null ? OutputLoader.getById(outputId) : null;
        if (output == null || !output.isEnabled()) {
            player.sendMessage(Messages.get("reload.failed"));
            return null;
        }

        String resolvedResultRef = ResultResolver.resolve(output.getResult());
        if (resolvedResultRef == null || resolvedResultRef.isBlank()) {
            Research.plugin.getLogger().severe("[Research] Could not resolve result for output '" + output.getId()
                    + "' at station start.");
            player.sendMessage(Messages.get("reload.failed"));
            return null;
        }

        if (!tryConsumeStartItemFromHand(player, hand, input)) {
            player.sendMessage(Messages.get("station.missing_start_item"));
            return null;
        }

        ActiveProject project = new ActiveProject(input.getId(), output.getId(), resolvedResultRef);
        project.setAspectSlotOrder(AspectSlotOrder.buildRowAssignments(output));
        ResearchStation station = new ResearchStation(blockLocation(location), player.getUniqueId(), project);
        stations.add(station);
        StationStore.saveStation(station);
        StationStartEffects.play(player, blockLocation(location));
        return station;
    }

    private boolean tryConsumeStartItemFromHand(Player player, EquipmentSlot hand, InputDef inputDef) {
        if (!inputDef.requiresStartItem()) {
            return true;
        }
        return PlayerInventoryUtil.consumeFromHand(
                player,
                hand,
                inputDef.getStartItemRef(),
                inputDef.getStartItemAmount());
    }

    private void scrapStation(ResearchStation station, String ownerMessage) {
        UUID ownerUuid = station.getOwnerUuid();
        Location loc = blockLocation(station.getLocation());

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            String openTitle = owner.getOpenInventory().getTitle();
            if (openTitle.equals(InventoryManager.mainInventoryTitle())) {
                Location openLoc = openGui.get(ownerUuid);
                if (openLoc != null && station.isAt(openLoc)) {
                    ItemStack experiment = owner.getOpenInventory().getTopInventory()
                            .getItem(GridLayout.SLOT_EXPERIMENT);
                    if (experiment != null && experiment.getType() != Material.AIR) {
                        returnItemToPlayer(owner, experiment);
                    }
                    owner.closeInventory();
                }
            } else if (openTitle.equals(InventoryManager.scrapConfirmTitle())) {
                owner.closeInventory();
            }
            openGui.remove(ownerUuid);
            if (ownerMessage != null && !ownerMessage.isBlank()) {
                owner.sendMessage(ownerMessage);
            }
        }

        stations.remove(station);
        StationStore.deleteStation(loc);
    }

    private void openMainGui(Player player, ResearchStation station) {
        openGui.put(player.getUniqueId(), blockLocation(station.getLocation()));
        inventoryManager.openMain(player, station);
    }

    /**
     * Resolves the station for an open research GUI from the lectern location in {@link #openGui},
     * or the player's owned station if that session map was lost (e.g. after reload).
     */
    private ResearchStation resolveStationForPlayerGui(Player player) {
        Location openLoc = openGui.get(player.getUniqueId());
        if (openLoc != null) {
            ResearchStation atOpen = getStationAt(openLoc);
            if (atOpen != null && atOpen.getOwnerUuid().equals(player.getUniqueId())) {
                return atOpen;
            }
        }
        for (ResearchStation station : stations) {
            if (station.getOwnerUuid().equals(player.getUniqueId())) {
                openGui.put(player.getUniqueId(), blockLocation(station.getLocation()));
                return station;
            }
        }
        return null;
    }

    private Location blockLocation(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
