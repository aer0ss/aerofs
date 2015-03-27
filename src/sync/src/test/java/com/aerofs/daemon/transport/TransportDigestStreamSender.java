/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.ids.DID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class TransportDigestStreamSender
{
    private static final Logger l = LoggerFactory.getLogger(TransportDigestStreamSender.class);

    private final Semaphore streamSentSemaphore = new Semaphore(0);
    private final AtomicReference<byte[]> sentBytesDigest = new AtomicReference<>(null);
    private final Thread streamSenderThread;

    public TransportDigestStreamSender(final TransportResource transport, final DID destdid, final String digestType, final byte[] bytes)
    {
        streamSenderThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("create stream sender to {}", destdid);

                DigestOutputStream digestOutputStream = null;
                try {
                    digestOutputStream = new DigestOutputStream(transport.newOutgoingStream(destdid), MessageDigest.getInstance(digestType));
                    digestOutputStream.write(bytes);

                    sentBytesDigest.set(digestOutputStream.getMessageDigest().digest());
                    l.info(">>> complete writing stream -> {} digest:{}", destdid, sentBytesDigest.get());

                    streamSentSemaphore.release();
                } catch (Exception e) {
                    l.warn("fail send stream", e);
                } finally {
                    try {
                        if (digestOutputStream != null) digestOutputStream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        });

        streamSenderThread.setDaemon(false);
    }

    void start()
    {
        streamSenderThread.start();
    }

    byte[] getDigest(int timeoutInMs)
        throws Exception
    {
        streamSentSemaphore.tryAcquire(1, timeoutInMs, TimeUnit.MILLISECONDS);
        return sentBytesDigest.get();
    }

    void stop()
    {
        try {
            streamSenderThread.interrupt();
            streamSenderThread.join();
        } catch (InterruptedException e) {
            l.warn("interrupted during stop", e);
        }
    }
}
