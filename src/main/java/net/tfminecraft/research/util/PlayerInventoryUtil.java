package net.tfminecraft.research.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PlayerInventoryUtil {

    private PlayerInventoryUtil() {}

    public static ItemStack getStackInHand(Player player, EquipmentSlot hand) {
        if (player == null) {
            return null;
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            return player.getInventory().getItemInOffHand();
        }
        return player.getInventory().getItemInMainHand();
    }

    public static void setStackInHand(Player player, EquipmentSlot hand, ItemStack stack) {
        if (player == null) {
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(stack);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }

    /**
     * Consumes the start item from the hand used to open the station, not from other inventory slots.
     */
    public static boolean consumeFromHand(Player player, EquipmentSlot hand, String itemRef, int amount) {
        if (player == null || amount <= 0 || itemRef == null || itemRef.isBlank()) {
            return amount <= 0;
        }
        ItemStack stack = getStackInHand(player, hand);
        if (stack == null || stack.getType().isAir() || !ItemRef.matches(stack, itemRef)) {
            return false;
        }
        if (stack.getAmount() < amount) {
            return false;
        }
        stack.setAmount(stack.getAmount() - amount);
        if (stack.getAmount() <= 0) {
            setStackInHand(player, hand, null);
        } else {
            setStackInHand(player, hand, stack);
        }
        return true;
    }
}
