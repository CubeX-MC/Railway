package org.cubexmc.railway.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * GUI Holder for identifying and storing GUI data
 */
public class GuiHolder implements InventoryHolder {

    public enum GuiType {
        MAIN_MENU, // Main Menu
        LINE_LIST, // Line List
        STOP_LIST, // Stop List
        LINE_VARIANTS, // Line Variants List
        STOP_VARIANTS, // Stop Variants List
        LINE_DETAIL, // Line Detail
        STOP_DETAIL // Stop Detail
    }

    private final GuiType type;
    private final Map<String, Object> data;
    private Inventory inventory;

    public GuiHolder(GuiType type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    public GuiType getType() {
        return type;
    }

    public void setData(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }

    public <T> T getData(String key, T defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
