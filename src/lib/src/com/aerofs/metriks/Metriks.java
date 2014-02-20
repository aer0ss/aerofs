/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.metriks;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * An implementation of {@code IMetriks} that sends
 * json-serialized metrics to a triks server.
 */
public final class Metriks implements IMetriks
{
    private static final int SOCKET_TIMEOUT = (int) (5 * C.SEC);
    private static final long SLEEP_TIME_ON_FAILURE = 10 * C.SEC;

    //--------------------------------------------------------------------------------

    /**
     * Key-value map of metrics that will be serialized and sent to triks.
     */
    private class Metrik implements IMetrik
    {
        private final Map<String, Object> _fields = Maps.newHashMap();
        private final Gson _gson;
        private final Metriks _metriks;

        public Metrik(String type, UserID userId, DID did, String os, Gson gson, Metriks metriks)
        {
            _fields.put("metric_type", type);
            _fields.put("user_id", userId.getString());
            _fields.put("did", did.toStringFormal());
            _fields.put("os", os);
            _gson = gson;
            _metriks = metriks;
        }

        @Override
        public Metrik addField(String fieldName, Object fieldValue)
        {
            _fields.put(fieldName, fieldValue);
            return this;
        }

        @Override
        public void send()
        {
            _metriks.send(this);
        }

        String toJson()
        {
            return _gson.toJson(_fields);
        }
    }

    //--------------------------------------------------------------------------------

    private static final Logger l = LoggerFactory.getLogger(Metriks.class);

    private final Gson _gson = new Gson();
    private final ArrayBlockingQueue<Metrik> _sendQueue = Queues.newArrayBlockingQueue(1024);
    private final UserID _localUser;
    private final DID _did;
    private final String _os;
    private final URL _triksUrl;

    private volatile Thread _sendThread;

    /**
     * Constructor.
     *
     * @param os full platform name of the OS on which this client is installed
     * @param triksUrl url (in format http://...) to which metrics should be sent
     */
    public Metriks(UserID localUser, DID did, String os, URL triksUrl)
    {
        _localUser = localUser;
        _did = did;
        _os = os;
        _triksUrl = triksUrl;
    }

    @Override
    public void start()
    {
        _sendThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    l.info("pusher: startin pushing...");

                    // noinspection InfiniteLoopStatement
                    while (true) {
                        try {
                            Metrik metrik = _sendQueue.take();
                            BaseUtil.httpRequest(getTriksConnection(), metrik.toJson()); // FIXME (AG): use elasticsearch _bulk
                        } catch (IOException e) {
                            // IMPORTANT: We drop the metrik that we failed to send
                            // if this is not expected behaviour, we should peek() and only
                            // remove from the queue if the send succeeds.
                            //
                            // realistically, however, it's nbd if not all metriks make it
                            l.warn("pusher: send failed: {}", e);
                            Thread.sleep(SLEEP_TIME_ON_FAILURE);
                        }
                    }
                } catch (InterruptedException e) {
                    l.warn("pusher: interrupted");
                } catch (Throwable t) {
                    l.error("pusher: caught unexpected exception", t);
                } finally {
                    l.info("pusher: dying...");
                }
            }
        });

        _sendThread.setName("pusher");
        _sendThread.setDaemon(true);
        _sendThread.start();
    }

    @Override
    public void stop()
    {
        if (_sendThread != null) {
            _sendThread.interrupt();
        }
    }

    @Override
    public Metrik newMetrik(String topic)
    {
        return new Metrik(topic, _localUser, _did, _os, _gson, this);
    }

    /**
     * Add a {@code Metrik} to be sent to triks.
     * When, and how this object is sent is implementation-dependent.
     *
     * @param metrik the {@code Metrik} instance to send
     * @return false if the object <em>cannot</em> be sent. true if the object <em>may</em> be sent
     */
    private boolean send(Metrik metrik)
    {
        return _sendQueue.offer(metrik);
    }

    private HttpURLConnection getTriksConnection()
            throws IOException
    {
        URL url = new URL(_triksUrl.toExternalForm());

        HttpURLConnection triksConnection = (HttpURLConnection) url.openConnection();
        triksConnection.setUseCaches(false);
        triksConnection.setConnectTimeout(SOCKET_TIMEOUT);
        triksConnection.setReadTimeout(SOCKET_TIMEOUT);
        triksConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
        triksConnection.setDoOutput(true);
        triksConnection.connect();

        return triksConnection;
    }
}
