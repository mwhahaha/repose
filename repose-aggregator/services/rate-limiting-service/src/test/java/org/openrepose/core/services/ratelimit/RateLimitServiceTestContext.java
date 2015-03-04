package org.openrepose.core.services.ratelimit;

import org.openrepose.core.services.ratelimit.config.ConfiguredRatelimit;
import org.openrepose.core.services.ratelimit.config.HttpMethod;
import org.openrepose.core.services.ratelimit.config.QueryParam;
import org.openrepose.core.services.ratelimit.config.TimeUnit;

import java.util.List;
import java.util.Map;

public class RateLimitServiceTestContext {
    public static final String SIMPLE_URI_REGEX = "/loadbalancer/.*";
    public static final String COMPLEX_URI_REGEX = "/loadbalancer/vips/.*";
    public static final String GROUPS_URI_REGEX = "/loadbalancer/(.*)/1234";
    public static final String SIMPLE_URI = "*loadbalancer*";
    public static final String COMPLEX_URI = "*loadbalancer/vips*";
    public static final String GROUPS_URI = "*loadbalancer/vips/cap1/1234*";
    public static final String SIMPLE_ID = "12345-ABCDE";
    public static final String COMPLEX_ID = "09876-ZYXWV";

    protected ConfiguredRatelimit newLimitConfig(String limitId, String uri, String uriRegex, List<HttpMethod> methods, Map<String, String> queryParams) {
        final ConfiguredRatelimit configuredRateLimit = new ConfiguredRatelimit();

        configuredRateLimit.setId(limitId);
        configuredRateLimit.setUnit(TimeUnit.HOUR);
        configuredRateLimit.setUri(uri);
        configuredRateLimit.setUriRegex(uriRegex);
        configuredRateLimit.setValue(20);
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            QueryParam qp = new QueryParam();
            qp.setKeyRegex(entry.getKey());
            qp.setValueRegex(entry.getValue());

            configuredRateLimit.getQueryParam().add(qp);
        }
        for (HttpMethod m : methods) {
            configuredRateLimit.getHttpMethods().add(m);
        }

        return configuredRateLimit;
    }
}
