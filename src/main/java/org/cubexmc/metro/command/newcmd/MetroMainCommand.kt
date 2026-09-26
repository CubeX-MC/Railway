package org.cubexmc.metro.command.newcmd

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.util.OwnershipUtil
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission

class MetroMainCommand(
    private val plugin: Metro,
) {

    @Command("rw|railway|rail")
    @CommandDescription("Railway Main Command")
    fun root(sender: CommandSender) {
        help(sender)
    }

    @Command("rw|railway|rail help")
    @CommandDescription("Show Metro Help Menu")
    fun help(sender: CommandSender) {
        val lang = plugin.languageManager
        sender.sendMessage(lang.getMessage("command.help_header"))
        sender.sendMessage(lang.getMessage("command.help_gui"))
        sender.sendMessage(lang.getMessage("command.help_reload"))
        sender.sendMessage(lang.getMessage("command.help_line"))
        sender.sendMessage(lang.getMessage("command.help_stop"))
        sender.sendMessage(lang.getMessage("command.help_portal"))
    }

    @Command("rw|railway|rail gui")
    @CommandDescription("Open the Metro GUI")
    @Permission("railway.gui")
    fun gui(player: Player) {
        plugin.guiManager.openMainMenu(player)
    }

    @Command("rw|railway|rail reload")
    @CommandDescription("Reload Metro configuration")
    @Permission("railway.admin")
    fun reload(sender: CommandSender) {
        if (sender is Player && !OwnershipUtil.hasAdminBypass(sender)) {
            sender.sendMessage(plugin.languageManager.getMessage("plugin.no_permission"))
            return
        }

        val report = plugin.reloadRailway()
        if (!report.ok()) {
            val stage = report.failures().first().stage()
            sender.sendMessage("§cRailway reload aborted at stage '$stage'; see the console for details.")
            return
        }

        sender.sendMessage(plugin.languageManager.getMessage("plugin.reload"))
    }
}
