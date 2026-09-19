package com.voxelhorizons.voxelworlds.listener;

import com.voxelhorizons.voxelworlds.service.WorldEntryService;
import com.voxelhorizons.voxelworlds.service.WorldRegenerationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class WorldEntryListener implements Listener {

    private final WorldEntryService entryService;
    private final WorldRegenerationService regenerationService;

    public WorldEntryListener(WorldEntryService entryService, WorldRegenerationService regenerationService) {
        this.entryService = entryService;
        this.regenerationService = regenerationService;
    }

    /**
     * Lock regeneration targets before Multiverse-Portals, /mvtp, commands or
     * another plugin can move a player into the world.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedWorldTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }

        String destination = event.getTo().getWorld().getName();
        if (!regenerationService.isWorldLocked(destination)) {
            return;
        }

        event.setCancelled(true);
        regenerationService.redirectBlockedPlayer(event.getPlayer(), destination);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }

        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            entryService.handleTeleportFrom(event.getPlayer(), event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        String destination = event.getPlayer().getWorld().getName();

        // Defensive fallback for teleport mechanisms that bypass/cannot be
        // cancelled by PlayerTeleportEvent. Immediately evacuate the player.
        if (regenerationService.isWorldLocked(destination)) {
            regenerationService.redirectBlockedPlayer(event.getPlayer(), destination);
            return;
        }

        entryService.handleWorldChange(event.getPlayer(), event.getFrom());
    }
}
