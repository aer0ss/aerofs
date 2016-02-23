package com.aerofs.oauth;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.ssl.ICertificateProvider;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;
import org.jboss.netty.util.Timer;

import java.net.URI;

/**
 * Direct asynchronous verification of OAuth tokens
 */
public class TokenVerificationClient extends SimpleHttpClient<String, VerifyTokenResponse>
{
    private String _auth;

    public TokenVerificationClient(URI endpoint, final ICertificateProvider cacert,
            ClientSocketChannelFactory clientChannelFactory, final Timer timer,
            String auth)
    {
        super(endpoint, cacert, clientChannelFactory, timer);
        _auth = auth;
    }

    public static String makeAuth(String clientId, String clientSecret)
    {
        return "Basic " + Base64.encodeBytes(BaseUtil.string2utf(clientId + ":" + clientSecret));
    }

    public ListenableFuture<VerifyTokenResponse> verify(String accessToken)
    {
        return send(accessToken);
    }

    @Override
    protected String buildURI(String query) {
        QueryStringEncoder encoder = new QueryStringEncoder(_endpoint.getPath());
        encoder.addParam("access_token", query);
        return encoder.toString();
    }

    @Override
    protected void modifyRequest(HttpRequest req, String query) {
        req.headers().set(Names.AUTHORIZATION, _auth);
    }
}
