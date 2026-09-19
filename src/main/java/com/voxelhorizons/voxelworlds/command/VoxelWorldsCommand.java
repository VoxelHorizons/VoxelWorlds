package com.voxelhorizons.voxelworlds.command;

import com.voxelhorizons.voxelworlds.VoxelWorlds;
import com.voxelhorizons.voxelworlds.service.WorldEntryService;
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

    public VoxelWorldsCommand(VoxelWorlds plugin, WorldEntryService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§aVoxelWorlds configuration reloaded.");
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
        sender.sendMessage("§e/vw reset <player> <world>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.asList("reload", "reset");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            List<String> worlds = new ArrayList<String>();
            Bukkit.getWorlds().forEach(world -> worlds.add(world.getName()));
            return worlds;
        }

        return java.util.Collections.emptyList();
    }
}
