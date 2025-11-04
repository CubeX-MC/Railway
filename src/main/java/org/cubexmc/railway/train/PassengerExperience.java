package org.cubexmc.railway.train;

import java.util.List;

import org.bukkit.entity.Player;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.util.SoundUtil;
import org.cubexmc.railway.util.TextUtil;
import org.cubexmc.railway.util.AdventureUtil;

/**
 * Handles all passenger-facing experience (sounds, titles, actionbar, scoreboard)
 */
public class PassengerExperience {
    
    private final Railway plugin;
    private final TrainInstance train;
    
    public PassengerExperience(Railway plugin, TrainInstance train) {
        this.plugin = plugin;
        this.train = train;
    }
    
    /**
     * Play departure sound and show departure titles to all passengers
     */
    public void onDeparture(Stop currentStop, Stop nextStop, Stop terminalStop) {
        List<Player> passengers = train.getPassengers();
        if (passengers.isEmpty()) {
            return;
        }
        
        Line line = train.getLine();
        
        // Play departure sound
        if (plugin.isDepartureSoundEnabled()) {
            for (Player passenger : passengers) {
                SoundUtil.playNoteSequence(plugin, passenger, 
                    plugin.getDepartureNotes(), plugin.getDepartureInitialDelay());
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
            
            for (Player passenger : passengers) {
                AdventureUtil.sendTitle(passenger, title, subtitle, fadeIn, stay, fadeOut);
                AdventureUtil.sendActionBar(passenger, actionbar);
            }
        }
        
        // Update scoreboard for traveling
        for (Player passenger : passengers) {
            ScoreboardManager.updateTravelingScoreboard(passenger, line, 
                nextStop != null ? nextStop.getId() : null);
        }
    }
    
    /**
     * Play arrival sound and show arrival titles to all passengers
     */
    public void onArrival(Stop currentStop, Stop nextStop, Stop terminalStop, boolean isTerminal) {
        List<Player> passengers = train.getPassengers();
        if (passengers.isEmpty()) {
            return;
        }
        
        Line line = train.getLine();
        
        // Play arrival sound
        if (plugin.isArrivalSoundEnabled()) {
            for (Player passenger : passengers) {
                SoundUtil.playNoteSequence(plugin, passenger, 
                    plugin.getArrivalNotes(), plugin.getArrivalInitialDelay());
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
            
            for (Player passenger : passengers) {
                AdventureUtil.sendTitle(passenger, title, subtitle, fadeIn, stay, fadeOut);
                ScoreboardManager.updateTerminalScoreboard(passenger, line, currentStop.getId());
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
            
            for (Player passenger : passengers) {
                AdventureUtil.sendTitle(passenger, title, subtitle, fadeIn, stay, fadeOut);
                ScoreboardManager.updateEnteringStopScoreboard(passenger, line, currentStop.getId());
            }
        }
    }
    
    /**
     * Show waiting titles to all passengers (called periodically)
     */
    public void onWaiting(Stop currentStop, Stop nextStop, Stop terminalStop, int secondsRemaining) {
        List<Player> passengers = train.getPassengers();
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
        
        for (Player passenger : passengers) {
            AdventureUtil.sendTitle(passenger, title, subtitle, 0, 25, 5);
            AdventureUtil.sendActionBar(passenger, actionbar);
        }
    }
    
    /**
     * Update journey display for traveling passengers (called periodically)
     */
    public void updateJourneyDisplay(Stop nextStop, Stop terminalStop) {
        List<Player> passengers = train.getPassengers();
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
        
        for (Player passenger : passengers) {
            AdventureUtil.sendTitle(passenger, title, subtitle, fadeIn, stay, fadeOut);
            AdventureUtil.sendActionBar(passenger, actionbar);
        }
    }
}

