package com.aerofs.polaris.sparta;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.ManagedAccessManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.aerofs.baseline.Constants.SERVICE_NAME_INJECTION_KEY;
import static com.aerofs.polaris.Constants.DEPLOYMENT_SECRET_INJECTION_KEY;

@Singleton
public final class SpartaAccessManager implements ManagedAccessManager {

    private static final String SPARTA_API_VERSION = "v1.2";
    private static final long CONNECTION_ACQUIRE_TIMEOUT = TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);

    private static final Logger LOGGER = LoggerFactory.getLogger(SpartaAccessManager.class);

    private final String serviceName;
    private final String deploymentSecret;
    private final String spartaUrl;
    private final ObjectMapper mapper;
    private final CloseableHttpClient client;

    @Inject
    public SpartaAccessManager(
            @Named(SERVICE_NAME_INJECTION_KEY) String serviceName,
            @Named(DEPLOYMENT_SECRET_INJECTION_KEY) String deploymentSecret,
            ObjectMapper mapper,
            SpartaConfiguration configuration) throws MalformedURLException {
        this.serviceName = serviceName;
        this.deploymentSecret = deploymentSecret;
        this.spartaUrl = configuration.getUrl();
        this.mapper = mapper;

        URL url = new URL(configuration.getUrl());
        String host = url.getHost();
        short port = (short) url.getPort();
        LOGGER.info("setup sparta access manager {}:{}", host, port);

        SocketConfig socketConfig = SocketConfig
                .custom()
                .setTcpNoDelay(true)
                .build();

        RequestConfig requestConfig = RequestConfig
                .custom()
                .setConnectTimeout((int) configuration.getConnectTimeout())
                .setConnectionRequestTimeout((int) CONNECTION_ACQUIRE_TIMEOUT)
                .setSocketTimeout((int) configuration.getResponseTimeout())
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(configuration.getMaxConnections());
        connectionManager.setDefaultMaxPerRoute(configuration.getMaxConnections());

        this.client = HttpClients
                .custom()
                .setDefaultSocketConfig(socketConfig)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .build();
    }

    @Override
    public void start() throws Exception {
        // noop
    }

    @Override
    public void stop() {
        try {
            client.close();
        } catch (IOException e) {
            LOGGER.warn("fail shutdown sparta HTTP client", e);
        }
    }

    // FIXME (AG): do not throw AccessException when there's an IOException

    @Override
    public void checkAccess(UserID user, Collection<UniqueID> stores, Access... requested) throws AccessException {
        for (UniqueID store : stores) {
            // TODO (RD): cache responses and pipeline requests if necessary
            HttpGet get = new HttpGet(spartaUrl + String.format("/%s/shares/%s/members/%s", SPARTA_API_VERSION, store.toStringFormal(), user.getString()));
            get.addHeader(HttpHeaders.AUTHORIZATION, AeroService.getHeaderValue(serviceName, deploymentSecret));

            try {
                try (CloseableHttpResponse response = client.execute(get)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    // if we got a 404 then the user doesn't exist
                    if (statusCode == HttpStatus.SC_NOT_FOUND) {
                        LOGGER.warn("user does not belong to shared folder {}", store);
                        throw new AccessException(user, store, Access.READ);
                    } else if (statusCode != HttpStatus.SC_OK) {
                        LOGGER.warn("fail retrieve ACL from sparta sc:{}", statusCode);
                        throw new AccessException(user, store, requested);
                    }

                    List<String> nonReadAccesses = nonReadAccessRequested(requested);
                    // we haven't requested WRITE/MANAGE access
                    // members of a shared folder have READ access by default
                    if (nonReadAccesses.size() == 0) {
                        return;
                    }

                    // we've requested WRITE/MANAGE access and the user exists...
                    try (InputStream content = response.getEntity().getContent()) {
                        Member member = mapper.readValue(content, Member.class);
                        if (!member.permissions.containsAll(nonReadAccesses)) {
                            throw new AccessException(user, store, requested);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("fail retrieve ACL from sparta", e);
                throw new AccessException(user, store, requested);
            }
        }
    }

    private List<String> nonReadAccessRequested(Access[] requested)
    {
        List<String> accesses = Lists.newArrayList();
        for (Access access : requested) {
            if (access != Access.READ) {
                accesses.add(access.name());
            }
        }
        return accesses;
    }
}
