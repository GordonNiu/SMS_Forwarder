package com.smsforwarder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class TimezoneUtils {
    private TimezoneUtils() {
    }

    public static List<TimezoneOption> getAvailableOptions() {
        List<String> timezoneIds = new ArrayList<>();
        Collections.addAll(timezoneIds, TimeZone.getAvailableIDs());
        Collections.sort(timezoneIds);

        long now = System.currentTimeMillis();
        List<TimezoneOption> options = new ArrayList<>(timezoneIds.size());
        for (String timezoneId : timezoneIds) {
            options.add(new TimezoneOption(timezoneId, formatTimezone(timezoneId, now)));
        }
        return options;
    }

    public static String formatTimezone(String timezoneId, long timestampMillis) {
        TimeZone timezone = TimeZone.getTimeZone(timezoneId);
        int offsetMinutes = timezone.getOffset(timestampMillis) / (60 * 1000);
        char sign = offsetMinutes < 0 ? '-' : '+';
        int absoluteMinutes = Math.abs(offsetMinutes);
        int hours = absoluteMinutes / 60;
        int minutes = absoluteMinutes % 60;
        String[] timezoneParts = timezoneId.split("/");
        String locationName = timezoneParts[timezoneParts.length - 1].replace('_', ' ');
        return String.format(Locale.ROOT, "%s — UTC%c%02d:%02d (%s)",
            locationName, sign, hours, minutes, timezoneId);
    }

    public static String getTimeLabel(String timezoneId) {
        String[] timezoneParts = timezoneId.split("/");
        String locationName = timezoneParts[timezoneParts.length - 1].replace('_', ' ');
        return locationName + " Time";
    }

    public static final class TimezoneOption {
        public final String id;
        public final String label;

        private TimezoneOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
