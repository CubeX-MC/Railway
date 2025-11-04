package org.cubexmc.railway.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;

public class SelectionManager {

    private final Map<UUID, Location> corner1 = new HashMap<>();
    private final Map<UUID, Location> corner2 = new HashMap<>();

    public void setCorner1(UUID playerId, Location loc) {
        if (playerId == null || loc == null) return;
        corner1.put(playerId, loc.clone());
    }

    public void setCorner2(UUID playerId, Location loc) {
        if (playerId == null || loc == null) return;
        corner2.put(playerId, loc.clone());
    }

    public Location getCorner1(UUID playerId) {
        return playerId == null ? null : corner1.get(playerId);
    }

    public Location getCorner2(UUID playerId) {
        return playerId == null ? null : corner2.get(playerId);
    }

    public boolean isSelectionComplete(UUID playerId) {
        return playerId != null && corner1.containsKey(playerId) && corner2.containsKey(playerId);
    }

    public void clearSelection(UUID playerId) {
        if (playerId == null) return;
        corner1.remove(playerId);
        corner2.remove(playerId);
    }
}


