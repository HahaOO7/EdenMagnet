package at.haha007.edenmagnet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;

public class AnimationMagnetRunner implements MagnetRunner {

    @Override
    public void run(Block center, int radius, Inventory inventory) {
        Location centerPos = center.getLocation().toCenterLocation();
        Collection<Item> items = centerPos.getNearbyEntitiesByType(Item.class, radius + .5);
        int maxTries = 5;
        for (Item item : items) {
            ItemStack stack = item.getItemStack();
            int originalAmount = stack.getAmount();

            // Use a clone to avoid accidental mutation by inventory.addItem
            HashMap<Integer, ItemStack> remaining = inventory.addItem(stack.clone());
            int remainingAmount = remaining.values().stream().mapToInt(ItemStack::getAmount).sum();
            int transferredAmount = originalAmount - remainingAmount;

            Vector itemVec = item.getLocation().toVector();
            Vector centerVec = centerPos.toVector();
            Vector relativeStart = itemVec.clone().subtract(centerVec);

            // If some items were transferred to the inventory, spawn a display to animate that amount
            if (transferredAmount > 0) {
                ItemStack displayStack = stack.clone();
                displayStack.setAmount(transferredAmount);

                Location spawnLoc = new Location(centerPos.getWorld(), centerPos.getX(), centerPos.getY(), centerPos.getZ());
                ItemDisplay display = centerPos.getWorld().spawn(spawnLoc, ItemDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setItemStack(displayStack);
                    e.setBrightness(new Display.Brightness(item.getLocation().getBlock().getLightFromBlocks(), item.getLocation().getBlock().getLightFromSky()));

                    // position the display relative to the center (initial position = item's position)
                    Transformation transformation = e.getTransformation();
                    transformation.getTranslation().set((float) relativeStart.getX(), (float) relativeStart.getY(), (float) relativeStart.getZ());
                    e.setTransformation(transformation);

                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                });

                // schedule interpolation one tick later to ensure the entity exists server-side
                Bukkit.getScheduler().runTaskLater(EdenMagnetPlugin.INSTANCE, () -> animateAndScheduleRemoval(display), 1L);
            }

            if (remainingAmount == 0) {
                // all items were picked up -> remove the original entity
                item.remove();
            } else {
                // update the item entity to the remaining amount
                ItemStack remainingStack = stack.clone();
                remainingStack.setAmount(remainingAmount);
                item.setItemStack(remainingStack);

                maxTries--;
                if (maxTries <= 0) break;
            }
        }
    }

    private void animateAndScheduleRemoval(ItemDisplay display) {
        int totalTicks = 10;
        // Set interpolation BEFORE applying the new transformation so the change is animated
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(totalTicks);

        // target: center (0,0,0) relative to the display's base
        Transformation transformation = display.getTransformation();
        transformation.getTranslation().set(0f, 0f, 0f);
        display.setTransformation(transformation);

        // remove slightly after the interpolation finished
        Bukkit.getScheduler().runTaskLater(EdenMagnetPlugin.INSTANCE, display::remove, totalTicks + 1L);
    }
}
