package net.tfminecraft.research.manager;

import org.bukkit.entity.Player;

import net.tfminecraft.tfmccore.TFMCCore;
import net.tfminecraft.tfmccore.focus.FocusService;

public final class PlayerManager {

    private static PlayerManager instance;

    public PlayerManager() {
        instance = this;
    }

    public static PlayerManager getInstance() {
        return instance;
    }

    public int getMentalPoints(Player player) {
        FocusService focus = TFMCCore.getFocusService();
        return focus != null ? focus.getPoints(player) : 0;
    }

    public boolean trySpendMentalPoints(Player player, int amount) {
        FocusService focus = TFMCCore.getFocusService();
        return focus != null && focus.trySpend(player, amount);
    }

    public void grantMentalPoints(Player player, int amount) {
        FocusService focus = TFMCCore.getFocusService();
        if (focus != null) {
            focus.grant(player, amount);
        }
    }
}
