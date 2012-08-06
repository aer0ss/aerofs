/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.lib.id.DID;

import static com.aerofs.daemon.transport.lib.PulseManager.PulseToken;

/**
 * This interface has to be implemented by <strong>any</strong> event related to
 * pulsing
 */
public interface IPulseEvent extends IEvent
{
    public DID did();

    /**
     * @return the current pulse sequence id this event is scheduled for
     */
    public PulseToken tok_();

    /**
     * @param tok set the pulse sequence id this event is scheduled for
     */
    public void tok_(PulseToken tok);
}
