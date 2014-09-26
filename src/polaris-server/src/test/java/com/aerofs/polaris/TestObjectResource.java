package com.aerofs.polaris;

import com.aerofs.baseline.HttpClientResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

public final class TestObjectResource {

    @Rule
    public final PolarisResource server = new PolarisResource();

    @Rule
    public final HttpClientResource client = new HttpClientResource();

    @Test
    public void shouldReceiveErrorOnMakingPostWithEmptyBody() throws ExecutionException, InterruptedException {
//        HttpPost post = new HttpPost(ServerConfiguration.POLARIS_URI + "/objects/SF0");
//        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
//
//        Future<HttpResponse> future = client.getClient().execute(post, null);
//        HttpResponse response = future.get();
//        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldReceiveErrorOnMakingPostWithInvalidUpdateObject() throws ExecutionException, InterruptedException, JsonProcessingException {
//        Operation operation = new Operation();
//        operation.localVersion = 1;
//
//        String serialized = ServerConfiguration.OBJECT_MAPPER.writeValueAsString(operation);
//        ByteArrayInputStream contentInputStream = new ByteArrayInputStream(serialized.getBytes(Charsets.US_ASCII));
//        BasicHttpEntity entity = new BasicHttpEntity();
//        entity.setContent(contentInputStream);
//
//        HttpPost post = new HttpPost(ServerConfiguration.POLARIS_URI + "/objects/SF0");
//        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
//        post.setEntity(entity);
//
//        Future<HttpResponse> future = client.getClient().execute(post, null);
//        HttpResponse response = future.get();
//        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }
}