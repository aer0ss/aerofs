package com.aerofs.oauth;

import com.aerofs.base.C;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.testlib.AbstractBaseTest;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestTokenVerifier extends AbstractBaseTest
{
    static final String ID = "id";
    static final String SECRET = "secret";
    static final String AUTH = TokenVerificationClient.makeAuth(ID, SECRET);

    @Mock Ticker ticker;
    @Mock VerifyTokenResponse response;
    @Mock TokenVerificationClient client;

    TokenVerifier verifier;

    @Before
    public void setUp() throws Exception
    {
        verifier = new TokenVerifier(ID, SECRET, client,
                CacheBuilder.newBuilder()
                        .maximumSize(1)
                        .expireAfterWrite(1, TimeUnit.SECONDS)
                        .ticker(ticker));

        when(client.verify(anyString(), eq(AUTH)))
                .thenReturn(UncancellableFuture.createSucceeded(response));
    }

    @Test
    public void shouldFetchOnMiss() throws Exception
    {
        assertEquals(response, verifier.verifyToken("foo"));

        verify(client).verify("foo", AUTH);
    }

    @Test
    public void shouldNotFetchOnHit() throws Exception
    {
        assertEquals(response, verifier.verifyToken("foo"));
        assertEquals(response, verifier.verifyToken("foo"));
        assertEquals(response, verifier.verifyToken("foo"));

        verify(client).verify("foo", AUTH);
    }

    @Test
    public void shouldRefetchWhenRunningOutOfSpace() throws Exception
    {
        assertEquals(response, verifier.verifyToken("foo"));
        assertEquals(response, verifier.verifyToken("bar"));
        assertEquals(response, verifier.verifyToken("foo"));

        verify(client, times(2)).verify("foo", AUTH);
        verify(client, times(1)).verify("bar", AUTH);
    }

    @Test
    public void shouldRefetchWhenExpired() throws Exception
    {
        assertEquals(response, verifier.verifyToken("foo"));
        when(ticker.read()).thenReturn(2 * C.SEC * C.NSEC_PER_MSEC);
        assertEquals(response, verifier.verifyToken("foo"));

        verify(client, times(2)).verify("foo", AUTH);
    }
}
