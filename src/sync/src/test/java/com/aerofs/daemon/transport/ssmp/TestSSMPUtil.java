/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.ssmp;

import com.aerofs.ids.DID;
import com.aerofs.testlib.LoggerSetup;
import com.google.common.base.Charsets;
import org.junit.Test;

import java.io.IOException;

import static com.aerofs.daemon.transport.ssmp.SSMPUtil.decodeMcastPayload;
import static com.aerofs.daemon.transport.ssmp.SSMPUtil.encodeMcastPayload;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestSSMPUtil
{
    static
    {
        LoggerSetup.init();
    }

    private static final DID DID_0 = DID.generate();
    private static final byte[] BYTES_TO_ENCODE = "HELLO".getBytes(Charsets.US_ASCII);

    @Test
    public void shouldBeAbleToEncodeAndDecodeMulticastPacket()
            throws IOException
    {
        byte[] decoded = decodeMcastPayload(DID_0, encodeMcastPayload(BYTES_TO_ENCODE));
        assertThat(decoded, equalTo(BYTES_TO_ENCODE));
    }
}
