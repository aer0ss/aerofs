package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.lib.StreamKey;
import com.aerofs.ids.DID;

import java.io.InputStream;

public interface ResponseStream {
    DID did();
    Endpoint ep();
    InputStream is();
    StreamKey streamKey();
}
