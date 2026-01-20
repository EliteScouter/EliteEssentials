package com.eliteessentials.util;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for formatting messages with Minecraft-style color codes and clickable links.
 * Supports color codes (&0-f), hex colors (&#RRGGBB), formatting codes (&l, &o, &r), 
 * and automatic URL detection.
 * 
 * Hex color examples:
 * - &#FF0000 = Red
 * - &#00FF00 = Green  
 * - &#0000FF = Blue
 * - Per-character gradient: &#FF0000H&#FF5500e&#FFAA00l&#FFFF00l&#AAFF00o
 */
public class MessageFormatter {
    
    private static final Map<Character, Color> COLOR_MAP = new HashMap<>();
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!'()*+,;=%]+", Pattern.CASE_INSENSITIVE);
    private static final Color DEFAULT_COLOR = Color.WHITE;
    
    static {
        COLOR_MAP.put('0', Color.BLACK);
        COLOR_MAP.put('1', new Color(0x0000AA)); // Dark Blue
        COLOR_MAP.put('2', new Color(0x00AA00)); // Dark Green
        COLOR_MAP.put('3', new Color(0x00AAAA)); // Dark Aqua
        COLOR_MAP.put('4', new Color(0xAA0000)); // Dark Red
        COLOR_MAP.put('5', new Color(0xAA00AA)); // Dark Purple
        COLOR_MAP.put('6', new Color(0xFFAA00)); // Gold
        COLOR_MAP.put('7', new Color(0xAAAAAA)); // Gray
        COLOR_MAP.put('8', new Color(0x555555)); // Dark Gray
        COLOR_MAP.put('9', new Color(0x5555FF)); // Blue
        COLOR_MAP.put('a', new Color(0x55FF55)); // Green
        COLOR_MAP.put('b', new Color(0x55FFFF)); // Aqua
        COLOR_MAP.put('c', new Color(0xFF5555)); // Red
        COLOR_MAP.put('d', new Color(0xFF55FF)); // Light Purple
        COLOR_MAP.put('e', new Color(0xFFFF55)); // Yellow
        COLOR_MAP.put('f', Color.WHITE);
    }
    
    /**
     * Formats text with color codes and converts URLs to clickable links.
     * Supports multi-line text with \n separator.
     * 
     * @param text Text with color codes (& prefix)
     * @return Formatted Message object (never null)
     */
    @Nonnull
    public static Message format(String text) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }
        
        String[] lines = text.split("\n");
        if (lines.length == 1) {
            return processLine(text);
        }
        
        List<Message> allMessages = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                allMessages.add(Message.raw("\n"));
            }
            allMessages.add(processLine(lines[i]));
        }
        
        return Message.join(allMessages.toArray(new Message[0]));
    }
    
    /**
     * Convenience method to format text with a fallback color.
     * If the text contains color codes, they will be processed.
     * If not, the fallback color will be applied to the entire message.
     * 
     * @param text Text with optional color codes (& prefix)
     * @param fallbackColor Hex color to use if no color codes present (e.g., "#FF5555")
     * @return Formatted Message object (never null)
     */
    @Nonnull
    public static Message formatWithFallback(String text, @Nonnull String fallbackColor) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }
        
        // Check if text contains color codes
        if (text.contains("&") || text.contains("§")) {
            return format(text);
        }
        
        // No color codes, apply fallback color
        return Message.raw(text).color(fallbackColor);
    }

    
    @Nonnull
    private static Message processLine(String line) {
        if (line == null || line.isEmpty()) {
            return Message.raw("");
        }
        
        // Find all URLs first (to skip color codes inside them)
        List<int[]> urlRanges = findUrlRanges(line);
        
        // Build list of color segments
        List<ColorSegment> segments = new ArrayList<>();
        
        Color currentColor = DEFAULT_COLOR;
        boolean bold = false;
        boolean italic = false;
        int textStart = 0;
        
        int i = 0;
        while (i < line.length()) {
            // Check for color code prefix (& or §)
            if ((line.charAt(i) == '&' || line.charAt(i) == '§') && !isInsideUrl(i, urlRanges)) {
                // Check for hex color: &#RRGGBB (8 chars total)
                if (i + 7 < line.length() && line.charAt(i + 1) == '#') {
                    String hexPart = line.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        // Save text before this color code
                        if (i > textStart) {
                            String text = line.substring(textStart, i);
                            if (!text.isEmpty()) {
                                segments.add(new ColorSegment(text, currentColor, bold, italic));
                            }
                        }
                        // Parse hex color
                        currentColor = new Color(Integer.parseInt(hexPart, 16));
                        i += 8; // Skip &#RRGGBB
                        textStart = i;
                        continue;
                    }
                }
                // Check for simple color code: &X (2 chars)
                else if (i + 1 < line.length()) {
                    char code = Character.toLowerCase(line.charAt(i + 1));
                    if (COLOR_MAP.containsKey(code) || code == 'r' || code == 'l' || code == 'o' || code == 'k' || code == 'm' || code == 'n') {
                        // Save text before this color code
                        if (i > textStart) {
                            String text = line.substring(textStart, i);
                            if (!text.isEmpty()) {
                                segments.add(new ColorSegment(text, currentColor, bold, italic));
                            }
                        }
                        // Apply formatting
                        if (COLOR_MAP.containsKey(code)) {
                            currentColor = COLOR_MAP.get(code);
                        } else if (code == 'r') {
                            currentColor = DEFAULT_COLOR;
                            bold = false;
                            italic = false;
                        } else if (code == 'l') {
                            bold = true;
                        } else if (code == 'o') {
                            italic = true;
                        }
                        // k, m, n are ignored (obfuscated, strikethrough, underline - not supported)
                        i += 2; // Skip &X
                        textStart = i;
                        continue;
                    }
                }
            }
            i++;
        }
        
        // Add remaining text
        if (textStart < line.length()) {
            String text = line.substring(textStart);
            if (!text.isEmpty()) {
                segments.add(new ColorSegment(text, currentColor, bold, italic));
            }
        }
        
        // Convert segments to messages, handling URLs
        List<Message> messages = new ArrayList<>();
        int linePos = 0;
        for (ColorSegment seg : segments) {
            // Find where this segment starts in the original line (accounting for removed color codes)
            messages.addAll(processSegmentWithUrls(seg.text, linePos, urlRanges, seg.color, seg.bold, seg.italic, line));
            linePos += seg.text.length();
        }
        
        return messages.isEmpty() ? Message.raw("") : Message.join(messages.toArray(new Message[0]));
    }
    
    private static List<int[]> findUrlRanges(String line) {
        List<int[]> urlRanges = new ArrayList<>();
        Matcher urlMatcher = URL_PATTERN.matcher(line);
        while (urlMatcher.find()) {
            int start = urlMatcher.start();
            int end = urlMatcher.end();
            // Remove trailing punctuation
            while (end > start && isPunctuation(line.charAt(end - 1))) {
                end--;
            }
            urlRanges.add(new int[]{start, end});
        }
        return urlRanges;
    }
    
    private static boolean isInsideUrl(int position, List<int[]> urlRanges) {
        for (int[] range : urlRanges) {
            if (position >= range[0] && position < range[1]) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isPunctuation(char c) {
        return c == ')' || c == '.' || c == ',' || c == ';' || c == '!' || c == '?';
    }
    
    private static List<Message> processSegmentWithUrls(String segment, int segmentStart, List<int[]> urlRanges,
                                                        Color color, boolean bold, boolean italic, String fullLine) {
        List<Message> messages = new ArrayList<>();
        
        // Simple case: no URLs in segment
        boolean hasUrl = false;
        for (int[] range : urlRanges) {
            if (range[0] < segmentStart + segment.length() && range[1] > segmentStart) {
                hasUrl = true;
                break;
            }
        }
        
        if (!hasUrl) {
            if (!segment.isEmpty()) {
                messages.add(buildMessage(segment, color, bold, italic, null));
            }
            return messages;
        }
        
        // Complex case: handle URLs
        int lastIndex = 0;
        for (int[] urlRange : urlRanges) {
            int relativeStart = urlRange[0] - segmentStart;
            int relativeEnd = urlRange[1] - segmentStart;
            
            if (relativeEnd <= 0 || relativeStart >= segment.length()) {
                continue;
            }
            
            int urlStartInSegment = Math.max(0, relativeStart);
            int urlEndInSegment = Math.min(segment.length(), relativeEnd);
            
            if (urlStartInSegment > lastIndex) {
                String textBeforeUrl = segment.substring(lastIndex, urlStartInSegment);
                if (!textBeforeUrl.isEmpty()) {
                    messages.add(buildMessage(textBeforeUrl, color, bold, italic, null));
                }
            }
            
            String url = segment.substring(urlStartInSegment, urlEndInSegment);
            if (!url.isEmpty()) {
                String fullUrl = fullLine.substring(urlRange[0], urlRange[1]);
                String linkTarget = fullUrl.startsWith("www.") ? "https://" + fullUrl : fullUrl;
                messages.add(buildMessage(url, color, bold, italic, linkTarget));
            }
            
            lastIndex = urlEndInSegment;
        }
        
        if (lastIndex < segment.length()) {
            String remainingText = segment.substring(lastIndex);
            if (!remainingText.isEmpty()) {
                messages.add(buildMessage(remainingText, color, bold, italic, null));
            }
        }
        
        return messages;
    }
    
    @Nonnull
    private static Message buildMessage(String text, @Nonnull Color color, boolean bold, boolean italic, String linkUrl) {
        if (text.isEmpty()) {
            return Message.raw("");
        }
        
        Message msg = Message.raw(text).color(color);
        if (bold) {
            msg = msg.bold(true);
        }
        if (italic) {
            msg = msg.italic(true);
        }
        if (linkUrl != null) {
            msg = msg.link(linkUrl);
        }
        return msg;
    }
    
    /**
     * Helper class to store a text segment with its formatting.
     */
    private static class ColorSegment {
        final String text;
        final Color color;
        final boolean bold;
        final boolean italic;
        
        ColorSegment(String text, Color color, boolean bold, boolean italic) {
            this.text = text;
            this.color = color;
            this.bold = bold;
            this.italic = italic;
        }
    }
}
