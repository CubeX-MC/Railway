package org.cubexmc.metro.integration

import java.math.BigDecimal
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.cubexmc.economy.EconomyAccount
import org.cubexmc.economy.VaultEconomy
import org.cubexmc.metro.Metro
import java.util.UUID

class VaultIntegration(private val plugin: Metro) {

    private var economy: Economy? = null

    /**
     * The shared `cubex-economy` wrapper around the same provider, used for the
     * one thing raw Vault cannot express: routing a fare to `economy.account`
     * when the line has no owner to pay.
     */
    private var routed: VaultEconomy? = null

    private var account: EconomyAccount = EconomyAccount.None

    private val enabled: Boolean = setupEconomy()

    private fun setupEconomy(): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java) ?: return false
        val provider = rsp.provider
        economy = provider
        routed = provider?.let { VaultEconomy(it, plugin.log()) }
        return provider != null
    }

    /**
     * Sets where fares go when the line has no owner. Resolving a name can hit
     * the profile cache, so callers do this on enable and on reload - never per
     * fare (the resolved target lives in [VaultEconomy]).
     */
    fun useAccount(spec: EconomyAccount) {
        account = spec
        routed?.useAccount(spec)
    }

    /** Human readable description of the current fare destination, for logs. */
    fun accountDescription(): String = routed?.accountDescription() ?: account.label()

    /**
     * Withdraws the fare and routes it to `economy.account`.
     *
     * Used for lines with no owner: before this the money was simply destroyed,
     * which is what an empty `economy.account` still does - only now it is a
     * configured choice instead of the only option.
     *
     * The returned flag is the *withdrawal* result. A failed deposit into the
     * server account is not rolled back (the player already rode the train);
     * `VaultEconomy` logs it so the owner can reconcile.
     */
    fun chargeToAccount(player: Player, amount: Double): Boolean =
        routed?.charge(player, BigDecimal.valueOf(amount))?.success() ?: false

    fun isEnabled(): Boolean = enabled

    fun getEconomy(): Economy? = economy

    fun has(player: Player, amount: Double): Boolean {
        val economy = this.economy
        if (!enabled || economy == null) return false
        return economy.has(player, amount)
    }

    fun withdraw(player: Player, amount: Double): Boolean {
        val economy = this.economy
        if (!enabled || economy == null) return false
        return economy.withdrawPlayer(player, amount).transactionSuccess()
    }

    fun deposit(uuid: UUID?, amount: Double): Boolean {
        val economy = this.economy
        if (!enabled || uuid == null || economy == null) return false
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return economy.depositPlayer(offlinePlayer, amount).transactionSuccess()
    }

    fun format(amount: Double): String {
        val economy = this.economy
        if (!enabled || economy == null) return amount.toString()
        return economy.format(amount)
    }
}
