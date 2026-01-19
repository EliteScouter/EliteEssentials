package com.eliteessentials.util;

import com.hypixel.hytale.server.core.Message;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for formatting messages with Minecraft-style color codes and clickable links.
 * Supports color codes (&0-f), formatting codes (&l, &o, &r), and automatic URL detection.
 */
public class MessageFormatter {
    
    private static final Map<Character, Color> COLOR_MAP = new HashMap<>();
    private static final Pattern COLOR_PATTERN = Pattern.compile("[&§]([0-9a-fk-or])");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);
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
     * @return Formatted Message object
     */
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
     * @return Formatted Message object
     */
    public static Message formatWithFallback(String text, String fallbackColor) {
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
    
    private static Message processLine(String line) {
        if (line == null || line.isEmpty()) {
            return Message.raw("");
        }
        
        List<Message> messages = new ArrayList<>();
        Matcher urlMatcher = URL_PATTERN.matcher(line);
        Matcher colorMatcher = COLOR_PATTERN.matcher(line);
        
        int lastIndex = 0;
        Color currentColor = DEFAULT_COLOR;
        boolean bold = false;
        boolean italic = false;
        
        // Find all URLs
        List<int[]> urlRanges = new ArrayList<>();
        while (urlMatcher.find()) {
            int start = urlMatcher.start();
            int end = urlMatcher.end();
            
            // Remove trailing punctuation
            while (end > start && isPunctuation(line.charAt(end - 1))) {
                end--;
            }
            
            urlRanges.add(new int[]{start, end});
        }
        
        // Process color codes and text
        while (colorMatcher.find()) {
            int colorStart = colorMatcher.start();
            int colorEnd = colorMatcher.end();
            
            // Skip if inside URL
            if (isInsideUrl(colorStart, urlRanges)) {
                continue;
            }
            
            // Add text before color code
            if (colorStart > lastIndex) {
                String segment = line.substring(lastIndex, colorStart);
                messages.addAll(processSegmentWithUrls(segment, lastIndex, urlRanges, currentColor, bold, italic, line));
            }
            
            // Update formatting
            char colorCode = colorMatcher.group(1).charAt(0);
            if (COLOR_MAP.containsKey(colorCode)) {
                currentColor = COLOR_MAP.get(colorCode);
            } else if (colorCode == 'r') {
                currentColor = DEFAULT_COLOR;
                bold = italic = false;
            } else if (colorCode == 'l') {
                bold = true;
            } else if (colorCode == 'o') {
                italic = true;
            }
            
            lastIndex = colorEnd;
        }
        
        // Add remaining text
        if (lastIndex < line.length()) {
            String segment = line.substring(lastIndex);
            messages.addAll(processSegmentWithUrls(segment, lastIndex, urlRanges, currentColor, bold, italic, line));
        }
        
        return messages.isEmpty() ? Message.raw("") : Message.join(messages.toArray(new Message[0]));
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
        int lastIndex = 0;
        
        for (int[] urlRange : urlRanges) {
            int relativeStart = urlRange[0] - segmentStart;
            int relativeEnd = urlRange[1] - segmentStart;
            
            // Check if URL is in this segment
            if (relativeEnd <= 0 || relativeStart >= segment.length()) {
                continue;
            }
            
            // Adjust for partial overlaps
            int urlStartInSegment = Math.max(0, relativeStart);
            int urlEndInSegment = Math.min(segment.length(), relativeEnd);
            
            // Add text before URL
            if (urlStartInSegment > lastIndex) {
                String textBeforeUrl = segment.substring(lastIndex, urlStartInSegment);
                if (!textBeforeUrl.isEmpty()) {
                    messages.add(buildMessage(textBeforeUrl, color, bold, italic, null));
                }
            }
            
            // Add URL with link
            String url = segment.substring(urlStartInSegment, urlEndInSegment);
            if (!url.isEmpty()) {
                String fullUrl = fullLine.substring(urlRange[0], urlRange[1]);
                // Add https:// prefix if URL starts with www.
                String linkTarget = fullUrl.startsWith("www.") ? "https://" + fullUrl : fullUrl;
                messages.add(buildMessage(url, color, bold, italic, linkTarget));
            }
            
            lastIndex = urlEndInSegment;
        }
        
        // Add remaining text
        if (lastIndex < segment.length()) {
            String remainingText = segment.substring(lastIndex);
            if (!remainingText.isEmpty()) {
                messages.add(buildMessage(remainingText, color, bold, italic, null));
            }
        }
        
        return messages;
    }
    
    private static Message buildMessage(String text, Color color, boolean bold, boolean italic, String linkUrl) {
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
}
