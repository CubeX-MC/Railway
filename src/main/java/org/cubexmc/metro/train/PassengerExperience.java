package org.cubexmc.metro.train;

import java.util.Collections;
import java.util.List;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.SoundUtil;
import org.cubexmc.metro.util.TextUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Handles all passenger-facing experience (sounds, titles, actionbar,
 * scoreboard)
 */
public class PassengerExperience {

    private final Metro plugin;
    private final TrainInstance train;

    public PassengerExperience(Metro plugin, TrainInstance train) {
        this.plugin = plugin;
        this.train = train;
    }

    /**
     * Play departure sound and show departure titles to all passengers
     */
    public void onDeparture(Stop currentStop, Stop nextStop, Stop terminalStop) {
        List<org.bukkit.entity.HumanEntity> passengers = train.getPassengers();
        if (passengers.isEmpty()) {
            return;
        }

        Line line = train.getLine();

        // Play departure sound
        if (plugin.isDepartureSoundEnabled()) {
            for (org.bukkit.entity.HumanEntity passenger : passengers) {
                SoundUtil.playNoteSequence(plugin, (org.bukkit.entity.Player) passenger,
                        Collections.singletonList(plugin.getDepartureNotes()), plugin.getDepartureInitialDelay());
            }
        }

        // Show departure title
        if (plugin.isDepartureTitleEnabled()) {
            String title = plugin.getDepartureTitle();
            String subtitle = plugin.getDepartureSubtitle();
            String actionbar = plugin.getDepartureActionbar();

            title = TextUtil.replacePlaceholders(title, line, currentStop, null, nextStop,
                    terminalStop, plugin.getLineManager());
            subtitle = TextUtil.replacePlaceholders(subtitle, line, currentStop, null, nextStop,
                    terminalStop, plugin.getLineManager());
            actionbar = TextUtil.replacePlaceholders(actionbar, line, currentStop, null, nextStop,
                    terminalStop, plugin.getLineManager());

            int fadeIn = plugin.getDepartureFadeIn();
            int stay = plugin.getDepartureStay();
            int fadeOut = plugin.getDepartureFadeOut();

            for (org.bukkit.entity.HumanEntity passenger : passengers) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) passenger;
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                if (actionbar != null && !actionbar.isEmpty()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(actionbar));
                }
            }
        }

        // Update scoreboard for traveling
        for (org.bukkit.entity.HumanEntity passenger : passengers) {
            ScoreboardManager sm = plugin.getScoreboardManager();
            sm.updateTravelingScoreboard((org.bukkit.entity.Player) passenger, line,
                    nextStop != null ? nextStop.getId() : null);
        }
    }

    /**
     * Play arrival sound and show arrival titles to all passengers
     */
    public void onArrival(Stop currentStop, Stop nextStop, Stop terminalStop, boolean isTerminal) {
        List<org.bukkit.entity.HumanEntity> passengers = train.getPassengers();
        if (passengers.isEmpty()) {
            return;
        }

        Line line = train.getLine();

        // Play arrival sound
        if (plugin.isArrivalSoundEnabled()) {
            for (org.bukkit.entity.HumanEntity passenger : passengers) {
                SoundUtil.playNoteSequence(plugin, (org.bukkit.entity.Player) passenger,
                        Collections.singletonList(plugin.getArrivalNotes()), plugin.getArrivalInitialDelay());
            }
        }

        // Show arrival or terminal title
        if (isTerminal && plugin.isTerminalStopTitleEnabled()) {
            String title = plugin.getTerminalStopTitle();
            String subtitle = plugin.getTerminalStopSubtitle();

            title = TextUtil.replacePlaceholders(title, line, currentStop, null, null,
                    terminalStop, plugin.getLineManager());
            subtitle = TextUtil.replacePlaceholders(subtitle, line, currentStop, null, null,
                    terminalStop, plugin.getLineManager());

            int fadeIn = plugin.getTerminalStopFadeIn();
            int stay = plugin.getTerminalStopStay();
            int fadeOut = plugin.getTerminalStopFadeOut();

            for (org.bukkit.entity.HumanEntity passenger : passengers) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) passenger;
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                plugin.getScoreboardManager().updateTerminalScoreboard(player, line,
                        currentStop.getId());
            }
        } else if (plugin.isArriveStopTitleEnabled()) {
            String title = plugin.getArriveStopTitle();
            String subtitle = plugin.getArriveStopSubtitle();

            title = TextUtil.replacePlaceholders(title, line, currentStop, null, nextStop,
                    terminalStop, plugin.getLineManager());
            subtitle = TextUtil.replacePlaceholders(subtitle, line, currentStop, null, nextStop,
                    terminalStop, plugin.getLineManager());

            int fadeIn = plugin.getArriveStopFadeIn();
            int stay = plugin.getArriveStopStay();
            int fadeOut = plugin.getArriveStopFadeOut();

            for (org.bukkit.entity.HumanEntity passenger : passengers) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) passenger;
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                plugin.getScoreboardManager().updateEnteringStopScoreboard(player, line,
                        currentStop.getId());
            }
        }
    }

    /**
     * Show waiting titles to all passengers (called periodically)
     */
    public void onWaiting(Stop currentStop, Stop nextStop, Stop terminalStop, int secondsRemaining) {
        List<org.bukkit.entity.HumanEntity> passengers = train.getPassengers();
        if (passengers.isEmpty() || !plugin.isWaitingTitleEnabled()) {
            return;
        }

        Line line = train.getLine();

        String title = plugin.getWaitingTitle();
        String subtitle = plugin.getWaitingSubtitle();
        String actionbar = plugin.getWaitingActionbar();

        // Replace {countdown} placeholder
        title = title.replace("{countdown}", String.valueOf(secondsRemaining));
        subtitle = subtitle.replace("{countdown}", String.valueOf(secondsRemaining));
        actionbar = actionbar.replace("{countdown}", String.valueOf(secondsRemaining));

        title = TextUtil.replacePlaceholders(title, line, currentStop, null, nextStop,
                terminalStop, plugin.getLineManager());
        subtitle = TextUtil.replacePlaceholders(subtitle, line, currentStop, null, nextStop,
                terminalStop, plugin.getLineManager());
        actionbar = TextUtil.replacePlaceholders(actionbar, line, currentStop, null, nextStop,
                terminalStop, plugin.getLineManager());

        for (org.bukkit.entity.HumanEntity passenger : passengers) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) passenger;
            player.sendTitle(title, subtitle, 0, 25, 5);
            if (actionbar != null && !actionbar.isEmpty()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(actionbar));
            }
        }
    }

    /**
     * Update journey display for traveling passengers (called periodically)
     */
    public void updateJourneyDisplay(Stop nextStop, Stop terminalStop) {
        List<org.bukkit.entity.HumanEntity> passengers = train.getPassengers();
        if (passengers.isEmpty() || !plugin.isDepartureTitleEnabled()) {
            return;
        }

        Line line = train.getLine();

        String title = plugin.getDepartureTitle();
        String subtitle = plugin.getDepartureSubtitle();
        String actionbar = plugin.getDepartureActionbar();

        title = TextUtil.replacePlaceholders(title, line, null, null, nextStop,
                terminalStop, plugin.getLineManager());
        subtitle = TextUtil.replacePlaceholders(subtitle, line, null, null, nextStop,
                terminalStop, plugin.getLineManager());
        actionbar = TextUtil.replacePlaceholders(actionbar, line, null, null, nextStop,
                terminalStop, plugin.getLineManager());

        int fadeIn = plugin.getDepartureFadeIn();
        int stay = plugin.getDepartureStay();
        int fadeOut = plugin.getDepartureFadeOut();

        for (org.bukkit.entity.HumanEntity passenger : passengers) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) passenger;
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            if (actionbar != null && !actionbar.isEmpty()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(actionbar));
            }
        }
    }
}
