package com.aerofs.baseline;

import com.google.common.base.Charsets;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.CloseableHttpPipeliningClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public final class HttpClientResource extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientResource.class);

    private final CloseableHttpPipeliningClient httpClient = HttpAsyncClients.createPipelining();

    @Override
    protected void before() throws Throwable {
        httpClient.start();
    }

    @Override
    protected void after() {
        try {
            httpClient.close();
        } catch (IOException e) {
            LOGGER.warn("fail close http client cleanly", e);
        }
    }

    public CloseableHttpPipeliningClient getClient() {
        return httpClient;
    }

    // see http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
    public static String readStreamToString(InputStream in) {
        Scanner scanner = new Scanner(in).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    public static byte[] readStreamToBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            int bytesRead;
            byte[] chunk = new byte[1024];

            while ((bytesRead = in.read(chunk)) != -1) {
                out.write(chunk, 0, bytesRead);
            }

            return out.toByteArray();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                LOGGER.warn("fail close stream", e);
            }
        }
    }

    public static BasicHttpEntity writeStringToEntity(String content) {
        BasicHttpEntity basic = new BasicHttpEntity();
        basic.setContent(new ByteArrayInputStream(content.getBytes(Charsets.UTF_8)));
        return basic;
    }
}
