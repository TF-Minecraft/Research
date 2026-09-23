package net.tfminecraft.research.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;

public final class ResultSpawnEffects {

    private ResultSpawnEffects() {}

    /**
     * Spawns the result item above a lectern block with upward velocity and a crit trail.
     */
    public static boolean spawnAtStation(Location blockLoc, ItemStack item) {
        if (blockLoc == null || blockLoc.getWorld() == null || item == null || item.getType().isAir()) {
            return false;
        }

        World world = blockLoc.getWorld();
        Location base = blockLoc.clone().add(0.5, 0.8, 0.5);
        Location pop = base.clone().add(0, 0.15, 0);

        world.playSound(base, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);

        if (Cache.resultSpawnBurstParticles) {
            world.spawnParticle(Particle.CLOUD, base, 18, 0.35, 0.25, 0.35, 0.0);
            world.spawnParticle(Particle.ENCHANTED_HIT, base, 12, 0.25, 0.20, 0.25, 0.0);
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Item ent = world.dropItem(pop, item.clone());
        ent.setPickupDelay(0);
        ent.setCustomName(entityNameFor(item));
        ent.setCustomNameVisible(true);
        kickUp(ent, rng);
        startCritTrail(ent, Cache.resultSpawnTrailTicks, Cache.resultSpawnTrailIntervalTicks);
        return true;
    }

    private static void kickUp(Item ent, ThreadLocalRandom rng) {
        double vx = randomSigned(rng, Cache.resultSpawnKickHorizontalMin, Cache.resultSpawnKickHorizontalMax);
        double vz = randomSigned(rng, Cache.resultSpawnKickHorizontalMin, Cache.resultSpawnKickHorizontalMax);
        double vy = rng.nextDouble(Cache.resultSpawnKickVelocityMin, Cache.resultSpawnKickVelocityMax);
        ent.setVelocity(new Vector(vx, vy, vz));
    }

    private static double randomSigned(ThreadLocalRandom rng, double min, double max) {
        double v = rng.nextDouble(min, max);
        return rng.nextBoolean() ? v : -v;
    }

    private static void startCritTrail(Entity entity, int maxTicks, int intervalTicks) {
        int interval = Math.max(1, intervalTicks);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (entity == null || !entity.isValid() || entity.isDead() || t++ >= maxTicks) {
                    cancel();
                    return;
                }
                Location p = entity.getLocation().add(0, 0.1, 0);
                p.getWorld().spawnParticle(Particle.CRIT, p, 4, 0.05, 0.05, 0.05, 0.0);
            }
        }.runTaskTimer(Research.plugin, 0L, interval);
    }

    private static String entityNameFor(ItemStack item) {
        return Messages.formatBody("result.entity_name", Map.of(
                "amount", String.valueOf(item.getAmount()),
                "item", displayNameOf(item)));
    }

    private static String displayNameOf(ItemStack item) {
        if (item == null) {
            return "Item";
        }
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
