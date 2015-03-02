/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.ids.DID;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.lib.OutArg;
import com.google.common.base.Charsets;
import org.junit.Test;

import java.io.IOException;

import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.decodeBody;
import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.encodeBody;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestXMPPUtilities
{
    static
    {
        LoggerSetup.init();
    }

    private static final DID DID_0 = DID.generate();
    private static final byte[] BYTES_TO_ENCODE = "HELLO".getBytes(Charsets.US_ASCII);

    private final OutArg<Integer> outgoingWireLength = new OutArg<Integer>(-1);
    private final OutArg<Integer> incomingWireLength = new OutArg<Integer>(-1);
    private final MaxcastFilterReceiver maxcastFilterReceiver = new MaxcastFilterReceiver();

    @Test
    public void shouldBeAbleToEncodeAndDecodeUnfilteredMulticastPacket()
            throws IOException
    {
        byte[] decoded = decodeBody(DID_0, incomingWireLength, encodeBody(outgoingWireLength, XMPPUtilities.MAXCAST_UNFILTERED, BYTES_TO_ENCODE), maxcastFilterReceiver);
        assertThat(decoded, equalTo(BYTES_TO_ENCODE));
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeMulticastPacketWithMulticastIDSet()
            throws IOException
    {
        byte[] decoded = decodeBody(DID_0, incomingWireLength, encodeBody(outgoingWireLength, 20981, BYTES_TO_ENCODE), maxcastFilterReceiver);
        assertThat(decoded, equalTo(BYTES_TO_ENCODE));
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeMulticastPacketIfMaxcastFilterReceiverIsNull()
            throws IOException
    {
        byte[] decoded = decodeBody(DID_0, incomingWireLength, encodeBody(outgoingWireLength, 20981, BYTES_TO_ENCODE), null);
        assertThat(decoded, equalTo(BYTES_TO_ENCODE));
    }
}
