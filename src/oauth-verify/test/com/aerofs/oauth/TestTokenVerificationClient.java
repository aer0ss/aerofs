package com.aerofs.oauth;

import com.aerofs.base.TimerUtil;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.oauth.OAuthVerificationHandler.UnexpectedResponse;
import com.google.common.collect.ImmutableSet;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestTokenVerificationClient extends BifrostTest
{
    private TokenVerificationClient verificationClient;

    @Before
    public void setupClient()
    {
        verificationClient =
                new TokenVerificationClient(
                        URI.create("http://localhost:" + _port + "/tokeninfo"),
                        null,
                        new NioClientSocketChannelFactory(),
                        TimerUtil.getGlobalTimer());
    }

    private VerifyTokenResponse verify(String token, String id, String secret) throws Exception
    {
        try {
            return verificationClient
                    .verify(token, id, secret)
                    .get();
        } catch (ExecutionException e) {
            throw (Exception)e.getCause();
        }
    }

    @Test
    public void shouldReturnErrorResponseWhenAccessTokenInvalid() throws Exception
    {
        VerifyTokenResponse response = verify("totallynotatoken", RESOURCEKEY, RESOURCESECRET);

        Assert.assertEquals("not_found", response.error);
    }

    @Test
    public void shouldReturn401ResponseWhenResourceIdInvalid() throws Exception
    {
        try {
            verify(RW_TOKEN, "totallynotaresourceid", RESOURCESECRET);
            fail();
        } catch (UnexpectedResponse e) {
            assertEquals(401, e.statusCode);
        }
    }

    @Test
    public void shouldReturn401ResponseWhenResourceSecretInvalid() throws Exception
    {
        try {
            verify(RW_TOKEN, RESOURCEKEY, "totallynotasecret");
            fail();
        } catch (UnexpectedResponse e) {
            assertEquals(401, e.statusCode);
        }
    }

    @Test
    public void shouldReturnTokenInfo() throws Exception
    {
        VerifyTokenResponse response = verify(RW_TOKEN, RESOURCEKEY, RESOURCESECRET);

        Assert.assertNull(response.error);
        Assert.assertEquals(USERNAME, response.principal.getName());
        Assert.assertEquals(0L, response.expiresIn.longValue());
        Assert.assertEquals(ImmutableSet.of("files.read", "files.write"), response.scopes);
    }
}
