package net.tfminecraft.research.util;

import org.bukkit.entity.Player;

import net.Indyuce.mmocore.api.player.PlayerData;

public final class MmoAttributes {

    private MmoAttributes() {}

    public static double getTotal(Player player, String mmocoreId) {
        if (player == null || mmocoreId == null || mmocoreId.isBlank()) {
            return 0;
        }
        try {
            return PlayerData.get(player).getAttributes().getInstance(mmocoreId).getTotal();
        } catch (Exception ex) {
            return 0;
        }
    }
}
