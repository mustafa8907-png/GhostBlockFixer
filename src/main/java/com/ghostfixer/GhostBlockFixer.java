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

import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class GhostBlockFixer extends JavaPlugin implements Listener {

    private long delayTicks;
    private long cooldownMs;
    private boolean anchorFixEnabled;
    private boolean debug;
    private final WeakHashMap<UUID, Long> cooldowns = new WeakHashMap<>();

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);
        registerPacketListeners();

        getLogger().info("GhostBlockFixer loaded! (Compatible with 1.8 - 1.21)");
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
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                loadConfigValues();
                sender.sendMessage(ChatColor.GREEN + "[GhostBlockFixer] Reloaded!");
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
                if (player == null || isOnCooldown(player)) return;
                
                setCooldown(player);

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
        
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Block blockUnder = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (blockUnder.getType() == Material.AIR && player.isOnGround()) {
             updateBlock(player, blockUnder);
        }
    }

    private void syncTargetBlock(Player player) {
        // HATA BURADAYDI: (Set<Material>) null diyerek belirsizligi cozuyoruz
        Block targetBlock = player.getTargetBlock((Set<Material>) null, 6);
        if (targetBlock != null) {
            updateBlock(player, targetBlock);
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
            // 1.8 ve 1.21 arasi en stabil yol
            player.sendBlockChange(block.getLocation(), block.getType(), block.getData());
        } catch (Exception e) {
            if (debug) getLogger().warning("Failed to sync block at: " + block.getLocation());
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
                                   
