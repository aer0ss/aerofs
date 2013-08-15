/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.IPulseEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import org.slf4j.Logger;

import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.MakePulseResult;
import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.makepulse_;
import static com.aerofs.daemon.transport.lib.PulseManager.PulseToken;
import static com.aerofs.daemon.transport.lib.TPUtil.newControl;

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

        l.info("d:" + did + " hd pulse");

        // basic pre-pulse checks

        if (!ph.prepulsechecks_(ev)) return;

        // send a pulse

        PulseToken prevtok = ev.tok_();

        try {
            MakePulseResult ret = makepulse_(l, pm, did, prevtok);
            if (ret == null) {
                l.warn("d:" + did + " prevtok:" + printtok(prevtok) + " no ret");
                return;
            }

            ev.tok_(ret.tok());
            l.info("d:" + did + " prevtok:" + printtok(prevtok) + " tok:" + ret.tok() + " send pulse");

            uc.send(did, null, Prio.HI, newControl(ret.hdr()), null);
        } catch (ExDeviceOffline e) {
            pm.delInProgressPulse(did);
            l.info("d:" + did + " offline - term pulse");
            return;
        } catch (Exception e) {
            l.info("d:" + did + " err:" + e + " pulse resched");
        }

        // schedule the next pulse

        ph.schednextpulse_(ev);
    }

    private static String printtok(PulseToken prevtok)
    {
        return (prevtok == null ? "null" : prevtok.toString());
    }
}
