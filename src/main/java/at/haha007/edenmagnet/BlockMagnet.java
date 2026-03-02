package at.haha007.edenmagnet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.joml.Matrix4f;

import java.util.Objects;

public final class BlockMagnet {
    private final World world;
    private final int radius;
    private final int magnetX;
    private final int magnetY;
    private final int magnetZ;
    private final BlockDisplay[] currentEntities = new BlockDisplay[12];
    private BukkitTask removeLaterTask;
    private final Matrix4f[] cubeEdges;

    public BlockMagnet(World world, int radius, int x, int y, int z) {
        this.world = world;
        this.radius = radius;
        this.magnetX = x;
        this.magnetY = y;
        this.magnetZ = z;
        cubeEdges = new Matrix4f[]{
                // X
                new Matrix4f().identity().translate(-radius, 0f - radius, 0f - radius).scale(radius * 2f + 1f, .1f, .1f),
                new Matrix4f().identity().translate(-radius, .9f + radius, 0f - radius).scale(radius * 2f + 1f, .1f, .1f),
                new Matrix4f().identity().translate(-radius, 0f - radius, .9f + radius).scale(radius * 2f + 1f, .1f, .1f),
                new Matrix4f().identity().translate(-radius, .9f + radius, .9f + radius).scale(radius * 2f + 1f, .1f, .1f),
                // Y
                new Matrix4f().identity().translate(0f - radius, -radius, 0f - radius).scale(.1f, radius * 2f + 1f, .1f),
                new Matrix4f().identity().translate(.9f + radius, -radius, 0f - radius).scale(.1f, radius * 2f + 1f, .1f),
                new Matrix4f().identity().translate(0f - radius, -radius, .9f + radius).scale(.1f, radius * 2f + 1f, .1f),
                new Matrix4f().identity().translate(.9f + radius, -radius, .9f + radius).scale(.1f, radius * 2f + 1f, .1f),
                // Z
                new Matrix4f().identity().translate(0f - radius, 0f - radius, -radius).scale(.1f, .1f, radius * 2f + 1f),
                new Matrix4f().identity().translate(.9f + radius, 0f - radius, -radius).scale(.1f, .1f, radius * 2f + 1f),
                new Matrix4f().identity().translate(0f - radius, .9f + radius, -radius).scale(.1f, .1f, radius * 2f + 1f),
                new Matrix4f().identity().translate(.9f + radius, .9f + radius, -radius).scale(.1f, .1f, radius * 2f + 1f),
        };
    }

    public void runMagnet() {
        Block block = world.getBlockAt(magnetX, magnetY, magnetZ);
        BlockState targetBlock = block.getRelative(BlockFace.DOWN).getState(false);
        if (!(targetBlock instanceof Container container)) return;
        EdenMagnetPlugin.INSTANCE.magnetRunner().run(block, radius, container.getInventory());
    }

    public Block block() {
        return world.getBlockAt(magnetX, magnetY, magnetZ);
    }

    public BoundingBox boundingBox() {
        return new BoundingBox(
                (double) magnetX - radius,
                (double) magnetY - radius,
                (double) magnetZ - radius,
                (double) magnetX + radius + 1,
                (double) magnetY + radius + 1,
                (double) magnetZ + radius + 1);
    }

    public void showRadiusUsingParticles() {
        BlockMagnet magnet = this;
        BoundingBox bb = magnet.boundingBox();
        int stepsPerAxis = magnet.radius() * 4;
        double minX = bb.getMinX();
        double maxX = bb.getMaxX();
        double minY = bb.getMinY();
        double maxY = bb.getMaxY();
        double minZ = bb.getMinZ();
        double maxZ = bb.getMaxZ();
        for (int i = 0; i < stepsPerAxis; i++) {
            double x = minX + (maxX - minX) * i / stepsPerAxis;
            double y = minY + (maxY - minY) * i / stepsPerAxis;
            double z = minZ + (maxZ - minZ) * i / stepsPerAxis;
            Particle particle = Particle.FLAME;

            Location loc = new Location(magnet.world(), x, minY, minZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), x, maxY, minZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), x, minY, maxZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), x, maxY, maxZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);

            loc = new Location(magnet.world(), minX, y, minZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), maxX, y, minZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), minX, y, maxZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), maxX, y, maxZ);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);

            loc = new Location(magnet.world(), minX, minY, z);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), maxX, minY, z);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), minX, maxY, z);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
            loc = new Location(magnet.world(), maxX, maxY, z);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0.01);
        }
    }


    @SuppressWarnings("UnstableApiUsage")
    public void showRadiusUsingDisplayEntities(long ticks) {
        hideRadius();
        BlockType material = switch (radius) {
            case 1 -> BlockType.ORANGE_CONCRETE;
            case 2 -> BlockType.MAGENTA_CONCRETE;
            case 3 -> BlockType.LIGHT_BLUE_CONCRETE;
            case 4 -> BlockType.YELLOW_CONCRETE;
            case 5 -> BlockType.LIME_CONCRETE;
            case 6 -> BlockType.PINK_CONCRETE;
            case 7 -> BlockType.WHITE_CONCRETE;
            case 8 -> BlockType.LIGHT_GRAY_CONCRETE;
            case 9 -> BlockType.CYAN_CONCRETE;
            case 10 -> BlockType.PURPLE_CONCRETE;
            case 11 -> BlockType.BLUE_CONCRETE;
            case 12 -> BlockType.BROWN_CONCRETE;
            case 13 -> BlockType.GREEN_CONCRETE;
            case 14 -> BlockType.RED_CONCRETE;
            case 15 -> BlockType.BLACK_CONCRETE;
            default -> throw new IllegalStateException("Unexpected value: " + radius);
        };
        for (int i = 0; i < cubeEdges.length; i++) {
            Matrix4f cubeEdge = cubeEdges[i];
            BlockDisplay entity = world.spawn(new Location(world, magnetX, magnetY, magnetZ), BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setBlock(material.createBlockData());
                e.setTransformationMatrix(cubeEdge);
                e.setBrightness(new Display.Brightness(15, 15));
            });
            currentEntities[i] = entity;
        }
        removeLaterTask = Bukkit.getScheduler().runTaskLater(EdenMagnetPlugin.INSTANCE, this::hideRadius, ticks);
    }

    public void hideRadius() {
        if (removeLaterTask != null)
            removeLaterTask.cancel();
        for (int i = 0; i < currentEntities.length; i++) {
            if (currentEntities[i] == null) continue;
            currentEntities[i].remove();
            currentEntities[i] = null;
        }
    }

    public World world() {
        return world;
    }

    public int radius() {
        return radius;
    }

    public int x() {
        return magnetX;
    }

    public int y() {
        return magnetY;
    }

    public int z() {
        return magnetZ;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (BlockMagnet) obj;
        return Objects.equals(this.world, that.world) &&
                this.radius == that.radius &&
                this.magnetX == that.magnetX &&
                this.magnetY == that.magnetY &&
                this.magnetZ == that.magnetZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, radius, magnetX, magnetY, magnetZ);
    }

    @Override
    public String toString() {
        return "BlockMagnet[" +
                "world=" + world + ", " +
                "radius=" + radius + ", " +
                "x=" + magnetX + ", " +
                "y=" + magnetY + ", " +
                "z=" + magnetZ + ']';
    }

}
