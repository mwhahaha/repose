package org.openrepose.core.services.ratelimit.cache.util;

import java.util.concurrent.TimeUnit;

/**
 * @author jhopper
 */
public final class TimeUnitConverter {
    private TimeUnitConverter() {}

    public static TimeUnit fromSchemaTypeToConcurrent(org.openrepose.core.services.ratelimit.config.TimeUnit unit) {
        switch (unit) {
            case SECOND:
                return TimeUnit.SECONDS;
            case MINUTE:
                return TimeUnit.MINUTES;
            case HOUR:
                return TimeUnit.HOURS;
            case DAY:
                return TimeUnit.DAYS;
            default:
                throw new IllegalArgumentException("Time unit: " + unit.toString() + " is not supported");
        }
    }
}
