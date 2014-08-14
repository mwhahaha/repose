package com.rackspace.papi.service.headers.request;

import com.google.common.base.Optional;
import com.rackspace.papi.commons.util.http.CommonHttpHeader;
import com.rackspace.papi.commons.util.servlet.http.MutableHttpServletRequest;
import com.rackspace.papi.container.config.ContainerConfiguration;
import com.rackspace.papi.filter.SystemModelInterrogator;
import com.rackspace.papi.model.Node;
import com.rackspace.papi.model.SystemModel;
import com.rackspace.papi.service.headers.common.ViaHeaderBuilder;
import com.rackspace.papi.service.healthcheck.HealthCheckService;
import com.rackspace.papi.service.healthcheck.HealthCheckServiceProxy;
import com.rackspace.papi.service.healthcheck.Severity;
import org.openrepose.core.service.config.ConfigurationService;
import org.openrepose.core.service.config.manager.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class RequestHeaderServiceImpl implements RequestHeaderService {
    public static final Logger LOG = LoggerFactory.getLogger(RequestHeaderServiceImpl.class);
    public static final String SYSTEM_MODEL_CONFIG_HEALTH_REPORT = "SystemModelConfigError";

    private final SystemModelListener systemModelListener = new SystemModelListener();
    private final ContainerConfigurationListener configurationListener = new ContainerConfigurationListener();
    private final ConfigurationService configurationService;
    private final HealthCheckService healthCheckService;
    private final SystemModelInterrogator systemModelInterrogator;
    private final String reposeVersion;

    private String viaReceivedBy;
    private String hostname = "Repose";
    private ViaHeaderBuilder viaHeaderBuilder;
    private HealthCheckServiceProxy healthCheckServiceProxy;

    @Inject
    public RequestHeaderServiceImpl(ConfigurationService configurationService,
                                    HealthCheckService healthCheckService,
                                    SystemModelInterrogator systemModelInterrogator,
                                    @Qualifier("reposeVersion") String reposeVersion) {
        this.configurationService = configurationService;
        this.reposeVersion = reposeVersion;
        this.systemModelInterrogator = systemModelInterrogator;
        this.healthCheckService = healthCheckService;
    }

    @PostConstruct
    public void afterPropertiesSet() {
        healthCheckServiceProxy = healthCheckService.register();

        configurationService.subscribeTo("container.cfg.xml", configurationListener, ContainerConfiguration.class);
        configurationService.subscribeTo("system-model.cfg.xml", systemModelListener, SystemModel.class);
    }

    @PreDestroy
    public void destroy() {
        configurationService.unsubscribeFrom("container.cfg.xml", configurationListener);
        configurationService.unsubscribeFrom("system-model.cfg.xml", systemModelListener);
    }

    @Override
    public synchronized void updateConfig(ViaHeaderBuilder viaHeaderBuilder) {
        this.viaHeaderBuilder = viaHeaderBuilder;
    }

    @Override
    public void setXForwardedFor(MutableHttpServletRequest request) {
        request.addHeader(CommonHttpHeader.X_FORWARDED_FOR.toString(), request.getRemoteAddr());
    }

    @Override
    public void setVia(MutableHttpServletRequest request) {
        final String via = viaHeaderBuilder.buildVia(request);
        request.addHeader(CommonHttpHeader.VIA.toString(), via);
    }

    /**
     * Listens for updates to the container.cfg.xml file which holds the via
     * header receivedBy value.
     */
    private class ContainerConfigurationListener implements UpdateListener<ContainerConfiguration> {

        private boolean isInitialized = false;

        @Override
        public void configurationUpdated(ContainerConfiguration configurationObject) {

            if (configurationObject.getDeploymentConfig() != null) {
                viaReceivedBy = configurationObject.getDeploymentConfig().getVia();

                final ViaRequestHeaderBuilder viaBuilder = new ViaRequestHeaderBuilder(reposeVersion, viaReceivedBy, hostname);
                updateConfig(viaBuilder);
            }
            isInitialized = true;

        }

        @Override
        public boolean isInitialized() {
            return isInitialized;
        }
    }

    /**
     * Listens for updates to the system-model.cfg.xml file which holds the
     * hostname.
     */
    private class SystemModelListener implements UpdateListener<SystemModel> {

        private boolean isInitialized = false;

        @Override
        public void configurationUpdated(SystemModel systemModel) {

            Optional<Node> ln = systemModelInterrogator.getLocalNode(systemModel);

            if (ln.isPresent()) {
                hostname = ln.get().getHostname();

                final ViaRequestHeaderBuilder viaBuilder = new ViaRequestHeaderBuilder(reposeVersion, viaReceivedBy, hostname);
                updateConfig(viaBuilder);
                isInitialized = true;

                healthCheckServiceProxy.resolveIssue(SYSTEM_MODEL_CONFIG_HEALTH_REPORT);
            } else {
                LOG.error("Unable to identify the local host in the system model - please check your system-model.cfg.xml");
                healthCheckServiceProxy.reportIssue(SYSTEM_MODEL_CONFIG_HEALTH_REPORT, "Unable to identify the " +
                        "local host in the system model - please check your system-model.cfg.xml", Severity.BROKEN);
            }
        }

        @Override
        public boolean isInitialized() {
            return isInitialized;
        }
    }
}
