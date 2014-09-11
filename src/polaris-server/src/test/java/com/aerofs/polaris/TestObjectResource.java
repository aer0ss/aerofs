package com.aerofs.polaris;

import com.aerofs.polaris.api.Update;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Charsets;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public final class TestObjectResource {

    @Rule
    public final PolarisResource server = new PolarisResource();

    @Rule
    public final HttpClientResource client = new HttpClientResource();

    @Test
    public void shouldReceiveErrorOnMakingPostWithEmptyBody() throws ExecutionException, InterruptedException {
        HttpPost post = new HttpPost(TestStatics.POLARIS_URI + "/objects/SF0");
        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldReceiveErrorOnMakingPostWithInvalidUpdateObject() throws ExecutionException, InterruptedException, JsonProcessingException {
        Update update = new Update();
        update.localVersion = 1;

        String serialized = TestStatics.OBJECT_MAPPER.writeValueAsString(update);
        ByteArrayInputStream contentInputStream = new ByteArrayInputStream(serialized.getBytes(Charsets.US_ASCII));
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(contentInputStream);

        HttpPost post = new HttpPost(TestStatics.POLARIS_URI + "/objects/SF0");
        post.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
        post.setEntity(entity);

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();
        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_BAD_REQUEST));
    }
}