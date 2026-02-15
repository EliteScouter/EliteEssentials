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
 * Supports color codes (&0-f), hex colors (&#RRGGBB), formatting codes, automatic URL detection,
 * and labeled links with [display text](url) syntax.
 * 
 * IMPORTANT: Hytale chat only supports &l (bold) and &r (reset) for formatting.
 * Other formatting codes (&o italic, &m strikethrough, &n underline, &k obfuscated) 
 * are parsed but DO NOT render in Hytale's chat system.
 * 
 * Labeled link syntax:
 * - [DISCORD](https://discord.gg/invite) = Shows "DISCORD" as clickable text
 * - &b[Click Here](https://example.com) = Colored clickable text
 * - Color codes inside display text are supported: [&bClick &aHere](https://example.com)
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
    // Labeled link syntax: [display text](url)
    private static final Pattern LABELED_LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]+)\\]\\((https?://[^)]+)\\)");
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
     * Formats text with color codes, converts URLs to clickable links,
     * and supports labeled links with [text](url) syntax.
     * Supports multi-line text with \n separator.
     * 
     * @param text Text with color codes (& prefix) and optional labeled links
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
        
        // Check for labeled links [text](url) - process these first by splitting the line
        Matcher labeledMatcher = LABELED_LINK_PATTERN.matcher(line);
        if (labeledMatcher.find()) {
            return processLineWithLabeledLinks(line);
        }
        
        return processLineSimple(line);
    }
    
    /**
     * Process a line that contains labeled links like [DISCORD](https://discord.gg/xyz).
     * Splits the line around labeled links, processes each text segment for color codes
     * and bare URLs, then assembles the final message with labeled links properly linked.
     */
    @Nonnull
    private static Message processLineWithLabeledLinks(String line) {
        List<Message> messages = new ArrayList<>();
        Matcher matcher = LABELED_LINK_PATTERN.matcher(line);
        
        // Track the current color/formatting state across segments so labeled links
        // inherit the color codes that precede them
        Color currentColor = DEFAULT_COLOR;
        boolean bold = false;
        boolean italic = false;
        
        int lastEnd = 0;
        while (matcher.find()) {
            // Process text before this labeled link (may contain color codes and bare URLs)
            String before = line.substring(lastEnd, matcher.start());
            if (!before.isEmpty()) {
                // Parse color state from this segment to carry forward
                ColorState state = parseColorState(before, currentColor, bold, italic);
                currentColor = state.color;
                bold = state.bold;
                italic = state.italic;
                
                messages.add(processLineSimple(before));
            }
            
            // Build the labeled link message with current formatting state
            String displayText = matcher.group(1);
            String url = matcher.group(2);
            
            // The display text itself may contain color codes, so process it
            // but we need to apply the link to the whole thing
            messages.add(processLabeledLinkText(displayText, url, currentColor, bold, italic));
            
            lastEnd = matcher.end();
        }
        
        // Process remaining text after last labeled link
        String remaining = line.substring(lastEnd);
        if (!remaining.isEmpty()) {
            messages.add(processLineSimple(remaining));
        }
        
        return messages.isEmpty() ? Message.raw("") : Message.join(messages.toArray(new Message[0]));
    }
    
    /**
     * Process the display text of a labeled link, applying color codes within it
     * and attaching the link URL to each segment.
     */
    @Nonnull
    private static Message processLabeledLinkText(String displayText, String url, 
                                                   Color inheritedColor, boolean inheritedBold, boolean inheritedItalic) {
        // Parse color codes within the display text
        List<ColorSegment> segments = parseColorSegments(displayText, inheritedColor, inheritedBold, inheritedItalic);
        
        if (segments.isEmpty()) {
            return buildMessage(displayText, inheritedColor, inheritedBold, inheritedItalic, url);
        }
        
        // Build each segment with the link attached
        List<Message> messages = new ArrayList<>();
        for (ColorSegment seg : segments) {
            if (!seg.text.isEmpty()) {
                messages.add(buildMessage(seg.text, seg.color, seg.bold, seg.italic, url));
            }
        }
        
        return messages.isEmpty() ? Message.raw("") : Message.join(messages.toArray(new Message[0]));
    }
    
    /**
     * Parse color codes in text and return segments, without handling URLs.
     * Used for labeled link display text where we don't want URL auto-detection.
     */
    private static List<ColorSegment> parseColorSegments(String text, Color startColor, boolean startBold, boolean startItalic) {
        List<ColorSegment> segments = new ArrayList<>();
        Color currentColor = startColor;
        boolean bold = startBold;
        boolean italic = startItalic;
        int textStart = 0;
        
        int i = 0;
        while (i < text.length()) {
            if ((text.charAt(i) == '&' || text.charAt(i) == '§') && i + 1 < text.length()) {
                // Check for hex color: &#RRGGBB
                if (i + 7 < text.length() && text.charAt(i + 1) == '#') {
                    String hexPart = text.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        if (i > textStart) {
                            segments.add(new ColorSegment(text.substring(textStart, i), currentColor, bold, italic));
                        }
                        currentColor = new Color(Integer.parseInt(hexPart, 16));
                        i += 8;
                        textStart = i;
                        continue;
                    }
                }
                // Check for simple color code: &X
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (COLOR_MAP.containsKey(code) || code == 'r' || code == 'l' || code == 'o' || code == 'k' || code == 'm' || code == 'n') {
                    if (i > textStart) {
                        segments.add(new ColorSegment(text.substring(textStart, i), currentColor, bold, italic));
                    }
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
                    i += 2;
                    textStart = i;
                    continue;
                }
            }
            i++;
        }
        
        if (textStart < text.length()) {
            segments.add(new ColorSegment(text.substring(textStart), currentColor, bold, italic));
        }
        
        return segments;
    }
    
    /**
     * Parse color codes in text and return the final color/formatting state.
     * Used to carry formatting across labeled link boundaries.
     */
    private static ColorState parseColorState(String text, Color startColor, boolean startBold, boolean startItalic) {
        Color currentColor = startColor;
        boolean bold = startBold;
        boolean italic = startItalic;
        
        int i = 0;
        while (i < text.length()) {
            if ((text.charAt(i) == '&' || text.charAt(i) == '§') && i + 1 < text.length()) {
                if (i + 7 < text.length() && text.charAt(i + 1) == '#') {
                    String hexPart = text.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        currentColor = new Color(Integer.parseInt(hexPart, 16));
                        i += 8;
                        continue;
                    }
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (COLOR_MAP.containsKey(code)) {
                    currentColor = COLOR_MAP.get(code);
                    i += 2;
                    continue;
                } else if (code == 'r') {
                    currentColor = DEFAULT_COLOR;
                    bold = false;
                    italic = false;
                    i += 2;
                    continue;
                } else if (code == 'l') {
                    bold = true;
                    i += 2;
                    continue;
                } else if (code == 'o') {
                    italic = true;
                    i += 2;
                    continue;
                }
            }
            i++;
        }
        
        return new ColorState(currentColor, bold, italic);
    }
    
    /**
     * Process a line with no labeled links - handles color codes and bare URL auto-detection.
     */
    @Nonnull
    private static Message processLineSimple(String line) {
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
    
    /**
     * Helper class to track color/formatting state across labeled link boundaries.
     */
    private static class ColorState {
        final Color color;
        final boolean bold;
        final boolean italic;
        
        ColorState(Color color, boolean bold, boolean italic) {
            this.color = color;
            this.bold = bold;
            this.italic = italic;
        }
    }
    
    /**
     * Converts text with color codes to a plain string, stripping all formatting.
     * Used for translation overrides where color codes may not be supported.
     * 
     * @param text Text with color codes (& prefix)
     * @return Plain text without color codes
     */
    @Nonnull
    public static String toRawString(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // Replace labeled links [text](url) with just the display text
        text = LABELED_LINK_PATTERN.matcher(text).replaceAll("$1");
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            // Check for color code prefix (& or §)
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                // Check for hex color: &#RRGGBB (8 chars total)
                if (next == '#' && i + 7 < text.length()) {
                    String hexPart = text.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        i += 8; // Skip &#RRGGBB
                        continue;
                    }
                }
                // Check for simple color code: &X (2 chars)
                char code = Character.toLowerCase(next);
                if (COLOR_MAP.containsKey(code) || code == 'r' || code == 'l' || code == 'o' 
                        || code == 'k' || code == 'm' || code == 'n') {
                    i += 2; // Skip &X
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }
    
    /**
     * Strips color codes from text while preserving formatting codes.
     * Used when a player has format permission but not color permission.
     * 
     * @param text Text with color codes (& prefix)
     * @return Text with color codes removed but formatting preserved
     */
    @Nonnull
    public static String stripColors(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            // Check for color code prefix (& or §)
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                // Check for hex color: &#RRGGBB (8 chars total) - strip these
                if (next == '#' && i + 7 < text.length()) {
                    String hexPart = text.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        i += 8; // Skip &#RRGGBB
                        continue;
                    }
                }
                // Check for simple color code: &0-9, &a-f - strip these
                char code = Character.toLowerCase(next);
                if (COLOR_MAP.containsKey(code)) {
                    i += 2; // Skip &X (color)
                    continue;
                }
                // Preserve formatting codes: &l, &o, &r, &k, &m, &n
                if (code == 'r' || code == 'l' || code == 'o' || code == 'k' || code == 'm' || code == 'n') {
                    result.append(c);
                    result.append(next);
                    i += 2;
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }
    
    /**
     * Strips formatting codes from text while preserving color codes.
     * Used when a player has color permission but not format permission.
     * 
     * @param text Text with formatting codes (& prefix)
     * @return Text with formatting codes removed but colors preserved
     */
    @Nonnull
    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            // Check for color code prefix (& or §)
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                // Preserve hex colors: &#RRGGBB
                if (next == '#' && i + 7 < text.length()) {
                    String hexPart = text.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        result.append(text, i, i + 8);
                        i += 8;
                        continue;
                    }
                }
                char code = Character.toLowerCase(next);
                // Preserve color codes: &0-9, &a-f
                if (COLOR_MAP.containsKey(code)) {
                    result.append(c);
                    result.append(next);
                    i += 2;
                    continue;
                }
                // Strip formatting codes: &l, &o, &k, &m, &n (but keep &r as it resets colors too)
                if (code == 'l' || code == 'o' || code == 'k' || code == 'm' || code == 'n') {
                    i += 2; // Skip formatting code
                    continue;
                }
                // Keep &r (reset) as it affects colors
                if (code == 'r') {
                    result.append(c);
                    result.append(next);
                    i += 2;
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    /**
     * Strips all color and formatting codes from text, returning plain text.
     * Used for disconnect messages where color codes aren't rendered.
     * 
     * @param text Text with color codes (& or § prefix)
     * @return Plain text with all color/formatting codes removed
     */
    @Nonnull
    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                // Skip hex colors: &#RRGGBB
                if (next == '#' && i + 7 < text.length()) {
                    String hexPart = text.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        i += 8;
                        continue;
                    }
                }
                // Skip any known color/format code
                if ("0123456789abcdefklmnor".indexOf(next) >= 0) {
                    i += 2;
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }
}
