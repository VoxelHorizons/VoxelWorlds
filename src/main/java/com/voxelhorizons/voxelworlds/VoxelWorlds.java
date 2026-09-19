package com.voxelhorizons.voxelworlds;

import com.voxelhorizons.voxelworlds.command.VoxelWorldsCommand;
import com.voxelhorizons.voxelworlds.listener.WorldEntryListener;
import com.voxelhorizons.voxelworlds.service.WorldEntryService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoxelWorlds extends JavaPlugin {

    private WorldEntryService worldEntryService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        worldEntryService = new WorldEntryService(this);
        getServer().getPluginManager().registerEvents(
                new WorldEntryListener(worldEntryService), this);

        PluginCommand command = getCommand("voxelworlds");
        if (command != null) {
            VoxelWorldsCommand executor = new VoxelWorldsCommand(this, worldEntryService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("VoxelWorlds enabled.");
    }

    public WorldEntryService getWorldEntryService() {
        return worldEntryService;
    }
}
