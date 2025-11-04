package org.cubexmc.railway.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;

public class Stop {

    private String id;
    private String name;
    private Location corner1;
    private Location corner2;
    private Location stopPointLocation;
    private float launchYaw;
    private List<String> transferableLines = new ArrayList<>();
    private UUID owner;
    private final Set<UUID> admins = new HashSet<>();
    private final Set<String> linkedLineIds = new HashSet<>();
    private Map<String, Map<String, String>> customTitles = new HashMap<>();

    public Stop(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Stop(String id, ConfigurationSection section) {
        this.id = id;
        this.name = section.getString("display_name", "");

        String c1 = section.getString("corner1_location");
        if (c1 != null) {
            this.corner1 = locationFromString(c1);
        }

        String c2 = section.getString("corner2_location");
        if (c2 != null) {
            this.corner2 = locationFromString(c2);
        }

        String stopPoint = section.getString("stoppoint_location");
        if (stopPoint != null) {
            this.stopPointLocation = locationFromString(stopPoint);
        }

        this.launchYaw = (float) section.getDouble("launch_yaw", 0.0);

        this.transferableLines = section.getStringList("transferable_lines");
        if (this.transferableLines == null) {
            this.transferableLines = new ArrayList<>();
        }

        this.customTitles = new HashMap<>();
        ConfigurationSection titlesSection = section.getConfigurationSection("custom_titles");
        if (titlesSection != null) {
            for (String titleType : titlesSection.getKeys(false)) {
                ConfigurationSection titleConfigSection = titlesSection.getConfigurationSection(titleType);
                if (titleConfigSection == null) {
                    continue;
                }
                Map<String, String> titleConfig = new HashMap<>();
                for (String key : titleConfigSection.getKeys(false)) {
                    titleConfig.put(key, titleConfigSection.getString(key));
                }
                if (!titleConfig.isEmpty()) {
                    customTitles.put(titleType, titleConfig);
                }
            }
        }

        String ownerString = section.getString("owner");
        if (ownerString != null && !ownerString.isEmpty()) {
            try {
                this.owner = UUID.fromString(ownerString);
                admins.add(this.owner);
            } catch (IllegalArgumentException ignored) {
                this.owner = null;
            }
        }

        List<String> adminStrings = section.getStringList("admins");
        if (adminStrings != null) {
            for (String admin : adminStrings) {
                try {
                    UUID adminId = UUID.fromString(admin);
                    admins.add(adminId);
                } catch (IllegalArgumentException ignored) {
                    // skip invalid id
                }
            }
        }

        List<String> linkedLines = section.getStringList("linked_lines");
        if (linkedLines != null) {
            linkedLineIds.addAll(linkedLines);
        }
    }

    public void saveToConfig(ConfigurationSection section) {
        section.set("display_name", name);
        if (corner1 != null) section.set("corner1_location", locationToString(corner1));
        if (corner2 != null) section.set("corner2_location", locationToString(corner2));
        if (stopPointLocation != null) section.set("stoppoint_location", locationToString(stopPointLocation));
        section.set("launch_yaw", launchYaw);
        section.set("transferable_lines", transferableLines);
        section.set("owner", owner != null ? owner.toString() : null);
        if (!admins.isEmpty()) {
            java.util.List<String> adminStrings = new java.util.ArrayList<>();
            for (UUID id : admins) {
                if (owner != null && owner.equals(id)) continue;
                adminStrings.add(id.toString());
            }
            section.set("admins", adminStrings);
        } else {
            section.set("admins", null);
        }
        if (!linkedLineIds.isEmpty()) {
            section.set("linked_lines", new java.util.ArrayList<>(linkedLineIds));
        } else {
            section.set("linked_lines", null);
        }
        if (!customTitles.isEmpty()) {
            ConfigurationSection cts = section.createSection("custom_titles");
            for (Map.Entry<String, Map<String, String>> e : customTitles.entrySet()) {
                ConfigurationSection cs = cts.createSection(e.getKey());
                for (Map.Entry<String, String> v : e.getValue().entrySet()) {
                    cs.set(v.getKey(), v.getValue());
                }
            }
        }
    }

    private String locationToString(Location location) {
        return String.format("%s,%d,%d,%d",
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    }

    private Location locationFromString(String s) {
        String[] p = s.split(",");
        if (p.length != 4) return null;
        try {
            return new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isInStop(Location location) {
        if (corner1 == null || corner2 == null || location == null || location.getWorld() == null || !location.getWorld().equals(corner1.getWorld())) return false;
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Location getCorner1() { return corner1; }
    public void setCorner1(Location corner1) { this.corner1 = corner1; }
    public Location getCorner2() { return corner2; }
    public void setCorner2(Location corner2) { this.corner2 = corner2; }
    public Location getStopPointLocation() { return stopPointLocation; }
    public void setStopPointLocation(Location stopPointLocation) { this.stopPointLocation = stopPointLocation; }
    public float getLaunchYaw() { return launchYaw; }
    public void setLaunchYaw(float launchYaw) { this.launchYaw = launchYaw; }
    public List<String> getTransferableLines() { return new ArrayList<>(transferableLines); }
    public Map<String, String> getCustomTitle(String titleType) { return customTitles.get(titleType); }
    public Set<String> getLinkedLineIds() { return new HashSet<>(linkedLineIds); }
    public boolean isLineAllowed(String lineId) { return linkedLineIds.contains(lineId); }

    // Ownership & admins
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) {
        this.owner = owner;
        if (owner != null) admins.add(owner);
        admins.remove(null);
    }
    public java.util.Set<UUID> getAdmins() { return new java.util.HashSet<>(admins); }
    public void setAdmins(java.util.Collection<UUID> adminIds) {
        admins.clear();
        if (adminIds != null) admins.addAll(adminIds);
        if (owner != null) admins.add(owner);
        admins.remove(null);
    }
    public boolean addAdmin(UUID adminId) {
        if (adminId == null) return false;
        admins.remove(null);
        return admins.add(adminId);
    }
    public boolean removeAdmin(UUID adminId) {
        if (adminId == null) return false;
        if (owner != null && owner.equals(adminId)) return false;
        boolean removed = admins.remove(adminId);
        admins.remove(null);
        return removed;
    }

    // Linked lines allow/deny
    public boolean allowLine(String lineId) {
        if (lineId == null || lineId.isEmpty()) return false;
        return linkedLineIds.add(lineId);
    }
    public boolean denyLine(String lineId) {
        if (lineId == null || lineId.isEmpty()) return false;
        return linkedLineIds.remove(lineId);
    }

    // Transferable lines
    public boolean addTransferableLine(String lineId) {
        if (!transferableLines.contains(lineId)) return transferableLines.add(lineId);
        return false;
    }
    public boolean removeTransferableLine(String lineId) {
        return transferableLines.remove(lineId);
    }

    // Custom titles editing
    public void setCustomTitle(String titleType, java.util.Map<String,String> config) { customTitles.put(titleType, config); }
    public boolean removeCustomTitle(String titleType) { return customTitles.remove(titleType) != null; }
}


