package net.tfminecraft.research.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Ties a research menu to the station it was opened for, so a player with several stations
 * always acts on the lectern whose menu is open.
 */
public final class StationMenuHolder implements InventoryHolder {

    private final Location stationLocation;
    private final CharacterSession session;
    private Inventory inventory;

    public StationMenuHolder(Location stationLocation, CharacterSession session) {
        this.stationLocation = stationLocation;
        this.session = session;
    }

    public CharacterSession getSession() {
        return session;
    }

    public boolean isCurrent(Player player) {
        return session != null && session.ref().ownerId().equals(player.getUniqueId())
                && Characters.isCurrent(session);
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
