package com.ghostfixer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    // --- ANSI RENK KODLARI VE SEMBOLLER (Konsol İçin) ---
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String CHECK = "\u2713"; 
    private static final String CROSS = "\u274C"; 

    private long delayTicks;
    private long cooldownMs;
    private boolean anchorFixEnabled;
    private boolean debug;
    private final WeakHashMap<UUID, Long> cooldowns = new WeakHashMap<>();

    @Override
    public void onEnable() {
        // --- N-E-X-U-S BAŞLANGIÇ LOGU ---
        Bukkit.getConsoleSender().sendMessage(ANSI_CYAN + "------------N-E-X-U-S-------------" + ANSI_RESET);
        Bukkit.getConsoleSender().sendMessage(ANSI_GREEN + "Plugin created by mustafa8907" + ANSI_RESET);
        Bukkit.getConsoleSender().sendMessage(ANSI_YELLOW + "Website: mustafa8907.com.tr" + ANSI_RESET);
        Bukkit.getConsoleSender().sendMessage(ANSI_PURPLE + "Discord: discord.gg/mustafa8907" + ANSI_RESET);
        Bukkit.getConsoleSender().sendMessage(ANSI_CYAN + "-----------S-E-T-UP-S---------------" + ANSI_RESET);

        // --- PROTOCOLLIB KONTROLÜ ---
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            Bukkit.getConsoleSender().sendMessage(ANSI_RED + "ProtocolLib Undetected " + CROSS + ANSI_RESET);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getConsoleSender().sendMessage(ANSI_GREEN + "ProtocolLib Detected " + CHECK + ANSI_RESET);

        // --- VERSİYON KONTROLÜ VE BAŞLATMA MESAJLARI ---
        String serverVersion = Bukkit.getServer().getBukkitVersion().split("-")[0];
        
        Bukkit.getConsoleSender().sendMessage(ANSI_YELLOW + serverVersion + " detected" + ANSI_RESET);
        Bukkit.getConsoleSender().sendMessage(ANSI_CYAN + "GhostBlocks Fixing..." + ANSI_RESET);
        
        // Config Ayarları
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        loadConfigValues();

        // Event Kayıtları
        getServer().getPluginManager().registerEvents(this, this);
        registerPacketListeners();

        Bukkit.getConsoleSender().sendMessage(ANSI_GREEN + "GhostBlockFixer is Working" + ANSI_RESET);
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

                Bukkit.getScheduler().runTaskLater(GhostBlockFixer.this, () -> {
                    if (player.isOnline()) {
                        syncTargetBlock(player);
                    }
                }, delayTicks);
            }
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!anchorFixEnabled) return;
        
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Block blockUnder = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (blockUnder.getType() == Material.AIR && player.isOnGround()) {
            updateBlock(player, blockUnder);
        }
    }

    private void syncTargetBlock(Player player) {
        // Set<Material> null castine artık gerek yok, modern sürümde çalışıyoruz
        Block targetBlock = player.getTargetBlock(null, 6);
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

    private void updateBlock(Player player, Block block) {
        try {
            // Modern API: Direkt getBlockData() kullanımı
            player.sendBlockChange(block.getLocation(), block.getBlockData());
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
