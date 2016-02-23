package com.aerofs.havre;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UniqueID;
import com.aerofs.oauth.SimpleHttpClient;
import com.aerofs.oauth.SimpleHttpClient.UnexpectedResponse;
import com.aerofs.ssmp.EventHandler;
import com.aerofs.ssmp.SSMPEvent;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

public class RequestRouter extends CacheLoader<String, List<String>> implements EventHandler {
    private final static Logger l = LoggerFactory.getLogger(RequestRouter.class);

    @Override
    public void eventReceived(SSMPEvent e) {
        if (e.type != Type.MCAST || !e.from.isAnonymous()) return;
        String to = e.to.toString();
        if (!to.startsWith("loc/")) return;
        try {
            for (int i = 0; i + 23 < e.payload.length; i += 24) {
                String oid = BaseUtil.hexEncode(e.payload, i, 16);
                l.debug("invalidate {}", oid);
                _cache.invalidate(oid);
            }
        } catch (Exception ex) {
            l.warn("failed to parse location notif");
            _cache.invalidateAll();
        }
    }

    public static class LocationResponse {
        List<String> available;
    }

    private final String _auth;
    private final SimpleHttpClient<String, LocationResponse> _client;
    private final LoadingCache<String, List<String>> _cache;


    private final static ChannelBuffer EMPTY_FILTER = ChannelBuffers.wrappedBuffer(
            "{}".getBytes(StandardCharsets.UTF_8));

    RequestRouter(URI endpoint, ClientSocketChannelFactory channelFactory, Timer timer, String secret) {
        _auth = "Aero-Service-Shared-Secret havre " + secret;
        _client = new SimpleHttpClient<String, LocationResponse>(endpoint, null, channelFactory, timer) {
            @Override
            public String buildURI(String query) {
                return _endpoint.getPath() + "/" + query;
            }
            @Override
            public void modifyRequest(HttpRequest req, String query) {
                req.setMethod(HttpMethod.POST);
                req.headers().set(Names.AUTHORIZATION, _auth);
                req.headers().set(Names.CONTENT_LENGTH, EMPTY_FILTER.readableBytes());
                req.setContent(EMPTY_FILTER);
            }
        };
        _cache = CacheBuilder
                .newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(1000L)
                .build(this);
    }

    private final static String SUFFIX = "/content";

    public @Nullable DID route(String uri, List<DID> cand) {
        if (cand.isEmpty()) return null;
        if (cand.size() == 1) return cand.get(0);
        l.debug("route {} {}", uri, cand);
        if (uri.endsWith(SUFFIX) && uri.charAt(uri.length() - 1 - SUFFIX.length() - 4 * UniqueID.LENGTH) == '/') {
            int last = uri.length() - SUFFIX.length();
            String obj = uri.substring(last - 2 * UniqueID.LENGTH, last);

            try {
                List<String> dids = _cache.get(obj);
                // DIDs with most recent versions come first
                // pick first online device with highest available version
                l.debug("avail {}", dids);

                for (String h : dids) {
                    try {
                        DID did = new DID(h);
                        if (cand.contains(did)) {
                            l.debug("match {}", did);
                            return did;
                        }
                    } catch (ExInvalidID e) {
                        l.warn("invalid id {}", h);
                    }
                }
            } catch (ExecutionException e) {
                l.warn("failed to get locations info", BaseLogUtil.suppress(e.getCause(),
                        UnexpectedResponse.class));
            }
        }
        // fallback to random selection
        return cand.get(ThreadLocalRandom.current().nextInt(cand.size()));
    }

    @Override
    public List<String> load(@Nonnull String obj) throws Exception {
        ListenableFuture<LocationResponse> f = _client.send(obj);
        try {
            return f.get(5, TimeUnit.SECONDS).available;
        } catch (TimeoutException e) {
            f.cancel(false);
            throw e;
        } catch (ExecutionException e) {
            Throwables.propagateIfPossible(e.getCause(), Exception.class);
            throw e;
        }
    }
}
