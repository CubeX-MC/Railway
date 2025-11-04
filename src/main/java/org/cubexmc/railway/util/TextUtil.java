package org.cubexmc.railway.util;

import java.util.List;

import org.cubexmc.railway.manager.LineManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;

/**
 * Text utility class for text processing
 */
public class TextUtil {
    
    /**
     * Replace placeholders in text
     * 
     * @param text Original text
     * @param line Line object, can be null
     * @param stop Stop object, can be null
     * @param lastStop Previous stop object, can be null (null at start station)
     * @param nextStop Next stop object, can be null (null at terminal)
     * @param terminalStop Terminal stop object, can be null
     * @param lineManager Line manager for transfer information, can be null
     * @return Text with placeholders replaced
     */
    public static String replacePlaceholders(String text, Line line, Stop stop, Stop lastStop, Stop nextStop, 
                                           Stop terminalStop, LineManager lineManager) {
        if (text == null) {
            return "";
        }
        
        String result = text;
        
        // Replace line-related placeholders
        if (line != null) {
            result = result.replace("{line}", line.getName());
            result = result.replace("{line_id}", line.getId());
            result = result.replace("{line_color_code}", line.getColor());
            
            String termName = line.getTerminusName();
            if (termName == null || termName.isEmpty()) {
                termName = (terminalStop != null ? terminalStop.getName() : "");
            }
            result = result.replace("{terminus_name}", termName);
            
            // Destination stop (line terminal)
            if (!line.getOrderedStopIds().isEmpty()) {
                String destStopId = line.getOrderedStopIds().get(line.getOrderedStopIds().size() - 1);
                result = result.replace("{destination_stop_id}", destStopId);
            } else {
                result = result.replace("{destination_stop_id}", "");
            }
        }
        
        // Replace stop-related placeholders
        if (stop != null) {
            result = result.replace("{stop_name}", stop.getName());
            result = result.replace("{stop_id}", stop.getId());

            TransferText stopTransfer = lineManager != null ? formatTransferableLines(stop, lineManager) : TransferText.absent();
            result = replaceTransferPlaceholder(result, "{stop_transfers}", stopTransfer);
        } else {
            result = result.replace("{stop_name}", "");
            result = result.replace("{stop_id}", "");
            result = replaceTransferPlaceholder(result, "{stop_transfers}", TransferText.absent());
        }
        
        // Replace last stop placeholders
        if (lastStop != null) {
            result = result.replace("{last_stop_name}", lastStop.getName());
            result = result.replace("{last_stop_id}", lastStop.getId());
        } else {
            result = result.replace("{last_stop_name}", "");
            result = result.replace("{last_stop_id}", "");
        }
        
        // Replace next stop placeholders
        if (nextStop != null) {
            result = result.replace("{next_stop_name}", nextStop.getName());
            result = result.replace("{next_stop_id}", nextStop.getId());

            TransferText nextTransfer = lineManager != null ? formatTransferableLines(nextStop, lineManager) : TransferText.absent();
            result = replaceTransferPlaceholder(result, "{next_stop_transfers}", nextTransfer);
        } else {
            result = result.replace("{next_stop_name}", "");
            result = result.replace("{next_stop_id}", "");
            result = replaceTransferPlaceholder(result, "{next_stop_transfers}", TransferText.absent());
        }
        
        // Replace terminal stop placeholders
        if (terminalStop != null) {
            result = result.replace("{terminal_stop_name}", terminalStop.getName());
            result = result.replace("{terminal_stop_id}", terminalStop.getId());
            result = result.replace("{destination_stop_name}", terminalStop.getName());
        } else {
            result = result.replace("{terminal_stop_name}", "");
            result = result.replace("{terminal_stop_id}", "");
            result = result.replace("{destination_stop_name}", "");
        }
        
        return result;
    }
    
    /**
     * Format stop transferable lines information
     * 
     * @param stop Stop object
     * @param lineManager Line manager
     * @return Formatted transferable lines text
     */
    private static TransferText formatTransferableLines(Stop stop, LineManager lineManager) {
        if (stop == null || lineManager == null) {
            return TransferText.absent();
        }

        List<String> transferableLineIds = stop.getTransferableLines();
        if (transferableLineIds == null || transferableLineIds.isEmpty()) {
            return TransferText.absent();
        }

        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (String lineId : transferableLineIds) {
            Line transferLine = lineManager.getLine(lineId);
            if (transferLine != null) {
                if (!first) {
                    result.append("§f, ");
                }
                String colorCode = transferLine.getColor();
                if (colorCode == null) {
                    colorCode = "";
                }

                String displayName = transferLine.getName();
                if (displayName == null) {
                    displayName = transferLine.getId();
                }

                result.append(colorCode)
                      .append(displayName);
                first = false;
            }
        }

        if (result.length() == 0) {
            return TransferText.absent();
        }

        return TransferText.present(result.toString());
    }

    private static String replaceTransferPlaceholder(String text, String placeholder, TransferText transfer) {
        if (text == null || placeholder == null || !text.contains(placeholder)) {
            return text;
        }

        if (transfer.hasTransfers()) {
            return text.replace(placeholder, transfer.text());
        }

        return removePlaceholderSegment(text, placeholder);
    }

    private static String removePlaceholderSegment(String text, String placeholder) {
        if (text == null || placeholder == null) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text);
        int idx;
        while ((idx = sb.indexOf(placeholder)) != -1) {
            int start = idx;

            while (start > 0 && Character.isWhitespace(sb.charAt(start - 1))) {
                start--;
            }

            int boundary = start;
            boolean foundBoundary = false;
            while (boundary > 0) {
                char prev = sb.charAt(boundary - 1);
                if (prev == '|' || prev == '｜') {
                    boundary--;
                    while (boundary > 0 && Character.isWhitespace(sb.charAt(boundary - 1))) {
                        boundary--;
                    }
                    start = boundary;
                    foundBoundary = true;
                    break;
                }
                if (prev == '\n' || prev == '\r') {
                    start = boundary;
                    foundBoundary = true;
                    break;
                }
                boundary--;
            }

            if (!foundBoundary) {
                int lastSpace = -1;
                for (int i = start - 1; i >= 0; i--) {
                    char c = sb.charAt(i);
                    if (Character.isWhitespace(c)) {
                        lastSpace = i;
                    }
                    if (c == '\n' || c == '\r') {
                        lastSpace = i + 1;
                        break;
                    }
                }
                if (lastSpace >= 0) {
                    start = lastSpace;
                    while (start > 0 && Character.isWhitespace(sb.charAt(start - 1))) {
                        start--;
                    }
                } else {
                    start = 0;
                }
            }

            int end = idx + placeholder.length();
            while (end < sb.length() && Character.isWhitespace(sb.charAt(end))) {
                end++;
            }

            sb.delete(start, end);
        }

        return sb.toString();
    }

    private static final class TransferText {
        private final boolean hasTransfers;
        private final String text;

        private TransferText(boolean hasTransfers, String text) {
            this.hasTransfers = hasTransfers;
            this.text = text != null ? text : "";
        }

        static TransferText present(String text) {
            return new TransferText(true, text);
        }

        static TransferText absent() {
            return new TransferText(false, "");
        }

        boolean hasTransfers() {
            return hasTransfers;
        }

        String text() {
            return text;
        }
    }
    
}

