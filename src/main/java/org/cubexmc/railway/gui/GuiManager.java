package org.cubexmc.railway.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.gui.GuiHolder.GuiType;
import org.cubexmc.railway.manager.LanguageManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.util.OwnershipUtil;
import org.cubexmc.railway.util.SchedulerUtil;

/**
 * GUI Manager responsible for creating and opening various GUIs
 */
public class GuiManager {

    private final Railway plugin;

    // Items per page (4 rows x 9 columns = 36, bottom 2 rows for controls)
    private static final int ITEMS_PER_PAGE = 36;

    // Slot constants
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_FILTER = 49;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_BACK = 46;
    private static final int SLOT_PAGE_INFO = 47;

    public GuiManager(Railway plugin) {
        this.plugin = plugin;
    }

    private String msg(String key) {
        return plugin.getLanguageManager().getMessage(key);
    }

    private String msg(String key, String... replacements) {
        Map<String, Object> args = LanguageManager.args();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            LanguageManager.put(args, replacements[i], replacements[i + 1]);
        }
        return plugin.getLanguageManager().getMessage(key, args);
    }

    /**
     * Open Main Menu
     */
    public void openMainMenu(Player player) {
        GuiHolder holder = new GuiHolder(GuiType.MAIN_MENU);
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.DARK_GRAY + ChatColor.stripColor(msg("gui.main_menu.title")));
        holder.setInventory(inv);

        // Line Management Button
        inv.setItem(11, new ItemBuilder(Material.MINECART)
                .name(msg("gui.main_menu.line_manage"))
                .lore(msg("gui.main_menu.line_manage_lore1"),
                        msg("gui.main_menu.line_manage_lore2"))
                .build());

        // Stop Management Button
        inv.setItem(15, new ItemBuilder(Material.RAIL)
                .name(msg("gui.main_menu.stop_manage"))
                .lore(msg("gui.main_menu.stop_manage_lore1"),
                        msg("gui.main_menu.stop_manage_lore2"))
                .build());

        // Fill border
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }

    /**
     * Open Line List
     * 
     * @param player       Player
     * @param page         Page (0-indexed)
     * @param showOnlyMine Whether to show only owned lines
     */
    public void openLineList(Player player, int page, boolean showOnlyMine) {
        GuiHolder holder = new GuiHolder(GuiType.LINE_LIST);
        holder.setData("page", page);
        holder.setData("showOnlyMine", showOnlyMine);

        // Get line list
        List<Line> allLines = new ArrayList<>(plugin.getLineManager().getAllLines());

        // Filter
        List<Line> filteredLines;
        if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
            filteredLines = allLines.stream()
                    .filter(line -> OwnershipUtil.canManageLine(player, line))
                    .collect(Collectors.toList());
        } else {
            filteredLines = allLines;
        }

        // Group by name
        Map<String, List<Line>> groupedLines = filteredLines.stream()
                .collect(Collectors.groupingBy(Line::getName));

        // Sort names
        List<String> sortedNames = new ArrayList<>(groupedLines.keySet());
        sortedNames.sort(String::compareTo);

        holder.setData("lineNames", sortedNames);
        holder.setData("groupedLines", groupedLines);

        // Pagination logic
        int totalPages = Math.max(1, (int) Math.ceil((double) sortedNames.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        String titleKey = showOnlyMine ? "gui.line_list.title_mine" : "gui.line_list.title_all";
        String title = ChatColor.translateAlternateColorCodes('&', msg(titleKey));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // Fill line items
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, sortedNames.size());

        for (int i = start; i < end; i++) {
            String name = sortedNames.get(i);
            List<Line> variants = groupedLines.get(name);
            Line representative = variants.get(0);
            int slot = i - start;

            List<String> lore = new ArrayList<>();

            if (variants.size() > 1) {
                lore.add(msg("gui.common.variants", "count", String.valueOf(variants.size())));
                lore.add("");
                lore.add(msg("gui.line_list.click_view_variants"));
            } else {
                lore.add(msg("gui.common.id", "id", representative.getId()));
                lore.add(msg("gui.line_list.stop_count", "count",
                        String.valueOf(representative.getOrderedStopIds().size())));
                if (representative.getColor() != null) {
                    lore.add(msg("gui.line_list.color") + representative.getColor() + "■■■■■");
                }
                lore.add("");
                if (OwnershipUtil.canManageLine(player, representative)) {
                    lore.add(msg("gui.line_list.can_manage"));
                } else {
                    lore.add(msg("gui.line_list.view_only"));
                }
                lore.add("");
                lore.add(msg("gui.line_list.click_view"));
            }

            // Choose wool color based on line color
            Material material = getWoolByColor(representative.getColor());

            inv.setItem(slot, new ItemBuilder(material)
                    .name((representative.getColor() != null ? representative.getColor() : "&f") + name)
                    .lore(lore)
                    .build());
        }

        // Bottom control bar
        addControlBar(inv, page, totalPages, showOnlyMine);

        player.openInventory(inv);
    }

    /**
     * Open Line Variants List
     */
    public void openLineVariants(Player player, String lineName, int page) {
        GuiHolder holder = new GuiHolder(GuiType.LINE_VARIANTS);
        holder.setData("page", page);
        holder.setData("lineName", lineName);

        // Get all lines with this name
        List<Line> variants = plugin.getLineManager().getAllLines().stream()
                .filter(line -> line.getName().equals(lineName))
                .sorted(Comparator.comparing(Line::getId))
                .collect(Collectors.toList());

        holder.setData("lines", variants);

        int totalPages = Math.max(1, (int) Math.ceil((double) variants.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        String title = ChatColor.translateAlternateColorCodes('&', lineName + " - " + msg("gui.common.variants_title"));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, variants.size());

        for (int i = start; i < end; i++) {
            Line line = variants.get(i);
            int slot = i - start;

            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.common.id", "id", line.getId()));
            lore.add(msg("gui.line_list.stop_count", "count", String.valueOf(line.getOrderedStopIds().size())));
            if (line.getColor() != null) {
                lore.add(msg("gui.line_list.color") + line.getColor() + "■■■■■");
            }
            lore.add("");
            if (OwnershipUtil.canManageLine(player, line)) {
                lore.add(msg("gui.line_list.can_manage"));
            } else {
                lore.add(msg("gui.line_list.view_only"));
            }
            lore.add("");
            lore.add(msg("gui.line_list.click_view"));

            Material material = getWoolByColor(line.getColor());

            inv.setItem(slot, new ItemBuilder(material)
                    .name((line.getColor() != null ? line.getColor() : "&f") + line.getId()) // Use ID as name here to
                                                                                             // distinguish
                    .lore(lore)
                    .build());
        }

        // Add back button
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(msg("gui.common.back"))
                .build());

        // Add page controls if needed
        if (totalPages > 1) {
            if (page > 0) {
                inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.prev_page")).build());
            }
            inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                    .name(msg("gui.common.page_info", "page", String.valueOf(page + 1), "total",
                            String.valueOf(totalPages)))
                    .build());
            if (page < totalPages - 1) {
                inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.next_page")).build());
            }
        }

        player.openInventory(inv);
    }

    /**
     * Open Stop List
     * 
     * @param player       Player
     * @param page         Page (0-indexed)
     * @param showOnlyMine Whether to show only owned stops
     */
    public void openStopList(Player player, int page, boolean showOnlyMine) {
        GuiHolder holder = new GuiHolder(GuiType.STOP_LIST);
        holder.setData("page", page);
        holder.setData("showOnlyMine", showOnlyMine);

        // Get stop list
        List<Stop> allStops = plugin.getStopManager().getAllStopIds().stream()
                .map(id -> plugin.getStopManager().getStop(id))
                .filter(stop -> stop != null)
                .collect(Collectors.toList());

        // Filter
        List<Stop> filteredStops;
        if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
            filteredStops = allStops.stream()
                    .filter(stop -> OwnershipUtil.canManageStop(player, stop))
                    .collect(Collectors.toList());
        } else {
            filteredStops = allStops;
        }

        // Group by name
        Map<String, List<Stop>> groupedStops = filteredStops.stream()
                .collect(Collectors.groupingBy(Stop::getName));

        // Sort names
        List<String> sortedNames = new ArrayList<>(groupedStops.keySet());
        sortedNames.sort(String::compareTo);

        holder.setData("stopNames", sortedNames);
        holder.setData("groupedStops", groupedStops);

        // Pagination
        int totalPages = Math.max(1, (int) Math.ceil((double) sortedNames.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        String titleKey = showOnlyMine ? "gui.stop_list.title_mine" : "gui.stop_list.title_all";
        String title = ChatColor.translateAlternateColorCodes('&', msg(titleKey));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // Fill stop items
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, sortedNames.size());

        for (int i = start; i < end; i++) {
            String name = sortedNames.get(i);
            List<Stop> variants = groupedStops.get(name);
            Stop representative = variants.get(0);
            int slot = i - start;

            List<String> lore = new ArrayList<>();

            if (variants.size() > 1) {
                lore.add(msg("gui.common.variants", "count", String.valueOf(variants.size())));
                lore.add("");
                lore.add(msg("gui.stop_list.click_view_variants"));
            } else {
                lore.add(msg("gui.common.id", "id", representative.getId()));

                // Show lines containing this stop
                List<String> lineNames = new ArrayList<>();
                for (Line line : plugin.getLineManager().getAllLines()) {
                    if (line.containsStop(representative.getId())) {
                        String coloredName = (line.getColor() != null ? line.getColor() : "&f") + line.getName();
                        lineNames.add(coloredName);
                    }
                }
                if (!lineNames.isEmpty()) {
                    lore.add(msg("gui.stop_list.lines") + String.join("&7, ", lineNames));
                } else {
                    lore.add(msg("gui.stop_list.no_lines"));
                }

                lore.add("");
                if (OwnershipUtil.canManageStop(player, representative)) {
                    lore.add(msg("gui.line_list.can_manage"));
                } else {
                    lore.add(msg("gui.line_list.view_only"));
                }
                lore.add("");
                if (player.hasPermission("railway.tp")) { // Adjusted permission
                    if (representative.getStopPointLocation() != null) {
                        lore.add(msg("gui.stop_list.click_tp"));
                    } else {
                        lore.add(msg("gui.stop_list.no_stoppoint"));
                    }
                }
            }

            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("&a" + name)
                    .lore(lore)
                    .build());
        }

        // Bottom control bar
        addControlBar(inv, page, totalPages, showOnlyMine);

        player.openInventory(inv);
    }

    /**
     * Open Stop Variants List
     */
    public void openStopVariants(Player player, String stopName, int page) {
        GuiHolder holder = new GuiHolder(GuiType.STOP_VARIANTS);
        holder.setData("page", page);
        holder.setData("stopName", stopName);

        // Get all stops with this name
        List<Stop> variants = plugin.getStopManager().getAllStopIds().stream()
                .map(id -> plugin.getStopManager().getStop(id))
                .filter(stop -> stop != null && stop.getName().equals(stopName))
                .sorted(Comparator.comparing(Stop::getId))
                .collect(Collectors.toList());

        holder.setData("stops", variants);

        int totalPages = Math.max(1, (int) Math.ceil((double) variants.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        String title = ChatColor.translateAlternateColorCodes('&', stopName + " - " + msg("gui.common.variants_title"));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, variants.size());

        for (int i = start; i < end; i++) {
            Stop stop = variants.get(i);
            int slot = i - start;

            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.common.id", "id", stop.getId()));

            // Show lines
            List<String> lineNames = new ArrayList<>();
            for (Line line : plugin.getLineManager().getAllLines()) {
                if (line.containsStop(stop.getId())) {
                    String coloredName = (line.getColor() != null ? line.getColor() : "&f") + line.getName();
                    lineNames.add(coloredName);
                }
            }
            if (!lineNames.isEmpty()) {
                lore.add(msg("gui.stop_list.lines") + String.join("&7, ", lineNames));
            } else {
                lore.add(msg("gui.stop_list.no_lines"));
            }

            lore.add("");
            if (OwnershipUtil.canManageStop(player, stop)) {
                lore.add(msg("gui.line_list.can_manage"));
            } else {
                lore.add(msg("gui.line_list.view_only"));
            }
            lore.add("");
            if (player.hasPermission("railway.tp")) { // Adjusted permission
                if (stop.getStopPointLocation() != null) {
                    lore.add(msg("gui.stop_list.click_tp"));
                } else {
                    lore.add(msg("gui.stop_list.no_stoppoint"));
                }
            }

            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("&a" + stop.getId()) // Use ID as name
                    .lore(lore)
                    .build());
        }

        // Add back button
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(msg("gui.common.back"))
                .build());

        // Add page controls if needed
        if (totalPages > 1) {
            if (page > 0) {
                inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.prev_page")).build());
            }
            inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                    .name(msg("gui.common.page_info", "page", String.valueOf(page + 1), "total",
                            String.valueOf(totalPages)))
                    .build());
            if (page < totalPages - 1) {
                inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.next_page")).build());
            }
        }

        player.openInventory(inv);
    }

    /**
     * Open Line Detail (Stop List)
     */
    public void openLineDetail(Player player, String lineId, int page) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.LINE_DETAIL);
        holder.setData("lineId", lineId);
        holder.setData("page", page);

        List<String> stopIds = line.getOrderedStopIds();
        int totalPages = Math.max(1, (int) Math.ceil((double) stopIds.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        String coloredName = (line.getColor() != null ? line.getColor() : "") + line.getName();
        String title = ChatColor.translateAlternateColorCodes('&',
                msg("gui.line_detail.title") + coloredName);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        boolean canManage = OwnershipUtil.canManageLine(player, line);

        // Fill stop items
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, stopIds.size());

        for (int i = start; i < end; i++) {
            String stopId = stopIds.get(i);
            Stop stop = plugin.getStopManager().getStop(stopId);
            int slot = i - start;

            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.line_detail.index", "index", String.valueOf(i + 1)));

            if (stop != null) {
                lore.add(msg("gui.common.id", "id", stop.getId()));
                if (i == 0) {
                    lore.add(msg("gui.line_detail.start_stop"));
                } else if (i == stopIds.size() - 1) {
                    lore.add(msg("gui.line_detail.end_stop"));
                }
                lore.add("");
                if (player.hasPermission("railway.tp") && stop.getStopPointLocation() != null) { // Adjusted permission
                    lore.add(msg("gui.line_detail.click_tp"));
                }
                if (canManage) {
                    lore.add(msg("gui.line_detail.click_remove"));
                }

                inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                        .name("&a" + stop.getName())
                        .lore(lore)
                        .build());
            } else {
                lore.add(msg("gui.line_detail.stop_not_exist"));
                inv.setItem(slot, new ItemBuilder(Material.BARRIER)
                        .name("&c" + stopId)
                        .lore(lore)
                        .build());
            }
        }

        // Bottom control bar (simplified)
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Previous page
        if (page > 0) {
            inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.prev_page"))
                    .build());
        }

        // Page info
        inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", String.valueOf(page + 1), "total",
                        String.valueOf(totalPages)))
                .lore(msg("gui.control.stop_count", "count", String.valueOf(stopIds.size())))
                .build());

        // Next page
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.next_page"))
                    .build());
        }

        // Back button
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_line_list"))
                .build());

        player.openInventory(inv);
    }

    /**
     * Add control bar
     */
    private void addControlBar(Inventory inv, int page, int totalPages,
            boolean showOnlyMine) {
        // Fill bottom
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Previous page
        if (page > 0) {
            inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.prev_page"))
                    .build());
        }

        // Back to main
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_main"))
                .build());

        // Page info
        inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", String.valueOf(page + 1), "total",
                        String.valueOf(totalPages)))
                .build());

        // Filter button
        String filterName = showOnlyMine ? msg("gui.control.filter_mine") : msg("gui.control.filter_all");
        String filterLore = showOnlyMine ? msg("gui.control.filter_lore_mine") : msg("gui.control.filter_lore_all");
        inv.setItem(SLOT_FILTER, new ItemBuilder(Material.HOPPER)
                .name(filterName)
                .lore(filterLore)
                .build());

        // Next page
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.next_page"))
                    .build());
        }
    }

    /**
     * Get wool material by color code
     */
    private Material getWoolByColor(String colorCode) {
        if (colorCode == null)
            return Material.WHITE_WOOL;

        // Remove & prefix
        String code = colorCode.replace("&", "").toLowerCase();

        return switch (code) {
            case "0" -> Material.BLACK_WOOL; // Black
            case "1" -> Material.BLUE_WOOL; // Blue
            case "2" -> Material.GREEN_WOOL; // Green
            case "3" -> Material.CYAN_WOOL; // Cyan
            case "4" -> Material.RED_WOOL; // Red
            case "5" -> Material.PURPLE_WOOL; // Purple
            case "6" -> Material.ORANGE_WOOL; // Orange
            case "7" -> Material.LIGHT_GRAY_WOOL; // Gray
            case "8" -> Material.GRAY_WOOL; // Dark Gray
            case "9" -> Material.LIGHT_BLUE_WOOL; // Blue
            case "a" -> Material.LIME_WOOL; // Green
            case "b" -> Material.LIGHT_BLUE_WOOL; // Cyan
            case "c" -> Material.RED_WOOL; // Red
            case "d" -> Material.PINK_WOOL; // Pink
            case "e" -> Material.YELLOW_WOOL; // Yellow
            case "f" -> Material.WHITE_WOOL; // White
            default -> Material.WHITE_WOOL;
        };
    }
}
