package com.aerofs.polaris.sparta;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.ManagedAccessManager;
import com.aerofs.polaris.logical.FolderSharer;
import com.aerofs.polaris.logical.StoreRenamer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aerofs.auth.client.delegated.AeroDelegatedUserDevice.getHeaderValue;
import static com.aerofs.baseline.Constants.SERVICE_NAME_INJECTION_KEY;
import static com.aerofs.polaris.Constants.DEPLOYMENT_SECRET_INJECTION_KEY;

// TODO(AS): This class has more responsibilities than a super hero. Change its name.
@Singleton
public final class SpartaAccessManager implements ManagedAccessManager, FolderSharer, StoreRenamer {

    private static final String SPARTA_API_VERSION = "v1.3";
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
                    LOGGER.warn("user {} does not belong to shared folder {}", user.getString(), store);
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

    @Override
    public boolean shareFolder(AeroOAuthPrincipal principal, SID sid, String name)
    {
        HttpPost post = new HttpPost(spartaUrl + String.format("/%s/shares", SPARTA_API_VERSION));
        post.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        post.addHeader(HttpHeaders.AUTHORIZATION, getHeaderValue(serviceName, deploymentSecret,
                principal.getUser().getString(), principal.getDID().toStringFormal()));

        Map<String,String> map = Maps.newHashMap();
        map.put("id", sid.toStringFormal());
        map.put("name", name);

        try {
            post.setEntity(encodedBody(map));
            try (CloseableHttpResponse response = client.execute(post)) {
                int sc = response.getStatusLine().getStatusCode();
                LOGGER.warn("Result of sharing folder from sparta: {}", sc);
                return sc == 201 || sc == 204;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to share folder {}", e);
            return false;
        }
    }

    private static StringEntity encodedBody(Map<String, String> m) throws JsonProcessingException {
        return new StringEntity(new ObjectMapper().writeValueAsString(m),
                ContentType.APPLICATION_JSON);
    }

    @Override
    public boolean renameStore(AeroUserDevicePrincipal principal, UniqueID oid, String name) {
        HttpPatch patch = new HttpPatch(spartaUrl + String.format("/%s/shares/" + oid.toStringFormal(), SPARTA_API_VERSION));
        patch.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        patch.addHeader(HttpHeaders.AUTHORIZATION, getHeaderValue(serviceName, deploymentSecret,
                principal.getUser().getString(), principal.getDevice().toStringFormal()));

        Map<String,String> map = Maps.newHashMap();
        map.put("public_name", name);

        try {
            patch.setEntity(encodedBody(map));
            try (CloseableHttpResponse response = client.execute(patch)) {
                int sc = response.getStatusLine().getStatusCode();
                LOGGER.warn("Result of renaming shared folder from sparta: {}", sc);
                return sc == 204;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to rename shared folder", e);
            return false;
        }
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
