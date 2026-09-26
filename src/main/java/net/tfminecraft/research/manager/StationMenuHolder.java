package net.tfminecraft.research.manager;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Ties a research menu to the station it was opened for, so a player with several stations
 * always acts on the lectern whose menu is open.
 */
public final class StationMenuHolder implements InventoryHolder {

    private final Location stationLocation;
    private Inventory inventory;

    public StationMenuHolder(Location stationLocation) {
        this.stationLocation = stationLocation;
    }

    public Location getStationLocation() {
        return stationLocation;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
