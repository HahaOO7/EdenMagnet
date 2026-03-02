package at.haha007.edenmagnet;

import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.TrustTypes;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class EdenMagnetPlugin extends JavaPlugin implements Listener {
    private static final int TICK_RATE = 5;

    public static EdenMagnetPlugin INSTANCE;
    private ChunkedMagnetProvider magnetProvider;
    private NamespacedKey magnetKey;
    private NamespacedKey guiKey;
    private MagnetRunner magnetRunner = new PhysicsMagnetRunner();

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        INSTANCE = this;
        magnetKey = new NamespacedKey(this, "magnet");
        guiKey = new NamespacedKey(this, "gui");
        Bukkit.getPluginManager().registerEvents(this, this);
        magnetProvider = new ChunkedMagnetProvider();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            magnetProvider.streamLoadedMagnets().forEach(BlockMagnet::runMagnet);
        }, TICK_RATE, TICK_RATE);
        reload();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            LiteralArgumentBuilder<CommandSourceStack> command = literal("edenmagnet")
                    .requires(c -> c.getSender().hasPermission("edenmagnet.command"))
                    .then(literal("reload").executes(c -> {
                        reload();
                        c.getSource().getSender().sendMessage(Component.text("Reloaded magnets config."));
                        return 1;
                    }))
                    .then(argument("player", ArgumentTypes.player()).executes(c -> {
                        final PlayerSelectorArgumentResolver targetResolver = c.getArgument("player", PlayerSelectorArgumentResolver.class);
                        final Player player = targetResolver.resolve(c.getSource()).getFirst();
                        player.getInventory().addItem(createMagnetItem());
                        c.getSource().getSender().sendMessage(Component.text("Gave magnet to " + player.getName() + "."));
                        return 1;
                    }))
                    .executes(c -> {
                        c.getSource().getSender().sendMessage("/edenmagnet <player>");
                        c.getSource().getSender().sendMessage("/edenmagnet reload");
                        return 1;
                    });

            commands.registrar().register(command.build());
        });
    }

    private void reload() {
        reloadConfig();
        FileConfiguration config = getConfig();
        String type = config.getString("type", "NULL");
        switch (type) {
            case "PHYSICS":
                magnetRunner = new PhysicsMagnetRunner();
                break;
            case "ANIMATION":
                magnetRunner = new AnimationMagnetRunner();
                break;
            default:
                config.set("type", "PHYSICS");
                config.setComments("type", List.of("The type of magnet to use. Can be PHYSICS or ANIMATION."));
                saveConfig();
                break;
        }
    }

    @Override
    public void onDisable() {
        magnetProvider.saveAll();
    }

    //BLOCK LISTENERS

    @EventHandler
    private void onPiston(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Optional<BlockMagnet> om = magnetProvider.getMagnet(block);
            if (om.isEmpty()) continue;
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    private void onPiston(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Optional<BlockMagnet> om = magnetProvider.getMagnet(block);
            if (om.isEmpty()) continue;
            event.setCancelled(true);
            return;
        }
    }

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
        if (event.getPlayer().isSneaking() && !event.getPlayer().getInventory().getItemInMainHand().isEmpty())
            return;
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

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getSize() != 9)
            return;
        ItemStack item = event.getInventory().getItem(0);
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PersistentDataContainer container = pdc.get(guiKey, PersistentDataType.TAG_CONTAINER);
        if (container == null) return;
        MagnetContainer magnetContainer = new MagnetContainer(event.getPlayer().getWorld());
        BlockMagnet magnet = magnetContainer.fromPrimitive(container, pdc.getAdapterContext());
        //display magnet for 10 seconds by repeating task every 10 ticks
        magnetProvider.getMagnet(magnet.block()).ifPresent(m -> m.showRadiusUsingDisplayEntities(200L));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().getSize() != 9)
            return;
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
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
        magnet.hideRadius();
        magnet = new BlockMagnet(magnet.world(), radius, magnet.x(), magnet.y(), magnet.z());
        magnet.showRadiusUsingDisplayEntities(200L);
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

    public MagnetRunner magnetRunner() {
        return magnetRunner;
    }
}
