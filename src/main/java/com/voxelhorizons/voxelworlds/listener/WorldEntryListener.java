package com.voxelhorizons.voxelworlds.listener;

import com.voxelhorizons.voxelworlds.service.WorldEntryService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class WorldEntryListener implements Listener {

    private final WorldEntryService service;

    public WorldEntryListener(WorldEntryService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }

        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            service.handleTeleportFrom(event.getPlayer(), event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        service.handleWorldChange(event.getPlayer(), event.getFrom());
    }
}
