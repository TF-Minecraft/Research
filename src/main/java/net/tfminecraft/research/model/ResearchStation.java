package net.tfminecraft.research.model;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class ResearchStation {

    private final Location location;
    private UUID ownerUuid;
    private ActiveProject project;

    public ResearchStation(Location location, UUID ownerUuid, ActiveProject project) {
        this.location = location;
        this.ownerUuid = ownerUuid;
        this.project = project;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public ActiveProject getProject() {
        return project;
    }

    public void setProject(ActiveProject project) {
        this.project = project;
    }

    public boolean isAt(Location other) {
        if (other == null || location == null) {
            return false;
        }
        if (location.getWorld() == null || other.getWorld() == null) {
            return false;
        }
        return location.getWorld().equals(other.getWorld())
                && location.getBlockX() == other.getBlockX()
                && location.getBlockY() == other.getBlockY()
                && location.getBlockZ() == other.getBlockZ();
    }

    public static String locationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        String world = location.getWorld().getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        return world + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
    }

    public static Location locationFromKey(String worldName, int x, int y, int z) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }
}
