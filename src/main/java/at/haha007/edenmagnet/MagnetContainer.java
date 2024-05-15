package at.haha007.edenmagnet;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

class MagnetContainer implements PersistentDataType<PersistentDataContainer, BlockMagnet> {
    private final NamespacedKey xyzKey = new NamespacedKey(EdenMagnetPlugin.INSTANCE, "xyz");
    private final NamespacedKey radiusKey = new NamespacedKey(EdenMagnetPlugin.INSTANCE, "radius");

    private final World world;

    MagnetContainer(World world) {
        this.world = world;
    }

    @NotNull
    public Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @NotNull
    public Class<BlockMagnet> getComplexType() {
        return BlockMagnet.class;
    }

    @NotNull
    public PersistentDataContainer toPrimitive(@NotNull BlockMagnet magnet, @NotNull PersistentDataAdapterContext context) {
        var pdc = context.newPersistentDataContainer();
        pdc.set(xyzKey, PersistentDataType.INTEGER_ARRAY, new int[]{magnet.x(), magnet.y(), magnet.z()});
        pdc.set(radiusKey, PersistentDataType.INTEGER, magnet.radius());
        return pdc;
    }

    @NotNull
    public BlockMagnet fromPrimitive(@NotNull PersistentDataContainer pdc, @NotNull PersistentDataAdapterContext context) {
        int[] xyz = pdc.get(xyzKey, PersistentDataType.INTEGER_ARRAY);
        Integer radius = pdc.get(radiusKey, PersistentDataType.INTEGER);
        if (xyz == null) throw new IllegalArgumentException("xyz");
        if (radius == null) throw new IllegalArgumentException("radius");
        return new BlockMagnet(world, radius, xyz[0], xyz[1], xyz[2]);
    }
}
