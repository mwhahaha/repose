package org.openrepose.powerfilter.intrafilterLogging;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.openrepose.commons.utils.servlet.http.MutableHttpServletRequest;
import org.openrepose.powerfilter.filtercontext.FilterContext;

import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class RequestLog {

    String preamble;
    String timestamp;
    String currentFilter;
    String httpMethod;
    String requestURI;
    String requestBody;
    HashMap<String, String> headers;

    public RequestLog(MutableHttpServletRequest mutableHttpServletRequest,
                      FilterContext filterContext) throws IOException {

        preamble = "Intrafilter Request Log";
        timestamp = new DateTime().toString();
        currentFilter = filterContext.getFilterConfig().getId() + "-" + filterContext.getFilterConfig().getName();
        httpMethod = mutableHttpServletRequest.getMethod();
        requestURI = mutableHttpServletRequest.getRequestURI();
        headers = convertRequestHeadersToMap(mutableHttpServletRequest);

        //Have to wrap the input stream in somethign that can be buffered, as well as reset.
        ServletInputStream bin = mutableHttpServletRequest.getInputStream();
        bin.mark(Integer.MAX_VALUE); //Something doesn't support mark reset
        requestBody = IOUtils.toString(bin); //http://stackoverflow.com/a/309448
        bin.reset();
    }

    private HashMap<String, String> convertRequestHeadersToMap(
            MutableHttpServletRequest mutableHttpServletRequest) {

        HashMap<String, String> headerMap = new LinkedHashMap<String, String>();
        List<String> headerNames = Collections.list(mutableHttpServletRequest.getHeaderNames());

        for (String headername : headerNames) {
            headerMap.put(headername, mutableHttpServletRequest.getHeader(headername));
        }

        return headerMap;
    }
}
