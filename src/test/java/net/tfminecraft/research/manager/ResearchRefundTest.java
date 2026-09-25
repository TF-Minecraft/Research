package net.tfminecraft.research.manager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.research.Messages;
import net.tfminecraft.research.database.StationStore;
import net.tfminecraft.research.model.ResearchStation;
import net.tfminecraft.research.util.GridLayout;

class ResearchRefundTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void breakingStationRefundsExperimentOnce(boolean fullInventory) throws Exception {
        exerciseRefund(true, fullInventory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedCloseCannotRefundExperimentAgain(boolean fullInventory) throws Exception {
        exerciseRefund(false, fullInventory);
    }

    private void exerciseRefund(boolean breakStation, boolean fullInventory) throws Exception {
        UUID ownerId = UUID.randomUUID();
        Player owner = mock(Player.class);
        World world = mock(World.class);
        Location location = new Location(world, 10, 64, 10);
        Inventory top = mock(Inventory.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        InventoryView view = mock(InventoryView.class);
        ItemStack experiment = mock(ItemStack.class);
        AtomicReference<ItemStack> slot = new AtomicReference<>(experiment);
        when(experiment.getType()).thenReturn(Material.DIAMOND);
        when(experiment.getAmount()).thenReturn(1);
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.isOnline()).thenReturn(true);
        when(owner.getOpenInventory()).thenReturn(view);
        when(owner.getInventory()).thenReturn(inventory);
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(location);
        when(view.getTitle()).thenReturn("Research Station");
        when(view.getTopInventory()).thenReturn(top);
        when(view.getPlayer()).thenReturn(owner);
        when(top.getItem(GridLayout.SLOT_EXPERIMENT)).thenAnswer(call -> slot.get());
        doAnswer(call -> {
            slot.set(call.getArgument(1));
            return null;
        }).when(top).setItem(eq(GridLayout.SLOT_EXPERIMENT), any());
        when(inventory.addItem(experiment)).thenAnswer(call -> fullInventory
                ? new HashMap<>(Map.of(0, experiment)) : new HashMap<>());

        ResearchStation station = new ResearchStation(location, ownerId, null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<StationStore> store = mockStatic(StationStore.class);
                MockedStatic<InventoryManager> menus = mockStatic(InventoryManager.class);
                MockedStatic<Messages> messages = mockStatic(Messages.class)) {
            bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);
            store.when(StationStore::loadAll).thenReturn(List.of(station));
            menus.when(InventoryManager::mainInventoryTitle).thenReturn("Research Station");
            ResearchManager manager = new ResearchManager(null);
            manager.start();
            Field field = ResearchManager.class.getDeclaredField("openGui");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, Location> openGui = (Map<UUID, Location>) field.get(manager);
            openGui.put(ownerId, location);

            // Bukkit dispatches InventoryCloseEvent synchronously from closeInventory().
            doAnswer(call -> {
                manager.onInventoryClose(new InventoryCloseEvent(view));
                return null;
            }).when(owner).closeInventory();

            if (breakStation) {
                Block lectern = mock(Block.class);
                when(lectern.getType()).thenReturn(Material.LECTERN);
                when(lectern.getLocation()).thenReturn(location);
                manager.onStationBreak(new BlockBreakEvent(lectern, mock(Player.class)));
                verify(owner).closeInventory();
                assertNull(manager.getStationAt(location));
                store.verify(() -> StationStore.deleteStation(location));
            } else {
                manager.onInventoryClose(new InventoryCloseEvent(view));
                manager.onInventoryClose(new InventoryCloseEvent(view));
            }

            verify(inventory, times(1)).addItem(experiment);
            verify(world, times(fullInventory ? 1 : 0)).dropItemNaturally(location, experiment);
            assertNull(slot.get(), "Returned experiment must no longer be held by the menu");
        }
    }
}
