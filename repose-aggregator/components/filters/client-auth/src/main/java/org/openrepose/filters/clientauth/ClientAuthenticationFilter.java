package org.openrepose.filters.clientauth;

import org.openrepose.core.filter.FilterConfigHelper;
import org.openrepose.core.filter.logic.impl.FilterLogicHandlerDelegate;
import org.openrepose.core.services.config.ConfigurationService;
import org.openrepose.filters.clientauth.config.ClientAuthConfig;
import org.openrepose.core.services.datastore.DatastoreService;
import org.openrepose.core.services.httpclient.HttpClientService;
import org.openrepose.core.services.serviceclient.akka.AkkaServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.*;
import java.io.IOException;
import java.net.URL;

@Named
public class ClientAuthenticationFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(ClientAuthenticationFilter.class);
    private static final String DEFAULT_CONFIG = "client-auth-n.cfg.xml";
    private String config;
    private ClientAuthenticationHandlerFactory handlerFactory;
    private final DatastoreService datastoreService;
    private final ConfigurationService configurationService;
    private final HttpClientService httpClientService;
    private final AkkaServiceClient akkaServiceClient;

    @Inject
    public ClientAuthenticationFilter(DatastoreService datastoreService,
                                      ConfigurationService configurationService,
                                      HttpClientService httpClientService,
                                      AkkaServiceClient akkaServiceClient) {
        this.datastoreService = datastoreService;
        this.httpClientService = httpClientService;
        this.akkaServiceClient = akkaServiceClient;
        this.configurationService = configurationService;
    }

    @Override
    public void destroy() {
        handlerFactory.stopFeeds();
        configurationService.unsubscribeFrom(config, handlerFactory);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        new FilterLogicHandlerDelegate(request, response, chain).doFilter(handlerFactory.newHandler());
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        config = new FilterConfigHelper(filterConfig).getFilterConfig(DEFAULT_CONFIG);
        LOG.info("Initializing filter using config " + config);
        handlerFactory = new ClientAuthenticationHandlerFactory(datastoreService.getDefaultDatastore(), httpClientService, akkaServiceClient);
        URL xsdURL = getClass().getResource("/META-INF/schema/config/client-auth-n-configuration.xsd");
        configurationService.subscribeTo(filterConfig.getFilterName(), config, xsdURL, handlerFactory, ClientAuthConfig.class);
    }
}
