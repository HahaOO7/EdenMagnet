package at.haha007.edenmagnet;

import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;

public interface MagnetRunner {
    void run(Block center, int radius, Inventory inventory);

    default double maximumDistance(Vector delta) {
        double dx = Math.abs(delta.getX());
        double dy = Math.abs(delta.getY());
        double dz = Math.abs(delta.getZ());
        return Math.max(dx, Math.max(dy, dz)) - .5;
    }
}
