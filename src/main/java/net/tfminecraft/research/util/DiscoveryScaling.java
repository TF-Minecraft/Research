package net.tfminecraft.research.util;

import org.bukkit.entity.Player;

import net.tfminecraft.research.Cache;
import net.tfminecraft.research.model.OutputDef;

public final class DiscoveryScaling {

    private DiscoveryScaling() {}

    public static double effectiveRevealPercent(Player player) {
        double discovery = MmoAttributes.getTotal(player, Cache.discoveryMmocoreId);
        double reduced = Cache.aspectBaseRevealPercent
                - discovery * Cache.aspectRevealPercentReductionPerPoint;
        return Math.max(Cache.aspectMinRevealPercent, reduced);
    }

    public static double effectiveConfirmPercent(Player player) {
        double discovery = MmoAttributes.getTotal(player, Cache.discoveryMmocoreId);
        double reduced = Cache.aspectBaseConfirmPercent
                - discovery * Cache.aspectConfirmPercentReductionPerPoint;
        return Math.max(Cache.aspectMinConfirmPercent, reduced);
    }

    /**
     * Row is visible (Undiscovered Aspect + bar) once points reach the reveal fraction of required.
     */
    public static boolean isAspectRowRevealed(Player player, int currentPoints, int requiredPoints) {
        return meetsPointPercent(currentPoints, requiredPoints, effectiveRevealPercent(player));
    }

    /**
     * Real element identity. Requires the row to be revealed first if confirm % is below reveal %.
     */
    public static boolean isAspectIdentityRevealed(Player player, int currentPoints, int requiredPoints) {
        return isAspectRowRevealed(player, currentPoints, requiredPoints)
                && meetsPointPercent(currentPoints, requiredPoints, effectiveConfirmPercent(player));
    }

    public static boolean meetsConfirmThreshold(Player player, int currentPoints, int requiredPoints) {
        return isAspectIdentityRevealed(player, currentPoints, requiredPoints);
    }

    public static int effectiveProductRevealAfterConfirmed(Player player, OutputDef outputDef) {
        double discovery = MmoAttributes.getTotal(player, Cache.discoveryMmocoreId);
        double bonus = Math.min(Cache.capProductRevealBonus,
                discovery * Cache.productRevealBonusPerPoint);
        return Math.max(0, (int) Math.round(outputDef.getProductRevealAfterConfirmedAspects() - bonus));
    }

    private static boolean meetsPointPercent(int currentPoints, int requiredPoints, double percent) {
        if (requiredPoints <= 0) {
            return false;
        }
        return currentPoints >= requiredPoints * percent;
    }
}
