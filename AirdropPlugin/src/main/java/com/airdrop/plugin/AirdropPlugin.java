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

import java.util.*;

public class AirdropPlugin extends JavaPlugin implements Listener {

    private BukkitRunnable airdropTask;
    private final Map<Location, Airdrop> activeAirdrops = new HashMap<>();
    private Random random = new Random();

    @Override
    public void onEnable() {
        // Register listener
        getServer().getPluginManager().registerEvents(this, this);
        
        // Start the airdrop scheduler (every 30 minutes)
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

    private void startAirdropScheduler() {
        airdropTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnAirdrop();
            }
        };
        // Run every 30 minutes (30 * 60 * 20 ticks)
        airdropTask.runTaskTimer(this, 0L, 30 * 60 * 20L);
    }

    private void spawnAirdrop() {
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return;

        // Get world border
        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize();
        Location center = border.getCenter();

        // Find random location within border
        Location targetLocation = null;
        int attempts = 0;
        while (attempts < 100) {
            double offsetX = (random.nextDouble() - 0.5) * borderSize * 0.8;
            double offsetZ = (random.nextDouble() - 0.5) * borderSize * 0.8;
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
        String message = ChatColor.GREEN + "✈️ AIRDROP INCOMING! ✈️\n" +
                        ChatColor.YELLOW + "Location: " + ChatColor.WHITE + 
                        String.format("%.0f, %.0f, %.0f", targetLocation.getX(), targetLocation.getY(), targetLocation.getZ()) + "\n" +
                        ChatColor.GOLD + "Opens in 3 minutes!";
        Bukkit.broadcastMessage(message);

        // Play sound to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
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
            stand.setCustomName(ChatColor.RED + "Opening in: 3:00");
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
            int itemCount = 5 + random.nextInt(6);
            
            for (int i = 0; i < itemCount; i++) {
                if (!possibleLoot.isEmpty()) {
                    ItemStack item = possibleLoot.get(random.nextInt(possibleLoot.size())).clone();
                    item.setAmount(1 + random.nextInt(item.getMaxStackSize()));
                    inventory.addItem(item);
                }
            }
            barrel.update();
        }

        private List<ItemStack> getPossibleLoot() {
            List<ItemStack> loot = new ArrayList<>();
            loot.add(new ItemStack(Material.DIAMOND));
            loot.add(new ItemStack(Material.EMERALD));
            loot.add(new ItemStack(Material.IRON_INGOT));
            loot.add(new ItemStack(Material.GOLD_INGOT));
            loot.add(new ItemStack(Material.NETHERITE_SCRAP));
            loot.add(new ItemStack(Material.ENDER_PEARL));
            loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
            loot.add(new ItemStack(Material.TOTEM_OF_UNDYING));
            loot.add(new ItemStack(Material.NETHERITE_INGOT));
            loot.add(new ItemStack(Material.DIAMOND_HORSE_ARMOR));
            loot.add(new ItemStack(Material.SADDLE));
            loot.add(new ItemStack(Material.NAME_TAG));
            return loot;
        }

        private void startTimer() {
            final int[] timeLeft = {180};

            timerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    timeLeft[0]--;
                    
                    if (timeLeft[0] <= 0) {
                        isOpenable = true;
                        if (stand != null) {
                            stand.setCustomName(ChatColor.GREEN + "READY TO OPEN!");
                        }
                        cancel();
                        startDisappearTimer();
                    } else {
                        int minutes = timeLeft[0] / 60;
                        int seconds = timeLeft[0] % 60;
                        String timeString = String.format("%d:%02d", minutes, seconds);
                        
                        if (timeLeft[0] <= 30) {
                            stand.setCustomName(ChatColor.RED + "Opening in: " + timeString);
                        } else if (timeLeft[0] <= 60) {
                            stand.setCustomName(ChatColor.YELLOW + "Opening in: " + timeString);
                        } else {
                            stand.setCustomName(ChatColor.WHITE + "Opening in: " + timeString);
                        }
                    }
                }
            };
            timerTask.runTaskTimer(AirdropPlugin.this, 0L, 20L);
        }

        private void startDisappearTimer() {
            disappearTask = new BukkitRunnable() {
                @Override
                public void run() {
                    remove();
                    activeAirdrops.remove(location);
                }
            };
            disappearTask.runTaskLater(AirdropPlugin.this, 90 * 20L);
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
