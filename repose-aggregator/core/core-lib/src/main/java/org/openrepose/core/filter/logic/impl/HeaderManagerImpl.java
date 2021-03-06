package org.openrepose.core.filter.logic.impl;

import org.openrepose.commons.utils.http.header.HeaderName;
import org.openrepose.commons.utils.servlet.http.MutableHttpServletRequest;
import org.openrepose.commons.utils.servlet.http.MutableHttpServletResponse;
import org.openrepose.core.filter.logic.HeaderApplicationLogic;
import org.openrepose.core.filter.logic.HeaderManager;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author jhopper
 */
public class HeaderManagerImpl implements HeaderManager {

    private final Map<HeaderName, Set<String>> headersToAdd;
    private final Set<HeaderName> headersToRemove;
    private boolean removeAllHeaders;

    public HeaderManagerImpl() {
        headersToAdd = new HashMap<HeaderName, Set<String>>();
        headersToRemove = new HashSet<HeaderName>();
        removeAllHeaders = false;
    }

    private void applyTo(HeaderApplicationLogic applier) {
        // Remove headers first to make sure put logic stays sane
        if (!removeAllHeaders) {
            for (HeaderName header : headersToRemove()) {
                applier.removeHeader(header.getName());
            }
        } else {
            applier.removeAllHeaders();
        }

        for (Map.Entry<HeaderName, Set<String>> header : headersToAdd().entrySet()) {
            applier.addHeader(header.getKey().getName(), header.getValue());
        }
    }

    @Override
    public void applyTo(final MutableHttpServletRequest request) {
        final HeaderApplicationLogic applicationLogic = new RequestHeaderApplicationLogic(request);
        applyTo(applicationLogic);
    }

    @Override
    public void applyTo(final MutableHttpServletResponse response) {
        final ResponseHeaderApplicationLogic applicationLogic = new ResponseHeaderApplicationLogic(response);
        applyTo(applicationLogic);
    }

    @Override
    public boolean hasHeaders() {
        return !headersToAdd.isEmpty() || !headersToRemove.isEmpty() || removeAllHeaders;
    }

    @Override
    public Map<HeaderName, Set<String>> headersToAdd() {
        return headersToAdd;
    }

    @Override
    public Set<HeaderName> headersToRemove() {
        return headersToRemove;
    }

    @Override
    public void putHeader(String key, String... values) {
        // We remove the key first to preserve put logic such that any header put
        // will remove the header before setting new values
        headersToRemove.add(HeaderName.wrap(key));

        headersToAdd.put(HeaderName.wrap(key), new LinkedHashSet<String>(Arrays.asList(values)));
    }

    @Override
    public void putHeader(String key, String value, Double quality) {
        // We remove the key first to preserve put logic such that any header put
        // will remove the header before setting new values
        headersToRemove.add(HeaderName.wrap(key));

        headersToAdd.put(HeaderName.wrap(key), new LinkedHashSet<>(Arrays.asList(valueWithQuality(value, quality))));
    }

    @Override
    public void removeHeader(String key) {
        headersToRemove.add(HeaderName.wrap(key));
    }

    @Override
    public void appendHeader(String key, String... values) {
        Set<String> headerValues = headersToAdd.get(HeaderName.wrap(key));

        if (headerValues == null) {
            headerValues = new LinkedHashSet<String>();
            headersToAdd.put(HeaderName.wrap(key), headerValues);
        }

        headerValues.addAll(Arrays.asList(values));
    }

    private String valueWithQuality(String value, Double quality) {
        return value + ";q=" + quality;
    }

    @Override
    public void appendHeader(String key, String value, Double quality) {
        Set<String> headerValues = headersToAdd.get(HeaderName.wrap(key));

        if (headerValues == null) {
            headerValues = new LinkedHashSet<String>();
            headersToAdd.put(HeaderName.wrap(key), headerValues);
        }

        headerValues.add(valueWithQuality(value, quality));
    }

    @Override
    public void appendToHeader(HttpServletRequest request, String key, String value) {
        final String currentHeaderValue = request.getHeader(key);

        if (currentHeaderValue != null) {
            this.putHeader(key, currentHeaderValue + "," + value);
        } else {
            this.putHeader(key, value);
        }
    }

    @Override
    public void removeAllHeaders() {
        removeAllHeaders = true;
    }

    @Override
    public void appendDateHeader(String key, long value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
