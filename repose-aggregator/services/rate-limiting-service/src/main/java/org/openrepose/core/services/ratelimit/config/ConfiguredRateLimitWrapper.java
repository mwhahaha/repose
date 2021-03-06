package org.openrepose.core.services.ratelimit.config;

import java.util.List;
import java.util.regex.Pattern;

public class ConfiguredRateLimitWrapper extends ConfiguredRatelimit {

    private static final int PRIME = 31;
    private static final int ZERO = 0;
    private final ConfiguredRatelimit configuredRateLimit;
    private final Pattern regexPattern;

    public ConfiguredRateLimitWrapper(ConfiguredRatelimit configuredRateLimit) {
        this.configuredRateLimit = configuredRateLimit;
        this.regexPattern = Pattern.compile(configuredRateLimit.getUriRegex());
    }

    public Pattern getRegexPattern() {
        return regexPattern;
    }

    @Override
    public String getId() {
        return configuredRateLimit.getId();
    }

    @Override
    public void setId(String value) {
        configuredRateLimit.setId(value);
    }

    @Override
    public String getUri() {
        return configuredRateLimit.getUri();
    }

    @Override
    public void setUri(String value) {
        configuredRateLimit.setUri(value);
    }

    @Override
    public String getUriRegex() {
        return configuredRateLimit.getUriRegex();
    }

    @Override
    public void setUriRegex(String value) {
        configuredRateLimit.setUriRegex(value);
    }

    @Override
    public List<HttpMethod> getHttpMethods() {
        return configuredRateLimit.getHttpMethods();
    }

    @Override
    public int getValue() {
        return configuredRateLimit.getValue();
    }

    @Override
    public void setValue(int value) {
        configuredRateLimit.setValue(value);
    }

    @Override
    public TimeUnit getUnit() {
        return configuredRateLimit.getUnit();
    }

    @Override
    public void setUnit(TimeUnit value) {
        configuredRateLimit.setUnit(value);
    }

    @Override
    public List<String> getQueryParamNames() {
        return configuredRateLimit.getQueryParamNames();
    }

    @Override
    public String toString() {
        return configuredRateLimit.toString();
    }

    @Override
    public boolean equals(Object o) {
        // TODO Does this method need to be updated?
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConfiguredRateLimitWrapper that = (ConfiguredRateLimitWrapper) o;

        if (configuredRateLimit != null ? !configuredRateLimit.equals(that.configuredRateLimit) : that.configuredRateLimit != null) {
            return false;
        }

        if (regexPattern != null ? !regexPattern.equals(that.regexPattern) : that.regexPattern != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = configuredRateLimit != null ? configuredRateLimit.hashCode() : ZERO;
        result = PRIME * result + (regexPattern != null ? regexPattern.hashCode() : ZERO);
        return result;
    }
}
