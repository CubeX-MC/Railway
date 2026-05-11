package org.cubexmc.metro.train;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TrainPassengerRegistry {

    private final Map<UUID, org.bukkit.entity.HumanEntity> passengers = new HashMap<>();

    void add(org.bukkit.entity.HumanEntity player) {
        if (player != null) {
            passengers.put(player.getUniqueId(), player);
        }
    }

    void remove(org.bukkit.entity.HumanEntity player) {
        if (player != null) {
            passengers.remove(player.getUniqueId());
        }
    }

    boolean contains(org.bukkit.entity.HumanEntity player) {
        return player != null && passengers.containsKey(player.getUniqueId());
    }

    boolean contains(UUID playerId) {
        return playerId != null && passengers.containsKey(playerId);
    }

    List<org.bukkit.entity.HumanEntity> onlinePassengers() {
        List<org.bukkit.entity.HumanEntity> result = new ArrayList<>();
        for (org.bukkit.entity.HumanEntity player : passengers.values()) {
            if (player != null && isPlayerOnline(player)) {
                result.add(player);
            }
        }
        return result;
    }

    boolean hasOnlinePassengers() {
        for (org.bukkit.entity.HumanEntity player : passengers.values()) {
            if (player != null && isPlayerOnline(player)) {
                return true;
            }
        }
        return false;
    }

    Set<UUID> snapshotPassengerIds() {
        return new HashSet<>(passengers.keySet());
    }

    void clear() {
        passengers.clear();
    }

    private boolean isPlayerOnline(org.bukkit.entity.HumanEntity entity) {
        try {
            return (boolean) entity.getClass().getMethod("isOnline").invoke(entity);
        } catch (Exception e) {
            return false;
        }
    }
}
