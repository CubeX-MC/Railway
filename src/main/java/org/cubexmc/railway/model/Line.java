package org.cubexmc.railway.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Line {

    private String id;
    private String name;
    private final List<String> orderedStopIds = new ArrayList<>();
    private String color = "&f";
    private String terminusName = "";
    private Double maxSpeed = null;
    private UUID owner;
    private final Set<UUID> admins = new HashSet<>();

    // service fields
    private boolean serviceEnabled = false;
    private int headwaySeconds = 120;
    private int dwellTicks = 100;
    private int trainCars = 3;
    private String directionMode = "bi_directional";
    private long firstDepartureTick = 0L;
    // Optional control mode override (KINEMATIC|LEASHED|REACTIVE as string)
    private String controlMode = null;

    public Line(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getTerminusName() {
        return terminusName;
    }

    public void setTerminusName(String terminusName) {
        this.terminusName = terminusName;
    }

    public Double getMaxSpeed() {
        if (maxSpeed == null) return -1.0;
        return maxSpeed;
    }

    public void setMaxSpeed(Double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public List<String> getOrderedStopIds() {
        return new ArrayList<>(orderedStopIds);
    }

    public void addStop(String stopId, int index) {
        boolean isMakingCircular = !isCircular() && !orderedStopIds.isEmpty() && orderedStopIds.get(0).equals(stopId)
                && (index == -1 || index == orderedStopIds.size());
        if (orderedStopIds.contains(stopId) && !isMakingCircular) {
            orderedStopIds.remove(stopId);
        }
        if (isCircular() && index == -1) {
            orderedStopIds.add(orderedStopIds.size() - 1, stopId);
        } else if (index >= 0 && index < orderedStopIds.size()) {
            orderedStopIds.add(index, stopId);
        } else {
            orderedStopIds.add(stopId);
        }
    }

    public void delStop(String stopId) {
        orderedStopIds.remove(stopId);
    }

    public boolean containsStop(String stopId) {
        return orderedStopIds.contains(stopId);
    }

    public boolean isCircular() {
        if (orderedStopIds.isEmpty() || orderedStopIds.size() < 2) return false;
        return orderedStopIds.get(0).equals(orderedStopIds.get(orderedStopIds.size() - 1));
    }

    public String getNextStopId(String currentStopId) {
        int index = orderedStopIds.indexOf(currentStopId);
        if (index == -1) return null;
        if (index == orderedStopIds.size() - 1) {
            if (isCircular()) {
                if (orderedStopIds.size() > 1) return orderedStopIds.get(1);
                else return orderedStopIds.get(0);
            } else {
                return null;
            }
        }
        return orderedStopIds.get(index + 1);
    }

    public String getPreviousStopId(String currentStopId) {
        int index = orderedStopIds.indexOf(currentStopId);
        if (index <= 0) {
            if (isCircular()) {
                if (orderedStopIds.size() > 2) return orderedStopIds.get(orderedStopIds.size() - 2);
                else if (orderedStopIds.size() == 2) return orderedStopIds.get(0);
            }
            return null;
        }
        return orderedStopIds.get(index - 1);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        if (owner != null) admins.add(owner);
        admins.remove(null);
    }

    public Set<UUID> getAdmins() {
        return new HashSet<>(admins);
    }

    public void setAdmins(Collection<UUID> adminIds) {
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

    public boolean isServiceEnabled() {
        return serviceEnabled;
    }

    public void setServiceEnabled(boolean serviceEnabled) {
        this.serviceEnabled = serviceEnabled;
    }

    public int getHeadwaySeconds() {
        return headwaySeconds;
    }

    public void setHeadwaySeconds(int headwaySeconds) {
        this.headwaySeconds = headwaySeconds;
    }

    public int getDwellTicks() {
        return dwellTicks;
    }

    public void setDwellTicks(int dwellTicks) {
        this.dwellTicks = dwellTicks;
    }

    public int getTrainCars() {
        return trainCars;
    }

    public void setTrainCars(int trainCars) {
        this.trainCars = trainCars;
    }

    public String getDirectionMode() {
        return directionMode;
    }

    public void setDirectionMode(String directionMode) {
        this.directionMode = directionMode;
    }

    public long getFirstDepartureTick() {
        return firstDepartureTick;
    }

    public void setFirstDepartureTick(long firstDepartureTick) {
        this.firstDepartureTick = firstDepartureTick;
    }

    public String getControlMode() {
        return controlMode;
    }

    public void setControlMode(String controlMode) {
        this.controlMode = controlMode;
    }
}


