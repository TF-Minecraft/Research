package net.tfminecraft.research.manager;

import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.focus.FocusService;

public final class PlayerManager {

    private static PlayerManager instance;

    public PlayerManager() {
        instance = this;
    }

    public static PlayerManager getInstance() {
        return instance;
    }

    public int getMentalPoints(Player player) {
        FocusService focus = RPCharacters.getFocusService();
        return focus != null ? focus.getPoints(player) : 0;
    }

    public boolean trySpendMentalPoints(Player player, int amount) {
        FocusService focus = RPCharacters.getFocusService();
        return focus != null && focus.trySpend(player, amount);
    }

    public void grantMentalPoints(Player player, int amount) {
        FocusService focus = RPCharacters.getFocusService();
        if (focus != null) {
            focus.grant(player, amount);
        }
    }
}
