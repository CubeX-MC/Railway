package org.cubexmc.metro.update

import org.cubexmc.config.MigrationContext
import org.cubexmc.config.MigrationStep

/**
 * config v2 -> v3: adds `economy.account`.
 *
 * Fares from a line **without an owner** used to be withdrawn and then simply
 * destroyed. This key names the server account they are paid into instead.
 * The default is empty, which is exactly the old behaviour, so an upgraded
 * server keeps working the way it did until someone configures it - guessing an
 * account and moving real money into it is never this step's call.
 *
 * Owned lines are untouched: their fares still go to the line owner.
 */
class MetroEconomyAccountStep : MigrationStep {
    override fun fromVersion(): Int = 2

    override fun toVersion(): Int = MetroMigrations.CONFIG_VERSION

    override fun description(): String =
        "Add economy.account (where fares from unowned lines are paid in)."

    override fun migrate(context: MigrationContext) {
        if (!context.yaml().contains("economy.account")) {
            context.yaml()["economy.account"] = ""
        }
    }
}
