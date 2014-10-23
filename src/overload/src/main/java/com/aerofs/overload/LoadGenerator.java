package com.aerofs.overload;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Makes HTTP requests as the specified request rate to a server.
 */
public final class LoadGenerator {

    // threshold at which we switch from busy wait to making a single sleep() call
    private static final long THRESHOLD_FOR_SLEEP_CALL = TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS);

    // max number of simultaneous connections the load generator will create
    private static final int MAX_CONNECTIONS = 8192;

    // the max time (in ms) a connection will wait to connect to the remote server
    private static final int CONNECT_TIMEOUT = 10000;

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadGenerator.class);
    private static final MetricRegistry REGISTRY = new MetricRegistry();

    //
    // meters and timers
    //

    private static final Meter REQUEST_METER = REGISTRY.meter(MetricRegistry.name("throughput", "http", "requests"));

    private static final Meter STATUS_1XX_METER = REGISTRY.meter(MetricRegistry.name("throughput", "http", "code", "1xx"));
    private static final Meter STATUS_2XX_METER = REGISTRY.meter(MetricRegistry.name("throughput", "http", "code", "2xx"));
    private static final Meter STATUS_3XX_METER = REGISTRY.meter(MetricRegistry.name("throughput", "http", "code", "3xx"));
    private static final Meter STATUS_4XX_METER = REGISTRY.meter(MetricRegistry.name("throughput", "http", "code", "4xx"));
    private static final Meter STATUS_5XX_METER = REGISTRY.meter(MetricRegistry.name("throughput", "http", "code", "5xx"));

    private static final Meter FAILURE_METER = REGISTRY.meter(MetricRegistry.name("throughput", "http", "requests", "failure"));

    private static final Timer SUCCESSFUL_REQUEST_TIMER = REGISTRY.timer(MetricRegistry.name("throughput", "http", "requests", "successful"));

    private static final SubmittedRequestCallback SUBMITTED_REQUEST_CALLBACK = new SubmittedRequestCallback() {

        private final Timer.Context context = SUCCESSFUL_REQUEST_TIMER.time();

        @Override
        public void onWriteSucceeded() {
            REQUEST_METER.mark();
        }

        @Override
        public void onResponseReceived(FullHttpResponse response) {
            response.content().release(); // we don't actually care about the content for now

            context.stop(); // only stop the timer when we receive a response

            int statusDigit = (response.status().code() / 100);
            switch (statusDigit) {
                case 1:
                    STATUS_1XX_METER.mark();
                    break;
                case 2:
                    STATUS_2XX_METER.mark();
                    break;
                case 3:
                    STATUS_3XX_METER.mark();
                    break;
                case 4:
                    STATUS_4XX_METER.mark();
                    break;
                case 5:
                    STATUS_5XX_METER.mark();
                    break;
                default:
                    FAILURE_METER.mark();
                    LOGGER.warn("unexpected status digit {}", statusDigit);
            }
        }

        @Override
        public void onFailure(Throwable cause) {
            FAILURE_METER.mark();
        }
    };

    private final java.util.Timer timer = new java.util.Timer();
    private final AtomicBoolean keepRunning = new AtomicBoolean(true);
    private final Semaphore runCompleted = new Semaphore(0);
    private final Runnable requestRunner;

    public LoadGenerator(String serverHost, int serverPort, final HttpRequestProvider requestProvider, final int connectionCount, final int targetRequestRate) {

        int maxConcurrentConnections = Math.min(connectionCount, MAX_CONNECTIONS);
        final PipelinedHttpClient client = new PipelinedHttpClient(serverHost, serverPort, CONNECT_TIMEOUT, maxConcurrentConnections);

        this.requestRunner = new Runnable() {

            // time to wait between each request
            private final long nanosBetweenRequest = (TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS) / targetRequestRate);

            // allocator used by the underlying http client
            private final ByteBufAllocator allocator = client.getAllocator();

            // we assume that all method calls are instantaneous.
            // this is obviously untrue, so, we time how long calls take
            // and use this to reduce the delay between requests
            private long intervalCorrection = 0;

            // FIXME (AG): bound the number of requests per second to the requested rate
            @Override
            public void run() {
                try {
                    LOGGER.info("target: {} requests per second with {} ns wait between requests", targetRequestRate, nanosBetweenRequest);

                    client.start();

                    while (true) {
                        long startTime = System.nanoTime();

                        // check if the requester should keep running
                        if (!keepRunning.get()) {
                            break;
                        }

                        // get the request
                        // at this point we own the content buffer
                        final FullHttpRequest request = requestProvider.getRequest(allocator);

                        // keep the connection to the server alive
                        // no throw
                        HttpHeaders.setKeepAlive(request, true);

                        // send the request to the server
                        // pass ownership off to this method
                        executeRequest(request);

                        // pause between requests (if this one was actually written out)
                        pause(nanosBetweenRequest, startTime);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("requester run aborted", e);
                } finally {
                    client.shutdown();
                    runCompleted.release();
                }
            }

            // passes buffer ownership to underlying HTTP client
            private void executeRequest(FullHttpRequest request) throws InterruptedException {
                client.submit(request, SUBMITTED_REQUEST_CALLBACK);
            }

            private void pause(long targetInterval, long iterationStartTime) throws InterruptedException {
                long actualInterval = targetInterval - intervalCorrection;

                if (actualInterval >=THRESHOLD_FOR_SLEEP_CALL) {
                    long mills = actualInterval / 1000000;
                    long nanos = actualInterval % 1000000;

                    Thread.sleep(mills);
                    busyWait(nanos);
                } else {
                    busyWait(actualInterval);
                }

                intervalCorrection = Math.max((System.nanoTime() - iterationStartTime) - actualInterval, 0);
            }

            @SuppressWarnings("StatementWithEmptyBody")
            private void busyWait(long interval) {
                long startTime = System.nanoTime();
                while ((System.nanoTime() - startTime) < interval);
            }
        };
    }

    public void start() {
        requestRunner.run();
    }

    public void shutdown() {
        keepRunning.compareAndSet(true, false);

        try {
            runCompleted.tryAcquire(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("failed to stop request thread after 30 seconds");
        }

        timer.cancel();
    }

    public void logStats() {
        double factor = 1.0 / TimeUnit.MICROSECONDS.toNanos(1);

        LOGGER.info("outgoing requests (mean): {} req/s", REQUEST_METER.getMeanRate());
        LOGGER.info("outgoing requests (1min): {} req/s", REQUEST_METER.getOneMinuteRate());

        LOGGER.info("failures (mean): {} err/s", FAILURE_METER.getMeanRate());
        LOGGER.info("failures (1min): {} err/s", FAILURE_METER.getOneMinuteRate());

        LOGGER.info("1xx (mean): {} rep/s", STATUS_1XX_METER.getMeanRate());
        LOGGER.info("1xx (1min): {} rep/s", STATUS_1XX_METER.getOneMinuteRate());

        LOGGER.info("2xx (mean): {} rep/s", STATUS_2XX_METER.getMeanRate());
        LOGGER.info("2xx (1min): {} rep/s", STATUS_2XX_METER.getOneMinuteRate());

        LOGGER.info("3xx (mean): {} rep/s", STATUS_3XX_METER.getMeanRate());
        LOGGER.info("3xx (1min): {} rep/s", STATUS_3XX_METER.getOneMinuteRate());

        LOGGER.info("4xx (mean): {} rep/s", STATUS_4XX_METER.getMeanRate());
        LOGGER.info("4xx (1min): {} rep/s", STATUS_4XX_METER.getOneMinuteRate());

        LOGGER.info("5xx (mean): {} rep/s", STATUS_5XX_METER.getMeanRate());
        LOGGER.info("5xx (1min): {} rep/s", STATUS_5XX_METER.getOneMinuteRate());

        LOGGER.info("response time (medn): {}us", SUCCESSFUL_REQUEST_TIMER.getSnapshot().getMedian() * factor);
        LOGGER.info("response time (99pt): {}us", SUCCESSFUL_REQUEST_TIMER.getSnapshot().get99thPercentile() * factor);
    }
}
