package at.haha007.edenmagnet;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;

record BlockMagnet(World world, int radius, int x, int y, int z) {

    public void runMagnet() {
        Block block = world.getBlockAt(x, y, z);
        BlockState targetBlock = block.getRelative(BlockFace.DOWN).getState(false);
        if (!(targetBlock instanceof Container container)) return;

        Collection<Item> items = block.getLocation().toCenterLocation().getNearbyEntitiesByType(Item.class, radius + .5);
        Inventory inventory = container.getInventory();

        Vector centerPos = new Vector(x + .5, y + .5, z + .5);
        int maxTries = 5;
        for (Item item : items) {
            Vector delta = centerPos.clone().subtract(item.getLocation().toVector());
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
                if (remaining.isEmpty()) continue;
                maxTries--;
                if (maxTries <= 0) break;
            }
        }
    }

    private double maximumDistance(Vector delta) {
        double dx = Math.abs(delta.getX());
        double dy = Math.abs(delta.getY());
        double dz = Math.abs(delta.getZ());
        return Math.max(dx, Math.max(dy, dz)) - .5;
    }

    public Block block() {
        return world.getBlockAt(x, y, z);
    }
}
