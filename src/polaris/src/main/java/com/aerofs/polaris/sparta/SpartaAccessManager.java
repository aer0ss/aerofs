package com.aerofs.polaris.sparta;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.ManagedAccessManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final Cache<UserStore, List<Access>> permsCache = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).build();

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

    @Override
    public void checkAccess(UserID user, Collection<UniqueID> stores, Access... requested) throws AccessException {
        List<Access> req = Arrays.asList(requested);
        Map<UniqueID, List<Access>> perms = stores.parallelStream().collect(Collectors.toMap((store) -> store, (store) -> findAccessPermissions(user, store)));
        UniqueID rej = perms.entrySet().stream().filter((entry) -> !entry.getValue().containsAll(req)).findAny().map(Map.Entry::getKey).orElse(null);
        if (rej != null) {
            throw new AccessException(user, rej, requested);
        }
    }

    private List<Access> findAccessPermissions(UserID user, UniqueID store) {
        UserStore key = new UserStore(user, store);
        List<Access> cached = permsCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        HttpGet get = new HttpGet(spartaUrl + String.format("/%s/shares/%s/members/%s", SPARTA_API_VERSION, store.toStringFormal(), user.getString()));
        get.addHeader(HttpHeaders.AUTHORIZATION, AeroService.getHeaderValue(serviceName, deploymentSecret));

        try {
            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();

                // if we got a 404 then the user doesn't exist
                if (statusCode == HttpStatus.SC_NOT_FOUND) {
                    LOGGER.warn("user does not belong to shared folder {}", store);
                    return Lists.newArrayList();
                } else if (statusCode != HttpStatus.SC_OK) {
                    LOGGER.warn("fail retrieve ACL from sparta sc:{}", statusCode);
                    return Lists.newArrayList();
                }

                try (InputStream content = response.getEntity().getContent()) {
                    Member member = mapper.readValue(content, Member.class);
                    List<Access> accesses = accessFromMemberPermissions(member.permissions);
                    // cache the member's current permissions in the store, don't cache other negative responses
                    permsCache.put(key, accesses);
                    return accesses;
                }
            }
        } catch (IOException e) {
            // FIXME (AG): do not cause AccessException when there's an IOException
            LOGGER.warn("fail retrieve ACL from sparta", e);
            return Lists.newArrayList();
        }
    }

    private List<Access> accessFromMemberPermissions(List<String> perms) {
        List<Access> a = Lists.newArrayListWithCapacity(perms.size() + 1);
        a.add(Access.READ);
        for (String s : perms) {
            switch (s) {
                case "WRITE":
                    a.add(Access.WRITE);
                    break;
                case "MANAGE":
                    a.add(Access.MANAGE);
                    break;
                default:
                    LOGGER.warn("Unrecognized perm {}", s);
                    break;
            }
        }
        return a;
    }

    private static class UserStore
    {
        private final UserID user;
        private final UniqueID store;

        UserStore(UserID user, UniqueID store) {
            this.user = user;
            this.store = store;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UserStore other = (UserStore) o;
            return user.equals(other.user) && store.equals(other.store);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(user, store);
        }

        @Override
        public String toString() {
            return Objects
                    .toStringHelper(this)
                    .add("user", user)
                    .add("store", store)
                    .toString();
        }
    }
}
