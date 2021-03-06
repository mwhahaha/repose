package org.openrepose.nodeservice.distributed.servlet;

import org.openrepose.commons.utils.io.ObjectSerializer;
import org.openrepose.core.services.datastore.*;
import org.openrepose.core.services.datastore.distributed.ClusterConfiguration;
import org.openrepose.core.services.datastore.distributed.ClusterView;
import org.openrepose.core.services.datastore.impl.distributed.CacheRequest;
import org.openrepose.core.services.datastore.impl.distributed.MalformedCacheRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds most of the work for running a distributed datastore.
 * Exposes the ClusterView and the ACL for update.
 */
public class DistributedDatastoreServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedDatastoreServlet.class);

    private final ObjectSerializer objectSerializer = new ObjectSerializer(this.getClass().getClassLoader());
    private final AtomicReference<DatastoreAccessControl> hostAcl;
    private Datastore localDatastore;
    private final DatastoreService datastoreService;
    private final ClusterConfiguration clusterConfiguration;
    private static final String DISTRIBUTED_HASH_RING = "distributed/hash-ring";

    public DistributedDatastoreServlet(
            DatastoreService datastore,
            ClusterConfiguration clusterConfiguration,
            DatastoreAccessControl acl
    ) {
        this.datastoreService = datastore;
        this.clusterConfiguration = clusterConfiguration;
        this.hostAcl = new AtomicReference<>(acl);
        localDatastore = datastore.getDefaultDatastore();

        new org.openrepose.core.services.ratelimit.cache.UserRateLimit();
    }

    /**
     * Called from other threads to be able to tickle the cluster view for this servlet
     */
    public ClusterView getClusterView() {
        return clusterConfiguration.getClusterView();
    }

    /**
     * hit from other threads to update the ACL for this servlet.
     *
     * @param acl
     */
    public void updateAcl(DatastoreAccessControl acl) {
        this.hostAcl.set(acl);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        LOG.info("Registering datastore: {}", DISTRIBUTED_HASH_RING);

        datastoreService.createDatastore(DISTRIBUTED_HASH_RING, clusterConfiguration);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (isRequestValid(request, response)) {
            if ("PATCH".equals(request.getMethod())) {
                doPatch(request, response);
            } else {
                super.service(request, response);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        try {
            CacheRequest cacheGet = CacheRequest.marshallCacheRequest(req);
            final Serializable value = localDatastore.get(cacheGet.getCacheKey());

            if (value != null) {
                resp.getOutputStream().write(objectSerializer.writeObject(value));
                resp.setStatus(HttpServletResponse.SC_OK);

            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (MalformedCacheRequestException e) {

            LOG.error("Malformed cache request during GET", e);
            switch (e.error) {
                case NO_DD_HOST_KEY:
                    resp.getWriter().write(e.error.message());
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    break;
                case CACHE_KEY_INVALID:
                    resp.getWriter().write(e.error.message());
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    break;
                case OBJECT_TOO_LARGE:
                case TTL_HEADER_NOT_POSITIVE:
                case UNEXPECTED_REMOTE_BEHAVIOR:
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    break;
                default:
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    break;
            }
        } catch (IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (CacheRequest.isCacheRequestValid(req)) {
            try {
                final CacheRequest cachePut = CacheRequest.marshallCacheRequestWithPayload(req);
                localDatastore.put(cachePut.getCacheKey(), objectSerializer.readObject(cachePut.getPayload()), cachePut.getTtlInSeconds(), TimeUnit.SECONDS);
                resp.setStatus(HttpServletResponse.SC_ACCEPTED);
            } catch (IOException ioe) {
                LOG.error(ioe.getMessage(), ioe);
                throw new DatastoreOperationException("Failed to write payload.", ioe);
            } catch (ClassNotFoundException cnfe) {
                LOG.error(cnfe.getMessage(), cnfe);
                throw new DatastoreOperationException("Failed to deserialize a message. Couldn't find a matching class.", cnfe);
            } catch (MalformedCacheRequestException mcre) {
                handleputMalformedCacheRequestException(mcre, resp);
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (CacheRequest.isCacheRequestValid(req)) {
            try {
                final CacheRequest cacheDelete = CacheRequest.marshallCacheRequest(req);
                localDatastore.remove(cacheDelete.getCacheKey());
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } catch (MalformedCacheRequestException e) {
                LOG.trace("Malformed cache request on Delete", e);
                switch (e.error) {
                    case NO_DD_HOST_KEY:
                        resp.getWriter().write(e.error.message());
                        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        break;
                    case UNEXPECTED_REMOTE_BEHAVIOR:
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        break;
                    default:
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        break;
                }
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void doPatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (CacheRequest.isCacheRequestValid(request)) {
            try {
                final CacheRequest cachePatch = CacheRequest.marshallCacheRequestWithPayload(request);
                Serializable value = localDatastore.patch(cachePatch.getCacheKey(), (Patch) objectSerializer.readObject(cachePatch.getPayload()), cachePatch.getTtlInSeconds(), TimeUnit.SECONDS);
                response.getOutputStream().write(objectSerializer.writeObject(value));
                response.setStatus(HttpServletResponse.SC_OK);
            } catch (IOException ioe) {
                LOG.error(ioe.getMessage(), ioe);
                throw new DatastoreOperationException("Failed to write payload.", ioe);
            } catch (ClassNotFoundException cnfe) {
                LOG.error(cnfe.getMessage(), cnfe);
                throw new DatastoreOperationException("Failed to deserialize a message. Couldn't find a matching class.", cnfe);
            } catch (MalformedCacheRequestException mcre) {
                LOG.trace("Handling Malformed Cache Request", mcre);
                handleputMalformedCacheRequestException(mcre, response);
            } catch (ClassCastException e) {
                LOG.trace("Sending ERROR response", e);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public boolean isAllowed(HttpServletRequest request) {
        boolean allowed = hostAcl.get().shouldAllowAll();

        if (!allowed) {
            try {
                final InetAddress remoteClient = InetAddress.getByName(request.getRemoteHost());

                for (InetAddress allowedAddress : hostAcl.get().getAllowedHosts()) {
                    if (remoteClient.equals(allowedAddress)) {
                        allowed = true;
                        break;
                    }
                }
            } catch (UnknownHostException uhe) {
                LOG.error("Unknown host exception caught while trying to resolve host: " + request.getRemoteHost() + " Reason: " + uhe.getMessage(), uhe);
            }
        }

        return allowed;
    }

    private boolean isRequestValid(HttpServletRequest req, HttpServletResponse resp) {
        boolean valid = false;
        if (!isAllowed(req)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else if (!CacheRequest.isCacheRequestValid(req)) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            valid = true;
        }

        return valid;
    }

    @Override
    public void destroy() {
        super.destroy();
        LOG.info("Unregistering Datastore: {}", DISTRIBUTED_HASH_RING);
        datastoreService.destroyDatastore(DISTRIBUTED_HASH_RING);
    }

    private void handleputMalformedCacheRequestException(MalformedCacheRequestException mcre, HttpServletResponse response) throws IOException {

        LOG.error("Handling Malformed Cache Request", mcre);
        switch (mcre.error) {
            case NO_DD_HOST_KEY:
                response.getWriter().write(mcre.error.message());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                break;
            case OBJECT_TOO_LARGE:
                response.getWriter().write(mcre.error.message());
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                break;
            case CACHE_KEY_INVALID:
            case TTL_HEADER_NOT_POSITIVE:
            case UNEXPECTED_REMOTE_BEHAVIOR:
                response.getWriter().write(mcre.error.message());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                break;
            default:
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                break;
        }

    }
}
