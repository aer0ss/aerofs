/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.ids.DID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

final class TransportDigestStreamReceiver
{
    private static final Logger l = LoggerFactory.getLogger(TransportDigestStreamReceiver.class);

    private final Semaphore receivedStreamSemaphore = new Semaphore(0);
    private final AtomicReference<byte[]> receivedBytesDigest = new AtomicReference<byte[]>(null);
    private final Thread streamReceiverThread;

    public TransportDigestStreamReceiver(final DID sourcedid, final TransportInputStream inputStream, final int expectedIncomingByteCount, final String digestType)
    {
        streamReceiverThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("create stream receiver from {}", sourcedid);

                DigestInputStream digestInputStream = null;
                try {
                    digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance(digestType));

                    int bytesRead = digestInputStream.read(new byte[expectedIncomingByteCount]);
                    assertThat(bytesRead, equalTo(expectedIncomingByteCount));

                    receivedBytesDigest.set(digestInputStream.getMessageDigest().digest());
                    l.info(">>> complete reading stream <- {} digest:{}", sourcedid, receivedBytesDigest.get());

                    receivedStreamSemaphore.release();
                } catch (Exception e) {
                    l.warn("fail receive stream", e);
                } finally {
                    try {
                        if (digestInputStream != null) digestInputStream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        });

        streamReceiverThread.setDaemon(false);
    }

    public void start()
    {
        streamReceiverThread.start();
    }

    byte[] getDigest(int timeoutInMs)
        throws Exception
    {
        receivedStreamSemaphore.tryAcquire(1, timeoutInMs, TimeUnit.MILLISECONDS);
        return receivedBytesDigest.get();
    }

    public void stop()
    {
        try {
            streamReceiverThread.interrupt();
            streamReceiverThread.join();
        } catch (InterruptedException e) {
            l.warn("interrupted during stop", e);
        }
    }
}
