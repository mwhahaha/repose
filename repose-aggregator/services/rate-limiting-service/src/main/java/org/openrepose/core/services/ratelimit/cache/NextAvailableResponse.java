package org.openrepose.core.services.ratelimit.cache;

import org.apache.commons.lang3.tuple.Pair;
import org.openrepose.core.services.ratelimit.config.ConfiguredRatelimit;

import java.util.Date;

/**
 * @author jhopper
 */
public class NextAvailableResponse {

    private final Pair<ConfiguredRatelimit, CachedRateLimit> limitPair;

    public NextAvailableResponse(Pair<ConfiguredRatelimit, CachedRateLimit> limitPair) {
        this.limitPair = limitPair;
    }

    public Date getResetTime() {
        return new Date(limitPair.getValue().getNextExpirationTime());
    }

    public boolean hasRequestsRemaining() {
        return (limitPair == null) || (limitPair.getValue().maxAmount() - limitPair.getValue().amount() >= 0);
    }

    public int getCurrentLimitAmount() {
        return limitPair.getValue().amount();
    }

    public Pair<ConfiguredRatelimit, CachedRateLimit> getLimitPair() {
        return limitPair;
    }

    @Override
    public String toString() {
        return "NextAvailableResponse{" +
                "hasRequests=" + hasRequestsRemaining() +
                ", resetTime=" + getResetTime().getTime() +
                ", currentLimitAmount=" + getCurrentLimitAmount() +
                '}';
    }
}
