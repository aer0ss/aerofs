package com.aerofs.daemon.transport.ssmp;

import com.aerofs.daemon.transport.ISignallingService;
import com.aerofs.daemon.transport.ISignallingServiceListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPEvent;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.aerofs.ssmp.EventHandler;
import com.aerofs.ssmp.SSMPRequest;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.google.common.util.concurrent.Futures.addCallback;

class SignallingService implements ISignallingService, EventHandler {

    private final String _prefix;
    private final SSMPConnection _c;
    private final Base64.Encoder _encoder = Base64.getEncoder();
    private final Base64.Decoder _decoder = Base64.getDecoder();

    private ISignallingServiceListener _listener;

    SignallingService(String id, SSMPConnection c) {
        _c = c;
        _prefix = "sig:" + id + " ";
        _c.addUcastHandler("sig:" + id, this);
    }

    @Override
    public void registerSignallingClient(ISignallingServiceListener client) {
        _listener = client;
    }

    @Override
    public void sendSignallingMessage(DID did, byte[] msg, ISignallingServiceListener client) {
        try {
            addCallback(_c.request(SSMPRequest.ucast(
                            SSMPIdentifier.fromInternal(did.toStringFormal()),
                            _prefix + _encoder.encodeToString(msg))),
                    new FutureCallback<SSMPResponse>() {
                        @Override
                        public void onSuccess(SSMPResponse r) {
                            if (r.code != SSMPResponse.OK) {
                                _listener.sendSignallingMessageFailed(did, msg, new IOException(
                                        "UCAST to " + did + " failed with code " + r.code
                                ));
                            }
                        }
                        @Override
                        public void onFailure(Throwable t) {
                            if (t instanceof Exception) {
                                _listener.sendSignallingMessageFailed(did, msg, (Exception) t);
                            } else {
                                Throwables.propagate(t);
                            }
                        }
                    });
        } catch (Exception e) {
            _listener.sendSignallingMessageFailed(did, msg, e);
        }
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        if (ev.type != Type.UCAST) return;

        try {
            DID did = new DID(ev.from.toString());
            String payload = new String(ev.payload, StandardCharsets.UTF_8);
            if (payload.startsWith(_prefix)) {
                _listener.processIncomingSignallingMessage(did,
                        _decoder.decode(payload.substring(_prefix.length())));
            }
        } catch (ExInvalidID ex) {}
    }
}
