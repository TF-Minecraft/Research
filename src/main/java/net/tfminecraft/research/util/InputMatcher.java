package net.tfminecraft.research.util;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.research.loader.InputLoader;
import net.tfminecraft.research.model.InputDef;

public final class InputMatcher {

    private InputMatcher() {}

    public static InputDef findByStartItem(ItemStack held) {
        if (held == null || held.getType().isAir()) {
            return null;
        }
        for (InputDef input : InputLoader.get().values()) {
            if (!input.isEnabled() || !input.requiresStartItem()) {
                continue;
            }
            if (ItemRef.matches(held, input.getStartItemRef())) {
                return input;
            }
        }
        return null;
    }
}
