package org.cubexmc.railway.listener;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.manager.LanguageManager;
import org.cubexmc.railway.manager.LineManager;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.manager.SelectionManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;

import org.cubexmc.railway.util.AdventureUtil;

public class PlayerInteractListener implements Listener {

    private final Railway plugin;
    private final LanguageManager language;

    public PlayerInteractListener(Railway plugin) {
        this.plugin = plugin;
        this.language = plugin.getLanguageManager();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();
        Material selectionTool = plugin.getSelectionTool();
        // Admin selection with configured tool (default golden hoe)
        if (player.hasPermission("railway.admin") && selectionTool != null
                && player.getInventory().getItemInMainHand().getType() == selectionTool && clickedBlock != null) {
            SelectionManager sel = plugin.getSelectionManager();
            if (action == Action.LEFT_CLICK_BLOCK) {
                sel.setCorner1(player.getUniqueId(), clickedBlock.getLocation());
                player.sendMessage(language.getMessage("selection.corner1_set",
                        LanguageManager.put(LanguageManager.args(),
                                "location", clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ())));
                event.setCancelled(true);
                return;
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                sel.setCorner2(player.getUniqueId(), clickedBlock.getLocation());
                player.sendMessage(language.getMessage("selection.corner2_set",
                        LanguageManager.put(LanguageManager.args(),
                                "location", clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ())));
                event.setCancelled(true);
                return;
            }
        }
        if (action != Action.RIGHT_CLICK_BLOCK || clickedBlock == null) return;
        if (!player.hasPermission("railway.use")) return;
        if (!clickedBlock.getType().name().contains("RAIL")) return;

        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStopContainingLocation(clickedBlock.getLocation());
        if (stop == null) return;

        LineManager lineManager = plugin.getLineManager();
        Line line = null;
        for (Line l : lineManager.getAllLines()) {
            if (l.containsStop(stop.getId())) { line = l; break; }
        }
        if (line == null) return;

        int etaSec = plugin.getLineServiceManager().estimateNextEtaSeconds(line.getId(), stop.getId());
        Map<String, Object> placeholders = LanguageManager.args();
        LanguageManager.put(placeholders, "line_name", line.getName());
        LanguageManager.put(placeholders, "eta_seconds", etaSec);
        String msg = language.getMessage("interact.next_train_actionbar", placeholders);
        AdventureUtil.sendActionBar(player, msg);
        event.setCancelled(true);
    }
}


