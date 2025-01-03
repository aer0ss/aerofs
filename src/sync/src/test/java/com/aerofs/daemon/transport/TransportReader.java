/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.daemon.event.net.EIDevicePresence;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.testlib.LoggerSetup;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;

public final class TransportReader
{
    static
    {
        LoggerSetup.init();
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
        outgoingEventQueueReader = new Thread(() -> {
            while (running) {
                try {
                    OutArg<Prio> outArg = new OutArg<>(null);
                    IEvent event = outgoingEventSink.dequeueInterruptibly(outArg);

                    l.trace("handling event type:{}", event.getClass().getSimpleName());

                    if (event instanceof EIStoreAvailability) {
                        handleStoreAvailability((EIStoreAvailability) event);
                    } else if (event instanceof EIDevicePresence) {
                            handleDevicePresence((EIDevicePresence)event);
                    } else if (event instanceof EIUnicastMessage) {
                        handleUnicastMessage((EIUnicastMessage)event);
                    } else if (event instanceof EIStreamBegun) {
                        handleNewIncomingStream((EIStreamBegun)event);
                    } else {
                        l.warn("ignore transport event of type:{}", event.getClass().getSimpleName());
                    }
                } catch (InterruptedException t) {
                    l.warn("thread interrupted - checking if reader stopped");
                    checkState(!running, "thread interrupted, but stop() was not called");
                    break;
                }
            }

        });

        outgoingEventQueueReader.setName(readerName);
        outgoingEventQueueReader.start();
    }

    private void handleStoreAvailability(EIStoreAvailability ev)
    {
        if (ev._join) {
            transportListener.onStoreAvailableForDevice(ev._did, ev._sid);
        } else {
            transportListener.onStoreUnavailableForDevice(ev._did, ev._sid);
        }
    }

    private void handleDevicePresence(EIDevicePresence ev)
    {
        if (ev._online) {
            transportListener.onDeviceOnline(ev._did, ev._tp);
        } else {
            transportListener.onDeviceOffline(ev._did, ev._tp);
        }
    }

    private void handleUnicastMessage(EIUnicastMessage event)
    {
        try {
            int numBytes = event.is().available();
            byte[] packet = new byte[numBytes];

            // noinspection ResultOfMethodCallIgnored
            event.is().read(packet);

            transportListener.onIncomingPacket(event._ep.did(), event._userID, packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleNewIncomingStream(EIStreamBegun event)
    {
        transportListener.onNewStream(event._ep.did(), event._streamId, event.is());
    }
}
