package org.cubexmc.railway.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.gui.GuiHolder.GuiType;
import org.cubexmc.railway.manager.LanguageManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.util.OwnershipUtil;
import org.cubexmc.railway.util.SchedulerUtil;

import java.util.List;

/**
 * GUI Event Listener
 */
public class GuiListener implements Listener {

    private final Railway plugin;

    // Slot constants
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_FILTER = 49;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_BACK = 46;

    // Main menu slots
    private static final int SLOT_LINE_MANAGE = 11;
    private static final int SLOT_STOP_MANAGE = 15;

    public GuiListener(Railway plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();

        // Check if it is our GUI
        if (!(inv.getHolder() instanceof GuiHolder holder)) {
            return;
        }

        // Cancel event to prevent taking items
        event.setCancelled(true);

        // Ignore non-player clicks
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        // Ignore clicks outside GUI
        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }

        // Handle based on GUI type
        switch (holder.getType()) {
            case MAIN_MENU -> handleMainMenuClick(player, slot);
            case LINE_LIST -> handleLineListClick(player, holder, slot, event.isRightClick());
            case STOP_LIST -> handleStopListClick(player, holder, slot);
            case LINE_VARIANTS -> handleLineVariantsClick(player, holder, slot);
            case STOP_VARIANTS -> handleStopVariantsClick(player, holder, slot);
            case LINE_DETAIL -> handleLineDetailClick(player, holder, slot, event.isRightClick());
            case STOP_DETAIL -> {
            } // Not implemented
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Prevent dragging items in GUI
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle Main Menu Click
     */
    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case SLOT_LINE_MANAGE -> plugin.getGuiManager().openLineList(player, 0, false);
            case SLOT_STOP_MANAGE -> plugin.getGuiManager().openStopList(player, 0, false);
        }
    }

    /**
     * Handle Line List Click
     */
    private void handleLineListClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);

        // Handle control bar
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineList(player, page - 1, showOnlyMine);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineList(player, page + 1, showOnlyMine);
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openLineList(player, 0, !showOnlyMine);
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openMainMenu(player);
                return;
            }
        }

        // Handle line item click (first 36 slots)
        if (slot < 36) {
            List<String> lineNames = holder.getData("lineNames");
            java.util.Map<String, List<Line>> groupedLines = holder.getData("groupedLines");
            if (lineNames == null || groupedLines == null)
                return;

            int index = page * 36 + slot;
            if (index >= 0 && index < lineNames.size()) {
                String name = lineNames.get(index);
                List<Line> variants = groupedLines.get(name);

                if (variants.size() > 1) {
                    plugin.getGuiManager().openLineVariants(player, name, 0);
                } else {
                    plugin.getGuiManager().openLineDetail(player, variants.get(0).getId(), 0);
                }
            }
        }
    }

    /**
     * Handle Line Variants List Click
     */
    private void handleLineVariantsClick(Player player, GuiHolder holder, int slot) {
        int page = holder.getData("page", 0);
        String lineName = holder.getData("lineName");
        int totalPages = holder.getData("totalPages", 1);

        // Handle control bar
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page - 1);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page + 1);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openLineList(player, 0, false);
                return;
            }
        }

        // Handle item click
        if (slot < 36) {
            List<Line> lines = holder.getData("lines");
            if (lines == null)
                return;

            int index = page * 36 + slot;
            if (index >= 0 && index < lines.size()) {
                Line line = lines.get(index);
                plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
            }
        }
    }

    /**
     * Handle Stop List Click
     */
    private void handleStopListClick(Player player, GuiHolder holder, int slot) {
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);

        // Handle control bar
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openStopList(player, page - 1, showOnlyMine);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopList(player, page + 1, showOnlyMine);
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openStopList(player, 0, !showOnlyMine);
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openMainMenu(player);
                return;
            }
        }

        // Handle item click
        if (slot < 36) {
            List<String> stopNames = holder.getData("stopNames");
            java.util.Map<String, List<Stop>> groupedStops = holder.getData("groupedStops");
            if (stopNames == null || groupedStops == null)
                return;

            int index = page * 36 + slot;
            if (index >= 0 && index < stopNames.size()) {
                String name = stopNames.get(index);
                List<Stop> variants = groupedStops.get(name);

                if (variants.size() > 1) {
                    plugin.getGuiManager().openStopVariants(player, name, 0);
                } else {
                    Stop stop = variants.get(0);
                    handleStopClick(player, stop);
                }
            }
        }
    }

    /**
     * Handle Stop Variants Click
     */
    private void handleStopVariantsClick(Player player, GuiHolder holder, int slot) {
        int page = holder.getData("page", 0);
        String stopName = holder.getData("stopName");
        int totalPages = holder.getData("totalPages", 1);

        // Handle control bar
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page - 1);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page + 1);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openStopList(player, 0, false);
                return;
            }
        }

        // Handle item click
        if (slot < 36) {
            List<Stop> stops = holder.getData("stops");
            if (stops == null)
                return;

            int index = page * 36 + slot;
            if (index >= 0 && index < stops.size()) {
                Stop stop = stops.get(index);
                handleStopClick(player, stop);
            }
        }
    }

    private void handleStopClick(Player player, Stop stop) {
        // Check permission
        if (!player.hasPermission("railway.tp")) {
            return;
        }

        // Teleport
        if (stop.getStopPointLocation() != null) {
            player.closeInventory();
            SchedulerUtil.teleportEntity(player, stop.getStopPointLocation()).thenAccept(success -> {
                if (success) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                            LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                }
            });
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stoppoint_not_set",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        }
    }

    /**
     * Handle Line Detail Click
     */
    private void handleLineDetailClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        String lineId = holder.getData("lineId");
        int page = holder.getData("page", 0);
        int totalPages = holder.getData("totalPages", 1);

        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.closeInventory();
            return;
        }

        // Handle control bar
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineDetail(player, lineId, page - 1);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineDetail(player, lineId, page + 1);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openLineList(player, 0, false);
                return;
            }
        }

        // Handle item click
        if (slot < 36) {
            List<String> stopIds = line.getOrderedStopIds();
            int index = page * 36 + slot;
            if (index >= 0 && index < stopIds.size()) {
                String stopId = stopIds.get(index);
                Stop stop = plugin.getStopManager().getStop(stopId);

                if (stop == null)
                    return;

                if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
                    // Right click: remove stop from line
                    if (plugin.getLineManager().delStopFromLine(lineId, stopId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "stop_id", stopId), "line_id", lineId)));
                        // Refresh
                        plugin.getGuiManager().openLineDetail(player, lineId, page);
                    }
                } else if (!isRightClick && player.hasPermission("railway.tp") && stop.getStopPointLocation() != null) {
                    // Left click: teleport (if has perm)
                    player.closeInventory();
                    SchedulerUtil.teleportEntity(player, stop.getStopPointLocation()).thenAccept(success -> {
                        if (success) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                        }
                    });
                }
            }
        }
    }
}
