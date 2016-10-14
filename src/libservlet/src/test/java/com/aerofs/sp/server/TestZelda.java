package com.aerofs.sp.server;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class TestZelda
{
    private Zelda zelda;

    private HttpURLConnection conn;
    private ByteArrayOutputStream body;

    @Before
    public void setup()
            throws Exception
    {
        zelda = spy(Zelda.create("http://fake_bifrost_url", "fake_id", "fake_deployment_secret"));
        conn = mock(HttpURLConnection.class);
        body = new ByteArrayOutputStream();

        doReturn("zelda_secret").when(zelda).getSecret();
        doReturn(conn).when(zelda).openBifrostConnection(anyString());
        doReturn(body).when(conn).getOutputStream();
    }

    @Test
    public void createAccessToken_shouldQueryBifrost()
            throws Exception
    {
        mockResponse(conn, 200,
                "{\"access_token\":\"fake_access_token\"," +
                        "\"token_type\":\"bearer\"," +
                        "\"expires_in\":314," +
                        "\"scope\":\"files.read\"}"
        );

        String accessToken = zelda.createAccessToken("fake_soid", "fake_access_code", 314L);

        assertEquals("fake_access_token", accessToken);
        assertEquals("grant_type=authorization_code" +
                "&code=fake_access_code" +
                "&code_type=device_authorization" +
                "&client_id=aerofs-zelda" +
                "&client_secret=zelda_secret" +
                "&scope=linksharing%2Cfiles.read%3Afake_soid" + // url-encoded
                "&expires_in=314", body.toString(Zelda.CHARSET));

        verify(zelda, times(1)).openBifrostConnection(eq("/token"));
        verify(conn, times(1)).setRequestMethod(eq("POST"));
        verify(conn, times(1)).setDoOutput(eq(true));
        verify(conn, atLeastOnce()).getResponseCode();
    }

    @Test
    public void deleteToken_shouldQueryBifrost()
            throws Exception
    {
        mockResponse(conn, 200, "");

        zelda.deleteToken("fake_old_token");

        verify(zelda, times(1)).openBifrostConnection(eq("/token/fake_old_token"));
        verify(conn, times(1)).setRequestMethod("DELETE");
        verify(conn, atLeastOnce()).getResponseCode();
    }

    private void mockResponse(HttpURLConnection conn, int responseCode, String response)
            throws IOException
    {
        doReturn(responseCode).when(conn).getResponseCode();
        doReturn(new ByteArrayInputStream(response.getBytes(Zelda.CHARSET)))
                .when(conn).getInputStream();
    }
}
