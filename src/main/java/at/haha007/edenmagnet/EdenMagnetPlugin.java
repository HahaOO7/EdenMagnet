package at.haha007.edenmagnet;

import at.haha007.edencommands.CommandRegistry;
import at.haha007.edencommands.argument.player.PlayerArgument;
import at.haha007.edencommands.tree.ArgumentCommandNode;
import at.haha007.edencommands.tree.LiteralCommandNode;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.TrustTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

public final class EdenMagnetPlugin extends JavaPlugin implements Listener {

    public static Plugin INSTANCE;
    private ChunkedMagnetProvider magnetProvider;
    private static final int tickRate = 5;
    private CommandRegistry commandRegistry;
    private NamespacedKey magnetKey;
    private NamespacedKey guiKey;

    @Override
    public void onEnable() {
        INSTANCE = this;
        magnetKey = new NamespacedKey(this, "magnet");
        guiKey = new NamespacedKey(this, "gui");
        Bukkit.getPluginManager().registerEvents(this, this);
        magnetProvider = new ChunkedMagnetProvider();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            magnetProvider.streamLoadedMagnets().forEach(BlockMagnet::runMagnet);
        }, tickRate, tickRate);

        commandRegistry = new CommandRegistry(this);
        LiteralCommandNode cmd = LiteralCommandNode.builder("edenmagnet")
                .then(ArgumentCommandNode.builder("player", PlayerArgument.builder().build()).executor(c -> {
                    Player player = c.parameter("player");
                    player.getInventory().addItem(createMagnetItem());
                    c.sender().sendMessage(Component.text("Gave magnet to " + player.getName() + "."));
                })).executor(c -> {
                    c.sender().sendMessage("/edenmagnet <player>");
                }).requires(CommandRegistry.permission("edenmagnet.command"))
                .build();

        commandRegistry.register(cmd);
    }

    @Override
    public void onDisable() {
        magnetProvider.saveAll();
        commandRegistry.destroy();
    }

    //BLOCK LISTENERS

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlaceBlock(BlockPlaceEvent event) {
        if (!event.getItemInHand().getType().isBlock()) return;
        if (!isMagnet(event.getItemInHand())) return;
        Block block = event.getBlock();
        BlockMagnet magnet = new BlockMagnet(block.getWorld(), 5, block.getX(), block.getY(), block.getZ());
        magnetProvider.addMagnet(magnet);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FLETCHING_TABLE) return;
        Optional<BlockMagnet> om = magnetProvider.getMagnet(block);
        if (om.isEmpty()) return;
        BlockMagnet magnet = om.get();
        magnetProvider.removeMagnet(magnet);
        event.setCancelled(true);
        block.setType(Material.AIR);
        Location loc = event.getBlock().getLocation().toCenterLocation();
        block.getWorld().dropItemNaturally(loc, createMagnetItem());
    }

    //INVENTORY LOGIC
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.FLETCHING_TABLE) return;
        Optional<BlockMagnet> om = magnetProvider.getMagnet(block);
        if (om.isEmpty()) return;
        UUID uuid = event.getPlayer().getUniqueId();
        Claim claim = GriefDefender.getCore().getClaimAt(block.getLocation());
        if (claim == null || !claim.isUserTrusted(uuid, TrustTypes.CONTAINER)) {
            return;
        }
        BlockMagnet magnet = om.get();
        Inventory magnetInv = createMagnetInv(magnet);
        event.getPlayer().openInventory(magnetInv);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().getSize() != 9)
            return;
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if(meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PersistentDataContainer container = pdc.get(guiKey, PersistentDataType.TAG_CONTAINER);
        if (container == null) return;
        event.setCancelled(true);
        MagnetContainer magnetContainer = new MagnetContainer(event.getWhoClicked().getWorld());
        BlockMagnet magnet = magnetContainer.fromPrimitive(container, pdc.getAdapterContext());
        magnet = magnetProvider.getMagnet(magnet.block()).orElse(null);
        if (magnet == null) return;
        int slot = event.getSlot();
        int radius = slot + 1;
        if (magnet.radius() == radius) return;
        magnetProvider.removeMagnet(magnet);
        magnet = new BlockMagnet(magnet.world(), radius, magnet.x(), magnet.y(), magnet.z());
        magnetProvider.addMagnet(magnet);
        ItemStack[] contents = createMagnetInv(magnet).getContents();
        Bukkit.getScheduler().runTask(this, () -> event.getInventory().setContents(contents));
    }

    private Inventory createMagnetInv(BlockMagnet magnet) {
        Inventory inv = Bukkit.createInventory(null, 9);
        int radius = magnet.radius();
        for (int i = 0; i < 9; i++) {
            int iRadius = i + 1;
            ItemStack item = new ItemStack(iRadius == radius ? Material.GREEN_CONCRETE : Material.RED_CONCRETE);
            item.editMeta(meta -> meta.displayName(Component.text("Radius: ", NamedTextColor.GOLD)
                    .append(Component.text(iRadius, NamedTextColor.GREEN))));
            MagnetContainer magnetContainer = new MagnetContainer(magnet.world());
            item.editMeta(meta -> {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(guiKey, PersistentDataType.TAG_CONTAINER, magnetContainer.toPrimitive(magnet, pdc.getAdapterContext()));
            });
            inv.setItem(i, item);
        }
        return inv;
    }

    //HELPERS
    private boolean isMagnet(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().has(magnetKey);
    }

    private ItemStack createMagnetItem() {
        ItemStack item = new ItemStack(Material.FLETCHING_TABLE);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Magnet", Style.style(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
            meta.setCustomModelData(69420);
            meta.getPersistentDataContainer().set(magnetKey, PersistentDataType.BYTE_ARRAY, new byte[0]);
        });
        return item;
    }
}
