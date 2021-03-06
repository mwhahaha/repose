package org.openrepose.core.services.datastore.impl.distributed.remote.command;

import org.openrepose.commons.utils.http.ServiceClientResponse;
import org.openrepose.commons.utils.io.ObjectSerializer;
import org.openrepose.commons.utils.io.RawInputStreamReader;
import org.openrepose.core.services.RequestProxyService;
import org.openrepose.core.services.datastore.DatastoreOperationException;
import org.openrepose.core.services.datastore.distributed.RemoteBehavior;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

/**
 *
 * @author zinic
 */
public class Get extends AbstractRemoteCommand {
    private final ObjectSerializer objectSerializer = new ObjectSerializer(this.getClass().getClassLoader());

   public Get(String cacheObjectKey, InetSocketAddress remoteEndpoint) {
      super(cacheObjectKey, remoteEndpoint);
   }

    @Override
    public ServiceClientResponse execute(RequestProxyService proxyService, RemoteBehavior remoteBehavior) {
        return proxyService.get(getBaseUrl(), getCacheObjectKey(), getHeaders(remoteBehavior));
    }
    
   @Override
   public Object handleResponse(ServiceClientResponse response) throws IOException {
      final int statusCode = response.getStatus();

      if (statusCode == HttpServletResponse.SC_OK) {
         final InputStream internalStreamReference = response.getData();

          try {
              return objectSerializer.readObject(RawInputStreamReader.instance().readFully(internalStreamReference));
          } catch (ClassNotFoundException cnfe) {
              throw new DatastoreOperationException("Unable to marshall a java object from stored element contents. Reason: " + cnfe.getMessage(), cnfe);
          }
      } else if (statusCode == HttpServletResponse.SC_NOT_FOUND) {
         return null;
      }

      throw new DatastoreOperationException("Remote request failed with: " + statusCode);
   }
}
