package at.haha007.edenmagnet;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Stream;

public class ChunkedMagnetProvider implements Listener {
    private final Map<Chunk, List<BlockMagnet>> loadedChunksWithMagnets = new HashMap<>();
    private static final NamespacedKey chunkDataKey = new NamespacedKey(EdenMagnetPlugin.INSTANCE, "chunkData");

    public ChunkedMagnetProvider() {
        Bukkit.getPluginManager().registerEvents(this, EdenMagnetPlugin.INSTANCE);
    }

    public void saveAll() {
        loadedChunksWithMagnets.forEach(this::saveDataToPdc);
        loadedChunksWithMagnets.clear();
    }

    public Stream<BlockMagnet> streamLoadedMagnets() {
        return loadedChunksWithMagnets.values().stream().flatMap(Collection::stream);
    }

    public void addMagnet(BlockMagnet magnet) {
        if (magnet == null) throw new IllegalArgumentException("null");
        List<BlockMagnet> list = loadedChunksWithMagnets.computeIfAbsent(magnet.block().getChunk(), c -> new ArrayList<>());
        list.add(magnet);
    }

    public boolean removeMagnet(BlockMagnet magnet) {
        List<BlockMagnet> list = loadedChunksWithMagnets.get(magnet.block().getChunk());
        if (list == null) return false;
        return list.remove(magnet);
    }

    public Optional<BlockMagnet> getMagnet(Block block) {
        List<BlockMagnet> magnets = loadedChunksWithMagnets.get(block.getChunk());
        if (magnets == null || magnets.isEmpty()) return Optional.empty();
        for (BlockMagnet magnet : magnets) {
            if (magnet.block().equals(block)) return Optional.of(magnet);
        }
        return Optional.empty();
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        List<BlockMagnet> magnets = loadDataFromPdc(chunk);
        if (magnets.isEmpty())
            return;
        loadedChunksWithMagnets.put(chunk, magnets);
    }

    @EventHandler
    private void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        List<BlockMagnet> magnets = loadedChunksWithMagnets.remove(chunk);
        if (magnets == null)
            return;
        saveDataToPdc(chunk, magnets);
    }

    private List<BlockMagnet> loadDataFromPdc(Chunk chunk) {
        var pdc = chunk.getPersistentDataContainer();
        PersistentDataContainer[] containers = pdc.get(chunkDataKey, PersistentDataType.TAG_CONTAINER_ARRAY);
        if (containers == null) return List.of();
        if (containers.length == 0) return List.of();

        List<BlockMagnet> magnets = new ArrayList<>();
        MagnetContainer magnetContainer = new MagnetContainer(chunk.getWorld());
        for (PersistentDataContainer container : containers) {
            magnets.add(magnetContainer.fromPrimitive(container, pdc.getAdapterContext()));
        }

        return magnets;
    }

    private void saveDataToPdc(Chunk chunk, List<BlockMagnet> magnets) {
        var pdc = chunk.getPersistentDataContainer();
        PersistentDataContainer[] containers = new PersistentDataContainer[magnets.size()];
        MagnetContainer container = new MagnetContainer(chunk.getWorld());
        for (int i = 0; i < magnets.size(); i++) {
            BlockMagnet magnet = magnets.get(i);
            containers[i] = container.toPrimitive(magnet, pdc.getAdapterContext());
        }

        //no tag if no magnets in chunk
        if (containers.length == 0)
            pdc.remove(chunkDataKey);
        else
            pdc.set(chunkDataKey, PersistentDataType.TAG_CONTAINER_ARRAY, containers);
    }
}
