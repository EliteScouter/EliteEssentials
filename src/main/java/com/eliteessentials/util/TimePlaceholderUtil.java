package com.eliteessentials.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Replaces the {time} and {date} placeholders in chat format strings.
 *
 * Patterns are supplied by the server owner through config, so they are parsed defensively:
 * an unusable pattern falls back to a sane default and is logged once rather than throwing
 * on every chat message. Parsed formatters and zones are cached because chat is a hot path
 * and {@link DateTimeFormatter#ofPattern} is comparatively expensive.
 *
 * Both placeholders are resolved from a single timestamp per call, so a message sent at
 * midnight can never show tomorrow's date next to yesterday's time.
 */
public final class TimePlaceholderUtil {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    /** Used when the configured time pattern is missing or invalid. */
    public static final String DEFAULT_TIME_PATTERN = "HH:mm";
    /** Used when the configured date pattern is missing or invalid. */
    public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";

    private static final DateTimeFormatter FALLBACK_TIME = DateTimeFormatter.ofPattern(DEFAULT_TIME_PATTERN);
    private static final DateTimeFormatter FALLBACK_DATE = DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN);

    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ZoneId> ZONE_CACHE = new ConcurrentHashMap<>();

    private TimePlaceholderUtil() {
    }

    /**
     * Replace {time} and {date} in a format string.
     *
     * Call this on the format template before the player's message is substituted in,
     * otherwise a player could inject a working placeholder by typing it in chat.
     *
     * @param format      Format string that may contain {time} and/or {date}
     * @param timePattern {@link DateTimeFormatter} pattern for {time} (null/blank uses {@value #DEFAULT_TIME_PATTERN})
     * @param datePattern {@link DateTimeFormatter} pattern for {date} (null/blank uses {@value #DEFAULT_DATE_PATTERN})
     * @param timeZone    Zone ID such as "America/New_York" (null/blank uses the server's zone)
     * @return The format string with both placeholders replaced
     */
    public static String replace(String format, String timePattern, String datePattern, String timeZone) {
        if (format == null || format.isEmpty()) {
            return format;
        }

        boolean hasTime = format.contains("{time}");
        boolean hasDate = format.contains("{date}");
        if (!hasTime && !hasDate) {
            return format;
        }

        ZonedDateTime now = ZonedDateTime.now(resolveZone(timeZone));

        if (hasTime) {
            format = format.replace("{time}", resolveFormatter(timePattern, FALLBACK_TIME).format(now));
        }
        if (hasDate) {
            format = format.replace("{date}", resolveFormatter(datePattern, FALLBACK_DATE).format(now));
        }

        return format;
    }

    /**
     * Resolve a formatter for the given pattern, caching the result.
     * An invalid pattern is logged once and permanently mapped to the fallback.
     */
    private static DateTimeFormatter resolveFormatter(String pattern, DateTimeFormatter fallback) {
        if (pattern == null || pattern.isBlank()) {
            return fallback;
        }
        return FORMATTER_CACHE.computeIfAbsent(pattern, p -> {
            try {
                return DateTimeFormatter.ofPattern(p);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid date/time pattern in chatFormat config: '" + p
                        + "' (" + e.getMessage() + "). Using default instead.");
                return fallback;
            }
        });
    }

    /**
     * Resolve a zone for the given ID, caching the result.
     * An unknown zone is logged once and permanently mapped to the server default.
     */
    private static ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.systemDefault();
        }
        return ZONE_CACHE.computeIfAbsent(timeZone, tz -> {
            try {
                return ZoneId.of(tz);
            } catch (Exception e) {
                logger.warning("Unknown timeZone in chatFormat config: '" + tz
                        + "'. Using the server's time zone (" + ZoneId.systemDefault() + ") instead.");
                return ZoneId.systemDefault();
            }
        });
    }
}
