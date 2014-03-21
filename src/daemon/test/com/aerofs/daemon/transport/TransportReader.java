/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public final class TransportReader
{
    static
    {
        TransportLoggerSetup.init();
    }

    private static final Logger l = LoggerFactory.getLogger(TransportReader.class);

    private final String readerName;
    private final BlockingPrioQueue<IEvent> outgoingEventSink;
    private final TransportListener transportListener;

    private Thread outgoingEventQueueReader;
    private volatile boolean running;

    public TransportReader(String readerName, BlockingPrioQueue<IEvent> outgoingEventSink, TransportListener transportListener)
    {
        this.readerName = readerName;
        this.outgoingEventSink = outgoingEventSink;
        this.transportListener = transportListener;
    }

    public synchronized void start()
    {
        running = true;
        createAndStartIncomingTransportEventReader();
    }

    public synchronized void stop()
    {
        try {
            running = false;
            outgoingEventQueueReader.interrupt();
            outgoingEventQueueReader.join();
        } catch (InterruptedException e) {
            throw new IllegalStateException("fail stop transport reader cause:" + Throwables.getStackTraceAsString(e));
        }
    }

    private void createAndStartIncomingTransportEventReader()
    {
        outgoingEventQueueReader = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (running) {
                    try {
                        OutArg<Prio> outArg = new OutArg<Prio>(null);
                        IEvent event = outgoingEventSink.tryDequeue(outArg); // FIXME (AG): _have_ to use tryDequeue because dequeue is uninterruptible (WTF)
                        if (event == null) {
                            continue;
                        }

                        l.trace("handling event type:{}", event.getClass().getSimpleName());

                        if (event instanceof EIPresence) {
                            handlePresence((EIPresence)event);
                        } else if (event instanceof EIUnicastMessage) {
                            handleUnicastMessage((EIUnicastMessage)event);
                        } else if (event instanceof EIStreamBegun) {
                            handleNewIncomingStream((EIStreamBegun)event);
                        } else if (event instanceof EIChunk) {
                            handleIncomingStreamChunk((EIChunk) event);
                        } else {
                            l.warn("ignore transport event of type:{}", event.getClass().getSimpleName());
                        }
                    } catch (Throwable t) {
                        if (Thread.interrupted()) {
                            l.warn("thread interrupted - checking if reader stopped");
                            checkState(!running, "thread interrupted, but stop() was not called");
                            break;
                        } else {
                            throw new IllegalStateException("incoming transport event reader caught err", t);
                        }
                    }
                }

            }
        });

        outgoingEventQueueReader.setName(readerName);
        outgoingEventQueueReader.start();
    }

    private void handlePresence(EIPresence presence)
    {
        for (Map.Entry<DID, Collection<SID>> entry : presence._did2sids.entrySet()) {
            if (presence._online) {
                transportListener.onDeviceAvailable(entry.getKey(), entry.getValue());
            } else {
                transportListener.onDeviceUnavailable(entry.getKey(), entry.getValue());
            }
        }
    }

    private void handleUnicastMessage(EIUnicastMessage event)
            throws IOException
    {
        int numBytes = event.is().available();
        byte[] packet = new byte[numBytes];

        // noinspection ResultOfMethodCallIgnored
        event.is().read(packet);

        transportListener.onIncomingPacket(event._ep.did(), event._userID, packet);
    }

    private void handleNewIncomingStream(EIStreamBegun event)
    {
        transportListener.onNewStream(event._ep.did(), event._streamId);
        transportListener.onIncomingStreamChunk(event._ep.did(), event._streamId, event.is());
    }

    private void handleIncomingStreamChunk(EIChunk event)
    {
        transportListener.onIncomingStreamChunk(event._ep.did(), event._streamId, event.is());
    }
}
