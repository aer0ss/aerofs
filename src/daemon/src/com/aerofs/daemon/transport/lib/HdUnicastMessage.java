package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.lib.Profiler;
import org.jivesoftware.smack.XMPPException;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDeviceOffline;
import org.slf4j.Logger;

public class HdUnicastMessage implements IEventHandler<EOUnicastMessage>
{
    private static final Logger l = Loggers.getLogger(HdUnicastMessage.class);

    private final IUnicast _ucast;

    public HdUnicastMessage(ITransportImpl tp)
    {
        _ucast = tp.ucast();
    }

    @Override
    public void handle_(EOUnicastMessage ev, Prio prio)
    {
        try {
            byte[][] payload = TPUtil.newPayload(null, 0, ev.byteArray());
            _ucast.send(ev._to, new ProfiledWaiter(), prio, payload, null);
        } catch (Exception e) {
            l.warn("uc " + ev._to + ": " + Util.e(e, ExDeviceOffline.class, XMPPException.class));
        }
    }

    /**
     * An IResultWaiter that starts a timer and stops it when IResultWaiter.okay() is called.
     */
    private static class ProfiledWaiter implements IResultWaiter
    {
        private final Profiler _profiler;

        public ProfiledWaiter()
        {
            _profiler = new Profiler(EOUnicastMessage.class.getSimpleName());
            _profiler.start();
        }

        @Override
        public void okay()
        {
            _profiler.stop();
        }

        @Override
        public void error(Exception e)
        {
            _profiler.reset();
        }
    }

}
