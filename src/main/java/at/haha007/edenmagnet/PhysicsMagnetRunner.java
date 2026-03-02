package at.haha007.edenmagnet;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;

public class PhysicsMagnetRunner implements MagnetRunner {

    @Override
    public void run(Block center, int radius, Inventory inventory) {
        Location centerPos = center.getLocation().toCenterLocation();
        Collection<Item> items = centerPos.getNearbyEntitiesByType(Item.class, radius + .5);
        int maxTries = 5;
        for (Item item : items) {
            Vector delta = centerPos.toVector().subtract(item.getLocation().toVector());
            double distance = maximumDistance(delta);
            if (distance > .5) {
                item.setVelocity(delta.multiply(.15));
                item.setGravity(false);
            } else {
                ItemStack stack = item.getItemStack();
                HashMap<Integer, ItemStack> remaining = inventory.addItem(stack);
                stack.setAmount(remaining.values().stream().mapToInt(ItemStack::getAmount).sum());
                item.setItemStack(stack);
                item.setVelocity(delta.multiply(.15));
                item.setGravity(false);
                if (!remaining.isEmpty()) {
                    maxTries--;
                    if (maxTries <= 0) break;
                }
            }
        }
    }
}
