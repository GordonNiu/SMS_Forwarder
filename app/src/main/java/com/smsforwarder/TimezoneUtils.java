package com.smsforwarder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class TimezoneUtils {
    private static final String[] CANONICAL_PREFIXES = {
            "Africa/", "America/", "Antarctica/", "Asia/",
            "Atlantic/", "Australia/", "Europe/", "Indian/", "Pacific/"
    };

    private static final String[] POPULAR_IDS = {
            "Asia/Shanghai",
            "Asia/Hong_Kong",
            "Asia/Tokyo",
            "Asia/Singapore",
            "Europe/London",
            "America/New_York",
            "America/Chicago",
            "America/Los_Angeles",
            "Australia/Sydney",
            "UTC"
    };

    private static final Map<String, String> SEARCH_ALIASES = new HashMap<>();

    static {
        SEARCH_ALIASES.put("Asia/Shanghai", "beijing china cst 北京 上海 中国");
        SEARCH_ALIASES.put("Asia/Hong_Kong", "hk 香港");
        SEARCH_ALIASES.put("Asia/Taipei", "taiwan 台北 台湾");
        SEARCH_ALIASES.put("Asia/Tokyo", "japan jst 东京 日本");
        SEARCH_ALIASES.put("Asia/Seoul", "korea kst 首尔 韩国");
        SEARCH_ALIASES.put("Asia/Singapore", "sg sgt 新加坡");
        SEARCH_ALIASES.put("Asia/Dubai", "uae 迪拜");
        SEARCH_ALIASES.put("Asia/Kolkata", "india ist 印度 孟买 新德里");
        SEARCH_ALIASES.put("Europe/London", "uk gmt bst 伦敦 英国");
        SEARCH_ALIASES.put("Europe/Paris", "france cet 巴黎 法国");
        SEARCH_ALIASES.put("Europe/Berlin", "germany cet 柏林 德国");
        SEARCH_ALIASES.put("America/New_York", "est edt eastern 纽约 美东");
        SEARCH_ALIASES.put("America/Chicago", "cst cdt central 芝加哥 美中");
        SEARCH_ALIASES.put("America/Denver", "mst mdt mountain 丹佛");
        SEARCH_ALIASES.put("America/Los_Angeles", "pst pdt pacific san francisco 洛杉矶 旧金山 美西");
        SEARCH_ALIASES.put("America/Toronto", "canada 多伦多 加拿大");
        SEARCH_ALIASES.put("America/Vancouver", "canada 温哥华 加拿大");
        SEARCH_ALIASES.put("Australia/Sydney", "aest aedt 悉尼 澳大利亚");
        SEARCH_ALIASES.put("Pacific/Auckland", "nz 奥克兰 新西兰");
        SEARCH_ALIASES.put("UTC", "gmt universal 协调世界时 格林威治");
    }

    private TimezoneUtils() {
    }

    public static List<TimezoneOption> getAvailableOptions(String currentTimezoneId) {
        long now = System.currentTimeMillis();
        Set<String> selectedIds = new LinkedHashSet<>();
        for (String id : TimeZone.getAvailableIDs()) {
            if (isCanonicalId(id)) {
                selectedIds.add(id);
            }
        }
        selectedIds.add("UTC");
        if (currentTimezoneId != null && !currentTimezoneId.isEmpty()) {
            selectedIds.add(currentTimezoneId);
        }

        List<TimezoneOption> options = new ArrayList<>(selectedIds.size());
        for (String id : selectedIds) {
            options.add(createOption(id, now));
        }

        options.sort(Comparator
                .comparingInt((TimezoneOption option) -> option.offsetMinutes)
                .thenComparing(option -> option.locationName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(option -> option.id, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    public static List<TimezoneOption> getPopularOptions() {
        long now = System.currentTimeMillis();
        List<TimezoneOption> options = new ArrayList<>(POPULAR_IDS.length);
        for (String id : POPULAR_IDS) {
            options.add(createOption(id, now));
        }
        return options;
    }

    public static TimezoneOption createOption(String timezoneId, long timestampMillis) {
        TimeZone timezone = TimeZone.getTimeZone(timezoneId);
        int offsetMinutes = timezone.getOffset(timestampMillis) / (60 * 1000);
        String offsetText = formatOffset(offsetMinutes);
        String locationName = extractLocationName(timezoneId);
        String currentTimeText = formatCurrentTime(timezone, timestampMillis);
        String label = String.format(
                Locale.ROOT, "%s — %s (%s)", locationName, offsetText, timezoneId);
        String searchText = buildSearchText(timezoneId, locationName, offsetText, offsetMinutes);
        return new TimezoneOption(
                timezoneId, locationName, offsetText, offsetMinutes,
                currentTimeText, label, searchText);
    }

    public static String formatTimezone(String timezoneId, long timestampMillis) {
        return createOption(timezoneId, timestampMillis).label;
    }

    public static String getTimeLabel(String timezoneId) {
        return extractLocationName(timezoneId) + " Time";
    }

    private static boolean isCanonicalId(String id) {
        if ("UTC".equals(id)) {
            return true;
        }
        for (String prefix : CANONICAL_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String extractLocationName(String timezoneId) {
        String[] timezoneParts = timezoneId.split("/");
        return timezoneParts[timezoneParts.length - 1].replace('_', ' ');
    }

    private static String formatOffset(int offsetMinutes) {
        char sign = offsetMinutes < 0 ? '-' : '+';
        int absoluteMinutes = Math.abs(offsetMinutes);
        int hours = absoluteMinutes / 60;
        int minutes = absoluteMinutes % 60;
        return String.format(Locale.ROOT, "UTC%c%02d:%02d", sign, hours, minutes);
    }

    private static String formatCurrentTime(TimeZone timezone, long timestampMillis) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.ROOT);
        format.setTimeZone(timezone);
        return format.format(new Date(timestampMillis));
    }

    private static String buildSearchText(
            String id, String locationName, String offsetText, int offsetMinutes) {
        char sign = offsetMinutes < 0 ? '-' : '+';
        int absMinutes = Math.abs(offsetMinutes);
        int hours = absMinutes / 60;
        int minutes = absMinutes % 60;
        StringBuilder builder = new StringBuilder();
        builder.append(locationName).append(' ')
                .append(id).append(' ')
                .append(id.replace('_', ' ')).append(' ')
                .append(offsetText).append(' ')
                .append("utc").append(sign).append(hours).append(' ')
                .append("gmt").append(sign).append(hours).append(' ')
                .append(sign).append(hours);
        if (minutes > 0) {
            builder.append(String.format(Locale.ROOT, ":%02d", minutes));
        }
        String aliases = SEARCH_ALIASES.get(id);
        if (aliases != null) {
            builder.append(' ').append(aliases);
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    public static final class TimezoneOption {
        public final String id;
        public final String locationName;
        public final String offsetText;
        public final int offsetMinutes;
        public final String currentTimeText;
        public final String label;
        public final String searchText;

        private TimezoneOption(
                String id,
                String locationName,
                String offsetText,
                int offsetMinutes,
                String currentTimeText,
                String label,
                String searchText) {
            this.id = id;
            this.locationName = locationName;
            this.offsetText = offsetText;
            this.offsetMinutes = offsetMinutes;
            this.currentTimeText = currentTimeText;
            this.label = label;
            this.searchText = searchText;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
