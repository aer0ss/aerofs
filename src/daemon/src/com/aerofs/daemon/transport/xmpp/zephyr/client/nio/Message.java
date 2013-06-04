/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */
package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import java.nio.ByteBuffer;

import static com.aerofs.zephyr.Constants.ZEPHYR_BIND_MSG_LEN;
import static com.aerofs.zephyr.Constants.ZEPHYR_MAGIC;
import static com.aerofs.zephyr.Constants.ZEPHYR_MSG_BYTE_ORDER;

public final class Message
{
    /**
     * Create a Zephyr message to bind a local ZephyrClient with a remote instance
     *
     * @param b        {@link ByteBuffer} to populate with the BIND message
     * @param remoteid <code>Zephyr</code> channel id of the remote <code>ZephyrClient</code>
     * @return <code>b</code> filled in with the BIND message
     */
    public static ByteBuffer createBindMessage_(ByteBuffer b, int remoteid)
    {
        assert b.remaining() > ZEPHYR_BIND_MSG_LEN :
            ("ByteBuffer for bind message too small");

        assert b.order() == ZEPHYR_MSG_BYTE_ORDER :
            ("ByteBuffer has incorrect byte order");

        // write out the full message
        b.put(ZEPHYR_MAGIC);
        b.mark();
        b.putInt(0); // length placeholder

        int payloadpos = b.position();
        b.putInt(remoteid);

        // now write out the actual length
        int endpos = b.position();
        b.reset();
        int len = endpos - payloadpos;
        b.putInt(len);

        // move back to where we were before
        b.position(endpos);

        return b;
    }
}
