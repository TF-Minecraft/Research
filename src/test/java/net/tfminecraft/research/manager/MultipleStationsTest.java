package net.tfminecraft.research.manager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.research.Messages;
import net.tfminecraft.research.database.StationStore;
import net.tfminecraft.research.model.ResearchStation;

class MultipleStationsTest {

    @Test
    void scrapConfirmActsOnTheStationWhoseMenuIsOpen() {
        UUID ownerId = UUID.randomUUID();
        Player owner = mock(Player.class);
        World world = mock(World.class);
        Location first = new Location(world, 10, 64, 10);
        Location second = new Location(world, 20, 64, 20);
        ResearchStation firstStation = new ResearchStation(first, ownerId, null);
        ResearchStation secondStation = new ResearchStation(second, ownerId, null);

        Inventory top = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.isOnline()).thenReturn(true);
        when(owner.getOpenInventory()).thenReturn(view);
        when(view.getTitle()).thenReturn("Confirm Scrap");
        when(view.getTopInventory()).thenReturn(top);
        when(view.getPlayer()).thenReturn(owner);
        when(view.convertSlot(InventoryManager.CONFIRM_SCRAP_YES)).thenReturn(InventoryManager.CONFIRM_SCRAP_YES);
        when(view.getItem(InventoryManager.CONFIRM_SCRAP_YES)).thenReturn(mock(ItemStack.class));
        when(top.getHolder()).thenReturn(new StationMenuHolder(second));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<StationStore> store = mockStatic(StationStore.class);
                MockedStatic<InventoryManager> menus = mockStatic(InventoryManager.class);
                MockedStatic<Messages> messages = mockStatic(Messages.class)) {
            bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);
            store.when(StationStore::loadAll).thenReturn(List.of(firstStation, secondStation));
            menus.when(InventoryManager::mainInventoryTitle).thenReturn("Research Station");
            menus.when(InventoryManager::scrapConfirmTitle).thenReturn("Confirm Scrap");
            ResearchManager manager = new ResearchManager(null);
            manager.start();

            InventoryClickEvent click = new InventoryClickEvent(view, SlotType.CONTAINER,
                    InventoryManager.CONFIRM_SCRAP_YES, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            manager.onInventoryClick(click);

            assertTrue(click.isCancelled());
            assertNotNull(manager.getStationAt(first), "The other station must keep its project");
            assertNull(manager.getStationAt(second), "The station whose menu was open must be scrapped");
            store.verify(() -> StationStore.deleteStation(second));
            store.verify(() -> StationStore.deleteStation(first), never());
        }
    }
}
