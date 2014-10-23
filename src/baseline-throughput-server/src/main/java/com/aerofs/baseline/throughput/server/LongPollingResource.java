package com.aerofs.baseline.throughput.server;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Path("/polled")
@Singleton
public final class LongPollingResource {

    private final Timer timer = new Timer();

    @GET
    public void getTime(@Suspended final AsyncResponse response) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                response.resume(System.currentTimeMillis());
            }
        }, TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS));
    }
}
