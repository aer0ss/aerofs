/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.IPulseEvent;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransport;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.lib.event.Prio;
import org.slf4j.Logger;

import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.MakePulseResult;
import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.makepulse_;
import static com.aerofs.daemon.transport.lib.PulseManager.PulseToken;

/**
 * Event handler that handles all {@link IPulseEvent} events. This includes events
 * triggered by an incoming {@link com.aerofs.daemon.event.net.EOStartPulse} from the
 * core (i.e. {@link com.aerofs.daemon.event.net.EOTpStartPulse}), and those
 * scheduled by an {@link com.aerofs.daemon.transport.ITransport}.
 * This handler defines a basic recipe and checks for pulsing, leaving it up to
 * implementation classes to define the specifics.
 * <br></br>
 * The recipe operates in the following stages:
 * <ol>
 *     <li>Basic validity checks</li>
 *     <li>Pre-pulse checks (implemented by derived classes)</li>
 *     <li>Construct the pulse message</li>
 *     <li>Set the pulse sequence id for this event</li>
 *     <li>Send the pulse message</li>
 *     <li>Schedule the next pulse (implemented by derived classes)</li>
 * </ol>
 */
public class HdPulse<T extends IPulseEvent> implements IEventHandler<T>
{
    private static final Logger l = Loggers.getLogger(HdPulse.class);

    private final PulseManager pm;
    private final IUnicast uc;
    private final IPulseHandler<T> ph;

    /**
     * Constructor
     *
     * @param ph Implementation class that actually implements the missing pieces
     * of the pulse-checking recipe
     */
    public HdPulse(PulseManager pm, IUnicast uc, IPulseHandler<T> ph)
    {
        this.pm = pm;
        this.uc = uc;
        this.ph = ph;
    }

    @Override
    public void handle_(T ev, Prio prio)
    {
        DID did = ev.did();

        l.trace("{} hd pulse", did);

        if (!ph.prepulsechecks_(ev)) return;

        // send a pulse

        PulseToken prevtok = ev.tok_();

        try {
            MakePulseResult ret = makepulse_(l, pm, did, prevtok);
            if (ret == null) {
                l.error("{} prevtok:{} no ret", did, printtok(prevtok));
                return;
            }

            ev.tok_(ret.tok());
            l.trace("{} prevtok:{} tok:{} send pulse", did, printtok(prevtok), ret.tok());

            uc.send(did, null, Prio.HI, TransportProtocolUtil.newControl(ret.hdr()), null);

            ph.schednextpulse_(ev);
        } catch (ExTransportUnavailable e) {
            stopPulse(did, e);
        } catch (ExDeviceUnavailable e) {
            stopPulse(did, e);
        }
    }

    private void stopPulse(DID did, ExTransport e)
    {
        pm.delInProgressPulse(did);
        l.info("{} stop in-progress pulse", did, e);
    }

    private static String printtok(PulseToken prevtok)
    {
        return (prevtok == null ? "null" : prevtok.toString());
    }
}
