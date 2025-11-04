package org.cubexmc.railway.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.manager.LanguageManager;
import org.cubexmc.railway.manager.LineManager;
import org.cubexmc.railway.manager.SelectionManager;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.service.LineService;
import org.cubexmc.railway.service.LineServiceManager;
import org.cubexmc.railway.train.TrainInstance;
import org.cubexmc.railway.util.LocationUtil;
import org.cubexmc.railway.util.OwnershipUtil;

public class RailCommand implements CommandExecutor, TabCompleter {

    private final Railway plugin;
    private final LanguageManager language;

    public RailCommand(Railway plugin) {
        this.plugin = plugin;
        this.language = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMainHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!OwnershipUtil.hasAdminBypass(sender)) {
                    sender.sendMessage(language.getMessage("plugin.no_permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getLineManager().reload();
                plugin.getStopManager().reload();
                plugin.getLineServiceManager().rebuildFromLines();
                language.loadLanguages();
                sender.sendMessage(language.getMessage("plugin.reload"));
                return true;
            case "line":
                return handleLine(sender, args);
            case "stop":
                return handleStop(sender, args);
            default:
                sender.sendMessage(language.getMessage("command.unknown"));
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(Arrays.asList("reload", "line", "stop"), args[0]);

    if (args[0].equalsIgnoreCase("line")) {
        if (args.length == 2) return partial(Arrays.asList(
            "create","delete","list","info","status","stops","control",
                    "add-stop","remove-stop",
                    "set-name","set-color","service","set-headway","set-dwell",
                    "set-train-cars","set-owner","add-admin","remove-admin","set-terminus","set-maxspeed"), args[1]);

            LineManager lm = plugin.getLineManager();
            List<String> lineIds = new ArrayList<>();
            for (Line l : lm.getAllLines()) lineIds.add(l.getId());
            StopManager sm = plugin.getStopManager();
            List<String> stopIds = new ArrayList<>(sm.getAllStopIds());

            String sub = normalizeLineSubcommand(args[1]);
            switch (sub) {
                case "delete":
                case "set-name":
                case "set-color":
                case "service":
                case "set-headway":
                case "set-dwell":
                case "set-train-cars":
                case "add-stop":
                case "remove-stop":
                case "set-owner":
                case "add-admin":
                case "remove-admin":
                case "set-terminus":
                case "set-maxspeed":
                case "status":
                case "info":
                    if (args.length == 3) return partial(lineIds, args[2]);
                    break;
                case "stops":
                    if (args.length == 3) return partial(lineIds, args[2]);
                    break;
                case "control":
                    if (args.length == 3) return partial(lineIds, args[2]);
                    if (args.length == 4) return partial(Arrays.asList("kinematic","leashed","reactive"), args[3]);
                    break;
            }

            if (sub.equals("add-stop")) {
                if (args.length == 4) {
                    return partial(stopIds, args[3]);
                } else if (args.length == 5) {
                    // suggest indices based on current line size
                    Line line = lm.getLine(args[2]);
                    int size = line != null ? line.getOrderedStopIds().size() : 0;
                    List<String> indices = new ArrayList<>();
                    indices.add("-1");
                    for (int i = 0; i <= size; i++) indices.add(String.valueOf(i));
                    return partial(indices, args[4]);
                }
            }

            if (sub.equals("remove-stop")) {
                if (args.length == 4) {
                    Line line = lm.getLine(args[2]);
                    List<String> lineStops = line != null ? line.getOrderedStopIds() : Collections.emptyList();
                    return partial(lineStops, "");
                }
            }

            if (sub.equals("set-color") && args.length == 4) {
                return partial(Arrays.asList("&0","&1","&2","&3","&4","&5","&6","&7","&8","&9","&a","&b","&c","&d","&e","&f","&l","&n","&o","&r"), args[3]);
            }

            if (sub.equals("service") && args.length == 4) {
                return partial(Arrays.asList("enable","disable"), args[3]);
            }

            if (sub.equals("set-headway") && args.length == 4) {
                return partial(Arrays.asList("30","60","90","120","180"), args[3]);
            }
            if (sub.equals("set-dwell") && args.length == 4) {
                return partial(Arrays.asList("60","80","100","120","160"), args[3]);
            }
            if (sub.equals("set-train-cars") && args.length == 4) {
                return partial(Arrays.asList("1","2","3","4","5","6","7","8"), args[3]);
            }

            return Collections.emptyList();
        }

    if (args[0].equalsIgnoreCase("stop")) {
        if (args.length == 2) return partial(Arrays.asList(
            "create","delete","list","info","set-name","set-corners",
                    "set-point","set-owner","add-admin","remove-admin",
                    "allow-line","deny-line","add-transfer","remove-transfer","set-title","remove-title"), args[1]);
            StopManager sm = plugin.getStopManager();
            List<String> stopIds = new ArrayList<>(sm.getAllStopIds());
            String sub = normalizeStopSubcommand(args[1]);
            switch (sub) {
                case "delete":
                case "set-name":
                case "set-corners":
                case "set-point":
                case "set-owner":
                case "add-admin":
                case "remove-admin":
                case "allow-line":
                case "deny-line":
                case "add-transfer":
                case "remove-transfer":
                case "set-title":
                case "remove-title":
                case "info":
                    if (args.length == 3) return partial(stopIds, args[2]);
                    break;
            }
            if ((sub.equals("allow-line") || sub.equals("deny-line") || sub.equals("add-transfer") || sub.equals("remove-transfer")) && args.length == 4) {
                // suggest line ids
                List<String> lineIds = new ArrayList<>();
                for (Line l : plugin.getLineManager().getAllLines()) lineIds.add(l.getId());
                return partial(lineIds, args[3]);
            }
            if ((sub.equals("set-title") || sub.equals("remove-title")) && args.length >= 4) {
                if (args.length == 4) return partial(Arrays.asList("stop_continuous","arrive_stop","terminal_stop","departure","waiting"), args[3]);
                if (sub.equals("set-title") && args.length == 5) return partial(Arrays.asList("title","subtitle","actionbar"), args[4]);
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private List<String> partial(List<String> source, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : source) {
            if (s.startsWith(prefix.toLowerCase())) {
                out.add(s);
            }
        }
        return out;
    }

    private void send(CommandSender sender, String key) {
        sender.sendMessage(language.getMessage(key));
    }

    private void send(CommandSender sender, String key, Map<String, Object> values) {
        sender.sendMessage(language.getMessage(key, values));
    }

    private Map<String, Object> args(Object... pairs) {
        Map<String, Object> map = LanguageManager.args();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            LanguageManager.put(map, String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private UUID parseUuid(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // try resolve by name
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (offline != null && offline.getUniqueId() != null) {
            return offline.getUniqueId();
        }
        return null;
    }

    private String resolvePlayerName(UUID uuid) {
        if (uuid == null) {
            return language.getMessage("ownership.server");
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline != null && offline.getName() != null) {
            return offline.getName();
        }
        return uuid.toString();
    }

    private String formatAdminNames(Set<UUID> adminIds, UUID ownerId) {
        Set<String> names = new LinkedHashSet<>();
        if (adminIds != null) {
            for (UUID adminId : adminIds) {
                if (adminId == null || (ownerId != null && ownerId.equals(adminId))) {
                    continue;
                }
                names.add(resolvePlayerName(adminId));
            }
        }
        if (names.isEmpty()) {
            return language.getMessage("ownership.none");
        }
        return String.join(", ", names);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return language.getMessage("stop.info_not_set");
        }
        return String.format("%s (%d, %d, %d)",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    private String formatStopPoint(Stop stop) {
        if (stop == null) {
            return language.getMessage("stop.info_not_set");
        }
        Location location = stop.getStopPointLocation();
        if (location == null || location.getWorld() == null) {
            return language.getMessage("stop.info_not_set");
        }
    return String.format(Locale.US, "%s (%d, %d, %d) yaw=%.1f",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                stop.getLaunchYaw());
    }

    private String formatLineList(Set<String> lineIds) {
        if (lineIds == null || lineIds.isEmpty()) {
            return language.getMessage("ownership.none");
        }
        List<String> parts = new ArrayList<>();
        for (String lineId : lineIds) {
            if (lineId == null || lineId.isEmpty()) {
                continue;
            }
            Line line = plugin.getLineManager().getLine(lineId);
            if (line != null) {
                parts.add(line.getName() + " (" + lineId + ")");
            } else {
                parts.add(lineId);
            }
        }
        if (parts.isEmpty()) {
            return language.getMessage("ownership.none");
        }
        return String.join(", ", parts);
    }

    private void sendLinePermissionDenied(CommandSender sender, Line line) {
        String ownerName = resolvePlayerName(line.getOwner());
        String adminNames = formatAdminNames(line.getAdmins(), line.getOwner());
        send(sender, "line.permission_manage", args(
                "line_id", line.getId(),
                "owner", ownerName,
                "admins", adminNames));
    }

    private void sendStopPermissionDenied(CommandSender sender, Stop stop) {
        String ownerName = resolvePlayerName(stop.getOwner());
        String adminNames = formatAdminNames(stop.getAdmins(), stop.getOwner());
        send(sender, "stop.permission_manage", args(
                "stop_id", stop.getId(),
                "owner", ownerName,
                "admins", adminNames));
    }

    private void sendStopLinkDenied(CommandSender sender, Line line, Stop stop) {
        String ownerName = resolvePlayerName(stop.getOwner());
        send(sender, "stop.permission_link", args(
                "stop_id", stop.getId(),
                "owner", ownerName,
                "line_id", line.getId()));
    }
    
    private String normalizeLineSubcommand(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase().replace('_', '-');
    }

    private boolean showLineStatus(CommandSender sender, String lineId) {
        LineServiceManager manager = plugin.getLineServiceManager();
        if (lineId == null || lineId.isEmpty()) {
            send(sender, "status.header");
            int totalTrains = 0;
            for (String id : plugin.getLineManager().getAllLineIds()) {
                Line line = plugin.getLineManager().getLine(id);
                if (line == null) continue;
                LineService service = manager.getService(id);
                if (service == null || !line.isServiceEnabled()) continue;
                List<TrainInstance> trains = service.getActiveTrains();
                totalTrains += trains.size();
                String color = ChatColor.translateAlternateColorCodes('&', line.getColor());
                send(sender, "status.line_status", args(
                        "color", color,
                        "line_name", line.getName(),
                        "line_id", id,
                        "train_count", trains.size()));
            }
            send(sender, "status.total_trains", args("count", totalTrains));
            return true;
        }

        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            send(sender, "line.not_found", args("line_id", lineId));
            return true;
        }

        LineService service = manager.getService(lineId);
        String color = ChatColor.translateAlternateColorCodes('&', line.getColor());
        send(sender, "status.line_detail_header", args("color", color, "line_name", line.getName()));
        send(sender, "status.line_id", args("line_id", lineId));
        if (line.isServiceEnabled()) {
            send(sender, "status.service_enabled");
        } else {
            send(sender, "status.service_disabled");
        }

        if (service != null && line.isServiceEnabled()) {
            List<TrainInstance> trains = service.getActiveTrains();
            send(sender, "status.active_trains", args("count", trains.size()));
            send(sender, "status.headway", args("headway", service.getHeadwaySeconds()));

            int idx = 1;
            for (TrainInstance train : trains) {
                String stateKey = train.isMoving() ? "status.train_moving" : "status.train_stopped";
                String state = language.getMessage(stateKey);
                int passengers = train.getPassengers().size();
                send(sender, "status.train_info", args(
                        "number", idx,
                        "state", state,
                        "passengers", passengers));
                idx++;
            }
        }
        return true;
    }

    private String normalizeStopSubcommand(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase().replace('_', '-');
    }

    private void sendMainHelp(CommandSender sender) {
        send(sender, "command.help_header");
        send(sender, "command.help_line");
        send(sender, "command.help_stop");
        send(sender, "command.help_reload");
    }
    
    private void sendLineHelp(CommandSender sender) {
        send(sender, "line.help_header");
        send(sender, "line.help_create");
        send(sender, "line.help_delete");
        send(sender, "line.help_list");
    send(sender, "line.help_info");
        send(sender, "line.help_status");
        send(sender, "line.help_setname");
        send(sender, "line.help_setcolor");
        send(sender, "line.help_addstop");
        send(sender, "line.help_delstop");
        send(sender, "line.help_service");
        send(sender, "line.help_set_headway");
        send(sender, "line.help_set_dwell");
        send(sender, "line.help_set_train_cars");
        send(sender, "line.help_set_terminus");
        send(sender, "line.help_set_maxspeed");
        send(sender, "line.help_set_owner");
        send(sender, "line.help_add_admin");
        send(sender, "line.help_remove_admin");
    }
    
    private void sendStopHelp(CommandSender sender) {
        send(sender, "stop.help_header");
        send(sender, "stop.help_create");
        send(sender, "stop.help_delete");
        send(sender, "stop.help_list");
    send(sender, "stop.help_info");
        send(sender, "stop.help_setname");
        send(sender, "stop.help_setcorners");
        send(sender, "stop.help_setpoint");
        send(sender, "stop.help_allow_line");
        send(sender, "stop.help_deny_line");
        send(sender, "stop.help_add_transfer");
        send(sender, "stop.help_remove_transfer");
        send(sender, "stop.help_set_title");
        send(sender, "stop.help_remove_title");
        send(sender, "stop.help_set_owner");
        send(sender, "stop.help_add_admin");
        send(sender, "stop.help_remove_admin");
    }

    private boolean handleLine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendLineHelp(sender);
            return true;
        }

        LineManager lm = plugin.getLineManager();
        StopManager sm = plugin.getStopManager();
        LineServiceManager serviceManager = plugin.getLineServiceManager();

        String sub = normalizeLineSubcommand(args[1]);
        switch (sub) {
            case "stops": {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /rail line stops <lineId>");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                List<String> ordered = line.getOrderedStopIds();
                sender.sendMessage(ChatColor.AQUA + "Stops for line " + ChatColor.WHITE + line.getName() + ChatColor.GRAY + " (" + lineId + "):");
                if (ordered == null || ordered.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "  (no stops configured)");
                    return true;
                }
                int i = 1;
                for (String sid : ordered) {
                    Stop s = sm.getStop(sid);
                    String display = s != null ? s.getName() : sid;
                    boolean hasPoint = s != null && s.getStopPointLocation() != null;
                    sender.sendMessage(ChatColor.YELLOW + "  " + i + ". " + ChatColor.GREEN + display + ChatColor.DARK_GRAY + " [" + sid + "]" + (hasPoint ? "" : ChatColor.RED + " *no point"));
                    i++;
                }
                return true;
            }

            case "control": {
                if (args.length < 4) {
                    send(sender, "line.usage_control");
                    return true;
                }
                String lineId = args[2];
                String mode = args[3];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!org.cubexmc.railway.util.OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                String normalized = mode.toUpperCase(Locale.ROOT);
                if (!normalized.equals("KINEMATIC") && !normalized.equals("LEASHED") && !normalized.equals("REACTIVE")) {
                    send(sender, "line.control_invalid_mode", args("mode", mode));
                    return true;
                }
                boolean ok = lm.setLineControlMode(lineId, normalized);
                if (ok) {
                    send(sender, "line.control_set_success", args("line_id", lineId, "mode", normalized));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }
            case "create": {
                if (!OwnershipUtil.canCreateLine(sender)) {
                    send(sender, "plugin.no_permission");
                    return true;
                }
                if (args.length < 4) {
                    send(sender, "line.usage_create");
                    return true;
                }

                String lineId = args[2];
                String lineName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean created = lm.createLine(lineId, lineName);
                if (created && sender instanceof Player player) {
                    lm.setLineOwner(lineId, player.getUniqueId());
                    lm.addLineAdmin(lineId, player.getUniqueId());
                }
                send(sender, created ? "line.create_success" : "line.create_exists", args("line_id", lineId));
                return true;
            }

            case "delete": {
                if (args.length < 3) {
                    send(sender, "line.usage_delete");
                    return true;
                }

                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }

                boolean removed = lm.deleteLine(lineId);
                if (removed) {
                    serviceManager.stopService(lineId);
                }
                send(sender, removed ? "line.delete_success" : "line.delete_fail", args("line_id", lineId));
                return true;
            }

            case "list": {
                List<Line> lines = lm.getAllLines();
                if (lines.isEmpty()) {
                    send(sender, "line.list_empty");
                    return true;
                }

                send(sender, "line.list_header");
                for (Line line : lines) {
                    String status = language.getMessage(line.isServiceEnabled()
                            ? "line.list_status_enabled" : "line.list_status_disabled");
                    String color = ChatColor.translateAlternateColorCodes('&', line.getColor());
                    send(sender, "line.list_item", args(
                            "status", status,
                            "color", color,
                            "line_name", line.getName(),
                            "line_id", line.getId(),
                            "stop_count", line.getOrderedStopIds().size()));
                }
                return true;
            }

            case "info": {
                if (args.length < 3) {
                    send(sender, "line.usage_info");
                    return true;
                }

                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }

                String color = ChatColor.translateAlternateColorCodes('&', line.getColor());
                String terminus = line.getTerminusName();
                if (terminus == null || terminus.isEmpty()) {
                    terminus = language.getMessage("line.info_none");
                }

                double maxSpeedValue = line.getMaxSpeed();
                String maxSpeed = maxSpeedValue < 0
                        ? language.getMessage("line.maxspeed_unlimited")
                        : String.format(Locale.US, "%.2f", maxSpeedValue);

                send(sender, "line.info_header", args("color", color, "line_name", line.getName()));
                send(sender, "line.info_id", args("line_id", line.getId()));
                send(sender, "line.info_name", args("line_name", line.getName()));
                send(sender, "line.info_color", args("color", color));
                send(sender, "line.info_terminus", args("terminus", terminus));
                send(sender, "line.info_max_speed", args("max_speed", maxSpeed));
                send(sender, "line.info_owner", args("owner", resolvePlayerName(line.getOwner())));
                send(sender, "line.info_admins", args("admins", formatAdminNames(line.getAdmins(), line.getOwner())));
                send(sender, line.isServiceEnabled() ? "line.info_service_enabled" : "line.info_service_disabled");

                List<String> orderedStops = line.getOrderedStopIds();
                if (orderedStops.isEmpty()) {
                    send(sender, "line.info_no_stops");
                } else {
                    send(sender, "line.info_stops_header");
                    int index = 1;
                    for (String stopId : orderedStops) {
                        Stop stop = sm.getStop(stopId);
                        if (stop != null) {
                            send(sender, "line.info_stops_item", args(
                                    "index", index,
                                    "stop_name", stop.getName(),
                                    "stop_id", stopId));
                        } else {
                            send(sender, "line.info_stops_item_invalid", args(
                                    "index", index,
                                    "stop_id", stopId));
                        }
                        index++;
                    }
                }
                return true;
            }

            case "status": {
                String target = args.length >= 3 ? args[2] : null;
                return showLineStatus(sender, target);
            }

            case "set-name": {
                if (args.length < 4) {
                    send(sender, "line.usage_setname");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                String newName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean ok = lm.setLineName(lineId, newName);
                if (ok) {
                    send(sender, "line.setname_success", args("line_id", lineId, "line_name", newName));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "set-color": {
                if (args.length < 4) {
                    send(sender, "line.usage_setcolor");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                boolean ok = lm.setLineColor(lineId, args[3]);
                if (ok) {
                    send(sender, "line.setcolor_success", args("line_id", lineId, "color", args[3]));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "add-stop": {
                if (args.length < 4) {
                    send(sender, "line.usage_addstop");
                    return true;
                }

                String lineId = args[2];
                String stopId = args[3];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }

                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }

                if (!OwnershipUtil.canLinkStopToLine(sender, line, stop)) {
                    sendStopLinkDenied(sender, line, stop);
                    return true;
                }

                int index = -1;
                if (args.length >= 5) {
                    try {
                        index = Integer.parseInt(args[4]);
                    } catch (NumberFormatException e) {
                        send(sender, "line.index_format");
                        return true;
                    }
                }

                boolean ok = lm.addStopToLine(lineId, stopId, index);
                send(sender, ok ? "line.addstop_success" : "line.addstop_fail",
                        args("line_id", lineId, "stop_id", stopId));
                return true;
            }

            case "remove-stop": {
                if (args.length < 4) {
                    send(sender, "line.usage_delstop");
                    return true;
                }

                String lineId = args[2];
                String stopId = args[3];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }

                boolean ok = lm.delStopFromLine(lineId, stopId);
                send(sender, ok ? "line.delstop_success" : "line.delstop_fail",
                        args("line_id", lineId, "stop_id", stopId));
                return true;
            }

            case "service": {
                if (args.length < 4) {
                    send(sender, "line.usage_service");
                    return true;
                }

                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }

                boolean enable = args[3].equalsIgnoreCase("enable");
                boolean ok = lm.setServiceEnabled(lineId, enable);
                if (ok) {
                    if (enable) {
                        serviceManager.startService(line);
                        send(sender, "line.service_enabled", args("line_id", lineId));
                    } else {
                        serviceManager.stopService(lineId);
                        send(sender, "line.service_disabled", args("line_id", lineId));
                    }
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "set-headway": {
                if (args.length < 4) {
                    send(sender, "line.usage_set_headway");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    send(sender, "line.set_headway_invalid");
                    return true;
                }
                boolean ok = lm.setHeadway(lineId, seconds);
                if (ok) {
                    LineService service = serviceManager.getService(lineId);
                    if (service != null) service.setHeadwaySeconds(seconds);
                    send(sender, "line.set_headway_success", args("line_id", lineId, "headway", seconds));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "set-dwell": {
                if (args.length < 4) {
                    send(sender, "line.usage_set_dwell");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                int ticks;
                try {
                    ticks = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    send(sender, "line.set_dwell_invalid");
                    return true;
                }
                boolean ok = lm.setDwell(lineId, ticks);
                if (ok) {
                    LineService service = serviceManager.getService(lineId);
                    if (service != null) service.setDwellTicks(ticks);
                    send(sender, "line.set_dwell_success", args("line_id", lineId, "dwell", ticks));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "set-train-cars": {
                if (args.length < 4) {
                    send(sender, "line.usage_set_train_cars");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                int cars;
                try {
                    cars = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    send(sender, "line.set_train_cars_invalid");
                    return true;
                }
                boolean ok = lm.setTrainCars(lineId, cars);
                if (ok) {
                    LineService service = serviceManager.getService(lineId);
                    if (service != null) service.setTrainCars(cars);
                    send(sender, "line.set_train_cars_success", args("line_id", lineId, "cars", cars));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "set-terminus": {
                if (args.length < 4) {
                    send(sender, "line.usage_set_terminus");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                String terminus = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean ok = lm.setLineTerminusName(lineId, terminus);
                if (ok) {
                    send(sender, "line.set_terminus_success", args("line_id", lineId, "terminus", terminus));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "set-maxspeed": {
                if (args.length < 4) {
                    send(sender, "line.usage_set_maxspeed");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                double value;
                try {
                    value = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    send(sender, "line.set_maxspeed_invalid");
                    return true;
                }
                Double maxSpeed = value >= 0 ? value : null;
                boolean ok = lm.setLineMaxSpeed(lineId, maxSpeed);
                if (ok) {
                    send(sender, "line.set_maxspeed_success",
                            args("line_id", lineId, "speed", maxSpeed == null ? language.getMessage("line.maxspeed_unlimited") : String.valueOf(value)));
                } else {
                    send(sender, "line.not_found", args("line_id", lineId));
                }
                return true;
            }

            case "set-owner": {
                if (args.length < 4) {
                    send(sender, "line.usage_set_owner");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                UUID ownerId = parseUuid(args[3]);
                if (ownerId == null) {
                    send(sender, "command.player_not_found", args("player", args[3]));
                    return true;
                }
                boolean ok = lm.setLineOwner(lineId, ownerId);
                send(sender, ok ? "line.set_owner_success" : "line.set_owner_fail",
                        args("line_id", lineId, "owner", resolvePlayerName(ownerId)));
                return true;
            }

            case "add-admin": {
                if (args.length < 4) {
                    send(sender, "line.usage_add_admin");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                UUID adminId = parseUuid(args[3]);
                if (adminId == null) {
                    send(sender, "command.player_not_found", args("player", args[3]));
                    return true;
                }
                boolean ok = lm.addLineAdmin(lineId, adminId);
                send(sender, ok ? "line.add_admin_success" : "line.add_admin_fail",
                        args("line_id", lineId, "admin", resolvePlayerName(adminId)));
                return true;
            }

            case "remove-admin": {
                if (args.length < 4) {
                    send(sender, "line.usage_remove_admin");
                    return true;
                }
                String lineId = args[2];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                if (!OwnershipUtil.canManageLine(sender, line)) {
                    sendLinePermissionDenied(sender, line);
                    return true;
                }
                UUID adminId = parseUuid(args[3]);
                if (adminId == null) {
                    send(sender, "command.player_not_found", args("player", args[3]));
                    return true;
                }
                boolean ok = lm.removeLineAdmin(lineId, adminId);
                send(sender, ok ? "line.remove_admin_success" : "line.remove_admin_fail",
                        args("line_id", lineId, "admin", resolvePlayerName(adminId)));
                return true;
            }

            default:
                sendLineHelp(sender);
                return true;
        }
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendStopHelp(sender);
            return true;
        }

        StopManager sm = plugin.getStopManager();
        LineManager lm = plugin.getLineManager();
        SelectionManager selections = plugin.getSelectionManager();

        String sub = normalizeStopSubcommand(args[1]);
        switch (sub) {
            case "create": {
                if (!OwnershipUtil.canCreateStop(sender)) {
                    send(sender, "plugin.no_permission");
                    return true;
                }
                if (args.length < 3) {
                    send(sender, "stop.usage_create");
                    return true;
                }
                String stopId = args[2];
                String name = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : stopId;
                UUID owner = sender instanceof Player player ? player.getUniqueId() : null;

                Location corner1 = null;
                Location corner2 = null;
                if (sender instanceof Player player && selections.isSelectionComplete(player.getUniqueId())) {
                    Material selectionTool = plugin.getSelectionTool();
                    if (selectionTool != null && player.getInventory().getItemInMainHand().getType() == selectionTool) {
                        corner1 = selections.getCorner1(player.getUniqueId());
                        corner2 = selections.getCorner2(player.getUniqueId());
                    }
                }

                boolean created = sm.createStop(stopId, name, corner1, corner2, owner);
                if (created && sender instanceof Player player && corner1 != null && corner2 != null) {
                    selections.clearSelection(player.getUniqueId());
                }
                send(sender, created ? "stop.create_success" : "stop.create_exists",
                        args("stop_id", stopId, "stop_name", name));
                return true;
            }

            case "delete": {
                if (args.length < 3) {
                    send(sender, "stop.usage_delete");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                boolean removed = sm.deleteStop(stopId);
                send(sender, removed ? "stop.delete_success" : "stop.delete_fail", args("stop_id", stopId));
                return true;
            }

            case "list": {
                if (sm.getAllStopIds().isEmpty()) {
                    send(sender, "stop.list_empty");
                    return true;
                }
                send(sender, "stop.list_header");
                for (String stopId : sm.getAllStopIds()) {
                    Stop stop = sm.getStop(stopId);
                    if (stop == null) continue;
                    boolean configured = stop.getCorner1() != null && stop.getCorner2() != null && stop.getStopPointLocation() != null;
                    String status = language.getMessage(configured ? "stop.list_status_configured" : "stop.list_status_missing");
                    int lineCount = stop.getLinkedLineIds() != null ? stop.getLinkedLineIds().size() : 0;
                    send(sender, "stop.list_item", args(
                            "status", status,
                            "stop_name", stop.getName(),
                            "stop_id", stopId,
                            "line_count", lineCount));
                }
                return true;
            }

            case "info": {
                if (args.length < 3) {
                    send(sender, "stop.usage_info");
                    return true;
                }

                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }

                send(sender, "stop.info_header", args("stop_name", stop.getName()));
                send(sender, "stop.info_id", args("stop_id", stop.getId()));
                send(sender, "stop.info_name", args("stop_name", stop.getName()));
                send(sender, "stop.info_corner1", args("corner1", formatLocation(stop.getCorner1())));
                send(sender, "stop.info_corner2", args("corner2", formatLocation(stop.getCorner2())));
                send(sender, "stop.info_stoppoint", args("stoppoint", formatStopPoint(stop)));
                send(sender, "stop.info_owner", args("owner", resolvePlayerName(stop.getOwner())));
                send(sender, "stop.info_admins", args("admins", formatAdminNames(stop.getAdmins(), stop.getOwner())));
                send(sender, "stop.info_linked_lines", args("lines", formatLineList(stop.getLinkedLineIds())));

                List<String> transfers = stop.getTransferableLines();
                if (transfers == null || transfers.isEmpty()) {
                    send(sender, "stop.info_no_transfers");
                } else {
                    send(sender, "stop.info_transfers_header");
                    for (String lineId : transfers) {
                        Line line = plugin.getLineManager().getLine(lineId);
                        if (line != null) {
                            send(sender, "stop.info_transfer_item", args(
                                    "line_name", line.getName(),
                                    "line_id", lineId));
                        } else {
                            send(sender, "stop.info_transfer_item_invalid", args("line_id", lineId));
                        }
                    }
                }
                return true;
            }

            case "set-name": {
                if (args.length < 4) {
                    send(sender, "stop.usage_setname");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                String newName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean ok = sm.setStopName(stopId, newName);
                send(sender, ok ? "stop.setname_success" : "stop.not_found",
                        args("stop_id", stopId, "stop_name", newName));
                return true;
            }

            case "set-corners": {
                if (args.length < 3) {
                    send(sender, "stop.usage_setcorners");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    send(sender, "plugin.players_only");
                    return true;
                }
                if (!selections.isSelectionComplete(player.getUniqueId())) {
                    send(player, "stop.setcorners_need_selection");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(player, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(player, stop)) {
                    sendStopPermissionDenied(player, stop);
                    return true;
                }
                boolean ok = sm.setStopCorners(stopId,
                        selections.getCorner1(player.getUniqueId()),
                        selections.getCorner2(player.getUniqueId()));
                send(player, ok ? "stop.setcorners_success" : "stop.not_found", args("stop_id", stopId));
                return true;
            }

            case "set-point": {
                if (!(sender instanceof Player player)) {
                    send(sender, "plugin.players_only");
                    return true;
                }
                Location location = player.getLocation();
                if (!LocationUtil.isRail(location)) {
                    send(player, "stop.setpoint_not_rail");
                    return true;
                }

                Stop stop = null;
                float yaw = location.getYaw();

                if (args.length >= 3) {
                    Stop candidate = sm.getStop(args[2]);
                    if (candidate != null) {
                        stop = candidate;
                        if (args.length >= 4) {
                            try {
                                yaw = Float.parseFloat(args[3]);
                            } catch (NumberFormatException e) {
                                send(player, "stop.setpoint_yaw_invalid");
                                return true;
                            }
                        }
                    } else {
                        try {
                            yaw = Float.parseFloat(args[2]);
                        } catch (NumberFormatException e) {
                            send(player, "stop.not_found", args("stop_id", args[2]));
                            return true;
                        }
                    }
                }

                if (stop == null) {
                    stop = sm.getStopContainingLocation(location);
                    if (stop == null) {
                        send(player, "stop.no_stop_found_at_location");
                        return true;
                    }
                }
                if (!OwnershipUtil.canManageStop(player, stop)) {
                    sendStopPermissionDenied(player, stop);
                    return true;
                }

                boolean ok = sm.setStopPoint(stop.getId(), location, yaw);
                send(player, ok ? "stop.setpoint_success" : "stop.not_found",
                        args("stop_id", stop.getId(), "yaw", String.format(Locale.US, "%.2f", yaw)));
                return true;
            }

            case "set-owner": {
                if (args.length < 4) {
                    send(sender, "stop.usage_set_owner");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                UUID ownerId = parseUuid(args[3]);
                if (ownerId == null) {
                    send(sender, "command.player_not_found", args("player", args[3]));
                    return true;
                }
                boolean ok = sm.setStopOwner(stopId, ownerId);
                send(sender, ok ? "stop.set_owner_success" : "stop.set_owner_fail",
                        args("stop_id", stopId, "owner", resolvePlayerName(ownerId)));
                return true;
            }

            case "add-admin": {
                if (args.length < 4) {
                    send(sender, "stop.usage_add_admin");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                UUID adminId = parseUuid(args[3]);
                if (adminId == null) {
                    send(sender, "command.player_not_found", args("player", args[3]));
                    return true;
                }
                boolean ok = sm.addStopAdmin(stopId, adminId);
                send(sender, ok ? "stop.add_admin_success" : "stop.add_admin_fail",
                        args("stop_id", stopId, "admin", resolvePlayerName(adminId)));
                return true;
            }

            case "remove-admin": {
                if (args.length < 4) {
                    send(sender, "stop.usage_remove_admin");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                UUID adminId = parseUuid(args[3]);
                if (adminId == null) {
                    send(sender, "command.player_not_found", args("player", args[3]));
                    return true;
                }
                boolean ok = sm.removeStopAdmin(stopId, adminId);
                send(sender, ok ? "stop.remove_admin_success" : "stop.remove_admin_fail",
                        args("stop_id", stopId, "admin", resolvePlayerName(adminId)));
                return true;
            }

            case "allow-line": {
                if (args.length < 4) {
                    send(sender, "stop.usage_allow_line");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                String lineId = args[3];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                boolean ok = sm.allowLineLink(stopId, lineId);
                send(sender, ok ? "stop.allow_line_success" : "stop.allow_line_fail",
                        args("stop_id", stopId, "line_id", lineId));
                return true;
            }

            case "deny-line": {
                if (args.length < 4) {
                    send(sender, "stop.usage_deny_line");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                String lineId = args[3];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                boolean ok = sm.denyLineLink(stopId, lineId);
                send(sender, ok ? "stop.deny_line_success" : "stop.deny_line_fail",
                        args("stop_id", stopId, "line_id", lineId));
                return true;
            }

            case "add-transfer": {
                if (args.length < 4) {
                    send(sender, "stop.usage_add_transfer");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                String lineId = args[3];
                Line line = lm.getLine(lineId);
                if (line == null) {
                    send(sender, "line.not_found", args("line_id", lineId));
                    return true;
                }
                boolean ok = sm.addTransferLine(stopId, lineId);
                send(sender, ok ? "stop.add_transfer_success" : "stop.add_transfer_fail",
                        args("stop_id", stopId, "line_id", lineId));
                return true;
            }

            case "remove-transfer": {
                if (args.length < 4) {
                    send(sender, "stop.usage_remove_transfer");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                String lineId = args[3];
                boolean ok = sm.removeTransferLine(stopId, lineId);
                send(sender, ok ? "stop.remove_transfer_success" : "stop.remove_transfer_fail",
                        args("stop_id", stopId, "line_id", lineId));
                return true;
            }

            case "set-title": {
                if (args.length < 6) {
                    send(sender, "stop.usage_set_title");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                Map<String, String> map = new HashMap<>();
                map.put(args[4], String.join(" ", Arrays.copyOfRange(args, 5, args.length)));
                boolean ok = sm.setStopTitle(stopId, args[3], map);
                send(sender, ok ? "stop.set_title_success" : "stop.set_title_fail",
                        args("stop_id", stopId, "title_type", args[3]));
                return true;
            }

            case "remove-title": {
                if (args.length < 4) {
                    send(sender, "stop.usage_remove_title");
                    return true;
                }
                String stopId = args[2];
                Stop stop = sm.getStop(stopId);
                if (stop == null) {
                    send(sender, "stop.not_found", args("stop_id", stopId));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(sender, stop)) {
                    sendStopPermissionDenied(sender, stop);
                    return true;
                }
                boolean ok = sm.removeStopTitle(stopId, args[3]);
                send(sender, ok ? "stop.remove_title_success" : "stop.remove_title_fail",
                        args("stop_id", stopId, "title_type", args[3]));
                return true;
            }

            default:
                sendStopHelp(sender);
                return true;
        }
    }
}


