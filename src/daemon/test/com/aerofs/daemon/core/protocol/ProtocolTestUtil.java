/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBCore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.aerofs.lib.Util.rand;
import static org.mockito.Mockito.mock;

public class ProtocolTestUtil
{
    public static SOCKID newSOCKID(SIndex sidx)
    {
        return rand().nextBoolean() ? newMetadataSOCKID(sidx) : newContentSOCKID(sidx);
    }

    public static SOCKID newMetadataSOCKID(SIndex sidx)
    {
        return new SOCKID(sidx, OID.generate(), CID.META, KIndex.MASTER);
    }

    public static SOCKID newContentSOCKID(SIndex sidx)
    {
        // kindex must be >= 0
        KIndex kidx = new KIndex(Math.abs(rand().nextInt()));
        return new SOCKID(sidx, OID.generate(), CID.CONTENT , kidx);
    }

    public static UserID newUser()
    {
        return UserID.fromInternal(Long.toString(rand().nextLong()));
    }

    public static DigestedMessage newDigestedMessage(UserID sender, ByteArrayOutputStream os)
            throws IOException
    {
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        PBCore pb = PBCore.parseDelimitedFrom(is);

        return new DigestedMessage(pb, is, new Endpoint(mock(ITransport.class), DID.generate()),
                sender, null);
    }

    public static DigestedMessage newDigestedMessage(UserID sender, PBCore pb)
            throws IOException
    {
        return newDigestedMessage(sender, Util.writeDelimited(pb));
    }
}
