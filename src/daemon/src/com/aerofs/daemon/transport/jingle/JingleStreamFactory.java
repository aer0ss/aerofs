/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.jingle.JingleStream.IJingleStreamListener;
import com.aerofs.j.StreamInterface;

public class JingleStreamFactory
{
    JingleStream create(DID did, StreamInterface streamInterface, boolean incoming, IJingleStreamListener listener)
    {
        JingleStream stream = new JingleStream(did, streamInterface, incoming, listener);
        //JingleStream.EventSlot eventSlot = new JingleStream.EventSlot(stream);
        return stream;
    }
}