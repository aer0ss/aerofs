package com.aerofs.polaris;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public final class TestTasksResource {

    @Rule
    public final PolarisResource server = new PolarisResource();

    @Rule
    public final HttpClientResource client = new HttpClientResource();

    @Test
    public void shouldRunGCTask() throws ExecutionException, InterruptedException {
        HttpPost post = new HttpPost(TestStatics.POLARIS_ADMIN_URI + "/tasks/gc");

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
    }

    // FIXME (AG): programmatically verify this
    @Test
    public void shouldRunMetricsTask() throws ExecutionException, InterruptedException {
        HttpPost post = new HttpPost(TestStatics.POLARIS_ADMIN_URI + "/tasks/metrics");

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
    }

    // FIXME (AG): programmatically verify this
    @Test
    public void shouldRunMetricsTaskAndReturnPrettyPrintedOutput() throws ExecutionException, InterruptedException {
        HttpPost post = new HttpPost(TestStatics.POLARIS_ADMIN_URI + "/tasks/metrics?pretty=true");

        Future<HttpResponse> future = client.getClient().execute(post, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.SC_OK));
    }
}