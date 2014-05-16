/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.lib.log.LogUtil;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A DevicePresenceListener that attempts to precreate a unicast channel
 * when we receive multicast notification of a device online.
 *
 * This does nothing for device-unavailable.
 */
public class ChannelPreallocator implements IDevicePresenceListener
{
    private static Logger l = LoggerFactory.getLogger(ChannelPreallocator.class);
    private final IDevicePresenceService presence;
    private final ChannelDirectory directory;
    private final Timer connectTimer;

    public ChannelPreallocator(
            IDevicePresenceService presence,
            ChannelDirectory directory,
            Timer timer)
    {
        this.presence = presence;
        this.directory = directory;
        this.connectTimer = timer;
    }

    /**
     * Opportunistically create a unicast connection for the given DID.
     * Connect errors will be logged but do not propagate up to the caller.
     *
     * @param did {@link DID} of the remote device whose presence has changed
     * @param isPotentiallyAvailable true if a connection may be established to this device, false otherwise
     */
    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (!isPotentiallyAvailable) { return; }
        try {
            connectNewChannel(0, did);
        } catch (ExTransportUnavailable etu) {
            l.info("{}: prealloc failed", did, LogUtil.suppress(etu));
        } catch (ExDeviceUnavailable edu) {
            l.info("{}: prealloc failed", did, LogUtil.suppress(edu));
        }
    }

    private void scheduleConnect(final int iters, final DID did)
    {
        connectTimer.newTimeout(new TimerTask()
        {
            @Override
            public void run(Timeout timeout)
                    throws Exception
            {
                connectNewChannel(iters, did);
            }
        }, 30, TimeUnit.SECONDS); // FIXME: generalize delay calculator
    }

    private void connectNewChannel(final int iters, final DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        if (presence.isPotentiallyAvailable(did)) {

            directory.chooseActiveChannel(did).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture)
                                throws Exception
                        {
                            if (channelFuture.isSuccess()) {
                                l.debug("{} cc:online", did);
                            } else {
                                l.debug("{} cc:reconn", did);
                                scheduleConnect(iters + 1, did);
                            }
                        }
                    }
            );
        } else {
            l.info("{} cc:offline, stopping allocator", did);
        }
    }
}
