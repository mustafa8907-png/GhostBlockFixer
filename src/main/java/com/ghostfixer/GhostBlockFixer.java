package com.ghostfixer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.WeakHashMap;

public class GhostBlockFixer extends JavaPlugin implements Listener {

    // Config Variables
    private long delayTicks;
    private long cooldownMs;
    private boolean anchorFixEnabled;
    private boolean debug;

    // Cache
    private final WeakHashMap<UUID, Long> cooldowns = new WeakHashMap<>();

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load Config
        saveDefaultConfig();
        loadConfigValues();

        // Register Listeners
        getServer().getPluginManager().registerEvents(this, this);
        registerPacketListeners();

        getLogger().info("GhostBlockFixer loaded successfully! (Delay: " + delayTicks + " ticks)");
    }

    private void loadConfigValues() {
        reloadConfig();
        this.delayTicks = getConfig().getLong("sync-delay-ticks", 1L);
        this.cooldownMs = getConfig().getLong("cooldown-ms", 50L);
        this.anchorFixEnabled = getConfig().getBoolean("enable-ghost-anchor-fix", true);
        this.debug = getConfig().getBoolean("debug-messages", false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ghostfixer")) {
            if (!sender.hasPermission("ghostfixer.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                loadConfigValues();
                sender.sendMessage(ChatColor.GREEN + "[GhostBlockFixer] Configuration reloaded!");
                if (debug) getLogger().info("Debug mode enabled.");
                return true;
            }
        }
        return false;
    }

    private void registerPacketListeners() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this,
                ListenerPriority.HIGHEST,
                PacketType.Play.Client.BLOCK_PLACE,
                PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                final Player player = event.getPlayer();
                if (player == null) return;

                if (isOnCooldown(player)) return;
                setCooldown(player);

                if (debug) {
                    getLogger().info("Packet received from " + player.getName() + ". Syncing in " + delayTicks + " ticks.");
                }

                Bukkit.getScheduler().runTaskLater(GhostBlockFixer.this, new Runnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            syncTargetBlock(player);
                        }
                    }
                }, delayTicks);
            }
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!anchorFixEnabled) return;

        Player player = event.getPlayer();
        Location loc = player.getLocation();

        // Only check if coordinates changed significantly
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Block blockUnder = loc.clone().subtract(0, 1, 0).getBlock();

        // Ghost Anchor Logic: Server says AIR, Client says GROUND
        if (blockUnder.getType() == Material.AIR && player.isOnGround()) {
             if (debug) getLogger().info("Ghost Anchor detected for " + player.getName());
             updateBlock(player, blockUnder);
        }
    }

    private void syncTargetBlock(Player player) {
        Block targetBlock = player.getTargetBlock(null, 6);
        if (targetBlock != null) {
            updateBlock(player, targetBlock);
            // Sync neighbors
            updateBlock(player, targetBlock.getRelative(1, 0, 0));
            updateBlock(player, targetBlock.getRelative(-1, 0, 0));
            updateBlock(player, targetBlock.getRelative(0, 1, 0));
            updateBlock(player, targetBlock.getRelative(0, -1, 0));
            updateBlock(player, targetBlock.getRelative(0, 0, 1));
            updateBlock(player, targetBlock.getRelative(0, 0, -1));
        }
    }

    @SuppressWarnings("deprecation")
    private void updateBlock(Player player, Block block) {
        try {
            player.sendBlockChange(block.getLocation(), block.getType(), block.getData());
        } catch (Exception e) {
            // Ignored for safety in varying versions
        }
    }

    private boolean isOnCooldown(Player player) {
        return cooldowns.containsKey(player.getUniqueId()) && 
               (System.currentTimeMillis() - cooldowns.get(player.getUniqueId()) < cooldownMs);
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
  }
