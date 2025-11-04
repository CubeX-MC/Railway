package org.cubexmc.railway.util;

import java.util.List;

import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sound utility class for playing note sequences
 */
public class SoundUtil {
    
    /**
     * Play note sequence for specified player
     * 
     * @param plugin Plugin instance
     * @param player Target player
     * @param noteSequence Note sequence
     */
    public static void playNoteSequence(JavaPlugin plugin, Player player, List<String> noteSequence) {
        playNoteSequence(plugin, player, noteSequence, 0);
    }
    
    /**
     * Play note sequence for specified player with initial delay
     * 
     * @param plugin Plugin instance
     * @param player Target player
     * @param noteSequence Note sequence
     * @param initialDelay Initial delay for the entire sequence (ticks)
     */
    public static void playNoteSequence(JavaPlugin plugin, Player player, List<String> noteSequence, int initialDelay) {
        if (player == null || !player.isOnline() || noteSequence == null || noteSequence.isEmpty()) {
            return;
        }
        
        long totalDelay = initialDelay;
        
        for (String noteData : noteSequence) {
            String[] parts = noteData.split(",");
            if (parts.length < 4) {
                continue;
            }
            
            String type = parts[0].trim();
            
            try {
                int tone = Integer.parseInt(parts[1].trim());
                float volume = Float.parseFloat(parts[2].trim());
                String instrumentName = parts[3].trim();
                
                int delay = (parts.length > 4) ? Integer.parseInt(parts[4].trim()) : 0;
                totalDelay += delay;
                
                SchedulerUtil.regionRun(plugin, player.getLocation(), () -> {
                    if ("NOTE".equals(type)) {
                        playNote(player, tone, volume, instrumentName);
                    } else if ("CUSTOM".equals(type)) {
                        player.playSound(player.getLocation(), instrumentName, volume, getNoteFrequency(tone));
                    }
                }, totalDelay, -1);
            } catch (NumberFormatException e) {
                // Ignore malformed notes
            }
        }
    }
    
    /**
     * Play a single note for player
     */
    private static void playNote(Player player, int tone, float volume, String instrumentName) {
        Instrument instrument = getInstrument(instrumentName);
        Note note = getNote(tone);
        
        if (instrument != null && note != null) {
            player.playNote(player.getLocation(), instrument, note);
        }
    }
    
    /**
     * Get note block instrument type
     */
    private static Instrument getInstrument(String name) {
        try {
            return Instrument.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Instrument.PIANO;
        }
    }
    
    /**
     * Get note based on tone
     */
    private static Note getNote(int tone) {
        try {
            tone = Math.max(0, Math.min(tone, 24));
            return new Note(tone);
        } catch (IllegalArgumentException e) {
            return new Note(12);
        }
    }
    
    /**
     * Get frequency based on tone (for custom sounds)
     */
    private static float getNoteFrequency(int tone) {
        return (float) Math.pow(2.0, (tone - 12) / 12.0);
    }
}

