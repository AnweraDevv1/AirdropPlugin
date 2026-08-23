package com.airdrop.plugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Barrel;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.*;

public class AirdropPlugin extends JavaPlugin implements Listener, TabExecutor {

    private BukkitRunnable airdropTask;
    private final Map<Location, Airdrop> activeAirdrops = new HashMap<>();
    private Random random = new Random();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        config = getConfig();
        
        // Register listener and commands
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("airdrop")).setExecutor(this);
        Objects.requireNonNull(getCommand("airdrop")).setTabCompleter(this);
        
        // Start the airdrop scheduler
        startAirdropScheduler();
        
        getLogger().info("AirdropPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (airdropTask != null) {
            airdropTask.cancel();
        }
        // Remove all active airdrops on disable
        for (Airdrop airdrop : activeAirdrops.values()) {
            airdrop.remove();
        }
        activeAirdrops.clear();
        getLogger().info("AirdropPlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn":
                if (!player.hasPermission("airdrop.spawn") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to spawn airdrops!");
                    return true;
                }
                spawnAirdrop();
                player.sendMessage(ChatColor.GREEN + "Airdrop spawned successfully!");
                break;
                
            case "force":
                if (!player.hasPermission("airdrop.force") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to force open airdrops!");
                    return true;
                }
                forceOpenAll();
                player.sendMessage(ChatColor.GREEN + "All airdrops are now ready to open!");
                break;
                
            case "reload":
                if (!player.hasPermission("airdrop.reload") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to reload config!");
                    return true;
                }
                reloadConfig();
                config = getConfig();
                player.sendMessage(ChatColor.GREEN + "Config reloaded!");
                break;
                
            default:
                sendHelp(player);
                break;
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            
            if ("spawn".startsWith(prefix)) completions.add("spawn");
            if ("force".startsWith(prefix)) completions.add("force");
            if ("reload".startsWith(prefix)) completions.add("reload");
            
            return completions;
        }
        
        return Collections.emptyList();
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Airdrop Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/airdrop spawn" + ChatColor.WHITE + " - Spawn an airdrop instantly");
        player.sendMessage(ChatColor.YELLOW + "/airdrop force" + ChatColor.WHITE + " - Make all airdrops ready to open");
        player.sendMessage(ChatColor.YELLOW + "/airdrop reload" + ChatColor.WHITE + " - Reload configuration");
    }

    private void startAirdropScheduler() {
        int intervalMinutes = config.getInt("airdrop-interval", 30);
        long intervalTicks = intervalMinutes * 60L * 20L;
        
        airdropTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnAirdrop();
            }
        };
        airdropTask.runTaskTimer(this, 0L, intervalTicks);
    }

    private void spawnAirdrop() {
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return;

        // Get world border
        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize();
        Location center = border.getCenter();
        int safetyMargin = config.getInt("border-safety-margin", 50);

        // Find random location within border
        Location targetLocation = null;
        int attempts = 0;
        while (attempts < 100) {
            double offsetX = (random.nextDouble() - 0.5) * (borderSize - safetyMargin * 2);
            double offsetZ = (random.nextDouble() - 0.5) * (borderSize - safetyMargin * 2);
            int x = (int) (center.getX() + offsetX);
            int z = (int) (center.getZ() + offsetZ);
            
            // Check if location is within border
            if (!border.isInside(new Location(world, x, 0, z))) {
                attempts++;
                continue;
            }

            // Find highest block at this location
            int y = world.getHighestBlockYAt(x, z);
            if (y > 0) {
                targetLocation = new Location(world, x + 0.5, y + 1, z + 0.5);
                break;
            }
            attempts++;
        }

        if (targetLocation == null) {
            getLogger().warning("Could not find valid location for airdrop");
            return;
        }

        // Create the airdrop
        Airdrop airdrop = new Airdrop(targetLocation);
        activeAirdrops.put(targetLocation, airdrop);

        // Broadcast message
        int waitTime = config.getInt("wait-time", 3);
        String message = ChatColor.GREEN + "✈️ AIRDROP INCOMING! ✈️\n" +
                        ChatColor.YELLOW + "Location: " + ChatColor.WHITE + 
                        String.format("%.0f, %.0f, %.0f", targetLocation.getX(), targetLocation.getY(), targetLocation.getZ()) + "\n" +
                        ChatColor.GOLD + "Opens in " + waitTime + " minutes!";
        Bukkit.broadcastMessage(message);

        // Play sound to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        }
    }
    
    private void forceOpenAll() {
        for (Airdrop airdrop : activeAirdrops.values()) {
            airdrop.forceOpen();
        }
        if (!activeAirdrops.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "⚡ All airdrops are now ready to open!");
        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "No active airdrops found.");
        }
    }

    private class Airdrop {
        private Location location;
        private ArmorStand stand;
        private Block barrelBlock;
        private boolean isOpenable = false;
        private BukkitRunnable timerTask;
        private BukkitRunnable disappearTask;

        public Airdrop(Location loc) {
            this.location = loc;
            spawnBarrel();
            spawnArmorStand();
            startTimer();
        }

        private void spawnBarrel() {
            World world = location.getWorld();
            if (world == null) return;

            barrelBlock = location.getBlock();
            barrelBlock.setType(Material.BARREL);
            fillLoot();
        }

        private void spawnArmorStand() {
            World world = location.getWorld();
            if (world == null) return;

            Location standLocation = location.clone().add(0, 1.5, 0);
            stand = (ArmorStand) world.spawnEntity(standLocation, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            int waitTime = config.getInt("wait-time", 3);
            stand.setCustomName(ChatColor.RED + "Opening in: " + waitTime + ":00");
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.setGravity(false);
            stand.setMarker(true);
        }

        private void fillLoot() {
            if (!(barrelBlock.getState() instanceof Barrel)) return;
            
            Barrel barrel = (Barrel) barrelBlock.getState();
            Inventory inventory = barrel.getInventory();
            inventory.clear();
            
            List<ItemStack> possibleLoot = getPossibleLoot();
            int minItems = config.getInt("loot.min-items", 5);
            int maxItems = config.getInt("loot.max-items", 10);
            int itemCount = minItems + random.nextInt(maxItems - minItems + 1);
            
            for (int i = 0; i < itemCount; i++) {
                if (!possibleLoot.isEmpty()) {
                    ItemStack item = selectWeightedItem(possibleLoot).clone();
                    inventory.addItem(item);
                }
            }
            barrel.update();
        }

        private ItemStack selectWeightedItem(List<ItemStack> loot) {
            // Simple weighted selection based on config
            // For now, just return random item - full weight system would require more complex config parsing
            return loot.get(random.nextInt(loot.size()));
        }

        private List<ItemStack> getPossibleLoot() {
            List<ItemStack> loot = new ArrayList<>();
            
            // Load loot from config or use defaults
            if (config.isConfigurationSection("loot.items")) {
                for (String key : config.getConfigurationSection("loot.items").getKeys(false)) {
                    String materialName = config.getString("loot.items." + key + ".material");
                    int amount = config.getInt("loot.items." + key + ".amount", 1);
                    
                    try {
                        Material mat = Material.valueOf(materialName.toUpperCase());
                        ItemStack item = new ItemStack(mat, amount);
                        loot.add(item);
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid material in config: " + materialName);
                    }
                }
            }
            
            // If no items loaded from config, use defaults
            if (loot.isEmpty()) {
                loot.add(new ItemStack(Material.DIAMOND, 5));
                loot.add(new ItemStack(Material.EMERALD, 8));
                loot.add(new ItemStack(Material.IRON_INGOT, 16));
                loot.add(new ItemStack(Material.GOLD_INGOT, 12));
                loot.add(new ItemStack(Material.NETHERITE_INGOT, 1));
                loot.add(new ItemStack(Material.ENDER_PEARL, 16));
                loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
                loot.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
                loot.add(new ItemStack(Material.SADDLE, 2));
                loot.add(new ItemStack(Material.NAME_TAG, 3));
            }
            
            return loot;
        }

        private void startTimer() {
            int waitTimeMinutes = config.getInt("wait-time", 3);
            final int[] timeLeft = {waitTimeMinutes * 60};

            timerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    timeLeft[0]--;
                    
                    if (timeLeft[0] <= 0) {
                        isOpenable = true;
                        if (stand != null && !stand.isDead()) {
                            stand.setCustomName(ChatColor.GREEN + "READY TO OPEN!");
                        }
                        cancel();
                        startDisappearTimer();
                    } else {
                        int minutes = timeLeft[0] / 60;
                        int seconds = timeLeft[0] % 60;
                        String timeString = String.format("%d:%02d", minutes, seconds);
                        
                        ChatColor color;
                        if (timeLeft[0] <= 30) {
                            color = ChatColor.RED;
                        } else if (timeLeft[0] <= 60) {
                            color = ChatColor.YELLOW;
                        } else {
                            color = ChatColor.WHITE;
                        }
                        
                        if (stand != null && !stand.isDead()) {
                            stand.setCustomName(color + "Opening in: " + timeString);
                        }
                    }
                }
            };
            timerTask.runTaskTimer(AirdropPlugin.this, 0L, 20L);
        }

        private void startDisappearTimer() {
            int despawnTime = config.getInt("despawn-time", 90);
            disappearTask = new BukkitRunnable() {
                @Override
                public void run() {
                    remove();
                    activeAirdrops.remove(location);
                }
            };
            disappearTask.runTaskLater(AirdropPlugin.this, despawnTime * 20L);
        }

        public void forceOpen() {
            isOpenable = true;
            if (stand != null && !stand.isDead()) {
                stand.setCustomName(ChatColor.GREEN + "READY TO OPEN!");
            }
            if (timerTask != null) {
                timerTask.cancel();
            }
            if (disappearTask == null) {
                startDisappearTimer();
            }
        }

        public boolean tryOpen(Player player) {
            if (!isOpenable) {
                player.sendMessage(ChatColor.RED + "This airdrop is not ready yet!");
                return false;
            }
            return true;
        }

        public void remove() {
            if (timerTask != null) timerTask.cancel();
            if (disappearTask != null) disappearTask.cancel();
            if (stand != null && !stand.isDead()) stand.remove();
            if (barrelBlock != null && barrelBlock.getType() == Material.BARREL) {
                barrelBlock.setType(Material.AIR);
            }
        }
    }

    @EventHandler
    public void onBarrelInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.BARREL) return;

        Location loc = event.getClickedBlock().getLocation();
        Airdrop airdrop = activeAirdrops.get(loc);
        
        if (airdrop != null) {
            event.setCancelled(!airdrop.tryOpen(event.getPlayer()));
            if (!event.isCancelled()) {
                Bukkit.broadcastMessage(ChatColor.GOLD + event.getPlayer().getName() + 
                                      ChatColor.YELLOW + " opened an airdrop!");
            }
        }
    }
}
