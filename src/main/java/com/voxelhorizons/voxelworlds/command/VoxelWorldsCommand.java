package com.voxelhorizons.voxelworlds.command;

import com.voxelhorizons.voxelworlds.VoxelWorlds;
import com.voxelhorizons.voxelworlds.service.WorldEntryService;
import com.voxelhorizons.voxelworlds.service.WorldRegenerationService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public final class VoxelWorldsCommand implements CommandExecutor, TabCompleter {

    private final VoxelWorlds plugin;
    private final WorldEntryService service;
    private final WorldRegenerationService regeneration;

    public VoxelWorldsCommand(VoxelWorlds plugin, WorldEntryService service,
                              WorldRegenerationService regeneration) {
        this.plugin = plugin;
        this.service = service;
        this.regeneration = regeneration;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            regeneration.reload();
            sender.sendMessage("§aVoxelWorlds configuration reloaded.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("regen")) {
            boolean started = regeneration.regenerateNow(args[1]);
            sender.sendMessage(started
                    ? "§aStarted regeneration of " + args[1] + "."
                    : "§cCould not start regeneration of " + args[1]
                    + ". Check that it is configured, loaded, and Multiverse-Core is available.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("next")) {
            long next = regeneration.nextRegeneration(args[1]);
            if (next <= 0L) {
                sender.sendMessage("§eNo regeneration is scheduled for " + args[1] + ".");
            } else {
                sender.sendMessage("§aNext regeneration for " + args[1] + ": §f"
                        + new java.util.Date(next));
            }
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);

            if (!player.hasPlayedBefore() && !player.isOnline()) {
                sender.sendMessage("§cPlayer '" + args[1] + "' has not played on this server.");
                return true;
            }

            boolean reset = service.reset(player.getUniqueId(), args[2]);
            sender.sendMessage(reset
                    ? "§aReset first-entry state for " + player.getName() + " in " + args[2] + "."
                    : "§eNo first-entry state was recorded for " + args[1] + " in " + args[2] + ".");
            return true;
        }

        sender.sendMessage("§e/vw reload");
        sender.sendMessage("§e/vw regen <world>");
        sender.sendMessage("§e/vw next <world>");
        sender.sendMessage("§e/vw reset <player> <world>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.asList("reload", "regen", "next", "reset");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("regen") || args[0].equalsIgnoreCase("next"))) {
            return regeneration.configuredWorlds();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            List<String> worlds = new ArrayList<String>();
            Bukkit.getWorlds().forEach(world -> worlds.add(world.getName()));
            return worlds;
        }

        return java.util.Collections.emptyList();
    }
}
